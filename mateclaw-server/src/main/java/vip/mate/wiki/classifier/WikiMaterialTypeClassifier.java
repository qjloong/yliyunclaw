package vip.mate.wiki.classifier;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vip.mate.agent.AgentGraphBuilder;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.wiki.dto.WikiDomainProfileMaterialType;
import vip.mate.wiki.dto.WikiDomainProfileOption;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.service.WikiDomainProfileRegistryService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Automatic material type classifier for business-domain knowledge bases.
 * <p>
 * Uses a hybrid strategy:
 * <ol>
 *   <li><b>Rule classifier</b> (fast O(1)): matches file name, title, and text
 *       preview against keyword heuristics.</li>
 *   <li><b>LLM classifier</b> (on-demand): invoked only when rule confidence
 *       is MEDIUM or LOW, or when the text preview is ambiguous.</li>
 * </ol>
 * <p>
 * <b>Important constraint</b>: classification is only triggered when the
 * knowledge base has a bound {@code domainProfileId}. General KBs skip
 * classification and return {@code null}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikiMaterialTypeClassifier {

    private final WikiDomainProfileRegistryService profileRegistry;
    private final WikiStructureExtractor structureExtractor;
    private final ModelConfigService modelConfigService;
    private final AgentGraphBuilder agentGraphBuilder;

    /** Maximum characters sent to the LLM classifier (keeps cost low). */
    private static final int LLM_PREVIEW_LIMIT = 2000;

    // ---------- Rule patterns ----------

    private static final Pattern P_CURRICULUM = Pattern.compile(
            "(课程标准|课标|义务教育.*标准|普通高中.*标准|教学大纲)");
    private static final Pattern P_TEXTBOOK = Pattern.compile(
            "(教材|课本|教科书|语文.*七年级|语文.*八年级|语文.*九年级|部编版|统编版)");
    private static final Pattern P_CLASSIC = Pattern.compile(
            "(名著|《.*》|整本书阅读|西游记|朝花夕拾|骆驼祥子|海底两万里|红星照耀中国|昆虫记|钢铁是怎样炼成的)");
    private static final Pattern P_SAMPLE_QUESTION = Pattern.compile(
            "(样题|真题|试卷|模拟卷|期中.*卷|期末.*卷|中考.*题|单元.*测)");
    private static final Pattern P_QUESTION_RULE = Pattern.compile(
            "(题型|出题要求|命题要求|评分标准|采分点|答题规范|考试说明)");
    private static final Pattern P_ANSWER_RUBRIC = Pattern.compile(
            "(答案|评分标准|参考答案|标准答案|评分细则|阅卷标准)");

    /**
     * Classify raw material for a given knowledge base.
     *
     * @param kb        the knowledge base (must have domainProfileId to trigger)
     * @param title     material title or file name
     * @param textPreview first N characters of extracted text (may be null)
     * @return classification result, or {@code null} if kb is general or no profile found
     */
    public MaterialTypeClassifyResult classify(WikiKnowledgeBaseEntity kb, String title, String textPreview) {
        if (kb == null || !StringUtils.hasText(kb.getDomainProfileId())) {
            log.debug("[WikiClassifier] Skipping classification: kb is general or domainProfileId is empty");
            return null;
        }

        WikiDomainProfileOption profile = profileRegistry.findProfile(kb.getDomainProfileId());
        if (profile == null) {
            log.warn("[WikiClassifier] Unknown domainProfileId={}, skipping classification", kb.getDomainProfileId());
            return null;
        }

        List<String> allowedTypes = profile.materialTypes().stream()
                .map(WikiDomainProfileMaterialType::value)
                .toList();

        // Step 1: Rule-based classification
        RuleResult ruleResult = classifyByRules(title, textPreview, allowedTypes);

        // Step 2: If HIGH confidence and type is allowed, accept directly
        if ("HIGH".equals(ruleResult.confidence) && allowedTypes.contains(ruleResult.materialType)) {
            JSONObject fields = structureExtractor.extract(textPreview, title, ruleResult.materialType);
            String metadataJson = buildMetadataJson(ruleResult.materialType, fields);
            log.info("[WikiClassifier] Rule-based HIGH confidence: kbId={}, type={}, title={}",
                    kb.getId(), ruleResult.materialType, title);
            return new MaterialTypeClassifyResult(ruleResult.materialType, "HIGH", metadataJson, ruleResult.reason);
        }

        // Step 3: If MEDIUM/LOW or rule type not allowed, invoke LLM
        MaterialTypeClassifyResult llmResult = classifyByLlm(title, textPreview, allowedTypes);
        if (llmResult != null) {
            // Merge LLM fields with rule extractor fallback
            JSONObject llmFields = parseLlmFields(llmResult.materialMetadataJson());
            JSONObject ruleFields = structureExtractor.extract(textPreview, title, llmResult.materialType());
            // Rule fields fill gaps left by LLM
            for (String key : ruleFields.keySet()) {
                if (!llmFields.containsKey(key) || !StringUtils.hasText(llmFields.getStr(key))) {
                    llmFields.set(key, ruleFields.get(key));
                }
            }
            String mergedMetadata = buildMetadataJson(llmResult.materialType(), llmFields);
            log.info("[WikiClassifier] LLM-assisted classification: kbId={}, type={}, confidence={}, title={}",
                    kb.getId(), llmResult.materialType(), llmResult.confidence(), title);
            return new MaterialTypeClassifyResult(
                    llmResult.materialType(), llmResult.confidence(), mergedMetadata,
                    llmResult.reason() + " (rule fallback applied)");
        }

        // Step 4: Fallback to general if nothing matched
        log.info("[WikiClassifier] No clear classification, fallback to general: kbId={}, title={}", kb.getId(), title);
        return new MaterialTypeClassifyResult("general", "LOW", null,
                "No matching patterns or LLM inference failed; defaulted to general.");
    }

    // ---------- Rule engine ----------

    private RuleResult classifyByRules(String title, String textPreview, List<String> allowedTypes) {
        String probe = ((title == null ? "" : title) + " " + (textPreview == null ? "" : textPreview))
                .toLowerCase(Locale.ROOT);

        int maxScore = 0;
        String bestType = "general";
        String bestReason = "No strong pattern matched";

        Object[][] checks = {
                {score(P_CURRICULUM, probe), 3, "curriculum_standard", "Matched curriculum standard keywords"},
                {score(P_TEXTBOOK, probe), 4, "textbook_latest", "Matched textbook keywords"},
                {score(P_CLASSIC, probe), 4, "classic_manuscript", "Matched classic manuscript keywords"},
                {score(P_SAMPLE_QUESTION, probe), 3, "sample_question", "Matched sample question keywords"},
                {score(P_QUESTION_RULE, probe), 3, "question_rule", "Matched question rule keywords"},
                {score(P_ANSWER_RUBRIC, probe), 3, "answer_rubric", "Matched answer rubric keywords"},
        };

        for (Object[] c : checks) {
            int s = (int) c[0];
            if (s > maxScore) {
                maxScore = s;
                bestType = (String) c[2];
                bestReason = (String) c[3];
            }
        }

        // Determine confidence
        String confidence;
        if (maxScore >= 4) {
            confidence = "HIGH";
        } else if (maxScore >= 2) {
            confidence = "MEDIUM";
        } else {
            confidence = "LOW";
        }

        // If best type is not allowed, downgrade confidence
        if (!allowedTypes.contains(bestType)) {
            confidence = "LOW";
            bestReason += " (type not allowed by profile)";
        }

        return new RuleResult(bestType, confidence, bestReason, maxScore);
    }

    private int score(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) return 0;
        int count = 0;
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private record RuleResult(String materialType, String confidence, String reason, int score) {
    }

    // ---------- LLM classifier ----------

    private MaterialTypeClassifyResult classifyByLlm(String title, String textPreview, List<String> allowedTypes) {
        try {
            ModelConfigEntity model = modelConfigService.getDefaultModel();
            if (model == null) {
                log.warn("[WikiClassifier] No default chat model available; skipping LLM classification");
                return null;
            }

            ChatModel chatModel = agentGraphBuilder.buildRuntimeChatModel(model);
            String preview = StringUtils.hasText(textPreview)
                    ? textPreview.substring(0, Math.min(textPreview.length(), LLM_PREVIEW_LIMIT))
                    : "";

            String systemPrompt = buildLlmSystemPrompt(allowedTypes);
            String userPrompt = buildLlmUserPrompt(title, preview);

            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
            ChatResponse response = chatModel.call(prompt);

            if (response == null || response.getResult() == null
                    || response.getResult().getOutput() == null
                    || response.getResult().getOutput().getText() == null) {
                log.warn("[WikiClassifier] LLM returned empty response for title={}", title);
                return null;
            }

            String raw = response.getResult().getOutput().getText().trim();
            // Extract JSON from possible markdown fences
            String jsonText = extractJsonBlock(raw);
            if (!JSONUtil.isTypeJSON(jsonText)) {
                log.warn("[WikiClassifier] LLM response is not valid JSON: {}", raw.substring(0, Math.min(raw.length(), 200)));
                return null;
            }

            JSONObject obj = JSONUtil.parseObj(jsonText);
            String materialType = obj.getStr("materialType", "general").trim().toLowerCase();
            String confidence = obj.getStr("confidence", "LOW").trim().toUpperCase();
            if (!List.of("HIGH", "MEDIUM", "LOW").contains(confidence)) {
                confidence = "LOW";
            }
            String reason = obj.getStr("reason", "LLM inferred");
            JSONObject fields = obj.getJSONObject("fields");
            String metadataJson = buildMetadataJson(materialType, fields);

            return new MaterialTypeClassifyResult(materialType, confidence, metadataJson, reason);
        } catch (Exception e) {
            log.warn("[WikiClassifier] LLM classification failed for title={}: {}", title, e.getMessage());
            return null;
        }
    }

    private String buildLlmSystemPrompt(List<String> allowedTypes) {
        return """
                You are an educational material classification assistant.
                Your task is to classify a document into one material type and extract key structure fields.

                Allowed material types:
                """
                + String.join(", ", allowedTypes) + """

                Return ONLY a JSON object with this exact schema (no markdown, no explanations):
                {
                  "materialType": "one of the allowed types",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "fields": {
                    "edition": "",
                    "grade": "",
                    "volume": "",
                    "unit": "",
                    "chapter": "",
                    "classicName": "",
                    "source": ""
                  },
                  "reason": "brief reason in Chinese"
                }

                Rules:
                - HIGH confidence: title and content clearly indicate the type.
                - MEDIUM confidence: strong hints but some ambiguity.
                - LOW confidence: unclear or mixed content.
                - If the document is a complete textbook containing multiple modules (classical Chinese, ancient poetry, classics, modern reading), classify as "textbook_latest".
                - Fill as many structure fields as you can from the text.
                """;
    }

    private String buildLlmUserPrompt(String title, String preview) {
        return "File name / Title: " + (title == null ? "" : title) + "\n\nText preview (first "
                + preview.length() + " chars):\n" + preview;
    }

    // ---------- Helpers ----------

    private String extractJsonBlock(String text) {
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1);
            }
        }
        return text;
    }

    private JSONObject parseLlmFields(String metadataJson) {
        if (!JSONUtil.isTypeJSON(metadataJson)) {
            return new JSONObject();
        }
        JSONObject obj = JSONUtil.parseObj(metadataJson);
        JSONObject fields = new JSONObject();
        for (String key : List.of("edition", "grade", "volume", "unit", "chapter", "classicName", "source")) {
            if (obj.containsKey(key)) {
                fields.set(key, obj.getStr(key));
            }
        }
        return fields;
    }

    private String buildMetadataJson(String materialType, JSONObject fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        JSONObject normalized = new JSONObject();
        normalized.set("materialType", materialType);
        for (String key : fields.keySet()) {
            String val = fields.getStr(key);
            if (StringUtils.hasText(val)) {
                normalized.set(key, val.trim());
            }
        }

        // Build route tags
        Set<String> routeTags = new LinkedHashSet<>();
        routeTags.add(materialType);
        for (String key : List.of("edition", "source", "grade", "volume", "unit", "chapter", "classicName")) {
            String val = normalized.getStr(key);
            if (StringUtils.hasText(val)) {
                routeTags.add(val);
            }
        }
        if (!routeTags.isEmpty()) {
            normalized.set("routeTags", new JSONArray(new java.util.ArrayList<>(routeTags)));
        }

        return JSONUtil.toJsonStr(normalized);
    }
}
