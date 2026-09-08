#!/usr/bin/env python3
from pathlib import Path
import re,json,subprocess,shutil,sys
root=Path(__file__).resolve().parents[1]
app=(root/'app/src/main/assets/app.js').read_text()
checks=[]
def c(n,x,d=''):checks.append((n,bool(x),d))
functions=set(re.findall(r'function\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(',app))
case_calls=re.findall(r"case\s+'([^']+)'\s*:\s*return\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(",app)
missing=[{'route':r,'function':f} for r,f in case_calls if f not in functions]
c('Every route renderer exists',not missing,json.dumps(missing))
required_v23=['renderHome','renderHomeBase','renderMyWork','renderFiles','renderGames','renderProduction','renderEngineering','renderGarden','renderFarm','renderCantus','renderKernel','renderSystems','renderContinuity','renderBibleWorkspace','renderUnderstand','renderLive','renderAlexandria','renderHumanity','renderCreationMap','renderCantusLab','renderRunes','renderMeaningCourse','renderOccultResearch','renderPatents','renderTechnology','renderFamilyGifts','renderHumanTechniques','renderConstructor','renderSettings','renderJesusCenter','renderStudyLab','renderFaithWorksheets','renderLivingChurch','renderLivingTradition','renderAncestors','renderLibrary','renderMyNotes','ademicRuneSvg','cantusPayload','cantusExpansion','ancestorPeople','ancestorRelationships','ancestorConfidence','saveFaithStudyRecord','saveGeneric','stableHash','rowsFor','draw','drawPeople','drawRels','foundation','routeCard','wireRouteCards','projectCards','wireProjectButtons']
lost=[f for f in required_v23 if f not in functions]
c('V23 feature-function continuity',not lost,json.dumps(lost))
required_new=['renderPhoneIntake','renderUnifiedCore','renderGlobalSearch','renderUniversalViewer','renderRouteAtlas','coreResultCard','wireCoreResults']
lost_new=[f for f in required_new if f not in functions]
c('V24/V25 additive functions',not lost_new,json.dumps(lost_new))
for label,needle in [('Phone Intake',"case 'phoneintake':return renderPhoneIntake();"),('Unified Core',"case 'core':return renderUnifiedCore();"),('Global Search',"case 'search':return renderGlobalSearch();"),('Route Atlas',"case 'routeatlas':return renderRouteAtlas();")]: c(label+' route',needle in app)
c('Scripture page reader','Page turning' in app and 'readerPageNext' in app)
c('Ademic Cantus','Ademic Cantus' in app and 'ademicRuneSvg' in app)
c('Ancestors','renderAncestors' in app and 'ancestorRelationships' in app)
c('Alexandria','renderAlexandria' in app)
c('Living Church/Tradition','renderLivingChurch' in app and 'renderLivingTradition' in app)
c('Patents','renderPatents' in app)
c('Faith worksheets','renderFaithWorksheets' in app)
c('Meaning/Runes','renderMeaningCourse' in app and 'renderRunes' in app)
if shutil.which('node'):
 p=subprocess.run(['node','--check',str(root/'app/src/main/assets/app.js')],capture_output=True,text=True); c('JavaScript syntax',p.returncode==0,(p.stderr or p.stdout)[-1000:])
bad=[x for x in checks if not x[1]]
out={'schema':'ark.runtime-continuity.v25_2','functions':len(functions),'routes':len(case_calls),'passed':len(checks)-len(bad),'failed':len(bad),'checks':[{'name':n,'pass':ok,'detail':d} for n,ok,d in checks]}
(root/'qa').mkdir(exist_ok=True); (root/'qa/V25_2_FULL_RUNTIME_CONTINUITY.json').write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2)); sys.exit(1 if bad else 0)
