package vip.mate.teacher.service;

import vip.mate.teacher.model.QuestionTypeRule;
import vip.mate.teacher.model.TeacherRulePack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in Teacher rule packs.
 *
 * <p>The JSON resource under {@code teacher-rules/} is the editable seed and
 * documentation source. This service exposes the same v2 rules to runtime code
 * without requiring Agent construction to become Spring-managed.</p>
 */
public final class TeacherRulePackService {

    public static final String CLASSIC_READING_V2_ID = "teacher.rulepack.classic_reading.v2";
    public static final String CLASSICAL_CHINESE_V1_ID = "teacher.rulepack.classical_chinese.v1";
    public static final String MODERN_READING_V1_ID = "teacher.rulepack.modern_reading.v1";
    public static final String ANCIENT_POETRY_V1_ID = "teacher.rulepack.ancient_poetry.v1";
    public static final String BASIC_KNOWLEDGE_V1_ID = "teacher.rulepack.basic_knowledge.v1";
    public static final String WRITING_V1_ID = "teacher.rulepack.writing.v1";
    public static final String OVERRIDE_SETTING_PREFIX = "teacher.rulepack.override.";
    public static final String WORKSPACE_OVERRIDE_SETTING_PREFIX = "teacher.rulepack.workspace.override.";

    private static final TeacherRulePack CLASSIC_READING_V2 = new TeacherRulePack(
            CLASSIC_READING_V2_ID,
            "junior",
            "chinese",
            "classic_reading",
            "初中名著阅读规则包 v2",
            "2.0.0",
            Map.of(
                    "fillBlank", "10%",
                    "choice", "20%",
                    "shortAnswer", "15%",
                    "analysis", "30%",
                    "inquiry", "25%",
                    "microWriting", "extra_only"
            ),
            List.of(
                    new QuestionTypeRule("fillBlank", "填空题", "每空 1-2 分", "控制空格数量和每空字数，必须答案唯一或边界清楚。", true, false, false),
                    new QuestionTypeRule("choice", "选择题", "2 分", "支持排序、匹配、比较、辨析、情节理解等变化，选项长度接近。", true, false, false),
                    new QuestionTypeRule("shortAnswer", "简答题", "3-4 分", "必须结合材料或具体情节作答，不得泛泛而谈。", true, true, false),
                    new QuestionTypeRule("analysis", "分析题", "4-8 分", "必须分层分析人物、情节、主题、写法或语言，采分点可直接判分。", true, true, true),
                    new QuestionTypeRule("inquiry", "探究题", "4-8 分", "开放题必须给参考方向和采分标准，允许有依据的合理表达。", true, true, true),
                    new QuestionTypeRule("microWriting", "微写作", "附加题", "只能作为附加题型，考查名著理解和阅读体验，不扩大成作文训练。", true, true, true)
            ),
            List.of(
                    "主观题必须提供参考答案和可直接判分的采分点。",
                    "材料题必须提供原文、节选材料、情节材料或明确来源依据；材料不足时先请求补充。",
                    "辩论题、开放题、探究题不得省略答案方向和采分点。",
                    "批注题必须指定批注角度，例如人物、情节、语言、主题或写法。",
                    "微写作只能作为附加题型，必须考查名著理解和阅读体验，不扩大成作文训练。",
                    "填空题和表格题必须控制书写量，不得让学生大段抄写。",
                    "选择题形式应包含排序、匹配、比较、辨析、情节理解等变化，避免题型单一。",
                    "禁止默认使用超纲或学术化术语；必须使用时先解释并降阶表达。",
                    "每次正式出题都应形成独立任务上下文，不把上一轮题量、题型、材料范围自动污染到新任务。",
                    "命题说明和质量审核不作为主结果首屏内容；主结果优先展示试题。",
                    "不默认生成插图；涉及插图时要求用户上传或确认素材来源。"
            ),
            commonSourceRequirements("classic_reading"),
            List.of(
                    Map.of("id", "question_mix", "label", "题型比例", "rule", "默认比例为填空 10%、选择 20%、简答 15%、分析 30%、探究 25%，微写作仅附加。"),
                    Map.of("id", "subjective_answers", "label", "主观题答案", "rule", "主观题必须有参考答案和采分点。"),
                    Map.of("id", "material_questions", "label", "材料题材料", "rule", "材料题必须有材料或来源；材料不足时先请求补充。"),
                    Map.of("id", "no_overacademic", "label", "避免超纲术语", "rule", "禁止默认使用超纲或学术化术语。")
            )
    );
    private static final TeacherRulePack CLASSICAL_CHINESE_V1 = new TeacherRulePack(
            CLASSICAL_CHINESE_V1_ID,
            "junior",
            "chinese",
            "classical_chinese",
            "初中文言文阅读规则包 v1",
            "1.0.0",
            Map.of(
                    "wordExplanation", "20%",
                    "sentenceTranslation", "20%",
                    "punctuationPause", "15%",
                    "contentUnderstanding", "20%",
                    "themeEmotion", "15%",
                    "comparisonExtension", "10%"
            ),
            List.of(
                    new QuestionTypeRule("wordExplanation", "文言字词题", "每小题 1-2 分", "考查教材常见实词、12 个课标常用虚词、古今异义、一词多义、通假字和常见词类活用，不考生僻用法。", true, false, true),
                    new QuestionTypeRule("sentenceTranslation", "句子翻译题", "3-4 分", "直译为主、意译为辅，落实关键字词和常见特殊句式，不增删原意。", true, true, true),
                    new QuestionTypeRule("punctuationPause", "断句停顿题", "2-3 分", "依据语法结构、句意、对话标志、虚词和固定句式划分，不做复杂语法分析。", true, true, true),
                    new QuestionTypeRule("contentUnderstanding", "内容理解题", "3-4 分", "概括文章大意、事件经过、人物形象和关键信息，必须结合文本。", true, true, true),
                    new QuestionTypeRule("themeEmotion", "主旨情感题", "3-5 分", "分析作者情感、写作目的、核心道理和人生态度，不做过度解读。", true, true, true),
                    new QuestionTypeRule("comparisonExtension", "对比迁移题", "4-6 分", "可做课内外对比或联系现实谈启示，必须观点明确、结合文本、语言简洁。", true, true, true)
            ),
            List.of(
                    "文章来源优先为初中教材篇目；课外篇目需与教材难度相当、文体相近、考点重合。",
                    "课外文言文总字数控制在 150-300 字；生僻字词不超过 3-5 个，并应加注释。",
                    "严禁选用高中文言文篇目、晦涩古籍、生僻异体字过多、典故冷僻、句式过于复杂或价值观不适合中学生的材料。",
                    "字词题只考初中常见实词和 12 个常用虚词：之、其、而、以、于、者、也、则、乃、且、若、所。",
                    "翻译题必须标出关键字词、特殊句式和采分点，答案符合中考阅卷习惯。",
                    "文化常识只考作者朝代、出处、文体、称谓、官职、纪年法等初中常见内容，不考生僻背景。",
                    "内容理解、主旨情感和手法赏析必须源于文本，不做超出文本的深层探究或学术化阐释。",
                    "每套题应兼顾基础积累、理解运用、综合拓展，客观题和主观题搭配。"
            ),
            commonSourceRequirements("classical_chinese"),
            List.of(
                    Map.of("id", "source_scope", "label", "文章来源", "rule", "教材优先，课外篇目 150-300 字且难度与教材一致。"),
                    Map.of("id", "no_over_scope", "label", "禁止超纲", "rule", "不考高中文言文、复杂语法、生僻虚词和冷僻文化常识。"),
                    Map.of("id", "translation_rubric", "label", "翻译采分", "rule", "翻译题必须落实关键字词、句式和采分点。"),
                    Map.of("id", "text_grounding", "label", "文本依据", "rule", "理解、主旨、赏析题必须结合文本。")
            )
    );
    private static final TeacherRulePack MODERN_READING_V1 = new TeacherRulePack(
            MODERN_READING_V1_ID,
            "junior",
            "chinese",
            "modern_reading",
            "初中现代文阅读规则包 v1",
            "1.0.0",
            Map.of(
                    "informationExtraction", "15%",
                    "contentSummary", "20%",
                    "languageAppreciation", "20%",
                    "structureAnalysis", "15%",
                    "themeEmotion", "15%",
                    "extensionTask", "15%"
            ),
            List.of(
                    new QuestionTypeRule("informationExtraction", "信息提取题", "2-4 分", "筛选关键语句、信息整合、图表或材料转化，答案必须源于文本。", true, true, true),
                    new QuestionTypeRule("contentSummary", "内容概括题", "3-5 分", "概括事件、人物事迹、情感变化和行文脉络，优先用表格、框架图等形式避免直问直答。", true, true, true),
                    new QuestionTypeRule("languageAppreciation", "语言赏析题", "3-5 分", "围绕修辞、表现手法、描写方法、词语表达效果和语言特色，必须结合具体句段。", true, true, true),
                    new QuestionTypeRule("structureAnalysis", "结构作用题", "3-5 分", "分析标题、开头、中间段、结尾、过渡、插叙、照应、线索等作用。", true, true, true),
                    new QuestionTypeRule("themeEmotion", "主旨情感题", "4-6 分", "概括中心、作者情感、标题含义、深层意蕴和感悟启示，不空泛拔高。", true, true, true),
                    new QuestionTypeRule("extensionTask", "拓展任务题", "4-8 分", "可设计活动情境、对话、海报、卡片、微写作、跨学科任务，但必须可评分且不脱离文本。", true, true, true)
            ),
            List.of(
                    "文本类型可覆盖文学类文本、新闻、现代诗歌、戏剧、说明文、议论文、非连续性文本，必须先识别文本类型再出对应题。",
                    "文学类文本适配篇幅 800-1700 字；新闻 200-1500 字；现代诗歌 200-700 字；戏剧 800-1700 字；说明文 900-1300 字；议论文 1000-1400 字。",
                    "文章来源应真实可靠，可来自官媒副刊、文学期刊、现当代名家、官方平台；严禁捏造或篡改文章内容。",
                    "禁止选用语文课本课内课文、课后阅读篇目、网络口水文、自媒体低俗文案、心灵鸡汤碎片短文和版权/来源不清材料。",
                    "禁止涉及敏感政治、消极颓废、三观不正、猎奇怪异主题；语言必须贴合初中生理解层级。",
                    "每道题必须有采分点，答案必须源于文本，不能脱离文本空谈。",
                    "不同文体使用专属考点：散文看线索与情景，小说看情节人物环境，说明文看对象方法顺序语言，议论文看论点论据论证，新闻看导语结构真实性时效性。",
                    "避免成人化、学术化、空泛哲理和 AI 套话。"
            ),
            commonSourceRequirements("modern_reading"),
            List.of(
                    Map.of("id", "genre_specific", "label", "文体专属", "rule", "必须先识别文体，再使用对应考点。"),
                    Map.of("id", "source_authenticity", "label", "来源真实", "rule", "文章来源必须真实可靠，严禁编造和篡改。"),
                    Map.of("id", "text_based_answer", "label", "答案源于文本", "rule", "所有答案和采分点必须基于文本。"),
                    Map.of("id", "no_textbook_reuse", "label", "不复用课内课文", "rule", "现代文阅读不得默认选用课内课文或课后篇目。")
            )
    );
    private static final TeacherRulePack ANCIENT_POETRY_V1 = new TeacherRulePack(
            ANCIENT_POETRY_V1_ID,
            "junior",
            "chinese",
            "ancient_poetry",
            "初中古诗词鉴赏规则包 v1",
            "1.0.0",
            Map.of(
                    "recitationUnderstanding", "20%",
                    "imageEmotion", "25%",
                    "languageAppreciation", "20%",
                    "techniqueAnalysis", "15%",
                    "comparisonReading", "20%"
            ),
            List.of(
                    new QuestionTypeRule("recitationUnderstanding", "默写理解题", "每空 1 分", "只考课标和教材要求掌握篇目，填空不得泄露答案，不出冷僻异体字。", true, false, true),
                    new QuestionTypeRule("imageEmotion", "意象情感题", "3-4 分", "结合诗句分析意象、画面、情感和主旨，不空泛套话。", true, true, true),
                    new QuestionTypeRule("languageAppreciation", "炼字赏句题", "3-5 分", "围绕关键字词、修辞、节奏和表达效果，必须回到原句。", true, true, true),
                    new QuestionTypeRule("techniqueAnalysis", "手法分析题", "3-5 分", "考查借景抒情、托物言志、虚实结合、动静结合等初中常见手法。", true, true, true),
                    new QuestionTypeRule("comparisonReading", "比较阅读题", "4-6 分", "比较主题、情感、意象或写法，必须限定比较角度。", true, true, true)
            ),
            List.of(
                    "古诗词篇目优先使用最新教材和课标要求篇目；超出范围时必须说明并请求确认。",
                    "赏析题必须引用具体诗句，不得脱离文本泛谈情感。",
                    "默写题不得在题干中直接暴露答案，空格数量和答案边界必须清楚。",
                    "比较阅读必须给出明确比较维度和分点采分标准。",
                    "不得使用高中诗词术语体系强行拔高初中题目。"
            ),
            commonSourceRequirements("ancient_poetry"),
            List.of(
                    Map.of("id", "textbook_scope", "label", "教材课标范围", "rule", "优先使用绑定知识库中的最新教材和课标篇目。"),
                    Map.of("id", "quote_based", "label", "诗句依据", "rule", "赏析与情感分析必须基于具体诗句。"),
                    Map.of("id", "rubric", "label", "采分清楚", "rule", "主观题必须按意象、情感、手法、表达效果拆分采分点。")
            )
    );
    private static final TeacherRulePack BASIC_KNOWLEDGE_V1 = new TeacherRulePack(
            BASIC_KNOWLEDGE_V1_ID,
            "junior",
            "chinese",
            "basic_knowledge",
            "初中语文基础知识规则包 v1",
            "1.0.0",
            Map.of(
                    "phoneticsGlyph", "20%",
                    "wordUse", "20%",
                    "sentenceDisease", "20%",
                    "literaryCulture", "20%",
                    "comprehensivePractice", "20%"
            ),
            List.of(
                    new QuestionTypeRule("phoneticsGlyph", "字音字形题", "2 分", "考查初中常见易错字音字形，不出偏难生僻字。", true, false, false),
                    new QuestionTypeRule("wordUse", "词语运用题", "2-3 分", "考查成语、词语搭配、语境辨析和感情色彩，选项干扰合理。", true, true, false),
                    new QuestionTypeRule("sentenceDisease", "病句修改题", "2-4 分", "围绕搭配不当、成分残缺、语序不当、重复赘余等常见类型。", true, true, false),
                    new QuestionTypeRule("literaryCulture", "文学文化常识题", "2-4 分", "只考初中常见作家作品、文体、文化常识和教材相关内容。", true, true, true),
                    new QuestionTypeRule("comprehensivePractice", "综合性学习题", "4-8 分", "可设计活动、口语交际、图文转换、材料概括，必须可评分。", true, true, true)
            ),
            List.of(
                    "基础知识题必须贴合初中语文教材和课标，不考高中过深语法或冷僻文学常识。",
                    "客观题选项长度和迷惑度应相近，答案唯一且解析清楚。",
                    "病句题必须指出病因或修改依据，不能只给答案。",
                    "综合性学习题必须提供材料、任务情境和明确评分标准。"
            ),
            commonSourceRequirements("basic_knowledge"),
            List.of(
                    Map.of("id", "junior_scope", "label", "初中范围", "rule", "不得使用高中或竞赛级知识点。"),
                    Map.of("id", "single_answer", "label", "答案唯一", "rule", "客观题答案必须唯一且解析可验证。"),
                    Map.of("id", "material_task", "label", "情境材料", "rule", "综合性学习题必须有材料和评分标准。")
            )
    );
    private static final TeacherRulePack WRITING_V1 = new TeacherRulePack(
            WRITING_V1_ID,
            "junior",
            "chinese",
            "writing",
            "初中写作训练规则包 v1",
            "1.0.0",
            Map.of(
                    "topicReview", "20%",
                    "outlineDesign", "20%",
                    "materialSelection", "20%",
                    "fragmentWriting", "20%",
                    "revisionEvaluation", "20%"
            ),
            List.of(
                    new QuestionTypeRule("topicReview", "审题立意题", "3-5 分", "围绕题目关键词、限制条件、立意方向和偏题风险设计。", true, true, true),
                    new QuestionTypeRule("outlineDesign", "提纲设计题", "4-6 分", "要求结构清楚、材料匹配、详略得当，不代写整篇作文。", true, true, true),
                    new QuestionTypeRule("materialSelection", "素材选择题", "3-5 分", "考查素材与主题、文体、情感表达的匹配度。", true, true, true),
                    new QuestionTypeRule("fragmentWriting", "片段写作题", "6-10 分", "限定字数、场景和表达重点，必须给评分维度。", true, true, true),
                    new QuestionTypeRule("revisionEvaluation", "升格修改题", "4-8 分", "提供修改建议、理由和评分点，避免空泛评价。", true, true, true)
            ),
            List.of(
                    "写作模块以训练、审题、提纲、片段和修改为主；默认不代写完整作文。",
                    "题目必须贴合初中生活经验和认知水平，不成人化、不空泛。",
                    "评分标准至少覆盖立意、内容、结构、语言、书写或表达效果。",
                    "如需生成范文，必须先征得用户明确要求，并标注为示例。"
            ),
            commonSourceRequirements("writing"),
            List.of(
                    Map.of("id", "no_full_essay_by_default", "label", "默认不代写整文", "rule", "除非用户明确要求，否则不生成完整作文。"),
                    Map.of("id", "rubric_dimensions", "label", "评分维度", "rule", "写作训练必须给明确评分维度。"),
                    Map.of("id", "junior_context", "label", "初中语境", "rule", "情境和素材应贴合初中学生生活。")
            )
    );
    private static final Map<String, TeacherRulePack> OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<String, TeacherRulePack> WORKSPACE_OVERRIDES = new ConcurrentHashMap<>();

    private TeacherRulePackService() {
    }

    public static TeacherRulePack classicReadingV2() {
        return effectiveRulePack(CLASSIC_READING_V2_ID);
    }

    public static TeacherRulePack classicReadingV2(Long workspaceId) {
        return effectiveRulePack(CLASSIC_READING_V2_ID, workspaceId);
    }

    public static List<TeacherRulePack> listBuiltInRulePacks() {
        return List.of(
                effectiveRulePack(CLASSIC_READING_V2_ID),
                effectiveRulePack(CLASSICAL_CHINESE_V1_ID),
                effectiveRulePack(MODERN_READING_V1_ID),
                effectiveRulePack(ANCIENT_POETRY_V1_ID),
                effectiveRulePack(BASIC_KNOWLEDGE_V1_ID),
                effectiveRulePack(WRITING_V1_ID)
        );
    }

    public static TeacherRulePack getBuiltInRulePack(String id) {
        if (builtInRulePack(id) != null) {
            return effectiveRulePack(id);
        }
        return null;
    }

    public static TeacherRulePack builtInRulePack(String id) {
        if (CLASSIC_READING_V2_ID.equals(id)) {
            return CLASSIC_READING_V2;
        }
        if (CLASSICAL_CHINESE_V1_ID.equals(id)) {
            return CLASSICAL_CHINESE_V1;
        }
        if (MODERN_READING_V1_ID.equals(id)) {
            return MODERN_READING_V1;
        }
        if (ANCIENT_POETRY_V1_ID.equals(id)) {
            return ANCIENT_POETRY_V1;
        }
        if (BASIC_KNOWLEDGE_V1_ID.equals(id)) {
            return BASIC_KNOWLEDGE_V1;
        }
        if (WRITING_V1_ID.equals(id)) {
            return WRITING_V1;
        }
        return null;
    }

    public static TeacherRulePack effectiveRulePack(String id) {
        TeacherRulePack override = OVERRIDES.get(id);
        if (override != null) {
            return override;
        }
        return builtInRulePack(id);
    }

    public static TeacherRulePack effectiveRulePack(String id, Long workspaceId) {
        // T2-2-10e: 运行时规则优先级去 workspace 化，workspace 覆盖不再参与运行时优先级
        // 保留全局覆盖作为系统插件默认规则，实例级覆盖由调用方通过 configJson 单独处理
        return effectiveRulePack(id);
    }

    public static void putOverride(TeacherRulePack pack) {
        if (pack != null && pack.id() != null && builtInRulePack(pack.id()) != null) {
            OVERRIDES.put(pack.id(), pack);
        }
    }

    public static void clearOverride(String id) {
        if (id != null) {
            OVERRIDES.remove(id);
        }
    }

    public static void putWorkspaceOverride(Long workspaceId, TeacherRulePack pack) {
        if (workspaceId != null && pack != null && pack.id() != null && builtInRulePack(pack.id()) != null) {
            WORKSPACE_OVERRIDES.put(workspaceOverrideKey(pack.id(), workspaceId), pack);
        }
    }

    public static void clearWorkspaceOverride(String id, Long workspaceId) {
        if (id != null && workspaceId != null) {
            WORKSPACE_OVERRIDES.remove(workspaceOverrideKey(id, workspaceId));
        }
    }

    public static boolean hasOverride(String id) {
        return id != null && OVERRIDES.containsKey(id);
    }

    public static boolean hasWorkspaceOverride(String id, Long workspaceId) {
        return id != null && workspaceId != null && WORKSPACE_OVERRIDES.containsKey(workspaceOverrideKey(id, workspaceId));
    }

    public static String workspaceOverrideKey(String id, Long workspaceId) {
        return workspaceId + "." + id;
    }

    public static String classicReadingPromptRules() {
        TeacherRulePack pack = classicReadingV2();
        return promptRules(pack);
    }

    public static String classicReadingPromptRules(Long workspaceId) {
        TeacherRulePack pack = classicReadingV2(workspaceId);
        return promptRules(pack);
    }

    public static String promptRules(String rulePackId) {
        TeacherRulePack pack = effectiveRulePack(rulePackId);
        if (pack == null) {
            pack = effectiveRulePack(CLASSIC_READING_V2_ID);
        }
        return promptRules(pack);
    }

    public static String promptRules(String rulePackId, Long workspaceId) {
        TeacherRulePack pack = effectiveRulePack(rulePackId, workspaceId);
        if (pack == null) {
            pack = effectiveRulePack(CLASSIC_READING_V2_ID, workspaceId);
        }
        return promptRules(pack);
    }

    public static String rulePackIdForModule(String module) {
        if ("classical_chinese".equals(module)) {
            return CLASSICAL_CHINESE_V1_ID;
        }
        if ("modern_reading".equals(module)) {
            return MODERN_READING_V1_ID;
        }
        if ("ancient_poetry".equals(module)) {
            return ANCIENT_POETRY_V1_ID;
        }
        if ("basic_knowledge".equals(module)) {
            return BASIC_KNOWLEDGE_V1_ID;
        }
        if ("writing".equals(module)) {
            return WRITING_V1_ID;
        }
        if ("paper_assembly".equals(module)) {
            return CLASSIC_READING_V2_ID;
        }
        return CLASSIC_READING_V2_ID;
    }

    private static String promptRules(TeacherRulePack pack) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(pack.name()).append("（").append(pack.id()).append("）\n");
        if (pack.stage() != null && !pack.stage().isBlank()) {
            sb.append("- 学段：").append(pack.stage()).append("\n");
        }
        if (pack.subject() != null && !pack.subject().isBlank()) {
            sb.append("- 学科：").append(pack.subject()).append("\n");
        }
        sb.append("- 模块：").append(pack.module()).append("\n");
        sb.append("- 默认题型比例：").append(pack.defaultQuestionMix()).append("\n");
        sb.append("- 题型规则：\n");
        for (QuestionTypeRule rule : pack.questionTypeRules()) {
            sb.append("  - ").append(rule.displayName()).append("：")
                    .append(rule.defaultScore()).append("；")
                    .append(rule.generationRule());
            if (rule.requiresAnswer()) sb.append(" 必须给参考答案。");
            if (rule.requiresScoringRubric()) sb.append(" 必须给采分点。");
            if (rule.requiresMaterial()) sb.append(" 必须有材料或来源依据。");
            sb.append("\n");
        }
        sb.append("- 硬性规则：\n");
        for (String rule : pack.hardRules()) {
            sb.append("  - ").append(rule).append("\n");
        }
        if (pack.sourceRequirements() != null && !pack.sourceRequirements().isEmpty()) {
            sb.append("- 资料与课标要求：\n");
            for (Map<String, String> item : pack.sourceRequirements()) {
                sb.append("  - ").append(item.getOrDefault("label", item.getOrDefault("id", "资料要求")))
                        .append("：").append(item.getOrDefault("rule", "")).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static List<Map<String, String>> commonSourceRequirements(String module) {
        return List.of(
                Map.of("id", "curriculum_standard", "label", "课程标准", "rule", "优先使用已绑定知识库中标记为最新版本的初中语文课程标准；缺失时必须提示补充，不得假称已按最新课标生成。"),
                Map.of("id", "textbook", "label", "最新教材", "rule", "涉及教材篇目、册别、范围时，优先使用已绑定知识库中的最新教材或教材说明；新旧资料冲突时以最新标记资料为准。"),
                Map.of("id", "teaching_material", "label", "教学资料", "rule", "名著稿件、讲义、样题、评分标准应先上传、处理并绑定到 Agent；正式出题时在来源依据中说明使用了哪些资料。"),
                Map.of("id", "module_scope", "label", "模块边界", "rule", "当前规则包仅适用于初中语文 " + module + " 模块；小学、高中或其他学科请求不得混用本规则包。")
        );
    }
}
