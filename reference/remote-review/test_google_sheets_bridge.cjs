const fs=require('node:fs'),vm=require('node:vm'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const token='test-only-key',hash=crypto.createHash('sha256').update(token).digest('hex');
const source=fs.readFileSync(__dirname+'/google_sheets_bridge.gs','utf8').replace('__TOKEN_SHA256__',hash);
function fixture(rating=0){
  const events=[],historyRows=[['ID','Round','Digest','Rating','Note','Result','At','Video']];
  let rows=[['link',rating,'W-0001 · Test',2,'','waiting','round-one','a'.repeat(64)]];
  const sheet={getLastRow:()=>rows.length+5,getRange:(row,col,n=1,width=1)=>row==='A4'?{setValue(){}}:({getValues:()=>rows.map(v=>v.slice(col-1,col-1+width)),getFormulas:()=>rows.map(v=>v.slice(col-1,col-1+width).map(x=>typeof x==='string'&&x.startsWith('=')?x:'')),setValues:values=>values.forEach((v,i)=>v.forEach((x,j)=>rows[i][col-1+j]=x))}),deleteRow:()=>{events.push('delete');rows=[];},deleteRows:()=>{events.push('delete');rows=[];}};
  const history={getLastRow:()=>historyRows.length,getMaxRows:()=>1000,getRange:()=>({getValues:()=>historyRows.slice(1),setValues:values=>{events.push('archive');historyRows.push(...values);}}),appendRow:row=>{events.push('archive');historyRows.push(row);}};
  const sandbox={ContentService:{MimeType:{JSON:'json'},createTextOutput:text=>({setMimeType:()=>JSON.parse(text)})},Utilities:{DigestAlgorithm:{SHA_256:1},Charset:{UTF_8:1},computeDigest:(_a,text)=>[...crypto.createHash('sha256').update(text).digest()]},LockService:{getScriptLock:()=>({waitLock(){},hasLock:()=>true,releaseLock(){}})},SpreadsheetApp:{openById:()=>{events.push('open');return {getSheetByName:name=>name==='Review'?sheet:history};},flush:()=>events.push('flush')}};
  const properties={};sandbox.PropertiesService={getScriptProperties:()=>({getProperty:key=>properties[key]??null,setProperty:(key,value)=>properties[key]=value})};
  vm.createContext(sandbox);vm.runInContext(source,sandbox);
  return {events,historyRows,rows:()=>rows,call:p=>sandbox.doPost({postData:{contents:JSON.stringify({token,...p})}})};
}
const identity={id:'W-0001',round:'round-one',digest:'a'.repeat(64)};
{
  const f=fixture('');const payload={action:'failures',outputs:[{...identity,rating:-3,source:'compile_failure',result:'Native compilation failed'}]};
  assert.equal(f.call(payload).ok,true);assert.equal(f.historyRows[1][3],-3);
  assert.deepEqual(f.events.slice(-3),['archive','flush','delete']);
  assert.equal(f.call(payload).ok,true);assert.equal(f.historyRows.length,2);
  const marked=fixture(0);assert.equal(marked.call(payload).completed.length,0);assert.equal(marked.historyRows.length,1);
}
{
  const f=fixture('null');assert.equal(f.call({action:'snapshot'}).rows[0].rating,0);
  assert.equal(f.call({action:'complete',...identity,rating:0,note:''}).ok,true);
  assert.equal(f.historyRows.length,2);
}
{
  const f=fixture();assert.equal(f.call({token:'wrong',action:'snapshot'}).ok,false);assert.deepEqual(f.events,[]);
}
{
  const f=fixture('');assert.equal(f.call({action:'snapshot'}).rows.length,0);assert.equal(f.call({action:'complete',...identity,rating:0}).ok,false);assert.equal(f.historyRows.length,1);
}
{
  const f=fixture(0);assert.equal(f.call({action:'snapshot'}).rows[0].rating,0);
  const complete={action:'complete',...identity,rating:0,note:'',result:'Skipped training (0)'};
  assert.equal(f.call(complete).ok,true);assert.deepEqual(f.events.slice(-3),['archive','flush','delete']);
  assert.equal(f.call(complete).ok,true);assert.equal(f.historyRows.length,2);
}
{
  const f=fixture(2);assert.equal(f.call({action:'complete',...identity,rating:1,note:''}).ok,false);assert.equal(f.historyRows.length,1);assert.ok(!f.events.includes('delete'));
}
{
  const f=fixture(1);assert.equal(f.call({action:'complete',...identity,digest:'b'.repeat(64),rating:1}).ok,false);assert.equal(f.historyRows.length,1);
}
console.log('Google Sheets bridge: 7 authorization, rating, identity and durable-removal checks passed');
for(const base_url of ['https://example.com','https://hoord-videos.test.ts.net.evil.com','http://hoord-videos.test.ts.net','https://user@hoord-videos.test.ts.net','https://hoord-videos.test.ts.net/path']){
  const f=fixture();assert.equal(f.call({action:'configure_video_host',base_url}).ok,false);assert.ok(!f.events.includes('delete'));
}
console.log('Private video host rejects public, credentialed, insecure, and non-root URLs');
{
  const f=fixture(0),rows=f.rows();rows[0][0]='PC video setup pending';rows[0][4]='Keep my note';rows[0][5]='Connect PC videos through Tailscale';
  assert.equal(f.call({action:'configure_video_host',base_url:'https://hoord-videos.test.ts.net'}).ok,true);
  assert.ok(rows[0][0].includes('https://hoord-videos.test.ts.net/api/video/W-0001'));
  assert.equal(rows[0][1],0);assert.equal(rows[0][4],'Keep my note');assert.equal(rows[0][5],'Awaiting your rating');
  assert.equal(f.call({action:'configure_video_host',base_url:'https://hoord-videos.test.ts.net'}).ok,true);
  assert.equal(rows[0][1],0);assert.equal(rows[0][4],'Keep my note');
}
console.log('Host migration preserves human zero and notes, updates readiness, and is idempotent');
