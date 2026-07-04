package vip.mate.wiki.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Routes business materials to stable Wiki semantics without forking the Wiki model.
 */
@Component
public class WikiMaterialProcessingRouter {

    private final Map<String, WikiMaterialProcessingRecipe> recipes = new LinkedHashMap<>();

    public WikiMaterialProcessingRouter() {
        register(new WikiMaterialProcessingRecipe(
                "curriculum_standard",
                "curriculum",
                "policy_requirement",
                List.of("concept", "summary"),
                List.of("overview", "core_competency", "overall_goal", "grade_target",
                        "task_group", "quality_description", "evaluation_advice", "teaching_advice", "appendix"),
                List.of("classic_reading", "classical_chinese", "modern_reading", "ancient_poetry", "basic_knowledge", "writing"),
                List.of("constrains", "supports"),
                "课标只提供命题依据、能力要求和评价导向，不替代教材、原文或名著事实材料。"
        ));
        register(new WikiMaterialProcessingRecipe(
                "textbook_latest",
                "textbook",
                "source_text",
                List.of("entity", "event", "concept", "summary"),
                List.of("unit_overview", "chapter_slice", "knowledge_point", "classical_slice",
                        "poetry_slice", "writing_task", "classic_guide"),
                List.of("classic_reading", "classical_chinese", "modern_reading", "ancient_poetry", "basic_knowledge", "writing"),
                List.of("supports", "grounds"),
                "完整教材保留册别、单元、课文/章节、文体/模块和可命题知识点层级。"
        ));
        register(new WikiMaterialProcessingRecipe(
                "classic_manuscript",
                "classic",
                "source_text",
                List.of("person", "event", "entity", "concept", "summary"),
                List.of("chapter_slice", "plot_slice", "theme_slice", "excerpt", "language_slice"),
                List.of("classic_reading"),
                List.of("grounds", "supports"),
                "名著稿件按回目/章节、情节、主题、名段和语言切片；人物、法宝、地点和事件进入通用 Wiki 分类和图谱。"
        ));
        register(new WikiMaterialProcessingRecipe(
                "question_rule",
                "assessment_rule",
                "exam_rule",
                List.of("concept", "summary"),
                List.of("question_type_rule", "difficulty_rule", "coverage_rule", "scoring_rule", "material_rule", "forbidden_rule"),
                List.of("classic_reading", "classical_chinese", "modern_reading", "ancient_poetry", "basic_knowledge", "writing"),
                List.of("constrains"),
                "题型要求服务 RulePack 规划和验收，不作为文本事实材料。"
        ));
        register(new WikiMaterialProcessingRecipe(
                "sample_question",
                "sample_question",
                "exam_sample",
                List.of("concept", "summary"),
                List.of("question_item", "answer_item", "rubric_item", "source_item", "analysis_item"),
                List.of("classic_reading", "classical_chinese", "modern_reading", "ancient_poetry", "basic_knowledge", "writing"),
                List.of("exemplifies"),
                "样题用于仿题、题型结构和风格复核，事实仍需回到教材、原文或名著材料。"
        ));
        register(new WikiMaterialProcessingRecipe(
                "answer_rubric",
                "answer_rubric",
                "scoring_standard",
                List.of("concept", "summary"),
                List.of("rubric_dimension", "grade_level", "score_bracket", "common_error"),
                List.of("classic_reading", "classical_chinese", "modern_reading", "ancient_poetry", "basic_knowledge", "writing"),
                List.of("scores", "constrains"),
                "评分标准用于答案采分和质量复核，不替代事实来源。"
        ));
    }

    public WikiMaterialProcessingRecipe recipe(String materialType) {
        if (!StringUtils.hasText(materialType)) {
            return null;
        }
        return recipes.get(materialType.trim().toLowerCase(Locale.ROOT));
    }

    public WikiMaterialProcessingRecipe primaryRecipe(Collection<String> materialTypes) {
        if (materialTypes == null || materialTypes.isEmpty()) {
            return null;
        }
        for (String type : List.of("curriculum_standard", "textbook_latest", "classic_manuscript",
                "question_rule", "sample_question", "answer_rubric")) {
            if (materialTypes.contains(type)) {
                return recipe(type);
            }
        }
        return materialTypes.stream()
                .map(this::recipe)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public String deriveSliceType(String title, String purposeHint, Collection<String> materialTypes) {
        WikiMaterialProcessingRecipe recipe = primaryRecipe(materialTypes);
        String text = ((title == null ? "" : title) + " " + (purposeHint == null ? "" : purposeHint))
                .toLowerCase(Locale.ROOT);
        if (recipe == null) {
            return deriveGenericSliceType(text);
        }
        return switch (recipe.materialType()) {
            case "curriculum_standard" -> deriveCurriculumSliceType(text);
            case "textbook_latest" -> deriveTextbookSliceType(text);
            case "classic_manuscript" -> deriveClassicSliceType(text);
            case "question_rule" -> deriveQuestionRuleSliceType(text);
            case "sample_question" -> deriveSampleQuestionSliceType(text);
            case "answer_rubric" -> deriveRubricSliceType(text);
            default -> deriveGenericSliceType(text);
        };
    }

    private String deriveCurriculumSliceType(String text) {
        if (containsAny(text, "核心素养", "文化自信", "语言运用", "思维能力", "审美创造")) return "core_competency";
        if (containsAny(text, "总目标", "课程目标")) return "overall_goal";
        if (containsAny(text, "学段", "7-9", "七至九", "年级")) return "grade_target";
        if (containsAny(text, "任务群", "语言文字积累", "实用性阅读", "文学阅读", "思辨性阅读", "整本书阅读", "跨学科")) return "task_group";
        if (containsAny(text, "学业质量", "质量描述")) return "quality_description";
        if (containsAny(text, "评价", "命题")) return "evaluation_advice";
        if (containsAny(text, "教学建议", "课程实施")) return "teaching_advice";
        if (containsAny(text, "附录", "推荐书目", "优秀诗文", "字表")) return "appendix";
        return "overview";
    }

    private String deriveTextbookSliceType(String text) {
        if (containsAny(text, "单元", "unit")) return "unit_overview";
        if (containsAny(text, "文言", "古文", "实词", "虚词", "翻译", "断句")) return "classical_slice";
        if (containsAny(text, "古诗", "诗词", "诗歌", "炼字", "意象")) return "poetry_slice";
        if (containsAny(text, "写作", "作文", "表达训练")) return "writing_task";
        if (containsAny(text, "名著导读", "整本书", "名著阅读")) return "classic_guide";
        if (containsAny(text, "课文", "章节", "chapter", "lesson", "第", "课")) return "chapter_slice";
        return "knowledge_point";
    }

    private String deriveClassicSliceType(String text) {
        if (containsAny(text, "回目", "章回", "章节", "第", "回")) return "chapter_slice";
        if (containsAny(text, "名段", "赏析", "摘录", "原文片段")) return "excerpt";
        if (containsAny(text, "语言", "修辞", "描写", "风格")) return "language_slice";
        if (containsAny(text, "主题", "主旨", "思想", "内涵")) return "theme_slice";
        if (containsAny(text, "情节", "起因", "发展", "高潮", "结局")) return "plot_slice";
        return "chapter_slice";
    }

    private String deriveQuestionRuleSliceType(String text) {
        if (containsAny(text, "难度", "区分度", "基础", "提升")) return "difficulty_rule";
        if (containsAny(text, "考点", "覆盖", "能力")) return "coverage_rule";
        if (containsAny(text, "评分", "采分", "分值")) return "scoring_rule";
        if (containsAny(text, "材料", "原文", "来源")) return "material_rule";
        if (containsAny(text, "禁止", "限制", "超纲", "不得")) return "forbidden_rule";
        return "question_type_rule";
    }

    private String deriveSampleQuestionSliceType(String text) {
        if (containsAny(text, "答案", "解析")) return "answer_item";
        if (containsAny(text, "采分", "评分")) return "rubric_item";
        if (containsAny(text, "来源", "材料")) return "source_item";
        if (containsAny(text, "命题", "分析", "审核")) return "analysis_item";
        return "question_item";
    }

    private String deriveRubricSliceType(String text) {
        if (containsAny(text, "等级", "优秀", "良好", "合格", "a", "b", "c", "d")) return "grade_level";
        if (containsAny(text, "分值", "分数", "区间")) return "score_bracket";
        if (containsAny(text, "错误", "丢分", "问题")) return "common_error";
        return "rubric_dimension";
    }

    private String deriveGenericSliceType(String text) {
        if (containsAny(text, "课文", "章节", "chapter", "lesson", "回目")) return "chapter_slice";
        if (containsAny(text, "单元", "unit")) return "unit_overview";
        if (containsAny(text, "人物", "角色", "character")) return "character";
        if (containsAny(text, "情节", "主题", "plot", "theme")) return "theme_slice";
        return null;
    }

    private void register(WikiMaterialProcessingRecipe recipe) {
        recipes.put(recipe.materialType(), recipe);
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (StringUtils.hasText(term) && text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
