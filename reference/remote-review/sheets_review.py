"""Google Sheets transport. The Dev Panel owns this worker's lifetime."""
import re
import json
import urllib.request
import urllib.parse
import pc_video

class GoogleResponseRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        target=urllib.parse.urlparse(newurl)
        if target.scheme!='https' or target.netloc!='script.googleusercontent.com':
            raise ValueError('Unexpected Google bridge redirect')
        # Google's ContentService redirects to a GET result. Never forward the key.
        if code not in (301,302,303):raise ValueError('Unsafe Google bridge redirect')
        return urllib.request.Request(newurl,headers={'Accept':'application/json'})

def publish_video(worker, item):
    return pc_video.video_url(worker,item)

def request(config, payload):
    url=config.get('sheets_url','')
    parsed=urllib.parse.urlparse(url)
    if parsed.scheme!='https' or parsed.netloc!='script.google.com' or not re.fullmatch(r'/macros/s/[A-Za-z0-9_-]+/exec',parsed.path) or parsed.query or parsed.fragment:
        raise ValueError('Connect the deployed Google Sheets bridge in the Dev Panel first')
    if not config.get('sheets_token'):raise ValueError('Google Sheets machine key is missing')
    body=json.dumps(dict(payload,token=config['sheets_token'])).encode()
    req=urllib.request.Request(url,data=body,headers={'Content-Type':'application/json'})
    with urllib.request.build_opener(GoogleResponseRedirect()).open(req,timeout=120) as response:
        if 'application/json' not in response.headers.get('Content-Type',''):raise ValueError('Google Sheets bridge needs authorization or deployment')
        result=json.load(response)
    if not result.get('ok'):raise RuntimeError(result.get('error','Google Sheets request failed'))
    return result

def sync(worker,max_outputs=None):
    config=worker.read(worker.STATE/'sheets-connection.json',{})
    url_file=worker.STATE/'sheet-bridge.url'
    if url_file.exists():config['sheets_url']=url_file.read_text(encoding='utf-8-sig').strip()
    snapshot=request(config,{'action':'snapshot'})
    if not isinstance(snapshot.get('rows'),list):raise ValueError('Invalid Google Sheets snapshot')
    worker.save(worker.STATE/'sheets-snapshot.json',snapshot)
    known={row['id']:row for row in snapshot.get('entries',[])}
    base=pc_video.base_url(worker)
    migrated=snapshot.get('video_host')!=base or snapshot.get('video_host_configured') is False
    if migrated:request(config,{'action':'configure_video_host','base_url':base})
    items=sorted(worker.read(worker.STATE/'items.json',[]),key=lambda i:int(i['id'][2:]))
    acknowledged=worker.read(worker.STATE/'sheets-acknowledged.json',{})
    published=worker.read(worker.STATE/'sheets-published.json',{})
    # The authenticated migration rewrites only the existing video column in one
    # batch. Preserve its validated ready-state cache instead of rewriting every
    # other cell for hundreds of already-published clips.
    if migrated and base:
        for identity,value in published.items():
            if identity in known and value.get('state') in {'ready','waiting_for_pc'}:value.update(state='ready',host=base)
        worker.save(worker.STATE/'sheets-published.json',published)
    failures=[{'source':'compile_failure',**{k:i[k] for k in ('id','round','digest','rating','result')}} for i in items if i.get('rating_source')=='compile_failure' and (i['id'] not in acknowledged or i['id'] in known)]
    for start in range(0,len(failures),100):
        result=request(config,{'action':'failures','outputs':failures[start:start+100]})
        for identity in result.get('completed',[]):acknowledged[identity]={'automatic':True}
        worker.save(worker.STATE/'sheets-acknowledged.json',acknowledged)
    for item in items:
        remote=known.get(item['id'])
        if remote and (remote['round']!=item['round'] or remote['digest']!=item['digest']):raise ValueError('Google Sheets identity conflict: '+item['id'])
        if item.get('rating_source')=='compile_failure':continue
        if item['state'] in {'rated','learning','complete'} and 'rating' in item and (remote or item.get('rating_source') in {'sheets','compile_failure'}) and (item['id'] not in acknowledged or remote):
            automatic=item.get('rating_source')=='compile_failure'
            result=request(config,{'action':'failure' if automatic else 'complete',**{k:item[k] for k in ('id','round','digest','rating')},'source':item.get('rating_source',''),'note':item.get('note',''),'result':item.get('result','Rating saved; training pending'),'imported':item.get('rating_source')!='sheets'})
            acknowledged[item['id']]=result;worker.save(worker.STATE/'sheets-acknowledged.json',acknowledged)
    outputs=[]
    for item in items:
        if max_outputs is not None and len(outputs)>=max_outputs:break
        if 'rating' in item or item['state'] not in {'ready','rendering','error'}:continue
        state='waiting_for_pc' if item['state']=='ready' and not base else item['state']
        if item['id'] in known and published.get(item['id'],{})=={'state':state,'host':base}:continue
        output={k:item.get(k,'') for k in ('id','round','digest','name','state')}
        output['state']=state
        if state=='ready':
            p=item['preview']
            if not p['complete'] or p['truncated']:raise ValueError('Incomplete animation cannot be served')
            output.update(seconds=item['seconds'],frames=p['frame_count'],fps=p['fps'],complete=True,truncated=False,video_url=publish_video(worker,item))
        elif state=='waiting_for_pc':output['seconds']=item['seconds']
        elif item['state']=='error':output['error']=item.get('error','')[-300:]
        outputs.append(output)
    for start in range(0,len(outputs),100):
        batch=outputs[start:start+100]
        request(config,{'action':'publish','outputs':batch})
        for output in batch:published[output['id']]={'state':output['state'],'host':base}
        worker.save(worker.STATE/'sheets-published.json',published)
    stage=worker.read(worker.STATE/'status.json',{}).get('stage','waiting')
    request(config,{'action':'heartbeat','stage':stage,'generation':worker.generation_stats(items,worker.generation_timings(items[-25:]))})
    worker.save(worker.STATE/'sync-status.json',{'updated':worker.now(),'ok':True,'provider':'sheets','remote_ratings':len(snapshot['rows'])})
