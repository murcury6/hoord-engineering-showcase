// Bound to the existing Hoord ratings spreadsheet. No file sharing is changed.
const SHEET_ID = 'YOUR_SPREADSHEET_ID';
const TOKEN_SHA256 = '__TOKEN_SHA256__';
function json_(value) { return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON); }
function hash_(text) { return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8).map(b => ('0' + ((b + 256) % 256).toString(16)).slice(-2)).join(''); }
function doGet() { return json_({service:'Hoord Google Sheets review',version:5}); }
function videoHost_(){return PropertiesService.getScriptProperties().getProperty('PC_VIDEO_HOST')||'';}
function configureVideoHost_(sheet,base){
  if(typeof base!=='string'||(base!==''&&!/^https:\/\/[a-z0-9-]+\.[a-z0-9-]+\.ts\.net$/.test(base)))throw new Error('Private Tailscale host required');
  const properties=PropertiesService.getScriptProperties();
  if(properties.getProperty('PC_VIDEO_HOST')!==base||properties.getProperty('PC_VIDEO_SCHEMA')!=='5'){
    const count=sheet.getLastRow()-5;
    if(count>0){
      const range=sheet.getRange(6,1,count,1),values=range.getValues(),formulas=range.getFormulas();
      const statusRange=sheet.getRange(6,6,count,1),statuses=statusRange.getValues();
      for(const entry of entries_(sheet)){
        const i=entry.row-6;
        if(formulas[i][0].startsWith('=HYPERLINK(')||values[i][0]==='PC video setup pending'){
          values[i][0]=base?'=HYPERLINK("'+base+'/api/video/'+entry.id+'","Play full animation")':'PC video setup pending';
          statuses[i][0]=base?'Awaiting your rating':'Connect PC videos through Tailscale';
        }
      }
      range.setValues(values);statusRange.setValues(statuses);
    }
    SpreadsheetApp.flush();properties.setProperty('PC_VIDEO_HOST',base);properties.setProperty('PC_VIDEO_SCHEMA','5');
  }
  sheet.getRange('A4').setValue('Videos play from your awake PC. Connect your viewing device to the same Tailscale account. Blank = unmarked; null / 0 dismisses.');
  return {ok:true,video_host:base};
}
function setup() {
  const book = SpreadsheetApp.openById(SHEET_ID), sheet = book.getSheetByName('Review');
  if (!sheet) throw new Error('Review tab missing');
  sheet.getRange('A2').setValue('Start / Stop continuous generation in Dev Panel → 8 Weapon AI → Hitbox AI.');
  sheet.getRange('A3').setValue('Blank = unmarked · null or 0 = remove without training · −2 / −1 = dislike · +1 / +2 = like.');
  sheet.getRange('A4').setValue('New full animations appear below, oldest first. Saved ratings are archived before rows are removed.');
  sheet.getRange('B6:B' + sheet.getMaxRows()).setDataValidation(ratingRule_());
  sheet.setFrozenRows(5); sheet.hideColumns(7,2);
  let history = book.getSheetByName('_HoordHistory');
  if (!history) { history = book.insertSheet('_HoordHistory'); history.appendRow(['ID','Round','Source hash','Rating','Notes','Result','Processed at','Video']); history.hideSheet(); }
  return {ok:true};
}
function entries_(sheet) {
  const n=sheet.getLastRow()-5;if(n<=0)return [];
  const range=sheet.getRange(6,1,n,8),values=range.getValues(),formulas=range.getFormulas();
  return values.map((v,i)=>({row:i+6,id:String(v[2]).match(/^W-\d+/)?.[0],round:String(v[6]),digest:String(v[7]),rating:v[1],note:String(v[4]),video:formulas[i][0]||String(v[0]),formula:!!formulas[i][1],noteFormula:!!formulas[i][4]})).filter(v=>v.id&&v.round);
}
function score_(value) { if(value===''||value===null)return null;if(String(value).trim().toLowerCase()==='null')return 0;if(!['-2','-1','0','1','2'].includes(String(value)))throw new Error('Invalid human rating');return Number(value); }
function ratingRule_(){return SpreadsheetApp.newDataValidation().requireValueInList(['-2','-1','0','1','2','null'],true).setAllowInvalid(false).setHelpText('Blank = unmarked; null or 0 = remove without training.').build();}
function publish_(sheet,history,p){
  identity_(p);
  const matches=entries_(sheet).filter(e=>e.id===p.id||e.round===p.round);
  if(matches.length>1)throw new Error('Duplicate ID');
  const entry=matches[0],old=archived_(history,p.id);
  if(entry&&(entry.id!==p.id||entry.round!==p.round||entry.digest!==p.digest))throw new Error('Identity conflict');
  if(old){if(old[1]!==p.round||old[2]!==p.digest)throw new Error('Archived identity conflict');return;}
  const ready=p.state==='ready';
  if(!['ready','rendering','error','waiting_for_pc'].includes(p.state))throw new Error('Invalid preview state');
  const host=videoHost_(),url=host+'/api/video/'+p.id;
  if(ready&&(!host||!p.complete||p.truncated||!Number.isInteger(p.frames)||p.frames<1||![30,60].includes(p.fps)||!(p.seconds>0&&p.seconds<=600)||Math.abs(p.frames/p.fps-p.seconds)>.0001||p.video_url!==url))throw new Error('Full animation evidence required');
  let row=entry?.row;
  if(!entry){
    row=Math.max(6,sheet.getLastRow()+1);
    if(row>sheet.getMaxRows())sheet.insertRowsAfter(sheet.getMaxRows(),100);
    const later=entries_(sheet).find(e=>Number(e.id.slice(2))>Number(p.id.slice(2)));
    if(later){sheet.insertRowBefore(later.row);row=later.row;}
    sheet.getRange(row,1,1,8).setValues([['','',literal_(p.id+' · '+String(p.name).slice(0,160)),'','','',p.round,p.digest]]);
  }
  // Refresh preview/status only. Never overwrite a human rating or note.
  if(ready)sheet.getRange(row,1).setFormula('=HYPERLINK("'+url+'","Play full animation")');
  else sheet.getRange(row,1).setValue(p.state==='error'?'Preview failed':p.state==='waiting_for_pc'?'PC video setup pending':'Preparing full animation');
  sheet.getRange(row,4).setValue(ready||p.state==='waiting_for_pc'?p.seconds:'').setNumberFormat('0.00');
  sheet.getRange(row,6).setValue(ready?'Awaiting your rating':p.state==='error'?'Preview failed · '+String(p.error||'').slice(0,300):p.state==='waiting_for_pc'?'Connect PC videos through Tailscale':'Awaiting preview; unmarked');
  sheet.getRange(row,2).setDataValidation(ratingRule_());
  sheet.getRange(row,1,1,6).setVerticalAlignment('middle');sheet.setRowHeight(row,42);
}
function identity_(p) { if(!/^W-\d{4,}$/.test(p.id)||!/^round-[\w-]+$/.test(p.round)||! /^[a-f0-9]{64}$/.test(p.digest))throw new Error('Invalid output identity'); }
function literal_(value) { const text=String(value??'');return text.startsWith('=')?"'"+text:text; }
function history_(book) { const h=book.getSheetByName('_HoordHistory');if(!h)throw new Error('Run setup first');return h; }
function failures_(sheet,history,outputs){
  if(!Array.isArray(outputs)||outputs.length>100)throw new Error('Invalid failure batch');
  const entries=entries_(sheet),past=history.getLastRow()<2?[]:history.getRange(2,1,history.getLastRow()-1,8).getValues();
  const archive=[],remove=[],completed=[],seen=new Set();
  for(const p of outputs){
    identity_(p);if(seen.has(p.id)||p.rating!==-3||p.source!=='compile_failure')throw new Error('Invalid failure provenance');seen.add(p.id);
    const found=entries.filter(e=>e.id===p.id||e.round===p.round),old=past.find(e=>e[0]===p.id),entry=found[0];
    if(found.length>1||entry&&(entry.id!==p.id||entry.round!==p.round||entry.digest!==p.digest)||old&&(old[1]!==p.round||old[2]!==p.digest||Number(old[3])!==-3))throw new Error('Failure identity conflict');
    if(entry&&(entry.rating!==''||entry.formula||entry.noteFormula))continue;
    if(!old)archive.push([p.id,p.round,p.digest,-3,'',literal_(p.result),new Date().toISOString(),literal_(entry?.video||'')]);
    if(entry)remove.push(entry.row);completed.push(p.id);
  }
  if(archive.length){const end=history.getLastRow()+archive.length;if(end>history.getMaxRows())history.insertRowsAfter(history.getMaxRows(),end-history.getMaxRows());history.getRange(history.getLastRow()+1,1,archive.length,8).setValues(archive);}
  SpreadsheetApp.flush();
  const sorted=remove.sort((a,b)=>b-a);
  for(let i=0;i<sorted.length;){let end=sorted[i],start=end;i++;while(i<sorted.length&&sorted[i]===start-1){start=sorted[i++];}sheet.deleteRows(start,end-start+1);}
  return {ok:true,completed};
}
function archived_(history,id) { if(history.getLastRow()<2)return null;return history.getRange(2,1,history.getLastRow()-1,8).getValues().find(v=>v[0]===id); }
function doPost(event) {
  let lock;
  try {
    if(!event?.postData?.contents||event.postData.contents.length>100000)throw new Error('Invalid request size');
    const p=JSON.parse(event.postData.contents);
    if(typeof p.token!=='string'||p.token.length>200||hash_(p.token)!==TOKEN_SHA256)throw new Error('Unauthorized');
    lock=LockService.getScriptLock();lock.waitLock(25000);
    const book=SpreadsheetApp.openById(SHEET_ID),sheet=book.getSheetByName('Review'),history=history_(book);
    if(p.action==='configure_video_host')return json_(configureVideoHost_(sheet,p.base_url));
    if(p.action==='failures')return json_(failures_(sheet,history,p.outputs));
    if(p.action==='publish'){
      if(!Array.isArray(p.outputs)||p.outputs.length>100)throw new Error('Invalid output batch');
      for(const output of p.outputs)publish_(sheet,history,output);
      return json_({ok:true,published:p.outputs.length});
    }
    if(p.action==='snapshot') {
      const entries=entries_(sheet), rows=entries.filter(e=>!e.formula&&!e.noteFormula&&e.rating!=='').map(e=>({...e,rating:score_(e.rating),submitted:1}));
      return json_({ok:true,video_host:videoHost_(),video_host_configured:PropertiesService.getScriptProperties().getProperty('PC_VIDEO_SCHEMA')==='5',rows,entries:entries.map(e=>({id:e.id,round:e.round,digest:e.digest})),downloaded:new Date().toISOString()});
    }
    if(p.action==='heartbeat') {
      const g=p.generation||{},average=g.average_seconds==null?'—':Number(g.average_seconds).toFixed(1)+'s';
      sheet.getRange('A2').setValue('Dev panel: '+String(p.stage).slice(0,60)+' · Last sync '+new Date().toISOString()+' · Code failed '+(g.total?Math.round(g.failed/g.total*100):0)+'% ('+(g.failed||0)+'/'+(g.total||0)+') · Avg generation '+average);
      return json_({ok:true});
    }
    identity_(p);
    const found=entries_(sheet).filter(e=>e.id===p.id||e.round===p.round);
    if(found.length>1)throw new Error('Duplicate ID; resolve duplicate rows before processing');
    const entry=found[0],old=archived_(history,p.id);
    if(entry&&(entry.id!==p.id||entry.round!==p.round||entry.digest!==p.digest))throw new Error('Identity conflict');
    if(old&&(old[1]!==p.round||old[2]!==p.digest))throw new Error('Archived identity conflict');
    if(p.action==='complete'||p.action==='failure') {
      const automatic=p.action==='failure';
      if(automatic&&(p.rating!==-3||p.source!=='compile_failure'))throw new Error('Invalid failure provenance');
      const rating=automatic?-3:score_(p.rating);if(rating===null)throw new Error('Missing rating');
      if(old&&Number(old[3])!==rating)throw new Error('Archived rating conflict');
      if(entry) {
        // Migration permits removal of an untouched mirror row already rated on the old page.
        if(entry.formula||entry.noteFormula||((entry.rating!==''||(!p.imported&&!automatic))&&score_(entry.rating)!==rating))throw new Error('Rating changed; row retained');
        if(entry.rating!==''&&entry.note!==String(p.note||''))throw new Error('Notes changed; row retained');
      }
      if(!old)history.appendRow([p.id,p.round,p.digest,rating,literal_(p.note),literal_(p.result),new Date().toISOString(),literal_(entry?.video||'')]);
      SpreadsheetApp.flush();
      if(entry)sheet.deleteRow(entry.row);
      return json_({ok:true,completed:true,id:p.id});
    }
    if(p.action==='add') {
      if(old||entry)return json_({ok:true,added:true,id:p.id});
      if(!p.complete||p.truncated||!Number.isInteger(p.frames)||p.frames<1||![30,60].includes(p.fps)||!(p.seconds>0&&p.seconds<=600)||Math.abs(p.frames/p.fps-p.seconds)>.0001)throw new Error('Full animation evidence required');
      const host=videoHost_(),videoUrl=host+'/api/video/'+p.id;
      if(!host||p.video_url!==videoUrl)throw new Error('Invalid private animation URL');
      let row=Math.max(6,sheet.getLastRow()+1);
      if(row>sheet.getMaxRows())sheet.insertRowsAfter(sheet.getMaxRows(),100);
      // Insert by permanent ID, even when an earlier failed upload is retried.
      const later=entries_(sheet).find(e=>Number(e.id.slice(2))>Number(p.id.slice(2)));
      if(later){sheet.insertRowBefore(later.row);row=later.row;}
      sheet.getRange(row,1,1,8).setValues([['', '',literal_(p.id+' · '+String(p.name).slice(0,160)),p.seconds,'','Awaiting your rating',p.round,p.digest]]);
      sheet.getRange(row,1).setFormula('=HYPERLINK("'+videoUrl+'","Play full animation")');
      sheet.getRange(row,2).setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(['-2','-1','0','1','2'],true).setAllowInvalid(false).setHelpText('Blank = waiting; 0 = remove without training.').build());
      sheet.getRange(row,4).setNumberFormat('0.00');sheet.getRange(row,1,1,6).setVerticalAlignment('middle');sheet.setRowHeight(row,42);
      return json_({ok:true,added:true,id:p.id,video_url:videoUrl});
    }
    throw new Error('Unknown action');
  }catch(error){return json_({ok:false,error:String(error.message||error)});}
  finally{if(lock&&lock.hasLock())lock.releaseLock();}
}
