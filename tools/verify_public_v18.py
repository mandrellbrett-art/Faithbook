#!/usr/bin/env python3
from pathlib import Path
import json, re, sys
ROOT=Path(__file__).resolve().parents[1]
checks=[]
def ck(name, ok, detail=''):
    checks.append({'name':name,'ok':bool(ok),'detail':detail})

appjs=(ROOT/'app/src/main/assets/app.js').read_text(errors='replace')
index=(ROOT/'app/src/main/assets/index.html').read_text(errors='replace')
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text(errors='replace')
strings=(ROOT/'app/src/main/res/values/strings.xml').read_text(errors='replace')
ledger=json.loads((ROOT/'app/src/main/assets/feature-ledger.json').read_text())
spec=json.loads((ROOT/'app/src/main/assets/faith/ancestors-spec.json').read_text())

ck('visible product name Faithbook', '<title>Faithbook</title>' in index and 'FAITHBOOK' in index and 'Faithbook' in strings)
ck('ancestors in primary nav', "['ancestors','Ancestors']" in appjs)
ck('ancestors route renders', "case 'ancestors':return renderAncestors();" in appjs and 'function renderAncestors()' in appjs)
ck('ancestor person record type', "'ancestor_person'" in appjs)
ck('ancestor relationship record type', "'ancestor_relationship'" in appjs)
ck('confidence states present', all(x in appjs for x in ['DOCUMENTED','PROBABLE','FAMILY_TRADITION','DISPUTED','UNKNOWN']))
ck('equal dignity boundary present', 'does not determine holiness, intelligence, belonging, destiny, or value before God' in appjs)
ck('biblical genealogy separated', 'NOT A CLAIM ABOUT YOUR FAMILY LINE' in appjs)
ck('raw DNA not implemented', spec.get('privacy',{}).get('raw_dna_import') is False)
ck('no sensitive runtime permissions added', all(p not in manifest for p in ['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','RECORD_AUDIO','READ_CONTACTS','READ_SMS','READ_PHONE_STATE']))
ck('public feature ledger updated', any(f.get('name')=='Ancestors — Where Am I From?' for f in ledger.get('features',[])))
ck('package continuity retained', 'com.arkforge.faith' in (ROOT/'app/build.gradle').read_text(errors='replace'))
ck('ancestry docs present', all((ROOT/x).exists() for x in ['ANCESTORS.md','ANCESTRY_PRIVACY_NOTE.md','V18_CHANGELOG.md']))

report={'schema':'faithbook.qa.v18','version':'18.0.0-ancestors','checks':checks,'passed':sum(x['ok'] for x in checks),'total':len(checks),'all_passed':all(x['ok'] for x in checks),'release_truth':{'android_compiled':False,'signed_aab':False,'physical_phone_accepted':False,'google_play_approved':False}}
out=ROOT/'qa/V18_SOURCE_VERIFICATION.json';out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
sys.exit(0 if report['all_passed'] else 1)
