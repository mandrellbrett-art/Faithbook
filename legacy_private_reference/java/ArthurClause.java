package com.arkforge.faith;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Arthur Clause: a speculative continuity-kernel planner inside Angel Core.
 *
 * This module can preserve identity/history records, generate compatibility
 * and relocation plans, and maintain redundant hash-linked kernels. It does
 * NOT claim that a person's subjective consciousness can currently be scanned,
 * uploaded, transferred at death, or implanted into a new biological vessel.
 */
public final class ArthurClause {
    private static final Pattern STAGE = Pattern.compile("^[A-Z](?:[0-9]|10)$");
    private ArthurClause(){}

    private static String cleanStage(String raw){
        String s=(raw==null?"":raw.trim().toUpperCase(Locale.US));
        if(!STAGE.matcher(s).matches()) throw new IllegalArgumentException("Stage must look like A0 through Z10.");
        return s;
    }
    private static String text(String raw,String fallback){
        return raw==null||raw.trim().isEmpty()?fallback:raw.trim();
    }

    public static JSONObject plan(
            R10Database db,
            String stage,
            String dayVariant,
            String personId,
            String originLocation,
            String ageAtScan,
            String morphologyHypothesis,
            String lifespanModel,
            String historySummary,
            String targetStage,
            String targetExistenceGoals
    ) throws Exception {
        String s=cleanStage(stage);
        String target=cleanStage(targetStage);
        String day=text(dayVariant,"UNSPECIFIED");
        String pid=text(personId,"UNASSIGNED_PERSON_ID");
        String origin=text(originLocation,"UNSPECIFIED_ORIGIN");
        String age=text(ageAtScan,"UNSPECIFIED");
        String morph=text(morphologyHypothesis,"UNSPECIFIED / REQUIRES EVIDENCE");
        String lifespan=text(lifespanModel,"UNSPECIFIED / NOT A MEDICAL PREDICTION");
        String history=text(historySummary,"NO_HISTORY_SUMMARY_SUPPLIED");
        String goals=text(targetExistenceGoals,"preserve identity, autonomy, safety, relationships, capability, rest, habitat fit and reversibility");

        JSONObject kernel=new JSONObject();
        kernel.put("kernel_id","AK-"+Integer.toHexString((pid+"|"+s+"|"+day+"|"+origin).hashCode()).toUpperCase(Locale.US));
        kernel.put("person_id",pid);
        kernel.put("origin_stage",s);
        kernel.put("origin_day",day);
        kernel.put("origin_location",origin);
        kernel.put("age_at_record",age);
        kernel.put("morphology_hypothesis",morph);
        kernel.put("lifespan_model",lifespan);
        kernel.put("history_summary",history);
        kernel.put("identity_policy","The kernel is a continuity record for this person; it is not the person and does not own the person.");
        kernel.put("replication_model","HASH_LINKED_REDUNDANT_KERNELS; 'entangled' is a project term, not a claim of quantum entanglement.");

        JSONArray preserved=new JSONArray();
        String[] preserve={"self-chosen name/identity","consent directives","language/communication","relationships and social context","memories supplied or lawfully recorded","skills and preferences","values and boundaries","medical/sensory/accessibility needs only when voluntarily supplied","stage/day provenance","Cantus history","rest/autonomy preferences","rollback/refuge address"};
        for(String x:preserve) preserved.put(x);
        kernel.put("preservation_fields",preserved);

        JSONArray upgrade=new JSONArray();
        String[] steps={
                "Verify Origin/Core/Destination anchors and Cantus chain",
                "Compare target environment against organism and armor profiles",
                "Update biological quarantine and ecosystem compatibility filters",
                "Select a reversible safe-zone route before any transition",
                "Preserve redundant kernel copies with independent hashes and provenance",
                "Evaluate target vessel/habitat only against verified compatibility evidence",
                "Ask/retain the person's own goals and consent; do not optimize by hidden authority",
                "Run identity-drift review: preserve continuity without forcing morphology sameness",
                "Keep prior kernel version immutable and create an additive upgrade revision",
                "Block promotion if evidence is missing, contradictory, or only hypothetical"
        };
        for(String x:steps) upgrade.put(x);

        JSONObject continuation=new JSONObject();
        continuation.put("target_stage",target);
        continuation.put("goal",goals);
        continuation.put("best_possible_existence_rule","Optimize only within the person's stated goals, consent, dignity, habitat needs, relationships and safety constraints; stage does not determine rank.");
        continuation.put("new_biological_vessel","CONCEPTUAL_ONLY_UNTIL A REAL, ETHICAL, VERIFIED METHOD EXISTS");
        continuation.put("consciousness_capture","UNDEMONSTRATED");
        continuation.put("consciousness_transfer","UNDEMONSTRATED");
        continuation.put("subjective_continuity","NOT ESTABLISHED BY COPYING RECORDS OR RUNNING A SOFTWARE MODEL");
        continuation.put("death_trigger_policy","Biological death may close an origin-stage life record, but software must not claim that consciousness moved into the kernel. A future transfer bridge would require independent evidence and explicit ethical/consent gates.");

        JSONObject fallback=new JSONObject();
        fallback.put("R0","Immediate Safe Zone");
        fallback.put("R1","Biosecurity Refuge");
        fallback.put("R2","Habitat Match");
        fallback.put("R3","Origin Rollback / last verified checkpoint");
        fallback.put("R4","Ark Refuge");
        fallback.put("RH","Heaven Refuge model");

        JSONObject out=new JSONObject();
        out.put("ok",true);
        out.put("module","Angel Core / Arthur Clause");
        out.put("status","SPECULATIVE_CONTINUITY_PLAN");
        out.put("arthur_role","Stage surveyor and continuity custodian. For person-class beings, scanning/recording follows consent and privacy except immediate life-safety stabilization.");
        out.put("kernel",kernel);
        out.put("upgrade_plan",upgrade);
        out.put("continuation",continuation);
        out.put("relocation_circuits",fallback);
        out.put("truth_boundary","The kernel can preserve records, provenance, preferences and compatibility plans. Current science has not demonstrated capture of a person's consciousness at death or transfer of subjective experience into a new biological body.");
        db.log("angel-core","arthur-kernel-plan","SPECULATIVE",pid+" · "+s+"/"+day+" -> "+target+" · origin="+origin);
        return out;
    }
}
