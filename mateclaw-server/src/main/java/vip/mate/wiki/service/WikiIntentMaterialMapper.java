package vip.mate.wiki.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps business module identifiers to preferred material types for intent-aware retrieval.
 * <p>
 * This is a lightweight, domain-agnostic mapper. Each domain profile can register
 * its own mappings via {@link #registerMapping} at startup.
 * <p>
 * Currently populated with the education.exam.junior_chinese domain as the sample.
 */
@Component
public class WikiIntentMaterialMapper {

    private final Map<String, List<String>> moduleToPreferredTypes = new java.util.concurrent.ConcurrentHashMap<>();

    public WikiIntentMaterialMapper() {
        // Default sample mapping for education.exam.junior_chinese
        registerMapping("classic_reading", List.of("classic_manuscript", "textbook_latest",
                "curriculum_standard", "question_rule", "sample_question", "answer_rubric"));
        registerMapping("classical_chinese", List.of("textbook_latest", "curriculum_standard",
                "question_rule", "sample_question", "answer_rubric"));
        registerMapping("ancient_poetry", List.of("textbook_latest", "curriculum_standard",
                "question_rule", "sample_question", "answer_rubric"));
        registerMapping("modern_reading", List.of("textbook_latest", "curriculum_standard",
                "question_rule", "sample_question", "answer_rubric"));
        registerMapping("basic_knowledge", List.of("textbook_latest", "curriculum_standard",
                "question_rule", "sample_question", "answer_rubric"));
        registerMapping("writing", List.of("textbook_latest", "curriculum_standard",
                "question_rule", "sample_question", "answer_rubric"));
        registerMapping("paper_assembly", List.of("textbook_latest", "classic_manuscript", "curriculum_standard",
                "sample_question", "question_rule", "answer_rubric"));
    }

    public void registerMapping(String businessModule, List<String> preferredMaterialTypes) {
        moduleToPreferredTypes.put(businessModule, List.copyOf(preferredMaterialTypes));
    }

    public List<String> resolvePreferredTypes(String businessModule) {
        if (businessModule == null || businessModule.isBlank()) {
            return List.of();
        }
        return moduleToPreferredTypes.getOrDefault(businessModule, List.of());
    }
}
