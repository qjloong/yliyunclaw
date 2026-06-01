package vip.mate.wiki.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.teacher.service.TeacherIntentService;
import vip.mate.wiki.dto.WikiDomainProfileMetadataField;
import vip.mate.wiki.dto.WikiDomainProfileMaterialType;
import vip.mate.wiki.dto.WikiDomainProfileOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controlled registry for wiki business domain profiles.
 *
 * <p>Current implementation ships built-in platform profiles. Future stages can
 * extend this to support plugin-contributed and admin-managed registry entries.
 */
@Slf4j
@Service
public class WikiDomainProfileRegistryService {

    private static final List<WikiDomainProfileOption> BUILT_IN_PROFILES = List.of(
            new WikiDomainProfileOption(
                    "education.exam.junior_chinese",
                    "初中语文教学命题",
                    "适用于初中语文教材、课标、名著稿件、样题与评分标准等资料治理。",
                    "business",
                    TeacherIntentService.PLUGIN_KEY,
                    TeacherIntentService.CAPABILITY_PACK_ID,
                    List.of(
                            new WikiDomainProfileMaterialType(
                                    "general",
                                    "通用资料",
                                    List.of()
                            ),
                            new WikiDomainProfileMaterialType(
                                    "curriculum_standard",
                                    "课程标准",
                                    List.of(
                                            new WikiDomainProfileMetadataField("edition", "课标版本 / 年份", "如：2022版义务教育课标", true, 1),
                                            new WikiDomainProfileMetadataField("source", "适用来源 / 专题", "如：现代文阅读 / 课内同步 / 期中复习", true, 2),
                                            new WikiDomainProfileMetadataField("grade", "适用学段 / 年级", "如：初中 / 七年级", true, 3),
                                            new WikiDomainProfileMetadataField("unit", "主题 / 单元", "如：文学阅读与创意表达", true, 4),
                                            new WikiDomainProfileMetadataField("routeTagsInput", "路由标签", "如：教材同步, 阅读理解, 七上, 第一单元", true, 5)
                                    )
                            ),
                            new WikiDomainProfileMaterialType(
                                    "textbook_latest",
                                    "最新教材",
                                    List.of(
                                            new WikiDomainProfileMetadataField("edition", "教材版本 / 版次", "如：部编版 / 统编版", true, 1),
                                            new WikiDomainProfileMetadataField("grade", "适用年级", "如：七年级", true, 2),
                                            new WikiDomainProfileMetadataField("volume", "册别", "如：上册 / 下册", true, 3),
                                            new WikiDomainProfileMetadataField("unit", "单元 / 专题", "如：第一单元 / 名著导读", true, 4),
                                            new WikiDomainProfileMetadataField("chapter", "课文 / 章节", "如：《春》/ 第三课", true, 5),
                                            new WikiDomainProfileMetadataField("routeTagsInput", "路由标签", "如：教材同步, 阅读理解, 七上, 第一单元", true, 6)
                                    )
                            ),
                            new WikiDomainProfileMaterialType(
                                    "classic_manuscript",
                                    "名著稿件",
                                    List.of(
                                            new WikiDomainProfileMetadataField("source", "来源说明", "如：整本书阅读任务群", true, 1),
                                            new WikiDomainProfileMetadataField("chapter", "章节 / 篇目", "如：孙悟空三打白骨精", true, 2),
                                            new WikiDomainProfileMetadataField("classicName", "名著名称", "如：西游记 / 朝花夕拾", true, 3),
                                            new WikiDomainProfileMetadataField("routeTagsInput", "路由标签", "如：教材同步, 阅读理解, 七上, 第一单元", true, 4)
                                    )
                            ),
                            new WikiDomainProfileMaterialType(
                                    "question_rule",
                                    "题型要求",
                                    List.of(
                                            new WikiDomainProfileMetadataField("edition", "教材版本 / 版次", "如：部编版 / 统编版", true, 1),
                                            new WikiDomainProfileMetadataField("source", "适用来源 / 专题", "如：现代文阅读 / 课内同步 / 期中复习", true, 2),
                                            new WikiDomainProfileMetadataField("grade", "适用年级", "如：七年级", true, 3),
                                            new WikiDomainProfileMetadataField("volume", "适用册别 / 学期", "如：七上 / 九下 / 一轮复习", true, 4),
                                            new WikiDomainProfileMetadataField("unit", "单元 / 专题", "如：第一单元 / 名著导读", true, 5),
                                            new WikiDomainProfileMetadataField("chapter", "课文 / 章节", "如：《春》/ 第三课", true, 6),
                                            new WikiDomainProfileMetadataField("routeTagsInput", "路由标签", "如：教材同步, 阅读理解, 七上, 第一单元", true, 7)
                                    )
                            ),
                            new WikiDomainProfileMaterialType(
                                    "sample_question",
                                    "样题",
                                    List.of(
                                            new WikiDomainProfileMetadataField("edition", "教材版本 / 版次", "如：部编版 / 统编版", true, 1),
                                            new WikiDomainProfileMetadataField("source", "适用来源 / 专题", "如：现代文阅读 / 课内同步 / 期中复习", true, 2),
                                            new WikiDomainProfileMetadataField("grade", "适用年级", "如：七年级", true, 3),
                                            new WikiDomainProfileMetadataField("volume", "册别", "如：上册 / 下册", true, 4),
                                            new WikiDomainProfileMetadataField("unit", "单元 / 专题", "如：第一单元 / 名著导读", true, 5),
                                            new WikiDomainProfileMetadataField("chapter", "课文 / 章节", "如：《春》/ 第三课", true, 6),
                                            new WikiDomainProfileMetadataField("routeTagsInput", "路由标签", "如：教材同步, 阅读理解, 七上, 第一单元", true, 7)
                                    )
                            ),
                            new WikiDomainProfileMaterialType(
                                    "answer_rubric",
                                    "答案与评分标准",
                                    List.of(
                                            new WikiDomainProfileMetadataField("edition", "教材版本 / 版次", "如：部编版 / 统编版", true, 1),
                                            new WikiDomainProfileMetadataField("source", "适用来源 / 专题", "如：现代文阅读 / 课内同步 / 期中复习", true, 2),
                                            new WikiDomainProfileMetadataField("grade", "适用年级", "如：七年级", true, 3),
                                            new WikiDomainProfileMetadataField("volume", "册别", "如：上册 / 下册", true, 4),
                                            new WikiDomainProfileMetadataField("unit", "单元 / 专题", "如：第一单元 / 名著导读", true, 5),
                                            new WikiDomainProfileMetadataField("chapter", "课文 / 章节", "如：《春》/ 第三课", true, 6),
                                            new WikiDomainProfileMetadataField("routeTagsInput", "路由标签", "如：教材同步, 阅读理解, 七上, 第一单元", true, 7)
                                    )
                            )
                    )
            )
    );

    public List<WikiDomainProfileOption> listProfiles() {
        return List.copyOf(registryProfiles);
    }

    public WikiDomainProfileOption findProfile(String profileId) {
        if (!StringUtils.hasText(profileId)) {
            return null;
        }
        String normalizedId = profileId.trim();
        return registryProfiles.stream()
                .filter(item -> item.id().equals(normalizedId))
                .findFirst()
                .orElse(null);
    }


    public String displayNameOrDefault(String profileId) {
        WikiDomainProfileOption option = findProfile(profileId);
        if (option == null) {
            return StringUtils.hasText(profileId) ? profileId.trim() : "";
        }
        return StringUtils.hasText(option.displayName()) ? option.displayName() : option.id();
    }

    public int matchScore(String profileId, String userQuery) {
        WikiDomainProfileOption option = findProfile(profileId);
        if (option == null || !StringUtils.hasText(userQuery)) {
            return 0;
        }
        String normalizedQuery = normalize(userQuery);
        int score = 0;
        score += scoreCandidate(normalizedQuery, option.id(), 9);
        score += scoreCandidate(normalizedQuery, option.displayName(), 12);
        score += scoreCandidate(normalizedQuery, option.description(), 8);
        score += scoreCandidate(normalizedQuery, option.capabilityPackId(), 6);
        score += scoreCandidate(normalizedQuery, option.sourcePluginKey(), 4);
        return score;
    }

    public boolean isKnownProfile(String profileId) {
        if (!StringUtils.hasText(profileId)) {
            return true;
        }
        return registryProfiles.stream().anyMatch(item -> item.id().equals(profileId.trim()));
    }


    /**
     * T2-4-8: Register an externally-contributed profile (e.g., from capability pack JSON).
     * Duplicates are ignored (first registration wins).
     */
    public void registerProfile(WikiDomainProfileOption profile) {
        if (profile == null || !StringUtils.hasText(profile.id())) {
            return;
        }
        boolean exists = BUILT_IN_PROFILES.stream().anyMatch(p -> p.id().equals(profile.id().trim()));
        if (exists) {
            log.debug("[WikiDomainProfile] Profile {} already registered, skipping external contribution", profile.id());
            return;
        }
        // Copy into a mutable list, add, then replace (BUILT_IN_PROFILES is currently an immutable List.of)
        // To support dynamic registration, we switch to an internal mutable list.
        synchronized (this) {
            if (registryProfiles.stream().anyMatch(p -> p.id().equals(profile.id().trim()))) {
                return;
            }
            registryProfiles.add(profile);
            log.info("[WikiDomainProfile] Registered external profile: {}", profile.id());
        }
    }

    private final List<WikiDomainProfileOption> registryProfiles = new ArrayList<>(BUILT_IN_PROFILES);


    private int scoreCandidate(String normalizedQuery, String candidate, int directHitBonus) {
        String normalizedCandidate = normalize(candidate);
        if (!StringUtils.hasText(normalizedQuery) || !StringUtils.hasText(normalizedCandidate)) {
            return 0;
        }
        int score = 0;
        if (normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate)) {
            score += directHitBonus;
        }
        for (String token : tokenize(normalizedCandidate)) {
            if (token.length() >= 2 && normalizedQuery.contains(token)) {
                score += Math.min(6, Math.max(2, token.length()));
            }
        }
        return score;
    }

    private List<String> tokenize(String normalizedValue) {
        if (!StringUtils.hasText(normalizedValue)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalizedValue.split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            tokens.add(normalizedValue);
        }
        return tokens;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
    }
}
