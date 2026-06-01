package vip.mate.wiki.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vip.mate.wiki.dto.RawSearchRef;
import vip.mate.wiki.dto.WikiDerivedView;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.model.WikiRelationEntity;
import vip.mate.wiki.repository.WikiPageCitationMapper;
import vip.mate.wiki.repository.WikiPageMapper;
import vip.mate.wiki.repository.WikiRawMaterialMapper;
import vip.mate.wiki.repository.WikiRelationMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Wiki 页面服务
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiPageService {

    private final WikiPageMapper pageMapper;
    private final ObjectMapper objectMapper;
    private final WikiRawMaterialMapper rawMaterialMapper;
    private final WikiBusinessModuleDeriver businessModuleDeriver;
    private final WikiMaterialProcessingRouter materialProcessingRouter;
    private final WikiPageCitationMapper citationMapper;
    private final WikiRelationMapper relationMapper;
    private final WikiCitationService citationService;


    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)]]");
    private static final List<String> DERIVED_VIEW_ORDER = List.of(
            "textbook_unit",
            "textbook_chapter",
            "textbook_sync",
            "curriculum_view",
            "classic_chapter",
            "classic_theme",
            "classic_plot",
            "classic_excerpt",
            "classic_language",
            "classic_focus",
            "assessment_view",
            "slice_view"
    );

    /** 页面摘要缓存：kbId → (data, expiresAt)。5 分钟 TTL，写操作失效。 */
    private record CachedSummaries(List<WikiPageEntity> data, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }
    private final ConcurrentHashMap<Long, CachedSummaries> summaryCache = new ConcurrentHashMap<>();
    private static final long SUMMARY_CACHE_TTL_MS = 5 * 60_000; // 5 分钟

    /** Agent 引用计数器（内存，不持久化，重启归零） */
    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> refCounter = new ConcurrentHashMap<>();

    /** 记录 Agent 引用（WikiTool 调用时触发） */
    public void trackReference(Long kbId, String slug) {
        refCounter.computeIfAbsent(kbId + ":" + slug, k -> new java.util.concurrent.atomic.AtomicInteger(0))
                .incrementAndGet();
    }

    /** Agent 引用记录 */
    public record ReferenceEntry(String slug, String title, int refCount) {}

    /** 获取被引用最多的页面 Top N */
    public List<ReferenceEntry> getTopReferenced(Long kbId, int limit) {
        String prefix = kbId + ":";
        return refCounter.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                .limit(limit)
                .map(e -> {
                    String slug = e.getKey().substring(prefix.length());
                    WikiPageEntity page = getBySlug(kbId, slug);
                    String title = page != null ? page.getTitle() : slug;
                    return new ReferenceEntry(slug, title, e.getValue().get());
                })
                .toList();
    }

    /**
     * RFC-051 PR-7 follow-up: list ONLY archived pages — the inverse of the
     * default {@link #listByKbId} filter. Used by the admin UI's "show archived"
     * panel so users can see what they archived and recover it.
     */
    public List<WikiPageEntity> listArchivedByKbId(Long kbId) {
        List<WikiPageEntity> pages = pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .eq(WikiPageEntity::getArchived, 1)
                        .orderByDesc(WikiPageEntity::getUpdateTime));
        pages.forEach(p -> p.setContent(null));
        return pages;
    }

    /**
     * 列出知识库的所有页面（不含 content）。
     * RFC-051 PR-7: archived 页面默认不返回。
     */
    public List<WikiPageEntity> listByKbId(Long kbId) {
        List<WikiPageEntity> pages = pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .ne(WikiPageEntity::getArchived, 1)
                        .orderByAsc(WikiPageEntity::getTitle));
        pages.forEach(p -> p.setContent(null));
        return pages;
    }

    /**
     * 列出知识库所有页面（含 content，用于全文搜索）。
     * RFC-051 PR-7: archived 页面不参与 enrich / 全文搜索遍历。
     */
    public List<WikiPageEntity> listByKbIdWithContent(Long kbId) {
        return pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .ne(WikiPageEntity::getArchived, 1)
                        .orderByAsc(WikiPageEntity::getTitle));
    }

    /**
     * 列出页面摘要（用于上下文注入和 LLM 消化）。
     * 带 5 分钟 TTL 缓存，写操作自动失效。
     */
    public List<WikiPageEntity> listSummaries(Long kbId) {
        CachedSummaries cached = summaryCache.get(kbId);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }
        // RFC-051 PR-7: archived pages are hidden from default summary listings;
        // PR-2 added page_type so callers can filter system pages too.
        List<WikiPageEntity> pages = pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .select(WikiPageEntity::getSlug, WikiPageEntity::getTitle,
                                WikiPageEntity::getSummary, WikiPageEntity::getLastUpdatedBy,
                                WikiPageEntity::getPageType)
                        .eq(WikiPageEntity::getKbId, kbId)
                        .ne(WikiPageEntity::getArchived, 1)
                        .orderByAsc(WikiPageEntity::getTitle));
        summaryCache.put(kbId, new CachedSummaries(pages, System.currentTimeMillis() + SUMMARY_CACHE_TTL_MS));
        return pages;
    }

    /** 失效指定知识库的摘要缓存（页面增删改时调用） */
    public void evictSummaryCache(Long kbId) {
        summaryCache.remove(kbId);
    }

    /**
     * DB 级别搜索页面（不加载 content CLOB 到 Java 内存）
     */
    public List<WikiPageEntity> searchPages(Long kbId, String query) {
        String escaped = query.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return pageMapper.searchByKeyword(kbId, pattern);
    }

    public WikiPageEntity getBySlug(Long kbId, String slug) {
        return pageMapper.selectOne(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .eq(WikiPageEntity::getSlug, slug));
    }

    /**
     * 把 slug 规范化为 canonical 形式：去掉所有连字符 / 下划线 + 转小写。
     * <p>
     * 用于跨拼写匹配：{@code "shennong-bencao-jing"} 和 {@code "shen-nong-ben-cao-jing"}
     * 都规范化为 {@code "shennongbencaojing"}，被视为同一概念。LLM 在并行处理大文档时
     * 经常对同一概念给出不同 slug 拼写（按词分组 vs 按字分隔），这是兜底归一逻辑的基础。
     */
    public static String canonicalSlug(String slug) {
        if (slug == null) return "";
        return slug.toLowerCase().replace("-", "").replace("_", "");
    }

    /**
     * 按 canonical slug 在指定 KB 中查找已存在的 page。
     * <p>
     * 命中条件：现有 page 的 slug 经 {@link #canonicalSlug(String)} 后与给定 slug 的
     * canonical 形式相等。复用 {@link #listSummaries(Long)} 的 5 分钟缓存，命中后再
     * {@link #getBySlug(Long, String)} 拿完整 entity，避免额外全表扫描。
     *
     * @return 第一个 canonical 匹配的 page；找不到返回 {@code null}
     */
    public WikiPageEntity findByCanonicalSlug(Long kbId, String slug) {
        String canonical = canonicalSlug(slug);
        if (canonical.isEmpty()) return null;
        for (WikiPageEntity p : listSummaries(kbId)) {
            if (canonicalSlug(p.getSlug()).equals(canonical)) {
                return getBySlug(kbId, p.getSlug());
            }
        }
        return null;
    }

    public WikiPageEntity getById(Long id) {
        return pageMapper.selectById(id);
    }

    /**
     * Return recently created non-archived, non-system pages for hot-cache
     * summarization. Only light display fields are selected.
     */
    public List<WikiPageEntity> findRecentCreated(Long kbId, LocalDateTime since, int limit) {
        if (kbId == null || since == null || limit <= 0) {
            return List.of();
        }
        return pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .select(WikiPageEntity::getId,
                                WikiPageEntity::getKbId,
                                WikiPageEntity::getSlug,
                                WikiPageEntity::getTitle,
                                WikiPageEntity::getCreateTime,
                                WikiPageEntity::getUpdateTime)
                        .eq(WikiPageEntity::getKbId, kbId)
                        .ne(WikiPageEntity::getArchived, 1)
                        .ne(WikiPageEntity::getPageType, WikiScaffoldService.SYSTEM_PAGE_TYPE)
                        .ge(WikiPageEntity::getCreateTime, since)
                        .orderByDesc(WikiPageEntity::getCreateTime)
                        .last("LIMIT " + limit));
    }

    /**
     * Return recently updated non-archived, non-system pages for hot-cache
     * summarization. Excludes rows newly created in the same window so the
     * caller can present create/update buckets without duplication.
     */
    public List<WikiPageEntity> findRecentUpdated(Long kbId, LocalDateTime since, int limit) {
        if (kbId == null || since == null || limit <= 0) {
            return List.of();
        }
        return pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .select(WikiPageEntity::getId,
                                WikiPageEntity::getKbId,
                                WikiPageEntity::getSlug,
                                WikiPageEntity::getTitle,
                                WikiPageEntity::getCreateTime,
                                WikiPageEntity::getUpdateTime)
                        .eq(WikiPageEntity::getKbId, kbId)
                        .ne(WikiPageEntity::getArchived, 1)
                        .ne(WikiPageEntity::getPageType, WikiScaffoldService.SYSTEM_PAGE_TYPE)
                        .ge(WikiPageEntity::getUpdateTime, since)
                        .lt(WikiPageEntity::getCreateTime, since)
                        .orderByDesc(WikiPageEntity::getUpdateTime)
                        .last("LIMIT " + limit));
    }

    /**
     * Direct update by entity (used by enrichment service).
     */
    @Transactional
    public void updateById(WikiPageEntity entity) {
        pageMapper.updateById(entity);
        if (entity.getKbId() != null) {
            evictSummaryCache(entity.getKbId());
        }
    }

    /**
     * Create a new wiki page (without explicit pageType)
     */
    @Transactional
    public WikiPageEntity createPage(Long kbId, String slug, String title, String content,
                                      String summary, String sourceRawIds) {
        return createPage(kbId, slug, title, content, summary, sourceRawIds, null);
    }

    /**
     * Create a new wiki page with explicit pageType classification.
     * pageType is stored lowercase (concept / person / place / event / technology /
     * organization / product / term / process / other).
     */
    @Transactional
    public WikiPageEntity createPage(Long kbId, String slug, String title, String content,
                                      String summary, String sourceRawIds, String pageType) {
        return createPage(kbId, slug, title, content, summary, sourceRawIds, pageType, null);
    }

    /**
     * Create a new wiki page with explicit pageType and optional purpose hint.
     */
    @Transactional
    public WikiPageEntity createPage(Long kbId, String slug, String title, String content,
                                      String summary, String sourceRawIds, String pageType,
                                      String purposeHint) {
        WikiPageEntity entity = new WikiPageEntity();
        entity.setKbId(kbId);
        entity.setSlug(slug);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSummary(summary);
        entity.setOutgoingLinks(extractLinksAsJson(content));
        entity.setSourceRawIds(sourceRawIds);
        entity.setRouteTagsJson(buildRouteTagsJson(title, pageType, sourceRawIds));
        entity.setStructureMetadataJson(buildStructureMetadataJson(title, pageType, purposeHint, sourceRawIds));
        entity.setVersion(1);
        entity.setLastUpdatedBy("ai");
        if (pageType != null && !pageType.isBlank()) {
            entity.setPageType(pageType.toLowerCase());
        }
        if (purposeHint != null && !purposeHint.isBlank()) {
            entity.setPurposeHint(purposeHint.trim());
        }
        pageMapper.insert(entity);
        evictSummaryCache(kbId);
        return entity;
    }

    /**
     * List pages derived from a specific raw material (for UI sidebar filtering).
     * Uses a LIKE search on sourceRawIds JSON field — cheap and dialect-agnostic.
     */
    public List<WikiPageEntity> listBySourceRawId(Long kbId, Long rawId) {
        List<WikiPageEntity> pages = pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        // RFC-051 PR-7: a raw's archived pages stop showing up in the
                        // sidebar's "filter by raw" listing. Lineage is still queryable
                        // by hitting the page directly via slug.
                        .ne(WikiPageEntity::getArchived, 1)
                        .like(WikiPageEntity::getSourceRawIds, rawId.toString())
                        .orderByAsc(WikiPageEntity::getTitle));
        pages.forEach(p -> p.setContent(null));
        return pages;
    }

    /**
     * Build reusable canonical-source derived views from stable page slice metadata.
     */
    public List<WikiDerivedView> listDerivedViews(Long kbId, Long rawId) {
        List<WikiPageEntity> sourcePages = rawId != null ? listBySourceRawId(kbId, rawId) : listByKbId(kbId);
        Map<String, DerivedViewAccumulator> groups = new LinkedHashMap<>();
        for (WikiPageEntity page : sourcePages) {
            if (WikiScaffoldService.SYSTEM_PAGE_TYPE.equals(page.getPageType())) {
                continue;
            }
            StructureMetadata metadata = parseStructureMetadata(page.getStructureMetadataJson());
            DerivedViewDescriptor descriptor = describeDerivedView(page, metadata);
            groups.computeIfAbsent(descriptor.viewKey(), key -> new DerivedViewAccumulator(descriptor))
                    .add(page, metadata);
        }
        return groups.values().stream()
                .sorted(Comparator
                        .comparingInt((DerivedViewAccumulator acc) -> derivedViewOrder(acc.descriptor.viewType()))
                        .thenComparing(acc -> acc.descriptor.title(), String.CASE_INSENSITIVE_ORDER))
                .map(DerivedViewAccumulator::toView)
                .toList();
    }

    /**
     * AI 更新页面内容（手动编辑的页面不覆盖内容，仅追加来源）
     */
    @Transactional
    public WikiPageEntity updatePageByAi(Long kbId, String slug, String content,
                                          String summary, Long newRawId) {
        return updatePageByAi(kbId, slug, content, summary, newRawId, null);
    }

    /**
     * AI update with optional purpose hint refresh.
     */
    @Transactional
    public WikiPageEntity updatePageByAi(Long kbId, String slug, String content,
                                         String summary, Long newRawId, String purposeHint) {
        WikiPageEntity existing = getBySlug(kbId, slug);
        if (existing == null) {
            log.warn("[Wiki] Page not found for AI update: kbId={}, slug={}", kbId, slug);
            return null;
        }

        // 手动编辑的页面：AI 不覆盖内容，仅追加来源 raw id
        if ("manual".equals(existing.getLastUpdatedBy())) {
            log.info("[Wiki] Skipping AI content update for manually edited page: kbId={}, slug={}", kbId, slug);
            if (newRawId != null) {
                List<Long> rawIds = parseSourceRawIds(existing.getSourceRawIds());
                if (!rawIds.contains(newRawId)) {
                    rawIds.add(newRawId);
                    existing.setSourceRawIds(toJson(rawIds));
                    existing.setRouteTagsJson(buildRouteTagsJson(existing.getTitle(), existing.getPageType(), existing.getSourceRawIds()));
                    existing.setStructureMetadataJson(buildStructureMetadataJson(existing.getTitle(), existing.getPageType(), existing.getPurposeHint(), existing.getSourceRawIds()));
                    if (purposeHint != null && !purposeHint.isBlank()) {
                        existing.setPurposeHint(purposeHint.trim());
                    }
                    pageMapper.updateById(existing);
                    evictSummaryCache(kbId);
                    return getBySlug(kbId, slug); // 从 DB 重新加载确保一致性
                }
            }
            return existing;
        }

        existing.setContent(content);
        existing.setSummary(summary);
        existing.setOutgoingLinks(extractLinksAsJson(content));
        existing.setRouteTagsJson(buildRouteTagsJson(existing.getTitle(), existing.getPageType(), existing.getSourceRawIds()));
        existing.setVersion(existing.getVersion() + 1);
        existing.setLastUpdatedBy("ai");
        if (purposeHint != null && !purposeHint.isBlank()) {
            existing.setPurposeHint(purposeHint.trim());
        }

        // 追加新的 source raw id
        if (newRawId != null) {
            List<Long> rawIds = parseSourceRawIds(existing.getSourceRawIds());
            if (!rawIds.contains(newRawId)) {
                rawIds.add(newRawId);
                existing.setSourceRawIds(toJson(rawIds));
            }
        }

        existing.setRouteTagsJson(buildRouteTagsJson(existing.getTitle(), existing.getPageType(), existing.getSourceRawIds()));
        existing.setStructureMetadataJson(buildStructureMetadataJson(existing.getTitle(), existing.getPageType(), existing.getPurposeHint(), existing.getSourceRawIds()));

        pageMapper.updateById(existing);
        evictSummaryCache(kbId);
        return existing;
    }

    /**
     * RFC-047 P2: Paired source lineage entry (rawId + rawTitle snapshot at ingest time).
     * Keyed by rawId; rawTitle is a snapshot — the raw may be renamed later but lineage stays accurate.
     */
    public record SourceEntry(long rawId, String rawTitle) {}

    /**
     * RFC-047 P2: Merge a (rawId, rawTitle) pair into a page's source lineage.
     * Dual-writes to both sourceEntries (canonical) and sourceRawIds (legacy compat).
     * Idempotent: no-ops if rawId already present.
     */
    @Transactional
    public void mergeSourceLineage(Long pageId, Long rawId, String rawTitle) {
        WikiPageEntity page = pageMapper.selectById(pageId);
        if (page == null) return;

        List<SourceEntry> entries = parseSourceEntries(page.getSourceEntries());
        boolean entryExists = entries.stream().anyMatch(e -> e.rawId() == rawId);

        List<Long> rawIds = parseSourceRawIds(page.getSourceRawIds());
        boolean idExists = rawIds.contains(rawId);

        if (!entryExists) {
            entries.add(new SourceEntry(rawId, rawTitle != null ? rawTitle : ""));
            page.setSourceEntries(toJson(entries));
        }
        if (!idExists) {
            rawIds.add(rawId);
            page.setSourceRawIds(toJson(rawIds));
        }

        if (!entryExists || !idExists) {
            page.setRouteTagsJson(buildRouteTagsJson(page.getTitle(), page.getPageType(), page.getSourceRawIds()));
            page.setStructureMetadataJson(buildStructureMetadataJson(page.getTitle(), page.getPageType(), page.getPurposeHint(), page.getSourceRawIds()));
            pageMapper.updateById(page);
            evictSummaryCache(page.getKbId());
        }
    }

    /**
     * 手动更新页面内容
     */
    @Transactional
    public WikiPageEntity updatePageManually(Long kbId, String slug, String content, String summary) {
        WikiPageEntity existing = getBySlug(kbId, slug);
        if (existing == null) {
            throw new IllegalArgumentException("Page not found: " + slug);
        }
        existing.setContent(content);
        existing.setOutgoingLinks(extractLinksAsJson(content));
        existing.setRouteTagsJson(buildRouteTagsJson(existing.getTitle(), existing.getPageType(), existing.getSourceRawIds()));
        existing.setStructureMetadataJson(buildStructureMetadataJson(existing.getTitle(), existing.getPageType(), existing.getPurposeHint(), existing.getSourceRawIds()));
        existing.setVersion(existing.getVersion() + 1);
        existing.setLastUpdatedBy("manual");
        // 同步更新摘要，防止与 content 漂移
        if (summary != null) {
            existing.setSummary(summary);
        } else {
            // 无显式摘要时，从 content 首段提取
            existing.setSummary(extractFirstParagraph(content));
        }
        pageMapper.updateById(existing);
        evictSummaryCache(kbId);
        return existing;
    }

    /**
     * 从 Markdown 内容提取首段作为摘要
     */
    private String extractFirstParagraph(String content) {
        if (content == null || content.isBlank()) return null;
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() && sb.length() > 0) break; // 空行分段
            if (trimmed.startsWith("#")) continue; // 跳过标题行
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(trimmed);
            }
        }
        String para = sb.toString();
        if (para.length() > 300) para = para.substring(0, 300) + "...";
        return para.isEmpty() ? null : para;
    }

    /**
     * 获取反向链接（哪些页面链接到了这个页面）
     */
    public List<WikiPageEntity> getBacklinks(Long kbId, String slug) {
        // 在 outgoing_links JSON 中搜索包含此 slug 的页面
        List<WikiPageEntity> allPages = pageMapper.selectList(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .ne(WikiPageEntity::getSlug, slug));
        return allPages.stream()
                .filter(p -> p.getOutgoingLinks() != null && p.getOutgoingLinks().contains("\"" + slug + "\""))
                .peek(p -> p.setContent(null))
                .collect(Collectors.toList());
    }

    /**
     * RFC-051 PR-2: a page is protected from AI / tool / batch deletion when
     * either {@code locked == 1} or {@code pageType == "system"}. The system
     * pages ({@code overview} / {@code log}) carry both flags; users may set
     * {@code locked} on individual curated pages without making them system.
     */
    public static boolean isProtected(WikiPageEntity page) {
        if (page == null) return false;
        if (page.getLocked() != null && page.getLocked() == 1) return true;
        return "system".equals(page.getPageType());
    }

    @Transactional
    public void delete(Long kbId, String slug) {
        WikiPageEntity existing = getBySlug(kbId, slug);
        if (existing == null) {
            // Nothing to delete; preserve idempotent behavior.
            return;
        }
        if (isProtected(existing)) {
            log.warn("[Wiki] Refusing to delete protected page kbId={}, slug={}, type={}, locked={}",
                    kbId, slug, existing.getPageType(), existing.getLocked());
            return;
        }
        pageMapper.delete(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .eq(WikiPageEntity::getSlug, slug));
        cleanupPageGraph(existing.getId());
        evictSummaryCache(kbId);
    }

    /**
     * T2-5-6: Cascade-delete all non-protected pages in a knowledge base.
     */
    @Transactional
    public int deleteByKbId(Long kbId) {
        List<WikiPageEntity> pages = listByKbId(kbId);
        int deleted = 0;
        for (WikiPageEntity page : pages) {
            if (isProtected(page)) continue;
            pageMapper.delete(
                    new LambdaQueryWrapper<WikiPageEntity>()
                            .eq(WikiPageEntity::getKbId, kbId)
                            .eq(WikiPageEntity::getSlug, page.getSlug()));
            deleted++;
        }
        if (deleted > 0) {
            evictSummaryCache(kbId);
        }
        log.info("[Wiki] Cascade-deleted {} pages for KB={}", deleted, kbId);
        return deleted;
    }

    /**
     * RFC-051 PR-7: flip the {@code archived} flag.
     * <p>
     * Archive hides the page from default list/search/related results without
     * destroying it. Citation lineage and source-raw links survive, so an
     * archived page can still be unarchived later or audited from raw history.
     * Refuses to archive a system page since those are part of the KB's spine.
     *
     * @param archive true to archive, false to unarchive
     * @return true on a state change, false if no-op (page missing or already in target state)
     */
    @Transactional
    public boolean setArchived(Long kbId, String slug, boolean archive) {
        WikiPageEntity existing = getBySlug(kbId, slug);
        if (existing == null) return false;
        if ("system".equals(existing.getPageType())) {
            log.warn("[Wiki] Refusing to archive system page kbId={}, slug={}", kbId, slug);
            return false;
        }
        int target = archive ? 1 : 0;
        if (existing.getArchived() != null && existing.getArchived() == target) return false;
        existing.setArchived(target);
        pageMapper.updateById(existing);
        evictSummaryCache(kbId);
        return true;
    }

    /**
     * 批量删除页面（按 slug 列表）
     */
    @Transactional
    public int batchDelete(Long kbId, List<String> slugs) {
        int count = 0;
        for (String slug : slugs) {
            delete(kbId, slug);
            count++;
        }
        return count;
    }

    /**
     * 删除某材料独占的旧页面（重处理前清理）。
     * 安全策略：只删同时满足以下条件的页面：
     * 1. sourceRawIds 仅包含该 rawId（独占，非共享）
     * 2. lastUpdatedBy != 'manual'（非人工维护）
     * 多来源页面：仅移除该 rawId 引用，保留页面。
     */
    @Transactional
    public int deleteExclusiveBySourceRawId(Long kbId, Long rawId) {
        List<WikiPageEntity> allPages = listByKbId(kbId);
        int deleted = 0;
        for (WikiPageEntity page : allPages) {
            if ("manual".equals(page.getLastUpdatedBy())) continue;
            // RFC-051 PR-2: never sweep system / locked pages, even when their
            // source raw is being reprocessed.
            if (isProtected(page)) continue;
            List<Long> sourceIds = parseSourceRawIds(page.getSourceRawIds());
            if (sourceIds.contains(rawId)) {
                if (sourceIds.size() == 1) {
                    delete(kbId, page.getSlug());
                    deleted++;
                } else {
                    // Multi-source page: remove this rawId from both sourceRawIds and sourceEntries
                    sourceIds.remove(rawId);
                    page.setSourceRawIds(toJson(sourceIds));
                    List<SourceEntry> entries = parseSourceEntries(page.getSourceEntries());
                    entries.removeIf(e -> e.rawId() == rawId);
                    page.setSourceEntries(toJson(entries));
                    page.setRouteTagsJson(buildRouteTagsJson(page.getTitle(), page.getPageType(), page.getSourceRawIds()));
                    page.setStructureMetadataJson(buildStructureMetadataJson(page.getTitle(), page.getPageType(), page.getPurposeHint(), page.getSourceRawIds()));
                    pageMapper.updateById(page);
                    rebuildPageLineage(page);
                }
            }
        }
        if (deleted > 0) {
            evictSummaryCache(kbId);
        }
        return deleted;
    }

    /**
     * Force reprocess cleanup for one raw material.
     * <p>
     * Unlike the normal partial-resume path, this removes every AI-generated
     * page whose only source is the raw document, unlinks the raw from shared
     * pages, and invalidates citation / relation cache rows for affected pages.
     */
    @Transactional
    public int purgeGeneratedBySourceRawId(Long kbId, Long rawId) {
        List<WikiPageEntity> allPages = listByKbId(kbId);
        int affected = 0;
        int deleted = 0;
        int unlinked = 0;
        for (WikiPageEntity page : allPages) {
            if ("manual".equals(page.getLastUpdatedBy())) continue;
            if (isProtected(page)) continue;
            List<Long> sourceIds = parseSourceRawIds(page.getSourceRawIds());
            if (!sourceIds.contains(rawId)) continue;

            affected++;
            if (sourceIds.size() == 1) {
                pageMapper.delete(new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .eq(WikiPageEntity::getSlug, page.getSlug()));
                cleanupPageGraph(page.getId());
                deleted++;
                continue;
            }

            sourceIds.remove(rawId);
            page.setSourceRawIds(toJson(sourceIds));
            List<SourceEntry> entries = parseSourceEntries(page.getSourceEntries());
            entries.removeIf(e -> e.rawId() == rawId);
            page.setSourceEntries(toJson(entries));
            page.setRouteTagsJson(buildRouteTagsJson(page.getTitle(), page.getPageType(), page.getSourceRawIds()));
            page.setStructureMetadataJson(buildStructureMetadataJson(page.getTitle(), page.getPageType(), page.getPurposeHint(), page.getSourceRawIds()));
            pageMapper.updateById(page);
            rebuildPageLineage(page);
            unlinked++;
        }
        if (affected > 0) {
            evictSummaryCache(kbId);
        }
        log.info("[Wiki] Purged raw lineage before reprocess: kbId={}, rawId={}, affected={}, deleted={}, unlinked={}",
                kbId, rawId, affected, deleted, unlinked);
        return affected;
    }

    private void cleanupPageGraph(Long pageId) {
        if (pageId == null) return;
        citationMapper.softDeleteByPageId(pageId);
        relationMapper.update(null, new LambdaUpdateWrapper<WikiRelationEntity>()
                .eq(WikiRelationEntity::getPageAId, pageId)
                .or()
                .eq(WikiRelationEntity::getPageBId, pageId)
                .set(WikiRelationEntity::getDeleted, 1));
    }

    private void rebuildPageLineage(WikiPageEntity page) {
        if (page == null || page.getId() == null) return;
        cleanupPageGraph(page.getId());
        citationService.buildCitations(page.getId(), page.getKbId());
    }

    public int countByKbId(Long kbId) {
        return Math.toIntExact(pageMapper.selectCount(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)));
    }

    /**
     * Count wiki pages derived from a specific raw material.
     * Uses sourceRawIds JSON array field (e.g. "[123]" or "[123,456]").
     */
    public int countBySourceRawId(Long kbId, Long rawId) {
        // Use LIKE search on sourceRawIds JSON — works for both single and multi-source pages
        return Math.toIntExact(pageMapper.selectCount(
                new LambdaQueryWrapper<WikiPageEntity>()
                        .eq(WikiPageEntity::getKbId, kbId)
                        .like(WikiPageEntity::getSourceRawIds, rawId.toString())));
    }

    /**
     * Extract {@code [[links]]} (and {@code [[target|label]]} alias form,
     * RFC-051 PR-5) from Markdown content and return them as a JSON array of
     * canonical slugs.
     * <p>
     * For aliased links the {@code label} part is purely display — only
     * {@code target} feeds slug resolution. Without this split we'd canonicalize
     * "Spring AI|Spring AI Alibaba" as a single slug, polluting outgoingLinks
     * and breaking graph view / backlinks.
     */
    String extractLinksAsJson(String content) {
        if (content == null) return "[]";
        List<String> links = new ArrayList<>();
        Matcher matcher = WIKI_LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            int pipe = raw.indexOf('|');
            String target = pipe >= 0 ? raw.substring(0, pipe).trim() : raw;
            if (target.isEmpty()) continue;
            String slug = toSlug(target);
            if (slug.isEmpty()) continue;
            if (!links.contains(slug)) {
                links.add(slug);
            }
        }
        return toJson(links);
    }

    /**
     * 将标题转换为 slug（URL 安全标识符）
     */
    public static String toSlug(String title) {
        if (title == null) return "";
        return title.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private List<Long> parseSourceRawIds(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<SourceEntry> parseSourceEntries(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<SourceEntry>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String buildRouteTagsJson(String title, String pageType, String sourceRawIds) {
        LinkedHashSet<String> routeTags = new LinkedHashSet<>();
        appendRouteTag(routeTags, title);
        appendPageTypeTag(routeTags, pageType);
        for (RawSearchRef rawRef : loadRawSearchRefs(parseSourceRawIds(sourceRawIds))) {
            appendMaterialTypeTags(routeTags, rawRef.materialType());
            appendMetadataRouteTags(routeTags, rawRef.materialMetadataJson());
        }
        if (routeTags.isEmpty()) {
            return null;
        }
        return toJson(new ArrayList<>(routeTags));
    }

    private StructureMetadata parseStructureMetadata(String structureMetadataJson) {
        if (!StringUtils.hasText(structureMetadataJson)) {
            return StructureMetadata.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(structureMetadataJson);
            return new StructureMetadata(
                    textValue(node, "sliceType"),
                    textValue(node, "businessViewType"),
                    textValue(node, "canonicalEntityType"),
                    textValue(node, "grade"),
                    textValue(node, "volume"),
                    textValue(node, "unit"),
                    textValue(node, "chapter"),
                    textValue(node, "classicName"),
                    textValue(node, "source"),
                    readStringList(node.path("materialTypes")),
                    readStringList(node.path("rulePackModules")),
                    readStringList(node.path("relationSemantics")),
                    readStringList(node.path("routeTags"))
            );
        } catch (Exception ignored) {
            return StructureMetadata.empty();
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        node.forEach(item -> {
            String value = item.asText(null);
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        });
        return new ArrayList<>(values);
    }

    private DerivedViewDescriptor describeDerivedView(WikiPageEntity page, StructureMetadata metadata) {
        if (isCurriculumView(metadata)) {
            String title = curriculumViewTitle(metadata);
            return new DerivedViewDescriptor(
                    "curriculum_view",
                    "curriculum:" + normalizeKeyPart(firstNonBlank(metadata.sliceType(), title)) + ":"
                            + normalizeKeyPart(firstNonBlank(metadata.grade(), metadata.source())),
                    title,
                    joinNonBlank(" · ", metadata.grade(), metadata.source()),
                    metadata.sliceType(),
                    metadata.grade(),
                    metadata.volume(),
                    metadata.unit(),
                    metadata.chapter(),
                    metadata.classicName(),
                    metadata.source()
            );
        }
        if (isAssessmentView(metadata)) {
            String title = assessmentViewTitle(metadata);
            return new DerivedViewDescriptor(
                    "assessment_view",
                    "assessment:" + normalizeKeyPart(firstNonBlank(metadata.sliceType(), title)) + ":"
                            + normalizeKeyPart(firstNonBlank(metadata.source(), metadata.classicName(), metadata.grade())),
                    title,
                    joinNonBlank(" · ", metadata.source(), metadata.classicName(), metadata.grade(), metadata.volume()),
                    metadata.sliceType(),
                    metadata.grade(),
                    metadata.volume(),
                    metadata.unit(),
                    metadata.chapter(),
                    metadata.classicName(),
                    metadata.source()
            );
        }
        if (isTextbookView(metadata)) {
            String sliceType = firstNonBlank(metadata.sliceType(), "knowledge_point");
            String viewType = "unit_overview".equals(sliceType) ? "textbook_unit"
                    : "chapter_slice".equals(sliceType) || "classical_slice".equals(sliceType)
                    || "poetry_slice".equals(sliceType) || "writing_task".equals(sliceType)
                    || "classic_guide".equals(sliceType) ? "textbook_chapter" : "textbook_sync";
            String anchor = joinNonBlank(" / ", metadata.unit(), metadata.chapter());
            if (!StringUtils.hasText(anchor)) {
                anchor = firstNonBlank(metadata.chapter(), metadata.unit(), metadata.volume(), metadata.grade(), page.getTitle());
            }
            return new DerivedViewDescriptor(
                    viewType,
                    "textbook:" + normalizeKeyPart(sliceType) + ":"
                            + normalizeKeyPart(metadata.grade()) + ":"
                            + normalizeKeyPart(metadata.volume()) + ":"
                            + normalizeKeyPart(metadata.unit()) + ":"
                            + normalizeKeyPart(metadata.chapter()),
                    textbookViewTitle(sliceType, anchor),
                    joinNonBlank(" · ", metadata.grade(), metadata.volume(), metadata.source()),
                    metadata.sliceType(),
                    metadata.grade(),
                    metadata.volume(),
                    metadata.unit(),
                    metadata.chapter(),
                    metadata.classicName(),
                    metadata.source()
            );
        }
        if (isClassicView(metadata)) {
            String sliceType = firstNonBlank(metadata.sliceType(), "chapter_slice");
            String title = classicViewTitle(sliceType);
            return new DerivedViewDescriptor(
                    classicViewType(sliceType),
                    "classic:" + normalizeKeyPart(sliceType) + ":"
                            + normalizeKeyPart(firstNonBlank(metadata.classicName(), metadata.source(), metadata.grade())),
                    title,
                    joinNonBlank(" · ", metadata.source(), metadata.grade(), metadata.volume(), metadata.unit(), metadata.chapter()),
                    metadata.sliceType(),
                    metadata.grade(),
                    metadata.volume(),
                    metadata.unit(),
                    metadata.chapter(),
                    metadata.classicName(),
                    metadata.source()
            );
        }
        String sliceLabel = switch (firstNonBlank(metadata.sliceType(), page.getPageType(), "other")) {
            case "unit_overview" -> "教材单元视图";
            case "chapter_slice" -> "章节切片视图";
            case "character" -> "人物与角色视图";
            case "theme_slice" -> "主题视图";
            case "plot_slice" -> "情节视图";
            case "excerpt" -> "名段赏析视图";
            case "language_slice" -> "语言特色视图";
            default -> "未归类业务切片";
        };
        return new DerivedViewDescriptor(
                "slice_view",
                "slice:" + normalizeKeyPart(firstNonBlank(metadata.sliceType(), page.getPageType(), "other")) + ":"
                        + normalizeKeyPart(firstNonBlank(metadata.source(), metadata.grade(), metadata.classicName())),
                sliceLabel,
                joinNonBlank(" · ", metadata.source(), metadata.grade(), metadata.volume(), metadata.classicName()),
                metadata.sliceType(),
                metadata.grade(),
                metadata.volume(),
                metadata.unit(),
                metadata.chapter(),
                metadata.classicName(),
                metadata.source()
        );
    }

    private boolean isCurriculumView(StructureMetadata metadata) {
        return "curriculum".equals(metadata.businessViewType())
                || "curriculum_requirement".equals(metadata.sliceType())
                || (metadata.materialTypes() != null && metadata.materialTypes().contains("curriculum_standard"));
    }

    private boolean isAssessmentView(StructureMetadata metadata) {
        String sliceType = metadata.sliceType();
        boolean sliceMatch = "question_rule".equals(sliceType)
                || "sample_question".equals(sliceType)
                || "answer_rubric".equals(sliceType);
        List<String> materialTypes = metadata.materialTypes();
        boolean materialMatch = materialTypes != null && materialTypes.stream().anyMatch(type ->
                "question_rule".equals(type) || "sample_question".equals(type) || "answer_rubric".equals(type));
        return "assessment_rule".equals(metadata.businessViewType())
                || "sample_question".equals(metadata.businessViewType())
                || "answer_rubric".equals(metadata.businessViewType())
                || sliceMatch || materialMatch;
    }

    private boolean isTextbookView(StructureMetadata metadata) {
        return "textbook".equals(metadata.businessViewType())
                || (metadata.materialTypes() != null && metadata.materialTypes().contains("textbook_latest"));
    }

    private boolean isClassicView(StructureMetadata metadata) {
        return "classic".equals(metadata.businessViewType())
                || (metadata.materialTypes() != null && metadata.materialTypes().contains("classic_manuscript"));
    }

    private String curriculumViewTitle(StructureMetadata metadata) {
        return switch (firstNonBlank(metadata.sliceType(), "overview")) {
            case "core_competency" -> "课标核心素养视图";
            case "overall_goal" -> "课标课程目标视图";
            case "grade_target" -> "课标学段要求视图";
            case "task_group" -> "课标学习任务群视图";
            case "quality_description" -> "课标学业质量视图";
            case "evaluation_advice" -> "课标评价建议视图";
            case "teaching_advice" -> "课标教学建议视图";
            case "appendix" -> "课标附录视图";
            default -> "课标依据视图";
        };
    }

    private String assessmentViewTitle(StructureMetadata metadata) {
        return switch (firstNonBlank(metadata.sliceType(), metadata.businessViewType(), "question_rule")) {
            case "difficulty_rule" -> "难度规则视图";
            case "coverage_rule" -> "考点覆盖视图";
            case "scoring_rule" -> "评分规则视图";
            case "material_rule" -> "材料要求视图";
            case "forbidden_rule" -> "禁止项规则视图";
            case "question_item" -> "样题题目视图";
            case "answer_item" -> "样题答案视图";
            case "rubric_item" -> "样题采分点视图";
            case "source_item" -> "样题来源视图";
            case "analysis_item" -> "样题命题分析视图";
            case "rubric_dimension" -> "评分维度视图";
            case "grade_level" -> "等级描述视图";
            case "score_bracket" -> "分值区间视图";
            case "common_error" -> "常见错误视图";
            default -> "题型规则视图";
        };
    }

    private String textbookViewTitle(String sliceType, String anchor) {
        String prefix = switch (firstNonBlank(sliceType, "knowledge_point")) {
            case "unit_overview" -> "教材单元视图";
            case "chapter_slice" -> "教材课文章节视图";
            case "classical_slice" -> "教材文言文视图";
            case "poetry_slice" -> "教材古诗词视图";
            case "writing_task" -> "教材写作任务视图";
            case "classic_guide" -> "教材名著导读视图";
            default -> "教材知识点视图";
        };
        return StringUtils.hasText(anchor) ? prefix + "：" + anchor : prefix;
    }

    private String classicViewTitle(String sliceType) {
        return switch (firstNonBlank(sliceType, "chapter_slice")) {
            case "plot_slice" -> "名著情节视图";
            case "theme_slice" -> "名著主题视图";
            case "excerpt" -> "名段赏析视图";
            case "language_slice" -> "语言特色视图";
            default -> "名著章节视图";
        };
    }

    private String classicViewType(String sliceType) {
        return switch (firstNonBlank(sliceType, "chapter_slice")) {
            case "plot_slice" -> "classic_plot";
            case "theme_slice" -> "classic_theme";
            case "excerpt" -> "classic_excerpt";
            case "language_slice" -> "classic_language";
            default -> "classic_chapter";
        };
    }

    private int derivedViewOrder(String viewType) {
        int index = DERIVED_VIEW_ORDER.indexOf(viewType);
        return index >= 0 ? index : DERIVED_VIEW_ORDER.size();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String joinNonBlank(String delimiter, String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(delimiter));
    }

    private String normalizeKeyPart(String value) {
        if (!StringUtils.hasText(value)) {
            return "_";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s/\\\\:：|]+", "-");
    }

    private String buildStructureMetadataJson(String title, String pageType, String purposeHint, String sourceRawIds) {
        com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
        appendStructureValue(node, "title", title);
        appendStructureValue(node, "pageType", pageType == null ? null : pageType.toLowerCase(Locale.ROOT));
        appendStructureValue(node, "purposeHint", purposeHint);

        LinkedHashSet<String> materialTypes = new LinkedHashSet<>();
        LinkedHashSet<String> routeTags = new LinkedHashSet<>();
        for (RawSearchRef rawRef : loadRawSearchRefs(parseSourceRawIds(sourceRawIds))) {
            appendRouteTag(routeTags, rawRef.title());
            if (StringUtils.hasText(rawRef.materialType())) {
                materialTypes.add(rawRef.materialType());
            }
            mergeStructureMetadata(node, routeTags, rawRef.materialMetadataJson());
        }

        if (!materialTypes.isEmpty()) {
            node.set("materialTypes", objectMapper.valueToTree(new ArrayList<>(materialTypes)));
        }
        WikiMaterialProcessingRecipe recipe = materialProcessingRouter.primaryRecipe(materialTypes);
        if (recipe != null) {
            appendStructureValue(node, "businessViewType", recipe.businessViewType());
            appendStructureValue(node, "canonicalEntityType", recipe.canonicalEntityType());
            appendStructureValue(node, "processingGuidance", recipe.processingGuidance());
            node.set("genericDimensions", objectMapper.valueToTree(recipe.genericDimensions()));
            node.set("businessSliceTypes", objectMapper.valueToTree(recipe.businessSliceTypes()));
            node.set("rulePackModules", objectMapper.valueToTree(recipe.rulePackModules()));
            node.set("relationSemantics", objectMapper.valueToTree(recipe.relationSemantics()));
        }
        String sliceType = materialProcessingRouter.deriveSliceType(title, purposeHint, materialTypes);
        appendStructureValue(node, "sliceType", sliceType);
        if (!routeTags.isEmpty()) {
            node.set("routeTags", objectMapper.valueToTree(new ArrayList<>(routeTags)));
        }
        // T2-4-5: Derive businessModules from title, purposeHint, and materialTypes
        List<String> businessModules = businessModuleDeriver.derive(title, purposeHint, materialTypes);
        if (!businessModules.isEmpty()) {
            node.set("businessModules", objectMapper.valueToTree(businessModules));
        }
        return node.isEmpty() ? null : toJson(node);

    }

    private void mergeStructureMetadata(com.fasterxml.jackson.databind.node.ObjectNode node,
                                        LinkedHashSet<String> routeTags,
                                        String materialMetadataJson) {
        if (!StringUtils.hasText(materialMetadataJson)) {
            return;
        }
        try {
            JsonNode metadata = objectMapper.readTree(materialMetadataJson);
            copyStructureField(metadata, node, "edition");
            copyStructureField(metadata, node, "source");
            copyStructureField(metadata, node, "grade");
            copyStructureField(metadata, node, "volume");
            copyStructureField(metadata, node, "unit");
            copyStructureField(metadata, node, "chapter");
            copyStructureField(metadata, node, "classicName");
            JsonNode routeTagsNode = metadata.path("routeTags");
            if (routeTagsNode.isArray()) {
                routeTagsNode.forEach(tag -> appendRouteTag(routeTags, tag.asText(null)));
            }
        } catch (Exception ignored) {
            // ignore invalid metadata json
        }
    }

    private void copyStructureField(JsonNode source,
                                    com.fasterxml.jackson.databind.node.ObjectNode target,
                                    String fieldName) {
        if (!target.hasNonNull(fieldName)) {
            appendStructureValue(target, fieldName, source.path(fieldName).asText(null));
        }
    }

    private void appendStructureValue(com.fasterxml.jackson.databind.node.ObjectNode node, String key, String value) {
        if (StringUtils.hasText(value)) {
            node.put(key, value.trim());
        }
    }

    private List<RawSearchRef> loadRawSearchRefs(Collection<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        return rawMaterialMapper.selectBatchSearchRefs(rawIds);
    }

    private void appendMetadataRouteTags(LinkedHashSet<String> routeTags, String materialMetadataJson) {
        if (!StringUtils.hasText(materialMetadataJson)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(materialMetadataJson);
            appendRouteTag(routeTags, node.path("edition").asText(null));
            appendRouteTag(routeTags, node.path("source").asText(null));
            appendRouteTag(routeTags, node.path("grade").asText(null));
            appendRouteTag(routeTags, node.path("volume").asText(null));
            appendRouteTag(routeTags, node.path("unit").asText(null));
            appendRouteTag(routeTags, node.path("chapter").asText(null));
            appendRouteTag(routeTags, node.path("classicName").asText(null));
            JsonNode routeTagsNode = node.path("routeTags");
            if (routeTagsNode.isArray()) {
                routeTagsNode.forEach(tag -> appendRouteTag(routeTags, tag.asText(null)));
            }
        } catch (Exception ignored) {
            // ignore invalid metadata json
        }
    }

    private void appendMaterialTypeTags(LinkedHashSet<String> routeTags, String materialType) {
        if (!StringUtils.hasText(materialType)) {
            return;
        }
        appendRouteTag(routeTags, switch (materialType) {
            case "curriculum_standard" -> "课程标准";
            case "textbook_latest" -> "最新教材";
            case "classic_manuscript" -> "名著稿件";
            case "question_rule" -> "题型要求";
            case "sample_question" -> "样题";
            case "answer_rubric" -> "答案与评分标准";
            default -> materialType;
        });
    }

    private void appendPageTypeTag(LinkedHashSet<String> routeTags, String pageType) {
        if (!StringUtils.hasText(pageType)) {
            return;
        }
        appendRouteTag(routeTags, switch (pageType.toLowerCase(Locale.ROOT)) {
            case "source" -> "来源材料";
            case "synthesis" -> "综合整理";
            case "concept" -> "知识点";
            default -> pageType;
        });
    }

    private void appendRouteTag(LinkedHashSet<String> routeTags, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            routeTags.add(normalized);
        }
    }

    private record StructureMetadata(String sliceType,
                                     String businessViewType,
                                     String canonicalEntityType,
                                     String grade,
                                     String volume,
                                     String unit,
                                     String chapter,
                                     String classicName,
                                     String source,
                                     List<String> materialTypes,
                                     List<String> rulePackModules,
                                     List<String> relationSemantics,
                                     List<String> routeTags) {
        private static StructureMetadata empty() {
            return new StructureMetadata(null, null, null, null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), List.of());
        }
    }

    private record DerivedViewDescriptor(String viewType,
                                         String viewKey,
                                         String title,
                                         String subtitle,
                                         String sliceType,
                                         String grade,
                                         String volume,
                                         String unit,
                                         String chapter,
                                         String classicName,
                                         String source) {
    }

    private static final class DerivedViewAccumulator {
        private final DerivedViewDescriptor descriptor;
        private final List<WikiPageEntity> pages = new ArrayList<>();
        private final LinkedHashSet<String> materialTypes = new LinkedHashSet<>();
        private final LinkedHashSet<String> routeTags = new LinkedHashSet<>();

        private DerivedViewAccumulator(DerivedViewDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        private void add(WikiPageEntity page, StructureMetadata metadata) {
            pages.add(page);
            materialTypes.addAll(metadata.materialTypes());
            routeTags.addAll(metadata.routeTags());
        }

        private WikiDerivedView toView() {
            pages.sort(Comparator.comparing(WikiPageEntity::getTitle, String.CASE_INSENSITIVE_ORDER));
            return new WikiDerivedView(
                    descriptor.viewType(),
                    descriptor.viewKey(),
                    descriptor.title(),
                    descriptor.subtitle(),
                    descriptor.sliceType(),
                    descriptor.grade(),
                    descriptor.volume(),
                    descriptor.unit(),
                    descriptor.chapter(),
                    descriptor.classicName(),
                    descriptor.source(),
                    new ArrayList<>(materialTypes),
                    new ArrayList<>(routeTags),
                    pages.size(),
                    pages
            );
        }
    }
}
