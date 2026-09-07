(()=>{
'use strict';
if(window.ArkforgeNative) return;
const KEY='faithbook.v19.preview.records';
const load=()=>{try{return JSON.parse(localStorage.getItem(KEY)||'[]')}catch{return []}};
const save=x=>localStorage.setItem(KEY,JSON.stringify(x));
const ok=x=>JSON.stringify(Object.assign({ok:true},x||{}));
const err=e=>JSON.stringify({ok:false,error:String(e)});
const records=(type,archived=false,limit=1000)=>load().filter(x=>x.type===type&&!!x.archived===!!archived).slice(0,limit);
window.ArkforgeNative={
 bootstrap(){return ok({product:'HeritageFaith',version:'19.0.0-foundation-preview',runtime:'browser-preview',stats:{db_integrity:'PREVIEW',records:load().filter(x=>!x.archived).length,projects:0,managed_files:0,corpus_files:0,cantus_events:0},projects:[],features:{mode:'preview'}})},
 records(type,archived,limit){return ok({records:records(type,archived,limit)})},
 addRecord(type,title,body,metaJson){try{const all=load(),id='rec_'+Date.now().toString(36)+Math.random().toString(36).slice(2,7),now=new Date().toISOString();JSON.parse(metaJson||'{}');all.unshift({id,type,title,body:body||'',meta_json:metaJson||'{}',created_at:now,updated_at:now,archived:false});save(all);return ok({id})}catch(e){return err(e)}},
 updateRecord(id,title,body,metaJson){try{const all=load(),r=all.find(x=>x.id===id);if(!r)throw new Error('Record not found');JSON.parse(metaJson||'{}');Object.assign(r,{title,body:body||'',meta_json:metaJson||'{}',updated_at:new Date().toISOString()});save(all);return ok()}catch(e){return err(e)}},
 archiveRecord(id,archived){const all=load(),r=all.find(x=>x.id===id);if(r){r.archived=!!archived;save(all)}return ok()},
 projects(){return ok({projects:[]})}, projectFiles(){return ok({files:[]})},
 search(q){q=String(q||'').toLowerCase();return ok({results:load().filter(x=>JSON.stringify(x).toLowerCase().includes(q)).slice(0,100)})},
 searchCorpus(){return ok({results:[]})},
 continuityStatus(){return ok({runs:0,items:0,imported_bytes:0,reference_only:0,mismatches:0,missing:0,recent_runs:[]})},
 constructorAsk(message){const q=String(message||'').toLowerCase();let reply='Constructor preview can index, classify, and route HeritageFaith records. Native Android builds add managed-file hashing, project navigation, continuity checks, and import/export.';if(q.includes('status'))reply=`Preview status: ${load().filter(x=>!x.archived).length} local records. No native managed files in browser preview.`;return ok({mode:'browser-preview',reply})},
 verifyCantus(){return ok({events:0,issues:[]})}, cantus(){return ok({logs:[]})},
 bibleAtlasSpec(){return ok({atlas:{seven_leaves:[]},books:{},starter:{},sources:{}})},
 publicBibleStatus(){return ok({installed:false,status:'PREVIEW_NOT_INSTALLED'})}, publicBibleSource(){return ok({title:'Public Scripture source registry',status:'SOURCE_REGISTRY_ONLY'})},
 bibleCorpora(){return ok({corpora:[]})}, bibleChapter(){return ok({verses:[]})}, bibleSearch(){return ok({results:[]})}, publicBibleSearch(){return ok({results:[]})}, publicBibleChapter(){return ok({verses:[]})}, publicBibleBookInfo(){return ok({})},
 installPublicBible(){return err('Public Scripture installer is available in the Android build, not browser preview.')},
 stats(){return JSON.stringify({db_integrity:'PREVIEW',records:load().length})}, featureLedger(){return ok({})},
 printCurrent(){window.print()}, shareText(subject,text){navigator.clipboard?.writeText(text||'')}, openUrl(url){window.open(url,'_blank')},
 importFile(){alert('File import is available in the Android build.')}, exportBackup(){alert('Backup export is available in the Android build.')}
};
})();
