package vip.mate.wiki.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.wiki.WikiProperties;
import vip.mate.wiki.dto.PageSearchResult;
import vip.mate.wiki.dto.RawSearchRef;
import vip.mate.wiki.dto.RelatedPageResult;
import vip.mate.wiki.dto.WikiIntentFilter;
import vip.mate.wiki.dto.WikiPageLite;
import vip.mate.wiki.model.WikiChunkEntity;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.repository.WikiPageMapper;
import vip.mate.wiki.repository.WikiRawMaterialMapper;
import vip.mate.wiki.retrieval.SnippetExtractor;

import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * RFC-011 + RFC-032: Hybrid retrieval service.
 * <p>
 * Three search modes: keyword (DB LIKE), semantic (chunk vectors),
 * hybrid (RRF fusion). RFC-032 adds: N+1 fix, two-phase keyword search,
 * relation boost, snippet extraction, and PageSearchResult DTO.
 */
@Slf4j
@Service
public class HybridRetriever {

    private final WikiPageService pageService;
    private final WikiChunkService chunkService;
    private final WikiEmbeddingService embeddingService;
    private final WikiProperties properties;
    private final WikiPageMapper pageMapper;
    private final WikiRawMaterialMapper rawMaterialMapper;

    @Autowired(required = false)
    private WikiRelationService relationService;

    private static final double RELATION_BOOST = 0.15;

    public HybridRetriever(WikiPageService pageService,
                            WikiChunkService chunkService,
                            WikiEmbeddingService embeddingService,
                            WikiProperties properties,
                            WikiPageMapper pageMapper,
                            WikiRawMaterialMapper rawMaterialMapper) {
        this.pageService = pageService;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.pageMapper = pageMapper;
        this.rawMaterialMapper = rawMaterialMapper;
    }

    public enum Mode { KEYWORD, SEMANTIC, HYBRID }

    /**
     * Legacy page hit record (kept for backward compatibility).
     */
    public record PageHit(Long pageId, String slug, String title, String summary, double score) {}

    /**
     * Chunk-level search result (semantic search).
     * <p>
     * RFC-051 PR-1c: {@code pageNumber} and {@code headerBreadcrumb} are
     * populated when the chunk has those columns set (lazy ingest with
     * preprocessor on, or backfilled chunks). Both are nullable.
     */
    public record ChunkHit(Long chunkId, Long rawId, String snippet, float score,
                            Integer pageNumber, String headerBreadcrumb) {

        /** Backwards-compatible factory for callers that don't yet pass metadata. */
        public ChunkHit(Long chunkId, Long rawId, String snippet, float score) {
            this(chunkId, rawId, snippet, score, null, null);
        }
    }

    /**
     * RFC-032: Enhanced search returning PageSearchResult with snippet and matchedBy metadata.
     */
    public List<PageSearchResult> search(Long kbId, String query, String modeStr, int topK) {
        return search(kbId, query, modeStr, topK, null);
    }

    /**
     * T2-4-3: Intent-aware search that boosts business-relevant materials.
     */
    public List<PageSearchResult> search(Long kbId, String query, String modeStr, int topK, WikiIntentFilter intentFilter) {
        Mode mode = parseMode(modeStr);

        List<RankedItem> semantic = List.of();
        List<RankedItem> keyword = List.of();

        if (mode != Mode.KEYWORD && embeddingService.isAvailable()) {
            semantic = semanticSearch(kbId, query, topK * 3);
        }
        if (mode != Mode.SEMANTIC) {
            keyword = keywordSearch(kbId, query, topK * 3);
        }

        if (mode == Mode.SEMANTIC && semantic.isEmpty()) {
            keyword = keywordSearch(kbId, query, topK * 3);
        }

        List<RankedItem> fused;
        if (mode == Mode.KEYWORD || semantic.isEmpty()) {
            fused = keyword;
        } else if (mode == Mode.SEMANTIC) {
            fused = semantic;
        } else {
            fused = rrfFuse(semantic, keyword, 60);
        }

        // RFC-032: Relation boost (1-hop expansion on top-3 seeds)
        fused = applyRelationBoost(fused, kbId, topK);

        fused = applyMaterialBoost(fused, topK, query, intentFilter);


        // Batch-fetch page info (N+1 fix)
        List<Long> topIds = fused.stream().limit(topK).map(ri -> ri.pageId).toList();
        if (topIds.isEmpty()) return List.of();

        Map<Long, WikiPageLite> liteMap = pageMapper.selectBatchLite(topIds)
            .stream().collect(Collectors.toMap(WikiPageLite::id, p -> p));
        Map<Long, WikiPageEntity> pageMap = pageMapper.selectBatchIds(topIds)
                .stream().collect(Collectors.toMap(WikiPageEntity::getId, page -> page));
        Map<Long, RawSearchRef> rawRefMap = loadRawSearchRefs(pageMap.values());

        // Build result with snippets
        List<PageSearchResult> results = new ArrayList<>();
        for (RankedItem ri : fused.stream().limit(topK).toList()) {
            WikiPageLite lite = liteMap.get(ri.pageId);
            if (lite == null) continue;
            // RFC-051 PR-2: hide system pages (overview / log) from search results.
            if (lite.isSystem()) continue;

            String snippet = null;
            if (!ri.matchedBy.contains("relation_boost")) {
                String content = pageMapper.selectContentById(ri.pageId);
                if (content != null) {
                    snippet = SnippetExtractor.extract(content, query);
                }
            }

            String reason = buildReason(lite, ri.matchedBy, query);
            String routeTagReason = buildRouteTagReason(pageMap.get(ri.pageId), query);
            String structureReason = buildStructureReason(pageMap.get(ri.pageId), query);
            String materialReason = buildMaterialReason(pageMap.get(ri.pageId), rawRefMap, query);
            // RFC-051 §9.4: when the entry came from relation boost, override / append
            // the reason with the seed slug + dominant signals so callers can explain
            // why an out-of-search-corpus page surfaced.
            if (ri.relationReason() != null && !ri.relationReason().isBlank()) {
                reason = (reason == null || reason.isBlank())
                        ? ri.relationReason()
                        : reason + " · " + ri.relationReason();
            }
            if (StringUtils.hasText(routeTagReason)) {
                reason = (reason == null || reason.isBlank())
                        ? routeTagReason
                        : reason + " · " + routeTagReason;
            }
            if (StringUtils.hasText(structureReason)) {
                reason = (reason == null || reason.isBlank())
                        ? structureReason
                        : reason + " · " + structureReason;
            }
            if (StringUtils.hasText(materialReason)) {
                reason = (reason == null || reason.isBlank())
                        ? materialReason
                        : reason + " · " + materialReason;
            }
            results.add(new PageSearchResult(
                lite.slug(), lite.title(), lite.summary(),
                snippet != null ? snippet : lite.summary(),
                ri.matchedBy, reason, ri.score));
        }
        return results;
    }

    /**
     * Legacy searchPages — returns PageHit for backward compatibility.
     */
    public List<PageHit> searchPages(Long kbId, String query, String modeStr, int topK) {
        return search(kbId, query, modeStr, topK).stream()
            .map(r -> new PageHit(null, r.slug(), r.title(), r.summary(), r.score()))
            .toList();
    }

    /**
     * Chunk-level semantic search.
     */
    public List<ChunkHit> searchChunks(Long kbId, String query, int topK) {
        return searchChunks(kbId, query, topK, null);
    }

    public List<ChunkHit> searchChunks(Long kbId, String query, int topK, WikiIntentFilter intentFilter) {
        if (!embeddingService.isAvailable()) return List.of();

        float[] queryVec = embeddingService.embedQuery(kbId, query);
        if (queryVec == null) return List.of();

        List<WikiChunkEntity> allChunks = chunkService.listByKbId(kbId);

        Map<Long, RawSearchRef> rawRefMap = loadRawSearchRefs(allChunks.stream()
            .map(WikiChunkEntity::getRawId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));

        List<String> preferredTypes = intentFilter != null && intentFilter.preferredMaterialTypes() != null
                ? intentFilter.preferredMaterialTypes()
                : List.of();

        return allChunks.stream()
                .filter(c -> c.getEmbedding() != null)
                .map(c -> {
                    float[] chunkVec = WikiEmbeddingService.bytesToFloats(c.getEmbedding());
                    float score = WikiEmbeddingService.cosine(queryVec, chunkVec);
                    score += materialBoost(rawRefMap.get(c.getRawId()), query, 0.8d);
                    // T2-4-3: Intent boost for chunk-level results
                    if (!preferredTypes.isEmpty()) {
                        RawSearchRef ref = rawRefMap.get(c.getRawId());
                        if (ref != null && ref.materialType() != null && preferredTypes.contains(ref.materialType())) {
                            score += 0.25f;
                        }
                    }
                    String snippet = c.getContent().length() > 300
                            ? c.getContent().substring(0, 300) + "..."
                            : c.getContent();
                    return new ChunkHit(c.getId(), c.getRawId(), snippet, score,
                            c.getPageNumber(), c.getHeaderBreadcrumb());
                })
                .sorted(Comparator.comparingDouble(ChunkHit::score).reversed())
                .limit(topK)
                .toList();
    }


    // ==================== Internal methods ====================

    /** Semantic search: chunk cosine → aggregate to page level */
    private List<RankedItem> semanticSearch(Long kbId, String query, int limit) {
        float[] queryVec = embeddingService.embedQuery(kbId, query);
        if (queryVec == null) return List.of();

        List<WikiChunkEntity> allChunks = chunkService.listByKbId(kbId);
        if (allChunks.isEmpty()) return List.of();

        Map<Long, Float> chunkScores = new HashMap<>();
        for (WikiChunkEntity chunk : allChunks) {
            if (chunk.getEmbedding() == null || chunk.getRawId() == null) continue;
            float[] vec = WikiEmbeddingService.bytesToFloats(chunk.getEmbedding());
            float score = WikiEmbeddingService.cosine(queryVec, vec);
            chunkScores.merge(chunk.getRawId(), score, Math::max);
        }

        List<WikiPageEntity> allPages = pageService.listByKbId(kbId);
        Map<Long, Double> pageScores = new HashMap<>();
        for (WikiPageEntity page : allPages) {
            String rawIds = page.getSourceRawIds();
            if (rawIds == null) continue;
            for (String rawIdStr : rawIds.replaceAll("[\\[\\]\\s]", "").split(",")) {
                try {
                    long rawId = Long.parseLong(rawIdStr.trim());
                    Float score = chunkScores.get(rawId);
                    if (score != null) {
                        pageScores.merge(page.getId(), (double) score, Math::max);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return pageScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new RankedItem(e.getKey(), e.getValue(), List.of("semantic")))
                .toList();
    }

    /**
     * RFC-032: Two-phase keyword search — fast path (title+summary) first,
     * full content search only if needed to fill topK.
     */
    private List<RankedItem> keywordSearch(Long kbId, String query, int limit) {
        String kw = "%" + query.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";

        // Phase 1: fast path (title + summary only)
        List<Long> fastIds = pageMapper.searchFastIds(kbId, kw, limit);

        List<RankedItem> ranked = new ArrayList<>();
        for (int i = 0; i < fastIds.size(); i++) {
            ranked.add(new RankedItem(fastIds.get(i), 1.0 / (i + 1), List.of("title")));
        }

        if (fastIds.size() >= limit) return ranked;

        // Phase 2: full content search (supplement)
        List<Long> contentIds = pageMapper.searchContentIds(kbId, kw, fastIds, limit - fastIds.size());
        for (int i = 0; i < contentIds.size(); i++) {
            ranked.add(new RankedItem(contentIds.get(i),
                    1.0 / (fastIds.size() + i + 1), List.of("content")));
        }

        return ranked;
    }

    private List<RankedItem> applyMaterialBoost(List<RankedItem> hits, int topK, String query, WikiIntentFilter intentFilter) {
        if (hits.isEmpty()) {
            return hits;
        }
        int candidateLimit = Math.min(hits.size(), Math.max(topK * 3, topK));
        List<RankedItem> candidates = hits.stream().limit(candidateLimit).toList();
        Map<Long, WikiPageEntity> pageMap = pageMapper.selectBatchIds(candidates.stream().map(RankedItem::pageId).toList())
                .stream().collect(Collectors.toMap(WikiPageEntity::getId, page -> page));
        Map<Long, RawSearchRef> rawRefMap = loadRawSearchRefs(pageMap.values());

        List<String> preferredTypes = intentFilter != null && intentFilter.preferredMaterialTypes() != null
                ? intentFilter.preferredMaterialTypes()
                : List.of();

        List<RankedItem> reranked = new ArrayList<>();
        for (RankedItem hit : candidates) {
            WikiPageEntity page = pageMap.get(hit.pageId());
            double routeBoost = page == null ? 0.0d : pageRouteTagBoost(page, query);
            double structureBoost = page == null ? 0.0d : pageStructureBoost(page, query);
            double materialBoost = page == null ? 0.0d : pageMaterialBoost(page, rawRefMap, query);
            // T2-4-3: Intent-based extra boost for preferred material types
            double intentBoost = 0.0d;
            if (!preferredTypes.isEmpty() && page != null) {
                intentBoost = pageIntentBoost(page, rawRefMap, preferredTypes);
            }
            double boost = routeBoost + structureBoost + materialBoost + intentBoost;

            List<String> matchedBy = hit.matchedBy();
            if (routeBoost > 0.01d && !matchedBy.contains("route_tags")) {
                List<String> extended = new ArrayList<>(matchedBy);
                extended.add("route_tags");
                matchedBy = extended.stream().distinct().toList();
            }
            if (structureBoost > 0.01d && !matchedBy.contains("page_structure")) {
                List<String> extended = new ArrayList<>(matchedBy);
                extended.add("page_structure");
                matchedBy = extended.stream().distinct().toList();
            }
            if (boost > 0.01d && !matchedBy.contains("material_boost")) {
                List<String> extended = new ArrayList<>(matchedBy);
                extended.add("material_boost");
                matchedBy = extended.stream().distinct().toList();
            }
            reranked.add(new RankedItem(hit.pageId(), hit.score() + boost, matchedBy, hit.relationReason()));
        }

        List<RankedItem> remainder = new ArrayList<>(hits.stream().skip(candidateLimit).toList());
        List<RankedItem> combined = new ArrayList<>(reranked.size() + remainder.size());
        combined.addAll(reranked.stream()
                .sorted(Comparator.comparingDouble(RankedItem::score).reversed())
                .toList());
        combined.addAll(remainder);
        return combined;
    }

    private Map<Long, RawSearchRef> loadRawSearchRefs(Collection<WikiPageEntity> pages) {
        Set<Long> rawIds = new LinkedHashSet<>();
        for (WikiPageEntity page : pages) {
            rawIds.addAll(extractRawIds(page == null ? null : page.getSourceRawIds()));
        }
        return loadRawSearchRefs(rawIds);
    }

    private Map<Long, RawSearchRef> loadRawSearchRefs(Set<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Map.of();
        }
        return rawMaterialMapper.selectBatchSearchRefs(rawIds).stream()
                .collect(Collectors.toMap(RawSearchRef::id, ref -> ref));
    }

    private List<Long> extractRawIds(String sourceRawIds) {
        if (!StringUtils.hasText(sourceRawIds)) {
            return List.of();
        }
        List<Long> rawIds = new ArrayList<>();
        for (String rawIdStr : sourceRawIds.replaceAll("[\\[\\]\\s]", "").split(",")) {
            if (!StringUtils.hasText(rawIdStr)) {
                continue;
            }
            try {
                rawIds.add(Long.parseLong(rawIdStr.trim()));
            } catch (NumberFormatException ignored) {
                // ignore malformed ids
            }
        }
        return rawIds;
    }

    private double pageMaterialBoost(WikiPageEntity page,
                                     Map<Long, RawSearchRef> rawRefMap,
                                     String query) {
        double boost = 0.0d;
        for (Long rawId : extractRawIds(page.getSourceRawIds())) {
            boost += materialBoost(rawRefMap.get(rawId), query, 1.2d);
        }
        return Math.min(1.2d, boost);
    }

    /**
     * T2-4-3: Extra boost when page's source raw materials match intent-preferred types.
     */
    private double pageIntentBoost(WikiPageEntity page,
                                    Map<Long, RawSearchRef> rawRefMap,
                                    List<String> preferredMaterialTypes) {
        double boost = 0.0d;
        for (Long rawId : extractRawIds(page.getSourceRawIds())) {
            RawSearchRef ref = rawRefMap.get(rawId);
            if (ref != null && ref.materialType() != null
                    && preferredMaterialTypes.contains(ref.materialType())) {
                boost += 0.35d;
            }
        }
        return Math.min(1.0d, boost);
    }

    private double pageRouteTagBoost(WikiPageEntity page, String query) {

        if (page == null || !StringUtils.hasText(query)) {
            return 0.0d;
        }
        int score = 0;
        for (String routeTag : extractRouteTags(page.getRouteTagsJson())) {
            score += scoreByQuery(routeTag, query);
        }
        return Math.min(0.8d, score * 0.015d);
    }

    private double pageStructureBoost(WikiPageEntity page, String query) {
        if (page == null || !StringUtils.hasText(query)) {
            return 0.0d;
        }
        int score = scoreByQuery(page.getPurposeHint(), query);
        StructureMetadata metadata = parseStructureMetadata(page.getStructureMetadataJson());
        if (metadata != null) {
            score += scoreByQuery(metadata.sliceType(), query);
            score += scoreByQuery(metadata.grade(), query);
            score += scoreByQuery(metadata.volume(), query);
            score += scoreByQuery(metadata.unit(), query);
            score += scoreByQuery(metadata.chapter(), query);
            score += scoreByQuery(metadata.classicName(), query);
            score += scoreByQuery(metadata.source(), query);
        }
        return Math.min(0.9d, score * 0.018d);
    }

    private double materialBoost(RawSearchRef rawRef, String query, double maxBoost) {
        if (rawRef == null || !StringUtils.hasText(query)) {
            return 0.0d;
        }
        MaterialMetadata metadata = parseMaterialMetadata(rawRef.materialMetadataJson());
        int typeBonus = preferredMaterialBonus(rawRef.materialType(), query);
        int metadataScore = scoreByQuery(rawRef.title(), query)
                + scoreByQuery(rawRef.materialMetadataJson(), query)
                + scoreStructuredMetadata(metadata, query);
        double boost = typeBonus * 0.05d + metadataScore * 0.01d;
        return Math.min(maxBoost, boost);
    }

    private String buildMaterialReason(WikiPageEntity page,
                                       Map<Long, RawSearchRef> rawRefMap,
                                       String query) {
        if (page == null) {
            return null;
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (Long rawId : extractRawIds(page.getSourceRawIds())) {
            RawSearchRef rawRef = rawRefMap.get(rawId);
            if (rawRef == null) {
                continue;
            }
            MaterialMetadata metadata = parseMaterialMetadata(rawRef.materialMetadataJson());
            if (preferredMaterialBonus(rawRef.materialType(), query) > 0
                    || scoreByQuery(rawRef.materialMetadataJson(), query) > 0
                    || scoreStructuredMetadata(metadata, query) > 0) {
                labels.add(materialReasonLabel(rawRef.materialType(), metadata));
            }
            if (labels.size() >= 2) {
                break;
            }
        }
        if (labels.isEmpty()) {
            return null;
        }
        return "Material match: " + String.join(" / ", labels);
    }

    private String buildRouteTagReason(WikiPageEntity page, String query) {
        if (page == null || !StringUtils.hasText(page.getRouteTagsJson())) {
            return null;
        }
        List<String> matchedTags = new ArrayList<>();
        for (String tag : extractRouteTags(page.getRouteTagsJson())) {
            if (scoreByQuery(tag, query) > 0 && !matchedTags.contains(tag)) {
                matchedTags.add(tag);
            }
            if (matchedTags.size() >= 3) {
                break;
            }
        }
        if (matchedTags.isEmpty()) {
            return null;
        }
        return "Route tags: " + String.join(" / ", matchedTags);
    }

    private String buildStructureReason(WikiPageEntity page, String query) {
        if (page == null) {
            return null;
        }
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        if (scoreByQuery(page.getPurposeHint(), query) > 0) {
            matched.add(page.getPurposeHint());
        }
        StructureMetadata metadata = parseStructureMetadata(page.getStructureMetadataJson());
        if (metadata != null) {
            addMatchedStructure(matched, metadata.sliceType(), query);
            addMatchedStructure(matched, metadata.grade(), query);
            addMatchedStructure(matched, metadata.volume(), query);
            addMatchedStructure(matched, metadata.unit(), query);
            addMatchedStructure(matched, metadata.chapter(), query);
            addMatchedStructure(matched, metadata.classicName(), query);
        }
        if (matched.isEmpty()) {
            return null;
        }
        return "Page structure: " + matched.stream().limit(3).collect(Collectors.joining(" / "));
    }

    private void addMatchedStructure(LinkedHashSet<String> matched, String value, String query) {
        if (StringUtils.hasText(value) && scoreByQuery(value, query) > 0) {
            matched.add(value);
        }
    }

    private int preferredMaterialBonus(String materialType, String query) {
        String normalizedQuery = normalize(query);
        if (!StringUtils.hasText(materialType) || !StringUtils.hasText(normalizedQuery)) {
            return 0;
        }
        return switch (materialType) {
            case "curriculum_standard" -> queryIntentBonus(normalizedQuery, "课标", "课程标准", "新课标", "标准", "素养", "能力要求") + 10;
            case "textbook_latest" -> queryIntentBonus(normalizedQuery, "教材", "课文", "单元", "册", "七年级", "八年级", "九年级", "上册", "下册", "部编版") + 10;
            case "classic_manuscript" -> queryIntentBonus(normalizedQuery, "名著", "西游记", "水浒传", "骆驼祥子", "朝花夕拾", "昆虫记", "经典常谈", "红星照耀中国", "海底两万里") + 10;
            case "question_rule" -> queryIntentBonus(normalizedQuery, "题型", "要求", "规则", "命题", "分值", "比例") + 8;
            case "sample_question" -> queryIntentBonus(normalizedQuery, "样题", "例题", "真题", "模拟题", "试卷") + 8;
            case "answer_rubric" -> queryIntentBonus(normalizedQuery, "答案", "评分", "采分点", "评分标准", "参考答案") + 8;
            default -> 0;
        };
    }

    private String materialTypeLabel(String materialType) {
        if (!StringUtils.hasText(materialType)) {
            return "资料";
        }
        return switch (materialType) {
            case "curriculum_standard" -> "课程标准";
            case "textbook_latest" -> "最新教材";
            case "classic_manuscript" -> "名著稿件";
            case "question_rule" -> "题型要求";
            case "sample_question" -> "样题";
            case "answer_rubric" -> "答案与评分标准";
            default -> materialType;
        };
    }

    private String materialReasonLabel(String materialType, MaterialMetadata metadata) {
        List<String> segments = new ArrayList<>();
        segments.add(materialTypeLabel(materialType));
        if (metadata != null) {
            addReasonSegment(segments, metadata.grade());
            addReasonSegment(segments, metadata.volume());
            addReasonSegment(segments, metadata.unit());
            addReasonSegment(segments, metadata.chapter());
            addReasonSegment(segments, metadata.classicName());
            addReasonSegment(segments, metadata.edition());
        }
        return String.join(" · ", segments.stream().limit(4).toList());
    }

    private void addReasonSegment(List<String> segments, String value) {
        if (StringUtils.hasText(value) && !segments.contains(value)) {
            segments.add(value);
        }
    }

    private int scoreStructuredMetadata(MaterialMetadata metadata, String query) {
        if (metadata == null) {
            return 0;
        }
        int score = 0;
        score += scoreByQuery(metadata.edition(), query);
        score += scoreByQuery(metadata.source(), query);
        score += scoreByQuery(metadata.grade(), query);
        score += scoreByQuery(metadata.volume(), query);
        score += scoreByQuery(metadata.unit(), query);
        score += scoreByQuery(metadata.chapter(), query);
        score += scoreByQuery(metadata.classicName(), query);
        for (String routeTag : metadata.routeTags()) {
            score += scoreByQuery(routeTag, query);
        }
        return score;
    }

    private MaterialMetadata parseMaterialMetadata(String materialMetadataJson) {
        if (!JSONUtil.isTypeJSON(materialMetadataJson)) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(materialMetadataJson);
            return new MaterialMetadata(
                    normalizedMetadataValue(obj.getStr("edition")),
                    normalizedMetadataValue(obj.getStr("source")),
                    normalizedMetadataValue(obj.getStr("grade")),
                    normalizedMetadataValue(obj.getStr("volume")),
                    normalizedMetadataValue(obj.getStr("unit")),
                    normalizedMetadataValue(obj.getStr("chapter")),
                    normalizedMetadataValue(obj.getStr("classicName")),
                    extractRouteTags(obj.get("routeTags"))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private StructureMetadata parseStructureMetadata(String structureMetadataJson) {
        if (!JSONUtil.isTypeJSON(structureMetadataJson)) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(structureMetadataJson);
            return new StructureMetadata(
                    normalizedMetadataValue(obj.getStr("sliceType")),
                    normalizedMetadataValue(obj.getStr("grade")),
                    normalizedMetadataValue(obj.getStr("volume")),
                    normalizedMetadataValue(obj.getStr("unit")),
                    normalizedMetadataValue(obj.getStr("chapter")),
                    normalizedMetadataValue(obj.getStr("classicName")),
                    normalizedMetadataValue(obj.getStr("source"))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> extractRouteTags(Object value) {
        if (value == null) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (value instanceof JSONArray array) {
            for (Object item : array) {
                String normalized = normalizedMetadataValue(item == null ? null : String.valueOf(item));
                if (normalized != null) {
                    tags.add(normalized);
                }
            }
        } else {
            String text = value instanceof String ? (String) value : String.valueOf(value);
            if (JSONUtil.isTypeJSONArray(text)) {
                try {
                    JSONArray array = JSONUtil.parseArray(text);
                    for (Object item : array) {
                        String normalized = normalizedMetadataValue(item == null ? null : String.valueOf(item));
                        if (normalized != null) {
                            tags.add(normalized);
                        }
                    }
                } catch (Exception ignored) {
                    // fall back to plain text split below
                }
            }
            if (tags.isEmpty()) {
                for (String item : text.split("[\\n,，;；|]")) {
                    String normalized = normalizedMetadataValue(item);
                    if (normalized != null) {
                        tags.add(normalized);
                    }
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private String normalizedMetadataValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record MaterialMetadata(
            String edition,
            String source,
            String grade,
            String volume,
            String unit,
            String chapter,
            String classicName,
            List<String> routeTags
    ) {
    }

            private record StructureMetadata(
                String sliceType,
                String grade,
                String volume,
                String unit,
                String chapter,
                String classicName,
                String source
            ) {
            }

    private int scoreByQuery(String candidate, String userQuery) {
        String normalizedCandidate = normalize(candidate);
        String normalizedQuery = normalize(userQuery);
        if (!StringUtils.hasText(normalizedCandidate) || !StringUtils.hasText(normalizedQuery)) {
            return 0;
        }
        int score = 0;
        if (normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate)) {
            score += 18;
        }
        for (String token : tokenize(normalizedQuery)) {
            if (token.length() >= 2 && normalizedCandidate.contains(token)) {
                score += Math.min(8, Math.max(2, token.length()));
            }
        }
        return score;
    }

    private int queryIntentBonus(String normalizedQuery, String... keywords) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return 0;
        }
        int score = 0;
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalizedQuery.contains(normalize(keyword))) {
                score += 5;
            }
        }
        return score;
    }

    private List<String> tokenize(String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalizedQuery.split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            tokens.add(normalizedQuery);
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

    /** RRF fusion: score = Σ 1/(k + rank_i) */
    private List<RankedItem> rrfFuse(List<RankedItem> a, List<RankedItem> b, int k) {
        Map<Long, Double> fused = new HashMap<>();
        Map<Long, List<String>> matchedByMap = new HashMap<>();

        for (int i = 0; i < a.size(); i++) {
            fused.merge(a.get(i).pageId, 1.0 / (k + i + 1), Double::sum);
            matchedByMap.computeIfAbsent(a.get(i).pageId, x -> new ArrayList<>()).addAll(a.get(i).matchedBy);
        }
        for (int i = 0; i < b.size(); i++) {
            fused.merge(b.get(i).pageId, 1.0 / (k + i + 1), Double::sum);
            matchedByMap.computeIfAbsent(b.get(i).pageId, x -> new ArrayList<>()).addAll(b.get(i).matchedBy);
        }

        return fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(e -> new RankedItem(e.getKey(), e.getValue(),
                        matchedByMap.getOrDefault(e.getKey(), List.of()).stream().distinct().toList()))
                .toList();
    }

    /**
     * RFC-032: 1-hop relation boost on top-3 seed pages.
     * <p>
     * RFC-051 §9.4 makes the boost magnitude data-driven instead of a flat
     * constant when {@code mate.wiki.use-normalized-relation-boost} is on.
     */
    private List<RankedItem> applyRelationBoost(List<RankedItem> hits, Long kbId, int topK) {
        if (relationService == null || hits.isEmpty()) return hits;

        List<Long> seedIds = hits.stream().limit(3).map(h -> h.pageId).toList();
        // Per-candidate aggregate raw score (sum of contributions from each seed-relation
        // pair) plus a remembered "best" reason — the seed that contributed the highest
        // relation score and its dominant signals. Used for the human-readable reason
        // surfaced via PageSearchResult.reason.
        Map<Long, Double> rawScoreMap = new HashMap<>();
        Map<Long, RelationReasonRecord> reasonMap = new HashMap<>();

        for (Long seedId : seedIds) {
            List<WikiPageLite> seedLites = pageMapper.selectBatchLite(List.of(seedId));
            if (seedLites.isEmpty()) continue;
            WikiPageLite seed = seedLites.get(0);
            // RFC-051 PR-5: don't expand 1-hop neighborhood from a system page seed.
            // Otherwise the overview / log neighborhood — typically every page that
            // shares a raw with them — leaks into search results via boost. PR-2's
            // result-emit filter drops the system pages themselves; this guard ensures
            // we don't even use them as expansion roots.
            if (seed.isSystem()) continue;
            try {
                relationService.relatedPages(kbId, seed.slug(), 3)
                    .forEach(r -> {
                        WikiPageEntity relPage = pageService.getBySlug(kbId, r.slug());
                        if (relPage == null) return;
                        rawScoreMap.merge(relPage.getId(), r.score(), Double::sum);
                        // Keep the strongest single seed→neighbor pair as the reason.
                        RelationReasonRecord existing = reasonMap.get(relPage.getId());
                        if (existing == null || r.score() > existing.contribution) {
                            reasonMap.put(relPage.getId(),
                                new RelationReasonRecord(seed.slug(), r.signals(), r.score()));
                        }
                    });
            } catch (Exception e) {
                log.debug("[HybridRetriever] Relation boost failed for seed {}: {}", seed.slug(), e.getMessage());
            }
        }

        Set<Long> existingIds = hits.stream().map(h -> h.pageId).collect(Collectors.toSet());
        rawScoreMap.keySet().removeAll(existingIds);

        if (rawScoreMap.isEmpty()) return hits;

        // Choose boost magnitude per candidate: legacy flat constant or normalized × λ.
        Map<Long, Double> boostMap = new HashMap<>();
        if (properties != null && properties.isUseNormalizedRelationBoost()) {
            double maxRaw = rawScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double lambda = Math.max(0, properties.getRelationBoostLambda());
            if (maxRaw <= 0 || lambda <= 0) {
                rawScoreMap.forEach((pid, raw) -> boostMap.put(pid, 0.0));
            } else {
                final double maxRawF = maxRaw;
                rawScoreMap.forEach((pid, raw) -> boostMap.put(pid, (raw / maxRawF) * lambda));
            }
        } else {
            rawScoreMap.forEach((pid, raw) -> boostMap.put(pid, RELATION_BOOST));
        }

        List<RankedItem> expanded = new ArrayList<>(hits);
        boostMap.forEach((pid, score) -> {
            RelationReasonRecord rr = reasonMap.get(pid);
            String reason = rr == null ? null : formatRelationReason(rr);
            expanded.add(new RankedItem(pid, score, List.of("relation_boost"), reason));
        });
        return expanded;
    }

    private String buildReason(WikiPageLite lite, List<String> matchedBy, String query) {
        if (matchedBy.contains("relation_boost")) return "Structurally related to top search results";
        if (matchedBy.contains("page_structure") && matchedBy.contains("route_tags")) return "Page structure and route tag match";
        if (matchedBy.contains("page_structure")) return "Page structure match";
        if (matchedBy.contains("route_tags") && matchedBy.contains("title")) return "Title and route tag match";
        if (matchedBy.contains("route_tags")) return "Route tag match";
        if (matchedBy.contains("title") && matchedBy.contains("semantic")) return "Title and semantic match";
        if (matchedBy.contains("title")) return "Title match";
        if (matchedBy.contains("semantic")) return "Semantic similarity";
        if (matchedBy.contains("content")) return "Content match";
        return "Keyword match";
    }

    private Mode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            String defaultMode = properties.getSearchDefaultMode();
            return switch (defaultMode) {
                case "keyword" -> Mode.KEYWORD;
                case "semantic" -> Mode.SEMANTIC;
                default -> Mode.HYBRID;
            };
        }
        return switch (mode.toLowerCase()) {
            case "keyword" -> Mode.KEYWORD;
            case "semantic" -> Mode.SEMANTIC;
            default -> Mode.HYBRID;
        };
    }

    /**
     * RFC-051 §9.4: optional human-readable explanation for relation boost
     * entries. {@code null} when this RankedItem wasn't produced by the
     * relation pass.
     */
    private record RankedItem(Long pageId, double score, List<String> matchedBy, String relationReason) {
        /** Back-compat ctor — keyword/semantic items don't carry a relation reason. */
        RankedItem(Long pageId, double score, List<String> matchedBy) {
            this(pageId, score, matchedBy, null);
        }
    }

    /** Internal: which seed/signals contributed the strongest relation pull to a candidate. */
    private record RelationReasonRecord(String seedSlug, List<String> signals, double contribution) {}

    private static String formatRelationReason(RelationReasonRecord r) {
        if (r == null) return null;
        StringBuilder sb = new StringBuilder("related to '").append(r.seedSlug()).append("'");
        if (r.signals() != null && !r.signals().isEmpty()) {
            sb.append(" via ").append(String.join("+", r.signals()));
        }
        return sb.toString();
    }
}
