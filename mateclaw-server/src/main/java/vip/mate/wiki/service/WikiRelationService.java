package vip.mate.wiki.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.wiki.dto.*;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.relation.RelationSignalStrategy;
import vip.mate.wiki.repository.WikiPageCitationMapper;
import vip.mate.wiki.repository.WikiPageMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RFC-029: Wiki relation service — computes multi-signal structural
 * relevance between pages using all registered {@link RelationSignalStrategy} beans.
 */
@Slf4j
@Service
public class WikiRelationService {

    private final List<RelationSignalStrategy> signals;
    private final WikiPageMapper pageMapper;
    private final WikiPageService pageService;
    private final WikiPageCitationMapper citationMapper;

    public WikiRelationService(List<RelationSignalStrategy> signals,
                                WikiPageMapper pageMapper,
                                WikiPageService pageService,
                                WikiPageCitationMapper citationMapper) {
        this.signals = signals;
        this.pageMapper = pageMapper;
        this.pageService = pageService;
        this.citationMapper = citationMapper;
    }

    /**
     * Find pages related to a seed page, ranked by multi-signal score.
     */
    public List<RelatedPageResult> relatedPages(Long kbId, String seedSlug, int topK) {
        WikiPageEntity seed = pageService.getBySlug(kbId, seedSlug);
        if (seed == null) return List.of();

        Map<Long, Double> totalScores = new HashMap<>();
        Map<Long, List<String>> signalHits = new HashMap<>();

        for (RelationSignalStrategy signal : signals) {
            try {
                signal.score(seed.getId(), kbId).forEach((pid, s) -> {
                    totalScores.merge(pid, s, Double::sum);
                    signalHits.computeIfAbsent(pid, k -> new ArrayList<>()).add(signal.signalName());
                });
            } catch (Exception e) {
                log.warn("[WikiRelation] Signal '{}' failed for seed={}: {}",
                        signal.signalName(), seedSlug, e.getMessage());
            }
        }

        List<Long> topIds = totalScores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(topK)
            .map(Map.Entry::getKey)
            .toList();

        if (topIds.isEmpty()) return List.of();
        Map<Long, WikiPageLite> liteMap = pageMapper.selectBatchLite(topIds)
            .stream().collect(Collectors.toMap(WikiPageLite::id, l -> l));
        Map<Long, WikiPageEntity> pageMap = pageMapper.selectBatchIds(topIds)
                .stream().collect(Collectors.toMap(WikiPageEntity::getId, p -> p));

        return topIds.stream()
            .filter(liteMap::containsKey)
            // RFC-051 PR-2: hide system pages (overview / log) from related results.
            .filter(pid -> !liteMap.get(pid).isSystem())
            .map(pid -> {
                List<String> signals = new ArrayList<>(signalHits.getOrDefault(pid, List.of()));
                String teacherRelation = inferTeacherRelation(seed, pageMap.get(pid));
                if (teacherRelation != null && !signals.contains(teacherRelation)) {
                    signals.add(teacherRelation);
                }
                return new RelatedPageResult(
                        liteMap.get(pid).slug(),
                        liteMap.get(pid).title(),
                        liteMap.get(pid).summary(),
                        totalScores.get(pid),
                        signals);
            })
            .toList();
    }

    /**
     * Explain the relation between two pages with a per-signal breakdown.
     */
    public RelationExplanation explain(Long kbId, String slugA, String slugB) {
        WikiPageEntity a = pageService.getBySlug(kbId, slugA);
        WikiPageEntity b = pageService.getBySlug(kbId, slugB);
        if (a == null || b == null) return RelationExplanation.notFound();

        List<SignalScore> breakdown = new ArrayList<>();
        double total = 0;
        for (RelationSignalStrategy signal : signals) {
            try {
                Double score = signal.score(a.getId(), kbId).get(b.getId());
                if (score != null && score > 0) {
                    breakdown.add(new SignalScore(signal.signalName(), signal.weight(), score));
                    total += score;
                }
            } catch (Exception e) {
                log.warn("[WikiRelation] Signal '{}' failed for explain {}<->{}: {}",
                        signal.signalName(), slugA, slugB, e.getMessage());
            }
        }
        String teacherRelation = inferTeacherRelation(a, b);
        if (teacherRelation != null) {
            breakdown.add(new SignalScore(teacherRelation, 1.0, 1.0));
            total += 1.0;
        }
        return new RelationExplanation(slugA, slugB, total, breakdown);
    }

    private String inferTeacherRelation(WikiPageEntity a, WikiPageEntity b) {
        if (a == null || b == null) return null;
        String left = metadataProbe(a);
        String right = metadataProbe(b);
        if (left.isBlank() || right.isBlank()) return null;
        if (contains(left, "curriculum_standard") && hasAny(right, "question_rule", "sample_question", "answer_rubric", "textbook_latest")) {
            return "constrains";
        }
        if (contains(right, "curriculum_standard") && hasAny(left, "question_rule", "sample_question", "answer_rubric", "textbook_latest")) {
            return "constrains";
        }
        if (contains(left, "sample_question") && contains(right, "question_rule")
                || contains(right, "sample_question") && contains(left, "question_rule")) {
            return "exemplifies";
        }
        if (contains(left, "answer_rubric") && hasAny(right, "sample_question", "question_rule")
                || contains(right, "answer_rubric") && hasAny(left, "sample_question", "question_rule")) {
            return "scores";
        }
        if (hasAny(left, "textbook_latest", "classic_manuscript") && hasAny(right, "sample_question", "question_rule", "answer_rubric")
                || hasAny(right, "textbook_latest", "classic_manuscript") && hasAny(left, "sample_question", "question_rule", "answer_rubric")) {
            return "grounds";
        }
        if (hasAny(left, "textbook_latest", "classic_manuscript") && contains(right, "curriculum_standard")
                || hasAny(right, "textbook_latest", "classic_manuscript") && contains(left, "curriculum_standard")) {
            return "supports";
        }
        return null;
    }

    private String metadataProbe(WikiPageEntity page) {
        return String.join(" ",
                page.getPageType() == null ? "" : page.getPageType(),
                page.getRouteTagsJson() == null ? "" : page.getRouteTagsJson(),
                page.getStructureMetadataJson() == null ? "" : page.getStructureMetadataJson()
        ).toLowerCase(Locale.ROOT);
    }

    private boolean contains(String text, String value) {
        return text != null && value != null && text.contains(value.toLowerCase(Locale.ROOT));
    }

    private boolean hasAny(String text, String... values) {
        for (String value : values) {
            if (contains(text, value)) return true;
        }
        return false;
    }

    /**
     * Find all pages derived from a given raw material.
     */
    public List<WikiPageLite> pagesByRawId(Long rawId) {
        List<Long> pageIds = citationMapper.listPageIdsByRawId(rawId);
        if (pageIds.isEmpty()) return List.of();
        return pageMapper.selectBatchLite(pageIds);
    }

    /**
     * Find all pages that cite a given chunk.
     */
    public List<WikiPageLite> pagesByChunkId(Long chunkId) {
        List<Long> pageIds = citationMapper.listPageIdsByChunkId(chunkId);
        if (pageIds.isEmpty()) return List.of();
        return pageMapper.selectBatchLite(pageIds);
    }
}
