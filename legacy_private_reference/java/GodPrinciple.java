package com.arkforge.faith;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Arthur Clause V — God Principle / Human-Friendly Stewardship.
 *
 * This is a governance, provenance, and update-control layer. It can record a
 * person's faith interpretation of inspiration, but it never treats a claim of
 * divine selection as technical proof or as authority to bypass consent,
 * evidence, term limits, due process, or civil rights.
 */
public final class GodPrinciple {
    private static final Pattern STAGE = Pattern.compile("^[A-Z](?:[0-9]|10)$");
    private GodPrinciple(){}
    private static String text(String raw,String fallback){return raw==null||raw.trim().isEmpty()?fallback:raw.trim();}
    private static String stage(String raw){String s=text(raw,"A0").toUpperCase(Locale.US);if(!STAGE.matcher(s).matches())throw new IllegalArgumentException("Stage must look like A0 through Z10.");return s;}
    private static boolean yes(String raw){String x=text(raw,"no").toLowerCase(Locale.US);return x.equals("yes")||x.equals("true")||x.equals("1");}

    public static JSONObject updateProposal(R10Database db,String stage,String proposer,String faithInterpretation,String sensoryIntake,String uploadEvidence,String requestedChange,String affectedPeople,String rollbackPlan)throws Exception{
        String st=stage(stage), who=text(proposer,"UNASSIGNED_STEWARD");
        JSONObject out=new JSONObject(); out.put("ok",true); out.put("module","Arthur Clause V / God Principle"); out.put("stage",st); out.put("proposer",who);
        out.put("faith_interpretation",text(faithInterpretation,"none supplied"));
        out.put("sensory_intake",text(sensoryIntake,"no sensory/source notes supplied"));
        out.put("uploaded_evidence",text(uploadEvidence,"no upload/source reference supplied"));
        out.put("requested_change",text(requestedChange,"no change supplied"));
        out.put("affected_people",text(affectedPeople,"unknown / review required"));
        out.put("rollback_plan",text(rollbackPlan,"required before promotion"));
        out.put("truth_boundary","A person may interpret an idea through faith as divine inspiration. Angel Core records that interpretation without asserting that God, an angel, intelligence service, or hidden actor sent the idea. A claim of being chosen by God never grants administrator privileges by itself.");
        JSONArray path=new JSONArray();
        path.put("capture sensory/source input without altering the live system");
        path.put("preserve original evidence + provenance hashes");
        path.put("separate observation from interpretation");
        path.put("run the proposal in simulation/sandbox");
        path.put("publish risks, affected rights, alternatives and uncertainty");
        path.put("independent review + quorum approval");
        path.put("limited canary deployment");
        path.put("health/human-friendliness check");
        path.put("rollback automatically on serious harm, corruption or unexplained divergence");
        path.put("Cantus closeout with dissent and minority reports preserved");
        out.put("update_path",path);
        out.put("promotion_state","PROPOSAL_ONLY / NOT DIVINELY VERIFIED / NOT AUTO-PROMOTED");
        db.log("god-principle","update-proposal","PROPOSAL",st+" · "+who+" · "+text(requestedChange,"no change")); return out;
    }

    public static JSONObject leaderReview(R10Database db,String stage,String leader,String termYears,String concern,String evidence,String proposedAction,String emergency,String reviewerCount)throws Exception{
        String st=stage(stage), name=text(leader,"UNASSIGNED_LEADER"), issue=text(concern,"no concern supplied");
        int years=4;try{years=Integer.parseInt(text(termYears,"4"));}catch(Exception ignored){}years=Math.max(0,Math.min(years,100));
        int reviewers=7;try{reviewers=Integer.parseInt(text(reviewerCount,"7"));}catch(Exception ignored){}reviewers=Math.max(3,Math.min(reviewers,99));
        boolean emerg=yes(emergency);
        JSONObject out=new JSONObject();out.put("ok",true);out.put("module","God Principle / Leader Removal & Recall");out.put("stage",st);out.put("leader",name);out.put("term_years",years);out.put("concern",issue);out.put("evidence",text(evidence,"evidence required"));out.put("proposed_action",text(proposedAction,"review / recall vote"));out.put("emergency",emerg);out.put("independent_reviewers",reviewers);
        JSONArray causes=new JSONArray();String[] cc={
            "term expiration or voluntary resignation",
            "loss of election/recall vote under published rules",
            "incapacity with independent medical/legal review and temporary succession",
            "proven corruption, violence, sabotage, coercion, rights abuse or material falsification",
            "persistent refusal to obey constitutional constraints after due process",
            "serious conflicts of interest that cannot be mitigated"
        };for(String x:cc)causes.put(x);out.put("legitimate_removal_causes",causes);
        JSONArray guards=new JSONArray();String[] gg={
            "No removal because a leader is unpopular, socially awkward, from the wrong party/religion, or criticized by a powerful clique.",
            "No divine-mandate exception: claiming God chose someone cannot cancel elections, term limits, courts, consent or rights.",
            "No high-school domination tactics: hazing, humiliation, popularity ranking, rumor campaigns, social exclusion and loyalty tests are governance failures, not leadership tools.",
            "Emergency suspension must be narrow, time-limited, logged, independently reviewed, and automatically expire unless renewed through ordinary law.",
            "The accused leader can see the evidence, answer it, have representation, appeal, and preserve a Cantus record of dissent.",
            "Succession cannot transfer ownership of people, kernels, archives, Angel Core, the Round Table or the Commons to the replacement leader."
        };for(String x:gg)guards.put(x);out.put("anti_dictatorship_guards",guards);
        out.put("result","REVIEW_PLAN_CREATED / NO AUTOMATIC REMOVAL");
        db.log("god-principle","leader-review","REVIEW",st+" · "+name+" · "+issue);return out;
    }

    public static JSONObject humanityTest(R10Database db,String stage,String proposal,String voluntary,String reversible,String transparent,String rightsImpact,String dissentPath)throws Exception{
        String st=stage(stage);boolean v=yes(voluntary),r=yes(reversible),t=yes(transparent);
        JSONObject out=new JSONObject();out.put("ok",true);out.put("module","God Principle / Human-Friendly Test");out.put("stage",st);out.put("proposal",text(proposal,"unspecified proposal"));out.put("voluntary_where_possible",v);out.put("reversible",r);out.put("transparent",t);out.put("rights_impact",text(rightsImpact,"review required"));out.put("dissent_and_appeal_path",text(dissentPath,"required"));
        boolean pass=v&&r&&t&&!text(rightsImpact,"").toLowerCase(Locale.US).contains("erase rights");
        out.put("human_friendly_gate",pass?"PASS_FOR_FURTHER_REVIEW":"HOLD / REVISE");
        out.put("principle","Humanity is not a resource pool for leaders. Systems must reduce domination, preserve ordinary life, protect family/chosen-family, allow rest, keep dissent safe, and remain repairable by people other than their original designers.");
        out.put("faith_boundary","The theological layer may name God as the ultimate moral reference. Operational authority remains evidence-based, auditable, limited and human-reviewable.");
        db.log("god-principle","humanity-test",pass?"PASS_FOR_REVIEW":"HOLD",st+" · "+text(proposal,"unspecified"));return out;
    }
}
