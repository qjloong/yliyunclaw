package vip.mate.wiki.classifier;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight rule-based structure extractor for educational materials.
 * <p>
 * Extracts grade, volume, unit, chapter, classicName, characters, sections,
 * and curriculum structure from text previews using regex heuristics.
 * Does NOT require LLM for most extractions — runs in O(1) time.
 */
@Slf4j
@Component
public class WikiStructureExtractor {

    // 年级匹配
    private static final Pattern GRADE_PATTERN = Pattern.compile(
            "(七年级|八年级|九年级|初一|初二|初三|初中)");

    // 册别匹配
    private static final Pattern VOLUME_PATTERN = Pattern.compile(
            "(上册|下册|第一册|第二册|全一册)");

    // 单元匹配
    private static final Pattern UNIT_PATTERN = Pattern.compile(
            "(第[一二三四五六七八九十\\d]+单元|Unit\\s*\\d+)");

    // 课文/章节匹配
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "《([^》<]{1,40})》");

    // 名著名称匹配（课标12部 + 常见扩展）
    private static final Pattern CLASSIC_NAME_PATTERN = Pattern.compile(
            "(西游记|朝花夕拾|骆驼祥子|海底两万里|红星照耀中国|昆虫记|钢铁是怎样炼成的|" +
            "经典常谈|水浒传|唐诗三百首|简·爱|儒林外史|红岩|" +
            "鲁滨逊漂流记|格列佛游记|童年|名人传|繁星·春水|伊索寓言|" +
            "安徒生童话|格林童话|傅雷家书|培根随笔|围城|平凡的世界|红楼梦|三国演义)");

    // 名著回目匹配：第一回、第二回、第一章、第一篇
    private static final Pattern CLASSIC_CHAPTER_PATTERN = Pattern.compile(
            "(第[一二三四五六七八九十百千\\d]+[回章节篇])");

    // 课标版本匹配
    private static final Pattern EDITION_PATTERN = Pattern.compile(
            "(20\\d{2}版|义务教育课程标准|普通高中课程标准|部编版|统编版|人教版|语文版)");

    // 来源/专题匹配
    private static final Pattern SOURCE_PATTERN = Pattern.compile(
            "(现代文阅读|文言文阅读|古诗词鉴赏|名著阅读|写作|综合性学习|课内同步|期中复习|期末复习|一轮复习)");

    // 人物对话/动作引述模式（用于自动提取人物名）
    private static final Pattern DIALOGUE_PATTERN = Pattern.compile(
            "([^，。；！？\\s]{1,6})(道|说|曰|问道|喝道|笑道|叹道|喝道|嚷道|叫[道声]|答[道曰]|问[道曰]|喝[道曰])");

    // 课标篇章标题匹配（用于curriculum_standard切分）
    private static final Pattern CURRICULUM_SECTION_PATTERN = Pattern.compile(
            "(前言|课程性质|课程理念|课程目标|总目标|学段要求|核心素养|" +
            "课程内容|学习任务群|语言文字积累|实用性阅读|文学阅读|思辨性阅读|整本书阅读|跨学科学习|" +
            "学业质量|课程实施|教学建议|评价建议|教材编写|课程资源|教学研究|教师培训|附[录则]|" +
            "优秀诗文|推荐书目|语法修辞|字表)");

    // ------ T2-5-1: 12部课标名著人物词典（热启动优化） ------

    private static final Map<String, List<String>> CLASSIC_CHARACTER_MAP = new LinkedHashMap<>();

    static {
        CLASSIC_CHARACTER_MAP.put("西游记", List.of(
                "孙悟空", "猪八戒", "唐僧", "沙僧", "如来", "观音", "白骨精", "红孩儿",
                "牛魔王", "铁扇公主", "玉皇大帝", "太白金星", "二郎神", "哪吒", "托塔天王",
                "镇元大仙", "黄袍怪", "金角大王", "银角大王", "蜘蛛精", "白龙马", "六耳猕猴",
                "女儿国国王", "蝎子精", "黄眉大王", "金翅大鹏", "青牛精", "九头虫", "杨戬", "如来佛"));
        CLASSIC_CHARACTER_MAP.put("水浒传", List.of(
                "宋江", "林冲", "武松", "鲁智深", "李逵", "吴用", "杨志", "卢俊义",
                "燕青", "花荣", "张顺", "戴宗", "孙二娘", "扈三娘", "王英", "阮小二",
                "阮小五", "阮小七", "公孙胜", "关胜", "秦明", "呼延灼", "柴进", "石秀",
                "解珍", "解宝", "时迁", "顾大嫂", "金大坚", "王伦"));
        CLASSIC_CHARACTER_MAP.put("朝花夕拾", List.of(
                "鲁迅", "长妈妈", "藤野先生", "范爱农", "父亲", "衍太太", "寿镜吾"));
        CLASSIC_CHARACTER_MAP.put("骆驼祥子", List.of(
                "祥子", "虎妞", "刘四爷", "小福子", "曹先生", "高妈", "二强子"));
        CLASSIC_CHARACTER_MAP.put("海底两万里", List.of(
                "尼摩船长", "阿龙纳斯", "康塞尔", "尼德·兰"));
        CLASSIC_CHARACTER_MAP.put("红星照耀中国", List.of(
                "毛泽东", "周恩来", "朱德", "彭德怀", "贺龙", "徐海东", "斯诺"));
        CLASSIC_CHARACTER_MAP.put("昆虫记", List.of(
                "法布尔", "蝉", "蜜蜂", "螳螂", "蚂蚁", "萤火虫", "圣甲虫", "蜘蛛", "蟋蟀", "蜣螂"));
        CLASSIC_CHARACTER_MAP.put("钢铁是怎样炼成的", List.of(
                "保尔·柯察金", "保尔", "柯察金", "冬妮娅", "朱赫来", "丽达", "达雅", "谢廖沙"));
        CLASSIC_CHARACTER_MAP.put("经典常谈", List.of(
                "朱自清", "孔子", "孟子", "老子", "庄子", "屈原", "司马迁", "班固", "许慎"));
        CLASSIC_CHARACTER_MAP.put("唐诗三百首", List.of(
                "李白", "杜甫", "白居易", "王维", "孟浩然", "王昌龄", "李商隐", "杜牧",
                "苏轼", "辛弃疾", "李清照", "陆游"));
        CLASSIC_CHARACTER_MAP.put("简·爱", List.of(
                "简·爱", "简爱", "罗切斯特", "海伦·彭斯", "圣约翰", "里德太太"));
        CLASSIC_CHARACTER_MAP.put("儒林外史", List.of(
                "范进", "周进", "王冕", "严监生", "严贡生", "匡超人", "杜少卿", "马二先生",
                "沈琼枝", "蘧公孙", "鲍文卿", "王惠", "汤奉", "牛布衣", "季遐年"));
        CLASSIC_CHARACTER_MAP.put("红岩", List.of(
                "江姐", "江竹筠", "许云峰", "成岗", "刘思扬", "余新江", "甫志高", "华子良",
                "小萝卜头", "齐晓轩", "孙明霞", "徐鹏飞", "郑克昌", "胡浩", "老大哥"));
    }

    // ------ Public extraction API ------

    /**
     * Extract structure fields from text preview for a given material type.
     */
    public JSONObject extract(String textPreview, String title, String materialType) {
        String text = (textPreview == null ? "" : textPreview) + " " + (title == null ? "" : title);
        JSONObject fields = new JSONObject();

        String grade = findFirst(GRADE_PATTERN, text);
        String volume = findFirst(VOLUME_PATTERN, text);
        String edition = findFirst(EDITION_PATTERN, text);
        String source = findFirst(SOURCE_PATTERN, text);

        if (StringUtils.hasText(grade)) fields.set("grade", grade);
        if (StringUtils.hasText(volume)) fields.set("volume", volume);
        if (StringUtils.hasText(edition)) fields.set("edition", edition);
        if (StringUtils.hasText(source)) fields.set("source", source);

        switch (materialType) {
            case "textbook_latest" -> extractTextbookFields(text, fields);
            case "classic_manuscript" -> extractClassicFields(text, textPreview, fields);
            case "curriculum_standard" -> extractCurriculumFields(text, fields);
            case "sample_question", "question_rule", "answer_rubric" -> extractAssessmentFields(text, fields);
            default -> {
                // general: extract whatever is available
                if (!fields.containsKey("grade")) {
                    String g = findFirst(GRADE_PATTERN, text);
                    if (StringUtils.hasText(g)) fields.set("grade", g);
                }
            }
        }

        return fields;
    }

    /**
     * Attempt to build a contents index for textbook-style documents.
     */
    public List<ContentIndexEntry> extractContentsIndex(String textPreview, String title) {
        List<ContentIndexEntry> index = new ArrayList<>();
        if (!StringUtils.hasText(textPreview)) {
            return index;
        }

        String[] lines = textPreview.split("[\r\n]+");
        String currentUnit = null;
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 60) continue; // skip long prose lines

            String unit = findFirst(UNIT_PATTERN, line);
            String chapter = findChapterName(line);
            if (StringUtils.hasText(unit)) {
                currentUnit = unit;
            }
            if (StringUtils.hasText(chapter) && currentUnit != null) {
                List<String> modules = guessBusinessModules(line, chapter);
                index.add(new ContentIndexEntry(currentUnit, chapter, modules));
            }
        }
        return index;
    }

    // ------ T2-5-1: Classic manuscript extraction ------

    /**
     * Extract character names using three-tier strategy:
     * 1. Dictionary hit (known classic) → direct match
     * 2. Rule-based extraction (dialogue patterns) → candidates
     * 3. Returns candidates for LLM confirmation if dictionary miss
     *
     * @return list of character names (dictionary: confirmed; rule: candidates)
     */
    public List<String> extractClassicCharacters(String classicName, String fullText) {
        if (!StringUtils.hasText(fullText)) {
            return List.of();
        }

        // Tier 1: dictionary lookup
        List<String> dictChars = CLASSIC_CHARACTER_MAP.get(classicName);
        if (dictChars != null && !dictChars.isEmpty()) {
            List<String> found = new ArrayList<>();
            for (String name : dictChars) {
                if (fullText.contains(name)) {
                    found.add(name);
                }
            }
            if (!found.isEmpty()) {
                log.debug("[WikiStructure] Dictionary hit for {}: found {}/{} characters",
                        classicName, found.size(), dictChars.size());
                return found;
            }
        }

        // Tier 2: rule-based extraction from dialogue patterns
        List<String> candidates = extractCandidatesFromDialogue(fullText);
        if (candidates.size() >= 3) {
            log.debug("[WikiStructure] Rule-based extraction for {}: {} candidates", classicName, candidates.size());
            return candidates;
        }

        // Tier 3: low confidence, return empty for LLM confirmation
        log.debug("[WikiStructure] Low confidence extraction for {}: {} candidates (needs LLM)", classicName, candidates.size());
        return candidates;
    }

    /**
     * Extract chapter/section boundaries from classic manuscript text.
     * Matches patterns like "第一回" / "第一章" / "第3章" and returns
     * section markers for page splitting.
     */
    public List<String> extractClassicSections(String fullText) {
        List<String> sections = new ArrayList<>();
        if (!StringUtils.hasText(fullText)) {
            return sections;
        }

        Matcher m = CLASSIC_CHAPTER_PATTERN.matcher(fullText);
        while (m.find()) {
            String marker = m.group(1);
            int start = Math.max(0, m.start() - 10);
            int end = Math.min(fullText.length(), m.end() + 50);
            String context = fullText.substring(start, end).replace("\n", " ").trim();
            String entry = marker + " | " + context.substring(0, Math.min(context.length(), 60));
            sections.add(entry);
        }
        log.debug("[WikiStructure] Found {} chapter/section markers in classic text", sections.size());
        return sections;
    }

    /**
     * Extract famous excerpts marked with 《》 brackets.
     * Returns list of excerpt text segments.
     */
    public List<String> extractClassicExcerpts(String fullText) {
        List<String> excerpts = new ArrayList<>();
        if (!StringUtils.hasText(fullText)) {
            return excerpts;
        }

        Matcher m = CHAPTER_PATTERN.matcher(fullText);
        while (m.find()) {
            String excerpt = m.group(1);
            if (excerpt.length() >= 4) { // filter out very short references
                excerpts.add(excerpt);
            }
        }
        return excerpts;
    }

    // ------ T2-5-2: Curriculum standard extraction ------

    /**
     * Extract curriculum section boundaries from the 2022 Chinese Language
     * Curriculum Standard document.
     * Returns section markers for structured page splitting.
     */
    public List<String> extractCurriculumSections(String fullText) {
        List<String> sections = new ArrayList<>();
        if (!StringUtils.hasText(fullText)) {
            return sections;
        }

        String[] lines = fullText.split("[\r\n]+");
        String currentSection = null;
        StringBuilder content = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String section = findFirst(CURRICULUM_SECTION_PATTERN, trimmed);
            if (section != null) {
                // Flush previous section
                if (currentSection != null && content.length() > 0) {
                    sections.add(currentSection + " | " +
                            content.substring(0, Math.min(content.length(), 80)));
                    content.setLength(0);
                }
                currentSection = section;
            } else if (currentSection != null) {
                content.append(trimmed).append(" ");
            }
        }
        // Flush last section
        if (currentSection != null && content.length() > 0) {
            sections.add(currentSection + " | " +
                    content.substring(0, Math.min(content.length(), 80)));
        }

        log.debug("[WikiStructure] Extracted {} curriculum sections", sections.size());
        return sections;
    }

    // ------ Details ------

    private void extractTextbookFields(String text, JSONObject fields) {
        String unit = findFirst(UNIT_PATTERN, text);
        String chapter = findChapterName(text);
        if (StringUtils.hasText(unit)) fields.set("unit", unit);
        if (StringUtils.hasText(chapter)) fields.set("chapter", chapter);
    }

    private void extractClassicFields(String text, String textPreview, JSONObject fields) {
        String classicName = findFirst(CLASSIC_NAME_PATTERN, text);
        String chapter = findFirst(CLASSIC_CHAPTER_PATTERN, text);
        if (!StringUtils.hasText(classicName)) {
            classicName = extractClassicFromTitle(text);
        }
        if (StringUtils.hasText(classicName)) fields.set("classicName", classicName);
        if (StringUtils.hasText(chapter)) fields.set("chapter", chapter);
        if (!fields.containsKey("source")) {
            fields.set("source", "整本书阅读任务群");
        }

        // T2-5-1: Extract character and section information
        if (StringUtils.hasText(textPreview) && StringUtils.hasText(classicName)) {
            List<String> characters = extractClassicCharacters(classicName, textPreview);
            if (!characters.isEmpty()) {
                JSONArray charArr = new JSONArray();
                characters.forEach(charArr::add);
                fields.set("characters", charArr);
            }
            List<String> sections = extractClassicSections(textPreview);
            if (!sections.isEmpty()) {
                JSONArray secArr = new JSONArray();
                sections.forEach(secArr::add);
                fields.set("sections", secArr);
            }
            List<String> excerpts = extractClassicExcerpts(textPreview);
            if (!excerpts.isEmpty()) {
                JSONArray exArr = new JSONArray();
                excerpts.stream().limit(30).forEach(exArr::add);
                fields.set("excerpts", exArr);
            }
        }
    }

    private void extractCurriculumFields(String text, JSONObject fields) {
        String unit = findFirst(UNIT_PATTERN, text);
        if (StringUtils.hasText(unit)) fields.set("unit", unit);
        if (!fields.containsKey("source")) {
            fields.set("source", "课标要求");
        }
        // T2-5-2: Extract curriculum sections
        List<String> sections = extractCurriculumSections(text);
        if (!sections.isEmpty()) {
            JSONArray secArr = new JSONArray();
            sections.forEach(secArr::add);
            fields.set("curriculumSections", secArr);
        }
    }

    private void extractAssessmentFields(String text, JSONObject fields) {
        String unit = findFirst(UNIT_PATTERN, text);
        String chapter = findChapterName(text);
        if (StringUtils.hasText(unit)) fields.set("unit", unit);
        if (StringUtils.hasText(chapter)) fields.set("chapter", chapter);
    }

    // ------ Private helpers ------

    private String findFirst(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String findChapterName(String text) {
        String quoted = findFirst(CHAPTER_PATTERN, text);
        if (StringUtils.hasText(quoted)) return quoted;
        Pattern lessonPattern = Pattern.compile("(第[一二三四五六七八九十\\d]+[课章])");
        return findFirst(lessonPattern, text);
    }

    private String extractClassicFromTitle(String text) {
        for (String name : CLASSIC_CHARACTER_MAP.keySet()) {
            if (text.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private List<String> extractCandidatesFromDialogue(String text) {
        List<String> candidates = new ArrayList<>();
        Matcher m = DIALOGUE_PATTERN.matcher(text);
        Map<String, Integer> counts = new LinkedHashMap<>();

        while (m.find()) {
            String name = m.group(1);
            // Filter: must look like a name (2-4 Chinese chars, no special chars)
            if (name.matches("[\\u4e00-\\u9fa5·]{2,4}")) {
                counts.merge(name, 1, Integer::sum);
            }
        }

        // Return names with >= 3 occurrences
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= 3) {
                candidates.add(entry.getKey());
            }
        }
        return candidates;
    }

    private List<String> guessBusinessModules(String line, String chapter) {
        List<String> modules = new ArrayList<>();
        String lower = line.toLowerCase();
        if (lower.contains("文言") || lower.contains("古文") || lower.contains("诗词") || lower.contains("唐诗") || lower.contains("宋词")) {
            modules.add("classical_chinese");
        }
        if (lower.contains("古诗") || lower.contains("诗词") || lower.contains("鉴赏")) {
            modules.add("ancient_poetry");
        }
        if (lower.contains("名著") || lower.contains("导读") || lower.contains("整本书")) {
            modules.add("classic_reading");
        }
        if (lower.contains("写作") || lower.contains("作文") || lower.contains("表达")) {
            modules.add("writing");
        }
        if (lower.contains("基础") || lower.contains("字词") || lower.contains("拼音") || lower.contains("修辞")) {
            modules.add("basic_knowledge");
        }
        if (modules.isEmpty()) {
            modules.add("modern_reading");
        }
        return modules;
    }

    // ------ Public records ------

    public record ContentIndexEntry(
            String unit,
            String chapter,
            List<String> businessModules
    ) {
    }
}
