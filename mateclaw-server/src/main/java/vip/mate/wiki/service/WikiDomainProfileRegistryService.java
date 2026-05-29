package vip.mate.wiki.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.teacher.service.TeacherIntentService;
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
@Service
public class WikiDomainProfileRegistryService {

    private static final List<WikiDomainProfileOption> BUILT_IN_PROFILES = List.of(
            new WikiDomainProfileOption(
                    "education.exam.junior_chinese",
                    "初中语文教学命题",
                    "适用于初中语文教材、课标、名著稿件、样题与评分标准等资料治理。",
                    "business",
                    TeacherIntentService.PLUGIN_KEY,
                    TeacherIntentService.CAPABILITY_PACK_ID
            )
    );

    public List<WikiDomainProfileOption> listProfiles() {
        return BUILT_IN_PROFILES;
    }

    public WikiDomainProfileOption findProfile(String profileId) {
        if (!StringUtils.hasText(profileId)) {
            return null;
        }
        String normalizedId = profileId.trim();
        return BUILT_IN_PROFILES.stream()
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
        return BUILT_IN_PROFILES.stream().anyMatch(item -> item.id().equals(profileId.trim()));
    }

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
