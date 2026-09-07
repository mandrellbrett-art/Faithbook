package com.arkforge.faith;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Angel Core is a conceptual/simulation compatibility evaluator.
 * It intentionally does not claim physical time travel, immortality, or
 * survival in unknown environments. Unknown conditions fail closed.
 */
public final class AngelCore {
    private static final Pattern STAGE = Pattern.compile("^[A-Z](?:[0-9]|10)$");
    private AngelCore(){}

    private static String cleanStage(String raw){
        String s=(raw==null?"":raw.trim().toUpperCase(Locale.US));
        if(!STAGE.matcher(s).matches()) throw new IllegalArgumentException("Stage must look like A0 through Z10.");
        return s;
    }

    public static JSONObject evaluate(R10Database db,String origin,String originDay,String destination,String destinationDay,String organism,String environmentClass,String armorProfile) throws Exception {
        String o=cleanStage(origin), d=cleanStage(destination);
        String od=(originDay==null||originDay.isBlank())?"UNSPECIFIED":originDay.trim();
        String dd=(destinationDay==null||destinationDay.isBlank())?"UNSPECIFIED":destinationDay.trim();
        String org=(organism==null||organism.isBlank())?"UNSPECIFIED_ORGANISM":organism.trim();
        String env=(environmentClass==null||environmentClass.isBlank())?"UNKNOWN":environmentClass.trim();
        String armor=(armorProfile==null||armorProfile.isBlank())?"NO_VERIFIED_PROFILE":armorProfile.trim();

        JSONArray filters=new JSONArray();
        String[] names={"identity/core integrity","atmosphere/breathing medium","pressure","temperature/heat flux","radiation","gravity/acceleration/inertia","chemistry/toxins/corrosion","water/osmotic balance","pathogen/microbiome containment","food/metabolic compatibility","sensory/communication bandwidth","morphology/clearance","energy/power budget","personhood/consent boundary","return/relocation reserve"};
        for(String name:names){JSONObject f=new JSONObject();f.put("filter",name);f.put("state","REQUIRES_DESTINATION_EVIDENCE");filters.put(f);}

        JSONObject proof=new JSONObject();
        proof.put("origin_anchor",o+" / day="+od);
        proof.put("core_anchor","INVARIANT_IDENTITY_REQUIRED");
        proof.put("destination_anchor",d+" / day="+dd+" / environment="+env);
        proof.put("cantus_anchor","LOGGED_ON_EVALUATION");
        proof.put("verification","UNVERIFIED");

        JSONObject relocation=new JSONObject();
        relocation.put("default","R0_IMMEDIATE_SAFE_ZONE");
        relocation.put("biosecurity","R1_BIOSECURITY_REFUGE");
        relocation.put("habitat_mismatch","R2_HABITAT_MATCH");
        relocation.put("failed_proof_or_armor","R3_ORIGIN_ROLLBACK");
        relocation.put("ecosystem_collapse_risk","R4_ARK_REFUGE");
        relocation.put("terminal_model_refuge","RH_HEAVEN_REFUGE");

        JSONObject out=new JSONObject();
        out.put("ok",true);
        out.put("module","Angel Core");
        out.put("status","SIMULATION_ONLY_UNVERIFIED");
        out.put("origin_stage",o);out.put("origin_day",od);
        out.put("destination_stage",d);out.put("destination_day",dd);
        out.put("organism",org);out.put("environment_class",env);out.put("armor_profile",armor);
        out.put("core_policy","Identity preserved; stage is not rank; morphology is not ownership.");
        out.put("plague_gate","QUARANTINE_REQUIRED_UNTIL_BIDIRECTIONAL_BIOSECURITY_IS_VERIFIED");
        out.put("required_filters",filters);
        out.put("proof_triangle",proof);
        out.put("relocation_circuit",relocation);
        out.put("truth_boundary","No physical stage hop is being claimed. Unknown destination physics or biology routes to a safe-zone/review state.");
        db.log("angel-core","stage-evaluate","SIMULATION_ONLY",o+"/"+od+" -> "+d+"/"+dd+" · "+org+" · env="+env);
        return out;
    }
}
