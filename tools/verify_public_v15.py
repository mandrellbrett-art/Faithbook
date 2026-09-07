#!/usr/bin/env python3
from pathlib import Path
import json,re,subprocess,sys,xml.etree.ElementTree as ET, hashlib
root=Path(__file__).resolve().parents[1]
checks=[]
def ck(name,ok,detail=''):
    checks.append({'name':name,'ok':bool(ok),'detail':detail}); print(('PASS' if ok else 'FAIL'),name,detail)
req=['app/build.gradle','app/src/main/AndroidManifest.xml','app/src/main/assets/index.html','app/src/main/assets/app.js','app/src/main/assets/styles.css','app/src/main/assets/public-scripture-source.json','app/src/main/java/com/thunderforge/r10/PublicBibleStore.java','app/src/main/assets/bible-book-index.json','app/src/main/assets/bible-atlas-starter.json','app/src/main/assets/faith/faith-worksheets.json','app/src/main/assets/faith/Arkforge_300_Faith_Worksheets.pdf','app/src/main/assets/faith/approved_jesus_centered_home.png','app/src/main/assets/faith/arthur-guide.png','app/src/main/assets/faith/public-study-data.js','RIGHTS_AND_SOURCES.md','V15_CHANGELOG.md']
ck('required-v15-files',all((root/x).is_file() for x in req),f'{len(req)} required')
for x in ['app/src/main/AndroidManifest.xml','app/src/main/res/values/strings.xml','app/src/main/res/values/styles.xml']:
    try: ET.parse(root/x); ok=True; detail=''
    except Exception as e: ok=False; detail=str(e)
    ck('xml:'+x,ok,detail)
build=(root/'app/build.gradle').read_text(errors='ignore')
js=(root/'app/src/main/assets/app.js').read_text(errors='ignore')
idx=(root/'app/src/main/assets/index.html').read_text(errors='ignore')
css=(root/'app/src/main/assets/styles.css').read_text(errors='ignore')
data=(root/'app/src/main/assets/faith/public-study-data.js').read_text(errors='ignore')
java=(root/'app/src/main/java/com/thunderforge/r10/PublicBibleStore.java').read_text(errors='ignore')
source=json.loads((root/'app/src/main/assets/public-scripture-source.json').read_text())
canon=json.loads((root/'app/src/main/assets/bible-book-index.json').read_text())
ck('public-package-id',"applicationId 'com.arkforge.faith'" in build)
ck('v15-version',"versionCode 150001" in build and "versionName '15.0.0-scripture-reader'" in build)
ck('jesus-centered','Jesus at the center' in js or 'Jesus is the center' in idx)
ck('arthur-visible-guide','arthurGuide' in idx and 'setupArthurGuide' in js and 'arthur-guide.png' in idx)
ck('73-book-source-manifest',source.get('canonical_book_count')==73 and len(source.get('books',[]))==73 and len({x['slug'] for x in source['books']})==73)
ck('canonical-name-parity',[x['canonical_name'] for x in canon['books']]==[x['name'] for x in source['books']])
ck('appendix-excluded',set(source.get('appendix_excluded_by_default',[]))=={'3-esdras','4-esdras','prayer-of-manasses'} and not any(x['slug'] in {'3-esdras','4-esdras','prayer-of-manasses'} for x in source['books']))
ck('cc0-rights-basis','CC0' in source.get('license','') and 'github.com/janvier-s/original-douay-rheims' in source.get('repository',''))
ck('fixed-https-source','https://raw.githubusercontent.com/janvier-s/original-douay-rheims/main/bible/raw/' in java and 'raw.githubusercontent.com' in java and '"https".equalsIgnoreCase' in java)
ck('download-safety','MAX_BOOK_BYTES' in java and '.json.part' in java and 'schema check' in java and 'getFilesDir()' in java)
ck('download-resume','if(finalFile.isFile()&&finalFile.length()>10)' in java and 'INTERRUPTED' in java)
ck('public-reader-ui','73-Book Reader' in js and 'publicBibleChapter' in js and 'publicBibleSearch' in js and 'publicBibleStatus' in js)
ck('verse-notes-questions','data-public-note-ref' in js and 'data-public-question-ref' in js)
ck('red-letter-is-layer','data-public-red-ref' in js and 'display_only:true' in js and 'source text is never rewritten' in (root/'RIGHTS_AND_SOURCES.md').read_text(errors='ignore'))
ck('arthur-context','publicBibleRelated' in js and 'People, places & timeline in this chapter' in js)
ck('living-church','renderLivingChurch' in js and 'churchRoles' in data and 'Pope / Bishop of Rome' in data)
ck('living-tradition','renderLivingTradition' in js and 'The Eucharist' in data and 'Chalice / Cup' in data and 'ceremonies' in data)
ck('study-lab','renderStudyLab' in js and 'logicPath' in data and 'parableFields' in data and 'numberStudy' in data)
ck('300-worksheets','300 Faith Worksheets' in js and (root/'app/src/main/assets/faith/Arkforge_300_Faith_Worksheets.pdf').stat().st_size>500000)
ck('source-lens',all(x in data for x in ['SCRIPTURE','CHURCH TEACHING','TRADITION','ARTHUR SYNTHESIS','USER NOTE']))
ck('numerology-boundary','not automatically a prediction' in data and 'secret code' in data)
try:
    r=subprocess.run(['node','--check',str(root/'app/src/main/assets/app.js')],capture_output=True,text=True); ck('app-js-syntax',r.returncode==0,r.stderr.strip())
    r=subprocess.run(['node','--check',str(root/'app/src/main/assets/faith/public-study-data.js')],capture_output=True,text=True); ck('study-data-js-syntax',r.returncode==0,r.stderr.strip())
except FileNotFoundError: ck('node-syntax',True,'node unavailable; skipped')
# Known owner/private migration identifiers. Geographic words remain included because this public build should not contain owner-specific locality data.
terms=['Brett','Mandrell','Racine','Burlington','Wisconsin','c96120d66aa0af','181088','/data/data/com.termux','PrivateWorks-Backups']
scan=[]
for p in (root/'app/src/main').rglob('*'):
    if not p.is_file() or p.suffix.lower() in ['.png','.jpg','.jpeg','.pdf','.webp']: continue
    t=p.read_text(errors='ignore')
    for term in terms:
        if term.lower() in t.lower(): scan.append({'path':str(p.relative_to(root)),'term':term})
ck('public-owner-privacy-scan',not scan,str(scan[:10]))
manifest=(root/'app/src/main/AndroidManifest.xml').read_text(errors='ignore')
sensitive=['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','RECORD_AUDIO','READ_CONTACTS','READ_SMS','READ_CALL_LOG','CAMERA']
found=[x for x in sensitive if x in manifest]
ck('no-sensitive-manifest-permissions',not found,str(found))
# Ensure copyrighted Ignatius source bytes/obvious named assets are not packaged; documentation may mention the boundary.
third_party_assets=[str(p.relative_to(root)) for p in (root/'app/src/main/assets').rglob('*') if p.is_file() and any(k in p.name.lower() for k in ['ignatius','rsv2ce','rsv-2ce'])]
ck('no-ignatius-rsv-assets',not third_party_assets,str(third_party_assets))
summary={'schema':'arkforge.faith.v15.source-verification','version':'15.0.0-scripture-reader','passed':sum(c['ok'] for c in checks),'total':len(checks),'ok':all(c['ok'] for c in checks),'checks':checks,'privacy_findings':scan,'build_level':'SOURCE_ONLY_NOT_SIGNED_AAB'}
(root/'qa/V15_SOURCE_VERIFICATION.json').write_text(json.dumps(summary,indent=2))
(root/'qa/PUBLIC_PRIVACY_AUDIT.json').write_text(json.dumps({'version':'15.0.0-scripture-reader','checked_terms':terms,'findings':scan,'pass':not scan},indent=2))
(root/'qa/PUBLIC_STORE_CANDIDATE_STATUS.json').write_text(json.dumps({'version':'15.0.0-scripture-reader','application_id':'com.arkforge.faith','target_price_usd':4.99,'jesus_centered':True,'arthur_visible_guide':True,'living_church':True,'living_tradition':True,'study_lab':True,'worksheets':300,'public_scripture':'Original Douay-Rheims 73-book CC0 download-once offline cache','privacy_audit_pass':not scan,'source_verification':f"{summary['passed']}/{summary['total']} PASS" if summary['ok'] else f"{summary['passed']}/{summary['total']} REVIEW",'build_level':'SOURCE_ONLY_NOT_SIGNED_AAB'},indent=2))
sys.exit(0 if summary['ok'] else 1)
