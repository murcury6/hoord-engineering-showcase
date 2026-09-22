import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import test_worker_stub as r
import sheets_review as sheets
import pc_video

VIDEO_HOST='https://hoord-videos.test-tail.ts.net'

class SheetTests(unittest.TestCase):
    def test_all_unmarked_states_are_published_without_inventing_ratings(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(r,'STATE',Path(directory)):
            items=[{'id':f'W-{i:04d}','round':f'round-{i}','digest':'a'*64,'state':'error' if i%2 else 'rendering','name':'Output'} for i in range(1,205)]
            items.append(dict(items[0],id='W-0300',rating=0))
            r.save(r.STATE/'items.json',items)
            calls=[]
            def request(config,payload):
                calls.append(payload);return {'ok':True,'video_host':'','rows':[],'entries':[]}
            with patch.object(sheets,'request',side_effect=request),patch.object(r,'generation_timings',return_value={}):sheets.sync(r)
            batches=[c['outputs'] for c in calls if c['action']=='publish']
            self.assertEqual([len(x) for x in batches],[100,100,4])
            self.assertTrue(all('rating' not in x for batch in batches for x in batch))
    def test_redirect_drops_secret_and_rejects_other_hosts(self):
        handler=sheets.GoogleResponseRedirect()
        req=sheets.urllib.request.Request('https://script.google.com/macros/s/test/exec',data=b'secret')
        safe=handler.redirect_request(req,None,302,'',{},'https://script.googleusercontent.com/macros/echo?result=one')
        self.assertIsNone(safe.data)
        for url in ('https://example.com/result','http://script.googleusercontent.com/result'):
            with self.assertRaises(ValueError):handler.redirect_request(req,None,302,'',{},url)
    def test_pc_video_identity_conflict_does_not_upload(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(r,'STATE',Path(directory)):
            r.save(r.STATE/'video-host.json',{'base_url':VIDEO_HOST})
            path=r.STATE/'previews/round-one/animation.mp4';path.parent.mkdir(parents=True);path.write_bytes(b'video')
            with patch.object(r,'site_request') as network:
                with self.assertRaisesRegex(ValueError,'identity'):sheets.publish_video(r,{'id':'W-0001','round':'round-one','digest':'a'*64,'video':str(path),'preview':{}})
                network.assert_not_called()
    def test_sync_does_not_invent_ratings_and_archives_saved_zero(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(r,'STATE',Path(directory)):
            video=Path(directory)/'animation.mp4';video.write_bytes(b'video')
            item={'id':'W-0027','round':'round-next','digest':'a'*64,'name':'Next','state':'ready','video':str(video),'seconds':2,'preview':{'complete':True,'truncated':False,'frame_count':120,'fps':60}}
            old=dict(item,id='W-0001',round='round-old',state='complete',rating=0,rating_source='sheets')
            r.save(r.STATE/'items.json',[item,old])
            calls=[]
            def request(config,payload):
                calls.append(payload)
                return {'ok':True,'video_host':VIDEO_HOST,'rows':[],'entries':[]}
            with patch.object(pc_video,'base_url',return_value=VIDEO_HOST),patch.object(sheets,'request',side_effect=request),patch.object(sheets,'publish_video',return_value=VIDEO_HOST+'/api/video/W-0027'),patch.object(r,'generation_timings',return_value={}):sheets.sync(r)
            self.assertEqual([c['action'] for c in calls],['snapshot','complete','publish','heartbeat'])
            self.assertEqual(calls[1]['rating'],0)
            output=calls[2]['outputs'][0]
            self.assertNotIn('rating',output)
            self.assertNotIn('video',output)
            self.assertEqual(output['video_url'],VIDEO_HOST+'/api/video/W-0027')
            self.assertEqual(output['seconds'],2)
            self.assertEqual(r.read(r.STATE/'sheets-snapshot.json')['rows'],[])
    def test_bad_identity_never_deletes_or_uploads(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(r,'STATE',Path(directory)):
            r.save(r.STATE/'items.json',[{'id':'W-0001','round':'round-one','digest':'a'*64,'state':'ready'}])
            with patch.object(sheets,'request',return_value={'ok':True,'video_host':'','rows':[],'entries':[{'id':'W-0001','round':'round-other','digest':'a'*64}]}) as request:
                with self.assertRaisesRegex(ValueError,'identity'):sheets.sync(r)
                self.assertEqual(request.call_count,1)
    def test_credentials_are_not_sent_to_unrelated_host(self):
        with patch.object(sheets.urllib.request,'urlopen') as network:
            with self.assertRaises(ValueError):sheets.request({'sheets_url':'https://example.com/exec','sheets_token':'secret'},{'action':'snapshot'})
            network.assert_not_called()

if __name__=='__main__':unittest.main()
