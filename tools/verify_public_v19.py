#!/usr/bin/env python3
from pathlib import Path
import json,re,subprocess,hashlib,sys
ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/'app'
ASSETS=APP/'src/main/assets'
JAVA=APP/'src/main/java/com/arkforge/faith'
checks=[]
def check(name,ok,detail=''):
    checks.append({'name':name,'pass':bool(ok),'detail':str(detail)})

def contains(path,text):
    return text in Path(path).read_text(errors='ignore')

required=[
 'app/src/main/assets/index.html','app/src/main/assets/app.js','app/src/main/assets/styles.css',
 'app/src/main/assets/faith/foundation-data.js','app/src/main/assets/preview-bridge.js',
 'app/src/main/assets/feature-ledger.json','app/src/main/assets/PUBLIC_RELEASE_BOUNDARY.json',
 'app/src/main/assets/SCRIPTURE_CONTEXT_ATLAS.md','app/src/main/assets/scripture-context-atlas-spec.json','app/src/main/assets/CONSTRUCTOR_BACKEND_SPEC.json',
 'README.md','V19_CHANGELOG.md','FOUNDATION_ARCHITECTURE.md','ALEXANDRIA.md','ADEMIC_CANTUS.md','HUMANITY_ARK_V19.md'
]
for rel in required: check('required:'+rel,(ROOT/rel).is_file())

gradle=(APP/'build.gradle').read_text()
check('versionCode 190001','versionCode 190001' in gradle)
check('versionName 19.0.0-foundation',"versionName '19.0.0-foundation'" in gradle)
check('package id continuity',"applicationId 'com.arkforge.faith'" in gradle)
check('root project Faithbook',"rootProject.name = 'Faithbook'" in (ROOT/'settings.gradle').read_text())
check('app label Faithbook','>Faithbook<' in (APP/'src/main/res/values/strings.xml').read_text())

html=(ASSETS/'index.html').read_text()
for script in ['faith/faith-worksheets.js','faith/public-study-data.js','faith/jesus-study-data.js','faith/foundation-data.js','preview-bridge.js','app.js']:
    check('html script:'+script,f'src="{script}"' in html and (ASSETS/script).is_file())
check('quiet theme meta','#eee6d5' in html)

js=(ASSETS/'app.js').read_text()
for rel in ['app.js','preview-bridge.js','faith/foundation-data.js','faith/faith-worksheets.js','faith/public-study-data.js','faith/jesus-study-data.js']:
    try:
        p=subprocess.run(['node','--check',str(ASSETS/rel)],capture_output=True,text=True,timeout=30)
        check('javascript syntax:'+rel,p.returncode==0,(p.stderr or p.stdout).strip())
    except Exception as e: check('javascript syntax:'+rel,False,e)
for route in ['home','bible','understand','live','alexandria','humanity','ancestors','constructor','creationmap','cantuslab','runes','meaningcourse','occult','patents','technology','familygifts','techniques']:
    check('route:'+route,(f"case '{route}'" in js) or (route in ['home'] and "case 'home'" in js))
check('single active renderConstructor',len(re.findall(r'^function renderConstructor\(',js,re.M))==1)
check('Ademic rune generator','function ademicRuneSvg' in js)
check('Cantus reversible expansion','function cantusExpansion' in js)
check('Recursive Eye UI','RECURSIVE EYE' in js)
check('Patent registry UI','Patents & Inventions' in js and 'prior_art' in js)
check('Constructor backend responsibilities','Backend responsibilities' in js and 'Never silently delete or overwrite source data.' in js)

foundation=(ASSETS/'faith/foundation-data.js').read_text()
for term in ['Infrastructure','Textiles','Manufacturing','Fuel & Energy','Anatomy','Biology','Tree of Life','Stonemasons & Builders','Police & Public Safety','Patents & Inventions','Technology','Family Gifts & Traditions','Human Techniques']:
    check('foundation:'+term,term in foundation)
for term in ['Ademic Cantus','Resonance','Occult & Esoteric Traditions','The Human Art of Meaning','Map of Creation']:
    check('alexandria:'+term,term in foundation)
check('12-unit meaning course',len(re.findall(r'^\s*\[\d+,',foundation,re.M))>=12)

ledger=json.loads((ASSETS/'feature-ledger.json').read_text())
check('feature ledger schema',ledger.get('schema')=='faithbook.features.v19')
check('feature release state',ledger.get('release_state')=='SOURCE_FOUNDATION_NOT_STORE_CANDIDATE')
boundary=json.loads((ASSETS/'PUBLIC_RELEASE_BOUNDARY.json').read_text())
check('release boundary v19',boundary.get('schema')=='faithbook.public-release-boundary.v19')
check('promotion locked',boundary.get('promotion_allowed') is False)
check('private data not bundled declaration',boundary.get('private_owner_data_bundled') is False)

# Public runtime clean-room scan.
scan_files=[]
for base in [APP/'src/main']:
    scan_files += [p for p in base.rglob('*') if p.is_file() and p.suffix.lower() not in {'.png','.jpg','.jpeg','.webp','.pdf','.zip'}]
patterns=['Arthur','Arkforge Faith','AngelCore','angel-core','angel_ark','arthur-guide']
hits=[]
for p in scan_files:
    t=p.read_text(errors='ignore')
    for pat in patterns:
        if pat in t: hits.append(f'{p.relative_to(ROOT)}:{pat}')
check('runtime free of removed private/legacy identities',not hits,'; '.join(hits[:20]))
for rel in [
 'app/src/main/java/com/arkforge/faith/AngelCore.java','app/src/main/java/com/arkforge/faith/ArthurClause.java',
 'app/src/main/assets/angel-core-spec.json','app/src/main/assets/arthur-guide.png',
 'app/src/main/assets/faith/approved_jesus_centered_home.png']:
    check('not bundled:'+rel,not (ROOT/rel).exists())
check('legacy private history preserved',(ROOT/'legacy_private_reference/java/AngelCore.java').is_file())
check('legacy DB history preserved',(ROOT/'legacy_private_reference/java/R10Database_V18_WITH_ANGEL_ARK.java.txt').is_file())

manifest=(APP/'src/main/AndroidManifest.xml').read_text()
sensitive=['CAMERA','RECORD_AUDIO','ACCESS_FINE_LOCATION','READ_CONTACTS','WRITE_CONTACTS','READ_SMS','SEND_SMS','READ_CALL_LOG','BODY_SENSORS']
check('no sensitive core permissions',not any(('android.permission.'+x) in manifest for x in sensitive))
check('cleartext disabled','android:usesCleartextTraffic="false"' in manifest)

# Java structural scan that ignores strings, chars, and comments.
def java_brace_balance(text):
    i=0; bal=0; state='code'; escape=False
    while i<len(text):
        c=text[i]; n=text[i+1] if i+1<len(text) else ''
        if state=='code':
            if c=='/' and n=='/': state='line'; i+=2; continue
            if c=='/' and n=='*': state='block'; i+=2; continue
            if c=='"': state='string'; escape=False; i+=1; continue
            if c=="'": state='char'; escape=False; i+=1; continue
            if c=='{': bal+=1
            elif c=='}': bal-=1
        elif state=='line':
            if c=='\n': state='code'
        elif state=='block':
            if c=='*' and n=='/': state='code'; i+=2; continue
        elif state in ('string','char'):
            if escape: escape=False
            elif c=='\\': escape=True
            elif (state=='string' and c=='"') or (state=='char' and c=="'"): state='code'
        i+=1
    return bal,state
for p in JAVA.glob('*.java'):
    bal,state=java_brace_balance(p.read_text(errors='ignore'))
    check('java braces:'+p.name,bal==0 and state in ('code','line'),f"balance={bal}, end_state={state}")

passed=sum(c['pass'] for c in checks); total=len(checks)
result={
 'schema':'faithbook.qa.v19','product':'Faithbook','version':'19.0.0-foundation',
 'passed':passed,'total':total,'failed':total-passed,
 'android_compiled':False,'signed_aab_verified':False,'physical_phone_verified':False,'play_approved':False,
 'note':'Source/static verification only. Android SDK/Gradle compilation was not available in this environment.',
 'checks':checks
}
qa=ROOT/'qa';qa.mkdir(exist_ok=True)
(qa/'V19_SOURCE_VERIFICATION.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['passed','total','failed','android_compiled','signed_aab_verified']},indent=2))
if result['failed']:
    for c in checks:
        if not c['pass']: print('FAIL',c['name'],c['detail'])
    sys.exit(1)
