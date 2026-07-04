package vip.mate.wiki.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.wiki.dto.WikiMaterialCoverageReport;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.model.WikiRawMaterialEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes material coverage scores for business modules across
 * agent-bound knowledge bases.
 * <p>
 * Used by {@code wiki_check_material_coverage} tool so the Agent can
 * decide whether sufficient source material exists before generating exam questions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiMaterialCoverageService {

    private final WikiKnowledgeBaseService kbService;
    private final WikiPageService pageService;
    private final WikiRawMaterialService rawService;

    /**
     * Check coverage for required business modules under the given agent.
     *
     * @param agentId        the agent whose bound KBs should be scanned
     * @param requiredModules list of business module identifiers (e.g., classic_reading)
     * @return coverage report per module
     */
    public List<WikiMaterialCoverageReport> checkCoverage(Long agentId, List<String> requiredModules) {
        List<WikiKnowledgeBaseEntity> kbs = kbService.listByAgentId(agentId);
        if (kbs == null || kbs.isEmpty() || requiredModules == null || requiredModules.isEmpty()) {
            return List.of();
        }

        // Aggregate pages and raws from all bound KBs
        Map<String, Integer> pageCounts = new HashMap<>();
        Map<String, Set<Long>> rawIdsPerModule = new HashMap<>();

        for (WikiKnowledgeBaseEntity kb : kbs) {
            // Only scan business-domain KBs; general KBs don't contribute business module tags
            if (!StringUtils.hasText(kb.getDomainProfileId())) {
                continue;
            }
            List<WikiPageEntity> pages = pageService.listByKbId(kb.getId());
            for (WikiPageEntity page : pages) {
                List<String> modules = extractBusinessModules(page);
                for (String module : modules) {
                    if (requiredModules.contains(module)) {
                        pageCounts.merge(module, 1, Integer::sum);
                        // Track raw materials that contribute to this module
                        List<Long> rawIds = parseSourceRawIds(page.getSourceRawIds());
                        rawIdsPerModule.computeIfAbsent(module, k -> new HashSet<>()).addAll(rawIds);
                    }
                }
            }
        }

        List<WikiMaterialCoverageReport> reports = new ArrayList<>();
        for (String module : requiredModules) {
            int pageCount = pageCounts.getOrDefault(module, 0);
            int rawCount = rawIdsPerModule.getOrDefault(module, Set.of()).size();
            double score = computeScore(pageCount, rawCount);
            List<String> missing = new ArrayList<>();
            if (pageCount == 0) {
                missing.add("缺少 " + module + " 相关的知识库页面");
            } else if (pageCount < 3) {
                missing.add(module + " 相关页面较少（" + pageCount + " 页），建议补充更多资料");
            }
            if (rawCount == 0) {
                missing.add("缺少 " + module + " 相关的原始材料");
            }
            reports.add(new WikiMaterialCoverageReport(module, pageCount, rawCount, score, missing));
        }
        return reports;
    }

    private double computeScore(int pageCount, int rawCount) {
        if (pageCount >= 10 && rawCount >= 2) return 1.0;
        if (pageCount >= 5 && rawCount >= 1) return 0.8;
        if (pageCount >= 3) return 0.6;
        if (pageCount >= 1) return 0.4;
        if (rawCount >= 1) return 0.2;
        return 0.0;
    }

    private List<String> extractBusinessModules(WikiPageEntity page) {
        String json = page.getStructureMetadataJson();
        if (!StringUtils.hasText(json) || !JSONUtil.isTypeJSON(json)) {
            return List.of();
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            JSONArray arr = obj.getJSONArray("businessModules");
            if (arr == null) return List.of();
            List<String> modules = new ArrayList<>();
            for (Object item : arr) {
                if (item != null) modules.add(item.toString());
            }
            return modules;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Long> parseSourceRawIds(String sourceRawIds) {
        if (!StringUtils.hasText(sourceRawIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String s : sourceRawIds.replaceAll("[\\[\\]\\s]", "").split(",")) {
            if (!StringUtils.hasText(s)) continue;
            try {
                ids.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }
}
