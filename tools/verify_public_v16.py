#!/usr/bin/env python3
from pathlib import Path
import json,re,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
checks=[]
def ck(name,ok,detail=''): checks.append({'name':name,'pass':bool(ok),'detail':detail})
def read(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='ignore')

gradle=read('app/build.gradle'); manifest=read('app/src/main/AndroidManifest.xml'); app=read('app/src/main/assets/app.js'); index=read('app/src/main/assets/index.html')
ck('V16 version name', "16.0.0-jesus-study-engine" in gradle)
ck('Public package id', "applicationId 'com.arkforge.faith'" in gradle)
ck('Public namespace', "namespace 'com.arkforge.faith'" in gradle)
ck('ArkforgeNative JS bridge', 'window.ArkforgeNative' in app and '"ArkforgeNative"' in read('app/src/main/java/com/arkforge/faith/MainActivity.java'))
ck('Jesus study data loaded', 'faith/jesus-study-data.js' in index)
ck('Jesus nav route', "['jesus','Jesus']" in app and "case 'jesus':return renderJesusCenter()".replace(' ','') in app.replace(' ',''))
D=json.loads(read('app/src/main/assets/faith/jesus-study-data.json'))
for key,minn in [('timeline',20),('parables',25),('miracles',25),('teachings',15),('titles',10)]: ck(f'{key} >= {minn}',len(D.get(key,[]))>=minn,str(len(D.get(key,[]))))
ck('Seven Leaves direct UI','openSevenLeavesDialog' in app and 'data-public-leaves-ref' in app)
ck('Logic Path handoff','data-public-study-ref' in app and "mode='logic'" in app)
ck('Parable template UI','id="slParable"' in app and 'jd.parables' in app)
ck('Deep Scripture links','openBibleReference' in app and 'publicBibleTargetRef' in app)
W=json.loads(read('app/src/main/assets/faith/faith-worksheets.json'))
wc=len(W.get('worksheets',W if isinstance(W,list) else []))
ck('300 faith worksheets',wc==300,str(wc))
for perm,label in [('android.permission.CAMERA','camera'),('android.permission.RECORD_AUDIO','mic'),('android.permission.ACCESS_FINE_LOCATION','fine location'),('android.permission.ACCESS_COARSE_LOCATION','coarse location'),('android.permission.READ_CONTACTS','contacts'),('android.permission.READ_SMS','SMS'),('android.permission.READ_CALL_LOG','call log')]: ck('No '+label+' permission',perm not in manifest)
# Scan shippable text and public-facing docs. Do not put developer-owner names into the public verifier itself.
scan=[]
for base in [ROOT/'app/src/main',ROOT/'README.md',ROOT/'PUBLIC_SCOPE.md',ROOT/'PRIVACY_POLICY_DRAFT.md',ROOT/'RIGHTS_AND_SOURCES.md']:
    if base.is_file(): scan.append(base.read_text(encoding='utf-8',errors='ignore'))
    elif base.exists():
        for p in base.rglob('*'):
            if p.is_file() and p.suffix.lower() in {'.java','.js','.json','.html','.css','.md','.xml','.gradle','.txt'}: scan.append(p.read_text(encoding='utf-8',errors='ignore'))
text='\n'.join(scan)
ck('No private Termux home path',re.search(r'/data/data/com\.termux/files/home',text,re.I) is None)
emails=[m.group(0) for m in re.finditer(r'\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b',text,re.I) if not m.group(0).lower().endswith('@example.com')]; ck('No embedded real email address',not emails,', '.join(emails[:5]))
# Proprietary edition names may be mentioned in rights documentation; named proprietary files/assets must not be packaged.
asset_names=[p.name.lower() for p in (ROOT/'app/src/main/assets').rglob('*') if p.is_file()]
ck('No proprietary study-Bible asset filenames',not any(('ignatius' in n or 'rsv2ce' in n or 'rsv-2ce' in n) for n in asset_names))
# JS syntax
for rel in ['app/src/main/assets/app.js','app/src/main/assets/faith/public-study-data.js','app/src/main/assets/faith/faith-worksheets.js','app/src/main/assets/faith/jesus-study-data.js']:
    try:
        r=subprocess.run(['node','--check',str(ROOT/rel)],capture_output=True,text=True)
        ck('JS syntax '+rel.replace('app/src/main/assets/',''),r.returncode==0,(r.stderr or r.stdout).strip())
    except FileNotFoundError: ck('JS syntax '+rel,False,'node unavailable')
# JSON validity for key public assets
for rel in ['app/src/main/assets/faith/jesus-study-data.json','app/src/main/assets/faith/faith-worksheets.json','app/src/main/assets/public-scripture-source.json','app/src/main/assets/feature-ledger.json','app/src/main/assets/arthur-bible-atlas-spec.json']:
    try: json.loads(read(rel)); ck('JSON '+Path(rel).name,True)
    except Exception as e: ck('JSON '+Path(rel).name,False,str(e))
out={'schema':'arkforge.faith.v16.source-verification','passed':sum(x['pass'] for x in checks),'total':len(checks),'all_pass':all(x['pass'] for x in checks),'checks':checks}
(ROOT/'qa/V16_SOURCE_VERIFICATION.json').write_text(json.dumps(out,indent=2)+"\n",encoding='utf-8')
print(json.dumps(out,indent=2))
sys.exit(0 if out['all_pass'] else 1)
