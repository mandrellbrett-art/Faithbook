package com.arkforge.faith;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Round Table Continuity Commons.
 * Governance/lore and continuity planning only. This module does not claim
 * demonstrated resurrection, consciousness transfer, telepathic brainwave
 * networking, supernatural stage travel, or hidden real-world organizations.
 */
public final class StewardshipCommons {
    private static final Pattern STAGE = Pattern.compile("^[A-Z](?:[0-9]|10)$");
    private StewardshipCommons(){}
    private static String text(String raw,String fallback){return raw==null||raw.trim().isEmpty()?fallback:raw.trim();}
    private static String stage(String raw){String s=text(raw,"A0").toUpperCase(Locale.US);if(!STAGE.matcher(s).matches())throw new IllegalArgumentException("Stage must look like A0 through Z10.");return s;}

    public static JSONObject stagePlan(R10Database db,String stage,String eraReference,String primaryLeader,String councilModel,String arthurId,String healthCadence,String publicGuidancePolicy)throws Exception{
        String st=stage(stage);
        String era=text(eraReference,"UNSPECIFIED 100-YEAR CIVILIZATION THRESHOLD");
        String leader=text(primaryLeader,"ELECTED / STAGE-CHOSEN PRIMARY STEWARD");
        String council=text(councilModel,"1 commanding steward + 99 maintenance/teaching/repair stewards");
        String arthur=text(arthurId,"ARTHUR-"+st);
        String cadence=text(healthCadence,"daily voluntary wellbeing check");
        String guidance=text(publicGuidancePolicy,"transparent public information, plural viewpoints, no covert manipulation");

        JSONObject out=new JSONObject();
        out.put("ok",true);
        out.put("module","Angel Core / Round Table Continuity Commons");
        out.put("stage",st);
        out.put("era_reference",era);
        out.put("stage_arthur",arthur);
        out.put("angel_kernel","ANGEL-KERNEL-"+st);
        out.put("primary_steward",leader);
        out.put("term_model","Default civic term: 4 years for ordinary stage leadership; optional century-scale threshold is a historical/reference window, not a permanent ruler.");
        out.put("council",council);

        JSONArray roles=new JSONArray();
        String[] rr={"continuity maintenance","public explanation and education","habitat/ecology stewardship","archive and provenance","health/accessibility coordination","conflict mediation","infrastructure repair","Ark/seed/genetic-bank maintenance","family/chosen-family reunification","safe-zone and relocation readiness","science/engineering review","arts/culture/history"};
        for(String x:rr)roles.put(x);
        out.put("steward_roles",roles);

        JSONObject checks=new JSONObject();
        checks.put("cadence",cadence);
        checks.put("consent","REQUIRED except immediate emergency stabilization allowed by ordinary law/ethics");
        checks.put("scope","self-report, ordinary sensors, or voluntarily connected health data; non-diagnostic unless handled by qualified medical systems");
        checks.put("revival_status","UNDEMONSTRATED — checkpoint may preserve continuity evidence and trigger care/safe-zone routing, but cannot claim resurrection");
        out.put("wellbeing_checks",checks);

        JSONObject governance=new JSONObject();
        governance.put("round_table","leadership and role selection are auditable, revisable and protected by due process");
        governance.put("white_house_reference","May serve in lore as a civic/stewardship reference point for a stage, not as hereditary ownership of humanity or a universal monarchy");
        governance.put("archetypal_figureheads","Arthur, Merlin, Jesus, Buddha, Odin, Zeus and similar names may label fictional/archetypal offices or cultural teaching traditions; the software does not assert that such figures secretly govern the real world");
        governance.put("public_guidance",guidance);
        governance.put("anti_manipulation","No covert opinion control, secret psychological steering, compelled neural synchronization or 'Illuminati' authority. Public guidance must disclose source, purpose, uncertainty and dissenting views.");
        governance.put("personhood","continuity access is not conditional on wealth, fame, obedience, religion, usefulness or political status");
        governance.put("mistakes","serious wrongdoing receives accountability, victim protection, due process and rehabilitation opportunities; no automatic restoration of power and no eternal dehumanization");
        out.put("governance",governance);

        JSONObject mesh=new JSONObject();
        mesh.put("biological_mesh_goal","research/lore goal: resilient person-to-person continuity support that does not depend on wealth or a single computer");
        mesh.put("current_science_boundary","There is no demonstrated method for a 3-mile communal brainwave mesh to store a person's consciousness, revive the dead, or impart minds between people.");
        mesh.put("safe_present_day_analogue","voluntary community records, encrypted redundant archives, emergency contacts, family/chosen-family memory, health directives, skills and provenance");
        out.put("continuity_mesh",mesh);

        db.log("continuity-commons","stage-plan","SPECULATIVE",st+" · leader="+leader+" · "+council);
        return out;
    }

    public static JSONObject healthCheck(R10Database db,String personId,String stage,String selfReportedStatus,String consent)throws Exception{
        String pid=text(personId,"UNASSIGNED_PERSON_ID");
        String st=stage(stage);
        String c=text(consent,"no");
        boolean agreed="yes".equalsIgnoreCase(c)||"true".equalsIgnoreCase(c)||"consent".equalsIgnoreCase(c);
        JSONObject out=new JSONObject();
        out.put("ok",agreed);
        out.put("person_id",pid);
        out.put("stage",st);
        out.put("status",text(selfReportedStatus,"NO STATUS SUPPLIED"));
        out.put("consent",agreed?"RECORDED":"NOT RECORDED");
        out.put("medical_boundary","This is a continuity/wellbeing log, not a diagnosis or autonomous medical scan.");
        out.put("action",agreed?"LOG_AND_REVIEW_IF_REQUESTED":"NO COLLECTION");
        db.log("continuity-commons","health-check",agreed?"VOLUNTARY":"DECLINED",pid+" · "+st);
        return out;
    }
}
