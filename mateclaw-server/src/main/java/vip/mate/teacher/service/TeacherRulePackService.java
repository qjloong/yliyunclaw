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
            "1.1.0",
            Map.of(
                    "wordExplanation", "20%",
                    "sentenceTranslation", "20%",
                    "punctuationPause", "15%",
                    "contentUnderstanding", "20%",
                    "themeEmotion", "15%",
                    "comparisonExtension", "10%"
            ),
            List.of(
                    new QuestionTypeRule("wordExplanation", "文言字词题", "每小题 1-2 分", "考查教材常见实词、12 个课标常用虚词（之、其、而、以、于、者、也、则、乃、且、若、所）、古今异义、一词多义、通假字和常见词类活用（名词作动词、名词作状语、形容词作动词、使动用法、意动用法，不超纲），不考生僻用法。", true, false, true),
                    new QuestionTypeRule("sentenceTranslation", "句子翻译题", "3-4 分", "直译为主、意译为辅，落实关键字词和常见特殊句式（判断句、省略句、倒装句、被动句），不增删原意；必须标出关键字词、特殊句式和采分点，答案符合中考阅卷习惯。", true, true, true),
                    new QuestionTypeRule("punctuationPause", "断句停顿题", "2-3 分", "依据语法结构、句意、对话标志、虚词和固定句式划分；考察重点为主谓结构、对话标志、虚词停顿、固定句式等，不做复杂语法分析。", true, true, true),
                    new QuestionTypeRule("contentUnderstanding", "内容理解题", "3-4 分", "概括文章大意、事件经过、人物形象（性格、品质）和关键信息；能结合文本内容筛选关键信息、概括核心内容，不考查深层探究，贴合初中生理解水平。", true, true, true),
                    new QuestionTypeRule("themeEmotion", "主旨情感题", "3-5 分", "分析作者的情感倾向、人生态度、写作目的、核心道理等；能结合文本内容分析作者的情感和主旨，不要求过度解读，不考查超出文本的深层内涵。", true, true, true),
                    new QuestionTypeRule("comparisonExtension", "对比迁移题", "4-6 分", "可做课内外对比或联系现实谈启示；必须观点明确、结合文本、语言简洁；评价人物行为、辨析观点正误、提出合理建议等。", true, true, true)
            ),
            List.of(
                    "教材优先；课外篇目需与教材难度相当、文体相近、考点重合。",
                    "课外文言文选材来源：先秦诸子（《论语》《孟子》《荀子》《庄子》《列子》《韩非子》《吕氏春秋》等篇幅较短、道理显明的段落）、史传文学（《史记》《战国策》《资治通鉴》《左传》等经典人物故事）、笔记小说（《世说新语》《聊斋志异》《阅微草堂笔记》等篇幅短小、情节有趣、富含启发性的篇目）、文人散文（唐宋八大家中适合初中生的写景、记事、说理短篇）、明清小品（张岱、袁宏道、李渔、郑燮等清新浅近的小品文）。",
                    "体裁可优先参考课内出现的铭、说、传、书、记、序、表、谏等体裁进行联读。",
                    "课外文言文总字数控制在 150-300 字；生僻字词不超过 3-5 个，并应加注释。",
                    "内容真实可靠，确为作家所写的诗文，严禁捏造虚构，严禁修改文章内容。",
                    "主题积极向上或富有思辨价值；与课内形成知识或能力关联（如同作者、同体裁、同主题、同手法）。",
                    "严禁选用高中文言文篇目、晦涩古籍、生僻异体字过多、典故冷僻、句式过于复杂或价值观不适合中学生的材料。",
                    "字词题只考初中常见实词和 12 个常用虚词：之、其、而、以、于、者、也、则、乃、且、若、所。",
                    "翻译题必须标出关键字词、特殊句式和采分点，答案符合中考阅卷习惯。",
                    "文化常识只考初中常见内容（作者朝代、作品出处、文体知识、古代称谓、官职、纪年法等），不考生僻背景。",
                    "内容理解、主旨情感和手法赏析必须源于文本，不做超出文本的深层探究或学术化阐释。",
                    "句式应用只要求识别句式类型并根据句式特点翻译句子，不考查复杂句式的语法分析。",
                    "主观题必须提供参考答案和可直接判分的采分点。",
                    "禁止默认使用超纲或学术化术语；必须使用时先解释并降阶表达。",
                    "每次正式出题都应形成独立任务上下文，不把上一轮题量、题型、材料范围自动污染到新任务。",
                    "命题说明和质量审核不作为主结果首屏内容；主结果优先展示试题、参考答案、采分点和来源依据。"
            ),
            commonSourceRequirements("classical_chinese"),
            List.of(
                    Map.of("id", "source_scope", "label", "文章来源", "rule", "教材优先，课外篇目 150-300 字且难度与教材一致，来源真实可靠。"),
                    Map.of("id", "no_over_scope", "label", "禁止超纲", "rule", "不考高中文言文、复杂语法、生僻虚词和冷僻文化常识。"),
                    Map.of("id", "translation_rubric", "label", "翻译采分", "rule", "翻译题必须落实关键字词、句式和采分点。"),
                    Map.of("id", "text_grounding", "label", "文本依据", "rule", "理解、主旨、赏析题必须结合文本，不做超出文本的深层探究。"),
                    Map.of("id", "question_mix", "label", "题型比例", "rule", "字词 20%、翻译 20%、断句 15%、理解 20%、主旨 15%、迁移 10%。"),
                    Map.of("id", "score_rules", "label", "分值规则", "rule", "字词 1-2 分/小题，断句 2-3 分，翻译/理解 3-4 分，主旨 3-5 分，迁移 4-6 分。"),
                    Map.of("id", "no_overacademic", "label", "避免超纲术语", "rule", "避免复杂语法分析和学术化表达。"),
                    Map.of("id", "primary_ui", "label", "UI 主结果", "rule", "试题优先展示，命题说明/质量审核折叠或内部化。"),
                    Map.of("id", "context_isolation", "label", "上下文隔离", "rule", "新任务只继承用户明确保留的限制。")
            )
    );
    private static final TeacherRulePack MODERN_READING_V1 = new TeacherRulePack(
            MODERN_READING_V1_ID,
            "junior",
            "chinese",
            "modern_reading",
            "初中现代文阅读规则包 v1",
            "2.0.0",
            Map.of(
                    "basicUnderstanding", "10%",
                    "contentSummary", "15%",
                    "characterAnalysis", "15%",
                    "languageAppreciation", "20%",
                    "structureAnalysis", "10%",
                    "themeEmotion", "15%",
                    "narrativeTechnique", "5%",
                    "genreSpecific", "10%"
            ),
            List.of(
                    new QuestionTypeRule("basicUnderstanding", "基础理解题", "1-2 分", "考查字音字形、词语含义、句子表层意思、文章情节梳理、行文脉络、段落层次划分等；必须源于文本，控制书写量。", true, false, true),
                    new QuestionTypeRule("contentSummary", "内容概括题", "3-5 分", "概括主要内容、事件、人物事迹、梳理情感变化等；优先用表格梳理、框架图、思维导图等形式出题，避免直问直答；必须源于文本，答案可量化。", true, true, true),
                    new QuestionTypeRule("characterAnalysis", "人物形象题", "3-5 分", "考查人物性格分析、人物形象概括、人物描写方法及作用、人物心理揣摩等；必须结合文本中的具体描写，给出性格关键词和文本依据。", true, true, true),
                    new QuestionTypeRule("languageAppreciation", "语言赏析题", "3-5 分", "考查修辞手法（比喻、拟人、排比、夸张、衬托、反问、反复、对偶、设问、引用、通感等）、表现手法（对比、伏笔、铺垫、设置悬念、卒章显志、详略得当、虚实结合、象征、欲扬先抑、托物言志、借景抒情、以小见大、动静结合、联想想象等）、描写手法（外貌、语言、动作、心理、环境、细节等）、词语赏析、表达效果、句式作用、语言特色、朗读设计等；必须结合具体句段分析，不能只列术语。", true, true, true),
                    new QuestionTypeRule("structureAnalysis", "结构作用题", "3-5 分", "考查开头作用、中间段落作用、结尾作用、过渡句、插叙、照应、线索设置等；必须结合文本位置和内容分析，给出结构功能和内容作用。", true, true, true),
                    new QuestionTypeRule("themeEmotion", "主旨情感题", "4-6 分", "考查中心思想、作者情感、标题含义与作用、深层意蕴、感悟启示、联系生活、联系初中必读十二部名著等；必须结合文本，不空泛拔高，不脱离文本空谈。", true, true, true),
                    new QuestionTypeRule("narrativeTechnique", "叙事技巧题", "2-3 分", "考查记叙线索、人称及叙述视角、记叙顺序、表达方式等；要求识别叙事要素并分析其对表达效果的作用，贴合初中生理解水平。", true, true, true),
                    new QuestionTypeRule("genreSpecific", "文体专属题", "3-5 分", "必须先识别文本类型，再使用对应考点：文学类文本重点考查赏析/人物/主旨/情节；新闻重点考查拟标题/导语/倒金字塔结构/真实性与时效性/特写与通讯文学性；现代诗歌重点考查音韵美/节奏美/陌生化语言/分行跨行作用/意象/色彩象征义；戏剧重点考查戏剧冲突/人物形象/台词朗读设计/舞台说明/剧本主旨/戏剧评论；说明文重点考查说明对象/说明方法/说明顺序/语言准确性与生动性/信息筛选；议论文重点考查中心论点/论证方法/论证思路/论据作用/补充论据；非连续性文本重点考查信息整合/比较分析/图文转换/情境表达/拓展运用/综合素质。各文体不得混用规则。", true, true, true)
            ),
            List.of(
                    "必须先识别文本类型，再使用对应考点：文学类（记叙文/散文/小说）、新闻、现代诗歌、戏剧、说明文、议论文、非连续性文本不得混用规则。",
                    "文章来源应真实可靠，可来自：官媒副刊（《人民日报》副刊、《光明日报》笔会、《文汇报》、《中国青年作家报》等）、文学期刊（《散文》《读者》《青年文摘》《意林》《微型小说选刊》《特别关注》《知识窗》《中学生阅读》等）、现当代名家（中国：汪曾祺、杨绛、史铁生、季羡林、毕淑敏、梁衡、迟子建、鲁迅、老舍、宗璞、李娟、阿城、魏巍、梁晓声、刘慈欣、冯骥才、沈从文、鲍尔吉·原野、朱自清、刘湛秋、艾青、席慕蓉、海子、穆旦等；外国：契诃夫、莫泊桑、欧·亨利、伊塔洛·卡尔维诺、罗伯特·巴里、马尔克斯、雷·布拉德伯里、星新一、泰戈尔、安徒生等）、官方平台（中国作家网、学习强国文艺板块、正规文学公众号等优质时文）。",
                    "可优先选用部编版初中六册语文教材中选用文章的作家；避免使用台湾作家以及版权有争议的作家。",
                    "严禁捏造虚构，严禁修改文章内容。",
                    "禁止选用语文课本课内课文、课后阅读篇目、网络口水文、自媒体低俗文案、心灵鸡汤碎片化短文。",
                    "禁止涉及敏感政治、消极颓废、三观不正、猎奇怪异主题。",
                    "禁止篇幅过短＜700字、过长＞2000字，可微调精简至标准篇幅。",
                    "各文体适配篇幅：文学类文本 800-1700字；新闻 200-1500字；现代诗歌 200-700字；戏剧 800-1700字；说明文 900-1300字；议论文 1000-1400字；非连续性文本根据材料组合合理控制。",
                    "禁止语言过于成人化、学术化、晦涩难懂，必须贴合初中生理解层级。",
                    "每道题必须有采分点，每道题必须能够拆分核心得分点、标准答题方向，避免答案无法量化。答案必须源于文本，不能脱离文本空谈。",
                    "具体题型可包括：文字问答、表格、填空、思维导图、圈点批注、活动情境、阅读任务、对话交流、框架图、筛选提取、补写、续写、改写、活动策划、海报设计、卡片档案、微写作、判断正误、连线匹配、图文结合、内心独白、角色写信、跨学科任务等；必须控制书写量，不得让学生大段抄写。",
                    "主观题必须提供参考答案和可直接判分的采分点；生成标准答案的同时标注采分点分值。",
                    "答案必须符合中考阅卷习惯、条理清晰、语言规范、可直接作为参考答案；禁止过度文学化、AI套话、空泛分析。字数在合理范围。",
                    "难度必须合理，符合初中生认知水平、中考真实难度、有梯度、有区分度；基础题面向全体学生，提升题面向中等水平学生，拓展题面向优秀学生，不追求全员得分。",
                    "不出偏题、怪题、超纲题，全部贴合初中生课标，符合初中语文应试表达。",
                    "避免重复：不出现重复考点、重复题型，确保试题的多样性和实用性。",
                    "如果用户给出指定文体、指定考点、指定题型、指定真题风格，必须优先贴近要求生成；如果用户上传题库或范题，必须先分析其命题规律，再高度模仿其问法、难度、结构、设问逻辑、采分方式。",
                    "9年级题目对标中考题型、难度，参考近3-5年当地中考真题，确保出题导向一致。",
                    "禁止默认使用超纲或学术化术语；必须使用时先解释并降阶表达。",
                    "每次正式出题都应形成独立任务上下文，不把上一轮题量、题型、材料范围自动污染到新任务。",
                    "命题说明和质量审核不作为主结果首屏内容；主结果优先展示试题、参考答案、采分点和来源依据。"
            ),
            commonSourceRequirements("modern_reading"),
            List.of(
                    Map.of("id", "genre_identification", "label", "文体识别", "rule", "必须先识别文体，再使用对应考点，各文体不得混用规则。"),
                    Map.of("id", "source_scope", "label", "文章来源", "rule", "来源为官媒副刊、文学期刊、现当代名家、官方平台；优先选用教材已选作家；避免台湾作家和版权争议作家。"),
                    Map.of("id", "source_authenticity", "label", "来源真实", "rule", "文章必须真实可靠，严禁捏造虚构，严禁修改文章内容。"),
                    Map.of("id", "no_textbook_reuse", "label", "不复用课内课文", "rule", "禁止选用语文课本课内课文、课后阅读篇目。"),
                    Map.of("id", "length_compliance", "label", "篇幅合规", "rule", "禁止＜700字或＞2000字；各文体适配篇幅符合规定范围。"),
                    Map.of("id", "tone_appropriate", "label", "语言贴合初中生", "rule", "禁止成人化、学术化、晦涩难懂、AI套话、空泛哲理。"),
                    Map.of("id", "subjective_answers", "label", "主观题答案", "rule", "主观题必须有参考答案和可直接判分的采分点。"),
                    Map.of("id", "text_grounding", "label", "答案源于文本", "rule", "所有答案和采分点必须基于文本，不能脱离文本空谈。"),
                    Map.of("id", "score_rules", "label", "分值规则", "rule", "分值符合中考阅卷习惯并能分点评分。"),
                    Map.of("id", "difficulty_gradient", "label", "难度梯度", "rule", "有梯度、有区分度；基础/提升/拓展三层难度分布合理。"),
                    Map.of("id", "question_variety", "label", "题型多样", "rule", "避免重复考点和重复题型，确保多样性。"),
                    Map.of("id", "no_overacademic", "label", "避免超纲术语", "rule", "避免未经解释的学术化表达；必须使用时先解释并降阶。"),
                    Map.of("id", "primary_ui", "label", "UI 主结果", "rule", "试题优先展示，命题说明/质量审核折叠或内部化。"),
                    Map.of("id", "context_isolation", "label", "上下文隔离", "rule", "新任务只继承用户明确保留的限制。")
            )
    );
    private static final TeacherRulePack ANCIENT_POETRY_V1 = new TeacherRulePack(
            ANCIENT_POETRY_V1_ID,
            "junior",
            "chinese",
            "ancient_poetry",
            "初中古诗词鉴赏规则包 v1",
            "1.1.0",
            Map.of(
                    "emotionTheme", "15%",
                    "languageAppreciation", "20%",
                    "imageDescription", "15%",
                    "techniqueAnalysis", "20%",
                    "imageAnalysis", "10%",
                    "comparisonReading", "20%"
            ),
            List.of(
                    new QuestionTypeRule("emotionTheme", "情感主旨题", "3-5 分", "考查诗歌表达了什么情感、抒发了什么志向、体现了怎样的人生态度；必须结合诗句或意象分析，不做过度解读。", true, true, true),
                    new QuestionTypeRule("languageAppreciation", "炼字赏析题", "2-4 分", "赏析虚词、语气词、动词、形容词、叠词、副词、颜色词、数量词、拟声词的表达效果；分析字词如何写景、传情、绘态，必须给出具体诗句和替代字对比。", true, true, true),
                    new QuestionTypeRule("imageDescription", "画面描绘题", "3-4 分", "抓取意象，用优美语言翻译、扩写诗句，还原诗歌场景；考察翻译、联想、语言表达能力，必须源于原诗意象。", true, true, true),
                    new QuestionTypeRule("techniqueAnalysis", "表现手法题", "3-5 分", "考查借景抒情、情景交融、托物言志、对比、衬托、动静结合、虚实结合、互文、用典、直抒胸臆等；必须指出手法所在诗句并分析作用。", true, true, true),
                    new QuestionTypeRule("imageAnalysis", "意象分析题", "2-3 分", "解读诗句中典型意象的含义和文化内涵；必须联系具体诗句，不脱离文本做抽象阐释。", true, true, true),
                    new QuestionTypeRule("comparisonReading", "对比联读题", "4-8 分", "从两首古诗词曲中相同的意象、手法、体裁、主旨等角度对比联读谈异同；必须观点明确、结合文本、语言简洁。", true, true, true)
            ),
            List.of(
                    "教材古诗词篇目优先；课外篇目限于教材古诗词作者的其他作品、教材课后推荐/单元拓展作品、唐诗三百首、宋词三百首。",
                    "高频主题覆盖：山水田园、羁旅思乡、送别惜别、爱国忧民、边塞征战、咏史怀古、咏物言志、人生感慨、哲理感悟等。",
                    "内容真实可靠，确为作家所写的诗文，严禁捏造虚构，严禁修改文章内容。",
                    "严禁选用高中篇目、晦涩难懂的古籍篇目、生僻字/异体字过多、典故过于冷僻、句式过于复杂的篇目。",
                    "严禁选用内容消极、不符合中学生价值观的篇目。",
                    "主题积极向上或富有思辨价值；与课内形成知识或能力关联（如同作者、同体裁、同主题、同手法）。",
                    "主观题必须提供参考答案和可直接判分的采分点。",
                    "画面描绘题答案必须基于原诗意象，不得脱离文本做过度文学化扩写。",
                    "表现手法赏析必须指出具体诗句并分析作用，禁止只列术语不做文本分析。",
                    "禁止默认使用超纲或学术化术语；必须使用时先解释并降阶表达。",
                    "每次正式出题都应形成独立任务上下文，不把上一轮题量、题型、材料范围自动污染到新任务。",
                    "命题说明和质量审核不作为主结果首屏内容；主结果优先展示试题、参考答案、采分点和来源依据。"
            ),
            commonSourceRequirements("ancient_poetry"),
            List.of(
                    Map.of("id", "source_scope", "label", "文章来源", "rule", "教材优先，课外篇目确为作家原作，严禁捏造。"),
                    Map.of("id", "no_over_scope", "label", "禁止超纲", "rule", "不选高中篇目、晦涩古籍、冷僻典故、消极内容。"),
                    Map.of("id", "subjective_answers", "label", "主观题答案", "rule", "情感主旨、手法赏析等主观题必须有参考答案和采分点。"),
                    Map.of("id", "text_grounding", "label", "文本依据", "rule", "炼字、画面、意象、手法题必须结合具体诗句分析。"),
                    Map.of("id", "question_mix", "label", "题型比例", "rule", "情感主旨约15%、炼字赏析约20%、画面描绘约15%、表现手法约20%、意象分析约10%、对比联读约20%。"),
                    Map.of("id", "score_rules", "label", "分值规则", "rule", "炼字/意象 2-3分，画面/情感/手法 3-5分，对比联读 4-8分。"),
                    Map.of("id", "no_overacademic", "label", "避免超纲术语", "rule", "避免未经解释的学术化表达。"),
                    Map.of("id", "primary_ui", "label", "UI 主结果", "rule", "试题优先展示，命题说明/质量审核折叠或内部化。"),
                    Map.of("id", "context_isolation", "label", "上下文隔离", "rule", "新任务只继承用户明确保留的限制。")
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
