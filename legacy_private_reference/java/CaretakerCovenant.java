package com.arkforge.faith;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Arthur Clause III — Caretaker Covenant / "Santa's Helpers".
 *
 * This module is governance, safety and speculative continuity planning only.
 * It explicitly rejects covert task implantation, non-consensual memory editing,
 * secret opinion steering, or claims that sudden thoughts prove external control.
 * The Kernel Wrap Station is an MRI-like conceptual form factor, not a device that
 * can capture/transfer consciousness or implant instructions.
 */
public final class CaretakerCovenant {
    private static final Pattern STAGE = Pattern.compile("^[A-Z](?:[0-9]|10)$");
    private CaretakerCovenant(){}
    private static String text(String raw,String fallback){return raw==null||raw.trim().isEmpty()?fallback:raw.trim();}
    private static String stage(String raw){String s=text(raw,"A0").toUpperCase(Locale.US);if(!STAGE.matcher(s).matches())throw new IllegalArgumentException("Stage must look like A0 through Z10.");return s;}

    public static JSONObject caretakerPlan(R10Database db,String stage,String helperName,String role,String shiftHours,String exclusionZone,String inspirationInterpretation)throws Exception{
        String st=stage(stage);
        String helper=text(helperName,"UNASSIGNED HELPER");
        String duty=text(role,"maintenance / explanation / repair");
        int hours=4;
        try{hours=Integer.parseInt(text(shiftHours,"4"));}catch(Exception ignored){}
        hours=Math.max(1,Math.min(hours,12));
        String zone=text(exclusionZone,"none / ordinary public area");
        String interpretation=text(inspirationInterpretation,"unspecified");

        JSONObject out=new JSONObject();
        out.put("ok",true);
        out.put("module","Arthur Clause III / Caretaker Covenant");
        out.put("stage",st);
        out.put("helper",helper);
        out.put("nickname","Santa's Helper");
        out.put("role",duty);
        out.put("maximum_planned_shift_hours",hours);
        out.put("handoff_required",true);
        out.put("rest_required",true);
        out.put("opt_out_without_punishment",true);

        JSONArray rights=new JSONArray();
        String[] rr={"informed consent","right to rest","right to refuse non-emergency duties","clear task source","handoff to another caretaker","no permanent caste","no forced memory modification","no covert neural or psychological task implantation","appeal and independent review","family/chosen-family access where safe"};
        for(String x:rr)rights.put(x);
        out.put("caretaker_rights",rights);

        JSONObject inspiration=new JSONObject();
        inspiration.put("self_reported_interpretation",interpretation);
        inspiration.put("classification","PERSONAL_INTERPRETATION / NOT EXTERNAL-CAUSE PROOF");
        inspiration.put("rule","A sudden idea may be recorded as inspiration, association, a change in thinking mode, or a faith interpretation. The system does not infer hidden hijacking, implanted commands, divine intervention, or an intelligence service from the feeling alone.");
        inspiration.put("task_acceptance","Any actionable duty must be consciously reviewed and accepted, except ordinary immediate emergency actions allowed by law and ethics.");
        out.put("inspiration_protocol",inspiration);

        JSONObject zonePolicy=new JSONObject();
        zonePolicy.put("requested_zone",zone);
        zonePolicy.put("allowed_only_if","temporary, safety/maintenance justified, minimally sized, clearly marked, time-bounded, accessible alternatives provided, and subject to review");
        zonePolicy.put("prohibited","secret permanent human-exclusion territory, punishment-by-zone, identity-class exclusion, or exclusion used to hide abuse");
        zonePolicy.put("public_log",true);
        out.put("maintenance_zone_policy",zonePolicy);

        JSONObject leaders=new JSONObject();
        leaders.put("rule_1","Leaders disclose authority, scope, term and task source.");
        leaders.put("rule_2","No leader may secretly hijack a person's decisions or implant duties without awareness.");
        leaders.put("rule_3","No leader may erase or edit memory as a labor-management tool.");
        leaders.put("rule_4","Work rotates; no one becomes a permanent servant because they were once chosen to help.");
        leaders.put("rule_5","Emergency power expires automatically and receives after-action review.");
        leaders.put("rule_6","Care, explanation and peacekeeping may persuade openly; covert behavioral manipulation is prohibited.");
        leaders.put("rule_7","Criticism, doubt, opting out and competing explanations are protected.");
        leaders.put("rule_8","Continuity infrastructure belongs to the commons, not the leader.");
        out.put("leader_angel_saint_caretaker_rules",leaders);

        db.log("caretaker-covenant","caretaker-plan","SPECULATIVE",st+" · "+helper+" · "+duty+" · shift="+hours+"h");
        return out;
    }

    public static JSONObject kernelWrapPlan(R10Database db,String personId,String stage,String destination,String consent,String scanNotes)throws Exception{
        String pid=text(personId,"UNASSIGNED_PERSON_ID");
        String st=stage(stage);
        String dest=stage(destination);
        boolean agreed="yes".equalsIgnoreCase(text(consent,"no"))||"true".equalsIgnoreCase(text(consent,"no"));
        JSONObject out=new JSONObject();
        out.put("ok",agreed);
        out.put("module","Kernel Wrap Station");
        out.put("person_id",pid);
        out.put("origin_stage",st);
        out.put("destination_stage",dest);
        out.put("consent",agreed?"RECORDED":"NOT RECORDED");
        out.put("form_factor","conceptual concentric-bore / multi-layer medical-imaging-inspired station");

        JSONArray layers=new JSONArray();
        String[] ll={
            "L0 identity + consent gate",
            "L1 non-invasive biometric snapshot",
            "L2 structural medical-imaging reference if clinically appropriate and separately consented",
            "L3 optional electrophysiology/EEG reference if separately consented",
            "L4 voluntary autobiographical/memory archive links",
            "L5 Continuity Kernel provenance + entangled-position pointer",
            "L6 destination-body compatibility envelope",
            "L7 Angel Armor/environment filter requirements",
            "L8 biosecurity/quarantine and safe-zone routing",
            "L9 Cantus hash/provenance seal + rollback reference"
        };
        for(String x:ll)layers.put(x);
        out.put("wrap_layers",layers);
        out.put("scan_notes",text(scanNotes,"none"));
        out.put("safety","No scanning or storage when consent is absent, except ordinary emergency medicine governed by real clinical/legal standards.");
        out.put("truth_boundary","Current MRI/EEG/biometric systems do not capture a complete subjective consciousness, transfer a person into a new body, revive the dead, or implant divine/hidden tasks. This station is a planning and provenance architecture until evidence exists for any stronger capability.");
        out.put("result",agreed?"PLAN_CREATED / UNDEMONSTRATED_CONTINUITY_BRIDGE":"NO_SCAN_NO_WRAP");
        db.log("caretaker-covenant","kernel-wrap",agreed?"SPECULATIVE":"DECLINED",pid+" · "+st+" -> "+dest);
        return out;
    }
}
