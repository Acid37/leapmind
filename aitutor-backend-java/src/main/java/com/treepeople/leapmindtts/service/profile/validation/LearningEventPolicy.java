package com.treepeople.leapmindtts.service.profile.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.FieldViolation;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class LearningEventPolicy {
    private static final Map<String, String> SOURCES = Map.ofEntries(
            Map.entry("answer_question", "M1"), Map.entry("finish_practice", "M1"),
            Map.entry("request_explanation", "M2"), Map.entry("explanation_feedback", "M2"),
            Map.entry("weak_point_changed", "M3"), Map.entry("lecture_interact", "M4"),
            Map.entry("lesson_material_used", "M5"), Map.entry("ask_doubt", "M7"),
            Map.entry("mark_reviewed", "M6"), Map.entry("preference_changed", "M6"));
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "passwd", "pwd", "token", "accesstoken",
            "refreshtoken", "authorization", "idcard", "nationalid", "identitynumber", "privatekey",
            "secret", "clientsecret", "apikey", "credential", "credentials");
    private static final Pattern AUTHORIZATION_CREDENTIAL = Pattern.compile(
            "(?i)\\b(?:bearer|basic)[ \\t]+[A-Za-z0-9._~+/=-]{8,}\\b");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern PEM_PRIVATE_KEY = Pattern.compile(
            "-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----");
    private static final Pattern CHINESE_NATIONAL_ID = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern HIGH_ENTROPY_CANDIDATE = Pattern.compile(
            "(?<![A-Za-z0-9_+/\\-])(?<candidate>[A-Za-z0-9_+/\\-]+={0,2})(?![A-Za-z0-9_+/\\-=])");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern ULID = Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{16}(?:[0-9a-f]{16})?", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CONFUSION = Set.of("concept_unclear", "formula_confusion", "step_unclear", "application_difficulty", "careless_error");
    private static final Set<String> EXPLANATION_REASON = Set.of("WRONG_ANSWER", "REPEATED_ERROR", "USER_REQUEST", "LOW_CONFIDENCE", "REVIEW_NEEDED");
    private static final Set<String> WEAK_REASON = Set.of("ACCURACY_DROP", "REPEATED_ERROR", "TEACHER_MARKED", "RECALCULATED");

    private LearningEventPolicy() { }

    public static void validate(LearningEventRequest event) {
        invalid(!"1.0".equals(event.schemaVersion()), "PROFILE_EVENT_VERSION_UNSUPPORTED", "schemaVersion", "UNSUPPORTED");
        String source = SOURCES.get(event.eventType());
        invalid(source == null, "PROFILE_EVENT_TYPE_UNSUPPORTED", "eventType", "UNSUPPORTED");
        invalid(!source.equals(event.sourceModule()), "PROFILE_EVENT_INVALID", "sourceModule", "INVALID");
        invalid(event.data() == null || !event.data().isObject(), "PROFILE_EVENT_INVALID", "data", "INVALID");
        checkIdentifier(event.eventId(), "eventId");
        checkOptionalIdentifier(event.sessionId(), "sessionId");
        checkOptionalIdentifier(event.traceId(), "traceId");
        scan(event.data(), "data", event.eventType(), 0, new int[]{0});
        validateData(event);
        checkEnvelopeValue(event.eventId(), "eventId");
        checkEnvelopeValue(event.sessionId(), "sessionId");
        checkEnvelopeValue(event.traceId(), "traceId");
        invalid(EventPayloadCanonicalizer.canonical(event.data()).length > 16 * 1024, "PROFILE_EVENT_INVALID", "data", "TOO_LARGE");
    }

    private static void validateData(LearningEventRequest e) {
        JsonNode d = e.data();
        switch (e.eventType()) {
            case "answer_question" -> {
                keys(d, Set.of("isCorrect", "difficulty", "timeSpentSec", "hintCount", "confusionTag"));
                bool(d, "isCorrect", true); integer(d, "difficulty", 1, 5, true);
                integer(d, "timeSpentSec", 0, 86400, true); integer(d, "hintCount", 0, 100, true);
                enumeration(d, "confusionTag", CONFUSION, false);
            }
            case "finish_practice" -> {
                keys(d, Set.of("questionCount", "accuracy", "durationSec"));
                integer(d, "questionCount", 1, 10000, true); decimal(d, "accuracy", 0, 1, true);
                integer(d, "durationSec", 0, 86400, true);
            }
            case "request_explanation" -> {
                keys(d, Set.of("explainId", "reasonTag")); identifier(d, "explainId", true);
                enumeration(d, "reasonTag", EXPLANATION_REASON, true);
            }
            case "explanation_feedback" -> {
                keys(d, Set.of("explainId", "feedback", "repeatCount")); identifier(d, "explainId", true);
                enumeration(d, "feedback", Set.of("understood", "partly_understood", "still_confused"), true);
                integer(d, "repeatCount", 0, 100, true);
            }
            case "weak_point_changed" -> {
                keys(d, Set.of("oldScore", "newScore", "reason")); decimal(d, "oldScore", 0, 1, true);
                decimal(d, "newScore", 0, 1, true); enumeration(d, "reason", WEAK_REASON, true);
            }
            case "lecture_interact" -> {
                keys(d, Set.of("lectureId", "chapterId", "action")); identifier(d, "lectureId", true);
                identifier(d, "chapterId", true); enumeration(d, "action", Set.of("pause", "resume", "replay", "ask", "complete"), true);
            }
            case "lesson_material_used" -> {
                keys(d, Set.of("contentId", "materialType", "result")); identifier(d, "contentId", true);
                enumeration(d, "materialType", Set.of("text", "image", "audio", "video", "exercise"), true);
                enumeration(d, "result", Set.of("completed", "skipped", "helpful", "not_helpful"), true);
            }
            case "ask_doubt" -> {
                keys(d, Set.of("topic", "confusionTag", "isFollowUp"));
                invalid(text(d, "topic", true, 120).isBlank(), "PROFILE_EVENT_INVALID", "data.topic", "INVALID");
                enumeration(d, "confusionTag", CONFUSION, true); bool(d, "isFollowUp", true);
            }
            case "mark_reviewed" -> {
                keys(d, Set.of("result", "timeSpentSec", "hintCount"));
                enumeration(d, "result", Set.of("correct_without_hint", "correct_with_hint", "incorrect", "still_confused", "postponed"), true);
                integer(d, "timeSpentSec", 0, 86400, true); integer(d, "hintCount", 0, 100, true);
            }
            case "preference_changed" -> {
                keys(d, Set.of("preferenceKey", "preferenceValue"));
                String key = text(d, "preferenceKey", true);
                Set<String> values = switch (key) {
                    case "content_mode" -> Set.of("text", "image", "audio", "video", "exercise");
                    case "explanation_style" -> Set.of("step_by_step", "example_first", "concise", "detailed");
                    case "learning_pace" -> Set.of("slow", "moderate", "fast");
                    default -> Set.of();
                };
                invalid(values.isEmpty(), "PROFILE_EVENT_INVALID", "data.preferenceKey", "INVALID");
                enumeration(d, "preferenceValue", values, true);
            }
            default -> throw new IllegalStateException("validated event type missing schema");
        }
    }

    private static void keys(JsonNode n, Set<String> allowed) {
        n.fieldNames().forEachRemaining(k -> invalid(!allowed.contains(k) || sensitiveKey(k),
                "PROFILE_EVENT_INVALID", allowed.contains(k) ? "data." + k : "data", "INVALID"));
    }
    private static void bool(JsonNode d,String n,boolean required){JsonNode v=value(d,n,required);if(v!=null)invalid(!v.isBoolean(),"PROFILE_EVENT_INVALID","data."+n,"INVALID");}
    private static void integer(JsonNode d,String n,int min,int max,boolean required){JsonNode v=value(d,n,required);if(v!=null)invalid(!v.isIntegralNumber()||!v.canConvertToInt()||v.intValue()<min||v.intValue()>max,"PROFILE_EVENT_INVALID","data."+n,"INVALID");}
    private static void decimal(JsonNode d,String n,int min,int max,boolean required){JsonNode v=value(d,n,required);if(v!=null){invalid(!v.isNumber(),"PROFILE_EVENT_INVALID","data."+n,"INVALID");BigDecimal value=v.decimalValue();invalid(value.compareTo(BigDecimal.valueOf(min))<0||value.compareTo(BigDecimal.valueOf(max))>0,"PROFILE_EVENT_INVALID","data."+n,"INVALID");}}
    private static void enumeration(JsonNode d,String n,Set<String> values,boolean required){String v=text(d,n,required);if(v!=null)invalid(!values.contains(v),"PROFILE_EVENT_INVALID","data."+n,"INVALID");}
    private static void identifier(JsonNode d,String n,boolean required){String v=text(d,n,required);if(v!=null)checkIdentifier(v,"data."+n);}
    private static String text(JsonNode d,String n,boolean required){return text(d,n,required,2048);}
    private static String text(JsonNode d,String n,boolean required,int maxCodePoints){JsonNode v=value(d,n,required);if(v==null)return null;invalid(!v.isTextual()||v.textValue().codePointCount(0,v.textValue().length())>maxCodePoints,"PROFILE_EVENT_INVALID","data."+n,"INVALID");return v.textValue();}
    private static JsonNode value(JsonNode d,String n,boolean required){JsonNode v=d.get(n);invalid(v!=null&&v.isNull(),"PROFILE_EVENT_INVALID","data."+n,"INVALID");invalid(required&&v==null,"PROFILE_EVENT_INVALID","data."+n,"REQUIRED");return v;}
    private static void scan(JsonNode n,String path,String eventType,int depth,int[] count){invalid(depth>8||++count[0]>256,"PROFILE_EVENT_INVALID",path,"INVALID");if(n.isObject()){Iterator<Map.Entry<String,JsonNode>> it=n.fields();while(it.hasNext()){Map.Entry<String,JsonNode> f=it.next();String child=depth==0?trustedDataPath(eventType,f.getKey()):path;invalid(sensitiveKey(f.getKey()),"PROFILE_EVENT_INVALID",child,"INVALID");scan(f.getValue(),child,eventType,depth+1,count);}}else if(n.isArray()){for(JsonNode item:n)scan(item,path+"[item]",eventType,depth+1,count);}else if(n.isTextual()){String v=n.textValue();invalid(v.getBytes(StandardCharsets.UTF_8).length>2048||sensitiveValue(v),"PROFILE_EVENT_INVALID",path,"INVALID");}}
    private static String trustedDataPath(String eventType,String field){Set<String> allowed=switch(eventType){case "answer_question"->Set.of("isCorrect","difficulty","timeSpentSec","hintCount","confusionTag");case "finish_practice"->Set.of("questionCount","accuracy","durationSec");case "request_explanation"->Set.of("explainId","reasonTag");case "explanation_feedback"->Set.of("explainId","feedback","repeatCount");case "weak_point_changed"->Set.of("oldScore","newScore","reason");case "lecture_interact"->Set.of("lectureId","chapterId","action");case "lesson_material_used"->Set.of("contentId","materialType","result");case "ask_doubt"->Set.of("topic","confusionTag","isFollowUp");case "mark_reviewed"->Set.of("result","timeSpentSec","hintCount");case "preference_changed"->Set.of("preferenceKey","preferenceValue");default->Set.of();};return allowed.contains(field)?"data."+field:"data";}
    private static void checkIdentifier(String v,String field){invalid(v==null||!ID.matcher(v).matches(),"PROFILE_EVENT_INVALID",field,"INVALID");}
    private static void checkOptionalIdentifier(String v,String field){if(v!=null)checkIdentifier(v,field);}
    private static void checkEnvelopeValue(String value,String field){if(value!=null)invalid(obviousCredential(value),"PROFILE_EVENT_INVALID",field,"INVALID");}
    private static void invalid(boolean c,String code){if(c)throw new M6ApiException(HttpStatus.BAD_REQUEST,code,"学习事件无效");}
    private static void invalid(boolean condition, String code, String field, String reason) {
        if (condition) {
            throw new M6ApiException(HttpStatus.BAD_REQUEST, code, "Invalid learning event",
                    java.util.List.of(new FieldViolation(field, reason)));
        }
    }
    private static boolean sensitiveKey(String key){String normalized=Normalizer.normalize(key,Normalizer.Form.NFKC).replaceAll("[^A-Za-z0-9]","").toLowerCase(Locale.ROOT);return SENSITIVE_KEYS.contains(normalized);}
    private static boolean sensitiveValue(String value){
        String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC);
        if(obviousCredential(normalized))return true;
        var candidates=HIGH_ENTROPY_CANDIDATE.matcher(normalized);
        while(candidates.find()){
            String candidate=candidates.group("candidate");
            if(candidate.length()>=36&&!knownIdentifier(candidate)&&shannonEntropy(candidate)>=4.0d)return true;
        }
        return false;
    }
    private static boolean obviousCredential(String value){String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC);return AUTHORIZATION_CREDENTIAL.matcher(normalized).find()||JWT.matcher(normalized).find()||PEM_PRIVATE_KEY.matcher(normalized).find()||CHINESE_NATIONAL_ID.matcher(normalized).find();}
    private static boolean knownIdentifier(String value){
        return UUID.matcher(value).matches()||ULID.matcher(value).matches()||TRACE_ID.matcher(value).matches();
    }
    private static double shannonEntropy(String value){int[] frequencies=new int[128];for(int i=0;i<value.length();i++)frequencies[value.charAt(i)]++;double entropy=0;for(int frequency:frequencies)if(frequency>0){double probability=(double)frequency/value.length();entropy-=probability*(Math.log(probability)/Math.log(2));}return entropy;}
}
