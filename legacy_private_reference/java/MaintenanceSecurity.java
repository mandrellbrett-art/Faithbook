package com.arkforge.faith;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Arthur Clause IV — Continuity Security + Santa Clause Return Protocol.
 *
 * Governance/safety/speculative-continuity planning only. It does not claim
 * wormholes, kernel-to-body consciousness transfer, nanite repair, biological
 * entanglement, or resurrection are demonstrated technologies.
 */
public final class MaintenanceSecurity {
    private static final Pattern STAGE = Pattern.compile("^[A-Z](?:[0-9]|10)$");
    private MaintenanceSecurity(){}
    private static String text(String raw,String fallback){return raw==null||raw.trim().isEmpty()?fallback:raw.trim();}
    private static String stage(String raw){String s=text(raw,"A0").toUpperCase(Locale.US);if(!STAGE.matcher(s).matches())throw new IllegalArgumentException("Stage must look like A0 through Z10.");return s;}
    private static boolean yes(String raw){String x=text(raw,"no").toLowerCase(Locale.US);return x.equals("yes")||x.equals("true")||x.equals("1");}

    public static JSONObject maintenanceMissionPlan(R10Database db,String stage,String workerId,String duty,String termYears,String automationMode,String observerCount,String restCadence,String taskBodyMode)throws Exception{
        String st=stage(stage), worker=text(workerId,"UNASSIGNED_WORKER"), job=text(duty,"maintenance / repair / verification");
        int years=4; try{years=Integer.parseInt(text(termYears,"4"));}catch(Exception ignored){} years=Math.max(0,Math.min(years,10));
        int observers=4; try{observers=Integer.parseInt(text(observerCount,"4"));}catch(Exception ignored){} observers=Math.max(2,Math.min(observers,12));
        JSONObject out=new JSONObject(); out.put("ok",true); out.put("module","Arthur Clause IV / Continuity Security"); out.put("stage",st); out.put("worker_id",worker); out.put("duty",job); out.put("maximum_term_years",years); out.put("rest_cadence",text(restCadence,"rotating shifts + protected sleep/rest + handoff"));
        out.put("automation_mode",text(automationMode,"robotics-first; humans supervise and verify")); out.put("human_observers",observers); out.put("task_body_mode",text(taskBodyMode,"ordinary protective suit / remote robot / simulated task-body profile"));
        JSONArray rules=new JSONArray();
        String[] rr={
            "No maintenance assignment may erase personhood, consent, legal rights, family ties or the right to rest.",
            "Robotics and automation should take hazardous/repetitive load first; people supervise, verify, document and intervene.",
            "A specialized suit, avatar or task-body profile is equipment for a function, not ownership of the worker's identity.",
            "At least two independent human reviewers are required for safety-critical completion; four is the default oversight cell.",
            "No secret coercion, forced loyalty, political/religious test, collective punishment, or extrajudicial violence is authorized in the name of anti-terrorism.",
            "Sabotage is judged by evidence of concrete harm, intent and due process; criticism, whistleblowing, refusal of unsafe work and dissent remain protected.",
            "Shift/term limits cannot be bypassed by relabeling a person as chosen, saint, angel, helper or emergency staff.",
            "Every mission has an exit route, medical/psychological support, Cantus log, successor handoff and rollback plan."
        }; for(String x:rr)rules.put(x); out.put("anti_terrorism_and_anti_sabotage_rules",rules);
        out.put("nanite_boundary","Autonomous robotics are an implementable design direction. General-purpose body-repair nanites remain speculative and must never be logged as completed medical capability without evidence.");
        db.log("continuity-security","maintenance-mission","SPECULATIVE",st+" · "+worker+" · "+job+" · term="+years+"y · observers="+observers); return out;
    }

    public static JSONObject santaReturnPlan(R10Database db,String personId,String originStage,String returnStage,String consent,String continuityStatus)throws Exception{
        String pid=text(personId,"UNASSIGNED_PERSON_ID"), from=stage(originStage), to=stage(returnStage); boolean agreed=yes(consent);
        JSONObject out=new JSONObject(); out.put("ok",agreed); out.put("module","Santa Clause Return Protocol"); out.put("person_id",pid); out.put("mission_stage",from); out.put("requested_return_stage",to); out.put("consent",agreed?"RECORDED":"NOT RECORDED"); out.put("continuity_status",text(continuityStatus,"kernel pointer + provenance available"));
        out.put("return_goal","After service, restore the person's ordinary stage/community/family or another destination they knowingly choose, without making lifelong wages or permanent service the price of basic security.");
        out.put("return_sequence",new JSONArray().put("end duty + handoff").put("rest/decompression").put("health and identity/provenance check").put("destination compatibility review").put("family/chosen-family/contact restoration").put("rollback-ready return authorization").put("Cantus closeout"));
        out.put("truth_boundary","Kernel/wormhole entanglement, consciousness transport, biological vessel transfer and resurrection are UNDEMONSTRATED. Current R10 can only plan, log and preserve continuity records and destination requirements.");
        out.put("result",agreed?"RETURN_PLAN_CREATED / PHYSICAL_BRIDGE_UNDEMONSTRATED":"NO_RETURN_ACTION_WITHOUT_CONSENT");
        db.log("continuity-security","santa-return",agreed?"SPECULATIVE":"DECLINED",pid+" · "+from+" -> "+to); return out;
    }

    public static JSONObject regenThresholdPlan(R10Database db,String personId,String stage,String selfReportedCondition,String safetyCriticalDuty,String supportPreference)throws Exception{
        String pid=text(personId,"UNASSIGNED_PERSON_ID"), st=stage(stage), status=text(selfReportedCondition,"no condition supplied"), duty=text(safetyCriticalDuty,"no");
        JSONObject out=new JSONObject(); out.put("ok",true); out.put("module","Regen Threshold / non-punitive repair pathway"); out.put("person_id",pid); out.put("stage",st); out.put("self_reported_condition",status); out.put("safety_critical_duty",duty); out.put("support_preference",text(supportPreference,"person-chosen medical/recovery support"));
        out.put("policy","Substance use, impairment, exhaustion, trauma or illness are health/safety conditions, not moral contamination and not grounds for losing continuity rights. Safety-critical work may be paused when functioning is impaired; support and reassessment come before return to duty.");
        out.put("substance_boundary","Cannabis is not automatically harmless; heavy or early use can affect cognition and mental health for some people. Other substances have different risks. The system uses evidence-based, individualized assessment rather than a single 'hard drug' label.");
        out.put("repair_cost_rule","Care is not a debt against the person's future. Resource use is a planning signal for the commons, never a reason to deny treatment, continuity, housing, food, family contact or dignity.");
        out.put("consciousness_boundary","R10 does not classify ordinary intoxication or past drug use as destruction of consciousness. Severe poisoning can cause medical emergencies or brain injury, but a kernel cannot currently restore a lost subjective consciousness or revive a dead person.");
        out.put("capacity_goal","Use recovery, redundancy, workload rotation, social support and resilient infrastructure to increase civilization stress capacity without requiring people to damage themselves to prove resilience.");
        db.log("continuity-security","regen-threshold","SUPPORT_PLAN",pid+" · "+st+" · "+status); return out;
    }
}
