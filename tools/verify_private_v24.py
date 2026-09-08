#!/usr/bin/env python3
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
checks=[]
def c(n,x):checks.append((n,bool(x)))
db=(root/"app/src/main/java/com/arkforge/faith/R10Database.java").read_text()
pm=(root/"app/src/main/java/com/arkforge/faith/PhoneIntakeManager.java").read_text()
ma=(root/"app/src/main/java/com/arkforge/faith/MainActivity.java").read_text()
nb=(root/"app/src/main/java/com/arkforge/faith/NativeBridge.java").read_text()
app=(root/"app/src/main/assets/app.js").read_text()
grad=(root/"app/build.gradle").read_text()
c("DB v9","DB_VERSION = 9" in db);c("phone tables","phone_intake_runs" in db and "phone_intake_files" in db)
c("phone manager",(root/"app/src/main/java/com/arkforge/faith/PhoneIntakeManager.java").is_file())
c("Android tree picker","ACTION_OPEN_DOCUMENT_TREE" in ma);c("index mode","INDEX_ONLY" in pm);c("copy all mode","COPY_ALL_ACCESSIBLE" in pm)
c("SHA copy","SHA-256" in pm);c("free space reserve","MIN_FREE" in pm and "StatFs" in pm)
c("homebase classifier","thunderforge-homebase" in pm and "kernel" in pm and "constructor" in pm)
c("phone bridge","phoneIntakeSummary" in nb and "phoneIntakeSearch" in nb);c("baseline bridge","homeBaseBaseline" in nb)
c("phone route","case 'phoneintake':return renderPhoneIntake();" in app);c("homebase recovery","Critical Home Base domains" in app)
c("kernel r2","Kernel Computer R2 / Golden Field 144" in app);c("feature registry",(root/"app/src/main/assets/homebase-feature-registry.json").is_file())
c("continuity lock",(root/"app/src/main/assets/homebase-continuity-lock.json").is_file());c("parity",(root/"app/src/main/assets/homebase-v24-parity.json").is_file())
m=re.search(r"versionCode\s+(\d+)",grad);c("V24+ code",bool(m) and int(m.group(1))>=240001);c("V24 Home Base lineage",any(x in grad for x in ["homebase-phone-intake","unified-core","full-continuity"]))
bad=[n for n,x in checks if not x];out={"schema":"heritagefaith.qa.v24","passed":len(checks)-len(bad),"failed":len(bad),"checks":[{"name":n,"pass":x} for n,x in checks]}
(root/"qa").mkdir(exist_ok=True);(root/"qa/V24_HOMEBASE_PHONE_INTAKE_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2));sys.exit(1 if bad else 0)
