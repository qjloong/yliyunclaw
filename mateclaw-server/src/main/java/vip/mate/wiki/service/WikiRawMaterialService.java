package vip.mate.wiki.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vip.mate.tool.builtin.DocumentExtractTool;
import vip.mate.wiki.WikiProperties;
import vip.mate.wiki.event.WikiProcessingEvent;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.model.WikiRawMaterialEntity;
import vip.mate.wiki.repository.WikiRawMaterialMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wiki 原始材料服务
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiRawMaterialService {

    private final WikiRawMaterialMapper rawMapper;
    private final WikiKnowledgeBaseService kbService;
    private final WikiProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentExtractTool documentExtractTool;
    /** RFC-013：删除时级联清理 chunk */
    private final WikiChunkService chunkService;
    /** T2-5-6：删除时级联清理独占页面 */
    private final WikiPageService pageService;
    /** T2-4-1：材料类型自动识别器（仅对业务 KB 触发） */
    private final vip.mate.wiki.classifier.WikiMaterialTypeClassifier materialTypeClassifier;


    /**
     * RFC-012 follow-up #3：从 partial 状态触发的 reprocess 会在此 set 中打标，
     * 供 {@link vip.mate.wiki.service.WikiProcessingService#processRawMaterial(Long, boolean)}
     * 在 claim 之前消费，从而决定是否保留已生成的 exclusive page（续传语义）。
     * <p>
     * 内存态：server 重启会丢，但原 raw 的 status 已被 reprocess 改为 pending，
     * 重启后按正常 pending 流程跑（退化为「不删旧页的全量重跑」，功能不丢失只是
     * 没有走 route 的 "update" 识别路径）。
     */
    private final Set<Long> partialResumeIds = ConcurrentHashMap.newKeySet();

    public List<WikiRawMaterialEntity> listByKbId(Long kbId) {
        List<WikiRawMaterialEntity> list = rawMapper.selectList(
                new LambdaQueryWrapper<WikiRawMaterialEntity>()
                        .eq(WikiRawMaterialEntity::getKbId, kbId)
                        .orderByDesc(WikiRawMaterialEntity::getCreateTime));
        // 不返回大文本字段
        list.forEach(r -> {
            r.setOriginalContent(null);
            r.setExtractedText(null);
        });
        return list;
    }

    public WikiRawMaterialEntity getById(Long id) {
        return rawMapper.selectById(id);
    }

    public WikiRawMaterialEntity findBySourcePath(Long kbId, String sourcePath) {
        return rawMapper.selectOne(
                new LambdaQueryWrapper<WikiRawMaterialEntity>()
                        .eq(WikiRawMaterialEntity::getKbId, kbId)
                        .eq(WikiRawMaterialEntity::getSourcePath, sourcePath));
    }

    public List<WikiRawMaterialEntity> listPending(Long kbId) {
        return rawMapper.selectList(
                new LambdaQueryWrapper<WikiRawMaterialEntity>()
                        .eq(WikiRawMaterialEntity::getKbId, kbId)
                        .eq(WikiRawMaterialEntity::getProcessingStatus, "pending"));
    }

    /**
     * 添加文本类型的原始材料
     */
    @Transactional
    public WikiRawMaterialEntity addText(Long kbId, String title, String content) {
        return addText(kbId, title, content, null, null);
    }

    /**
     * 添加文本类型的原始材料
     */
    @Transactional
    public WikiRawMaterialEntity addText(Long kbId, String title, String content,
                                         String materialType, String materialMetadataJson) {
        String hash = computeHash(content);

        // Dedup: reuse any existing row with the same hash in this KB (any status)
        WikiRawMaterialEntity existing = rawMapper.selectOne(
                new LambdaQueryWrapper<WikiRawMaterialEntity>()
                        .eq(WikiRawMaterialEntity::getKbId, kbId)
                        .eq(WikiRawMaterialEntity::getContentHash, hash)
                        .last("LIMIT 1"));
        if (existing != null) {
            return handleDuplicate(existing);
        }

        // T2-4-1: Auto-classify when materialType is not explicitly provided
        MaterialTypeAutoResult autoResult = resolveMaterialTypeAuto(kbId, title, content, materialType, materialMetadataJson);
        String normalizedMaterialType = normalizeMaterialType(autoResult.materialType());
        String normalizedMetadataJson = normalizeMaterialMetadataJson(normalizedMaterialType, autoResult.materialMetadataJson());

        WikiRawMaterialEntity entity = new WikiRawMaterialEntity();
        entity.setKbId(kbId);
        entity.setTitle(title);
        entity.setSourceType("text");
        entity.setOriginalContent(content);
        entity.setMaterialType(normalizedMaterialType);
        entity.setMaterialMetadataJson(normalizedMetadataJson);
        entity.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        entity.setContentHash(hash);
        entity.setProcessingStatus("pending");
        applyAutoDetectFlags(entity, autoResult, materialType);
        rawMapper.insert(entity);

        kbService.incrementRawCount(kbId);

        if (properties.isAutoProcessOnUpload()) {
            eventPublisher.publishEvent(new WikiProcessingEvent(this, entity.getId(), kbId));
        }

        log.info("[Wiki] Raw material added: id={}, kbId={}, title={}, materialType={}",
                entity.getId(), kbId, title, entity.getMaterialType());
        return entity;
    }


    /**
     * 添加文件类型的原始材料（PDF/DOCX 等）
     */
    @Transactional
    public WikiRawMaterialEntity addFile(Long kbId, String title, String sourceType,
                                          String sourcePath, long fileSize) {
        return addFile(kbId, title, sourceType, sourcePath, fileSize, null, null);
    }

    /**
     * 添加文件类型的原始材料（PDF/DOCX 等）
     */
    @Transactional
    public WikiRawMaterialEntity addFile(Long kbId, String title, String sourceType,
                                         String sourcePath, long fileSize,
                                         String materialType, String materialMetadataJson) {
        // T2-4-1: Auto-classify when materialType is not explicitly provided (file uploads use title-only rules)
        MaterialTypeAutoResult autoResult = resolveMaterialTypeAuto(kbId, title, null, materialType, materialMetadataJson);
        String normalizedMaterialType = normalizeMaterialType(autoResult.materialType());
        String normalizedMetadataJson = normalizeMaterialMetadataJson(normalizedMaterialType, autoResult.materialMetadataJson());

        WikiRawMaterialEntity entity = new WikiRawMaterialEntity();
        entity.setKbId(kbId);
        entity.setTitle(title);
        entity.setSourceType(sourceType);
        entity.setSourcePath(sourcePath);
        entity.setMaterialType(normalizedMaterialType);
        entity.setMaterialMetadataJson(normalizedMetadataJson);
        entity.setFileSize(fileSize);
        entity.setProcessingStatus("pending");
        applyAutoDetectFlags(entity, autoResult, materialType);

        // Compute hash of original upload bytes (for dedup). RFC-051: hash raw bytes

        // directly — the previous `new String(bytes, UTF_8)` round-trip produced unstable
        // hashes for binary files (PDF/Office) because invalid UTF-8 sequences become
        // replacement characters, collapsing distinct files into the same hash.
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(sourcePath));
            entity.setContentHash(computeHashOfBytes(bytes));
        } catch (Exception e) {
            log.warn("[Wiki] Could not compute file hash for dedup: {}", e.getMessage());
        }

        // Dedup: reuse any existing row with the same hash in this KB (any status)
        if (entity.getContentHash() != null) {
            WikiRawMaterialEntity existing = rawMapper.selectOne(
                    new LambdaQueryWrapper<WikiRawMaterialEntity>()
                            .eq(WikiRawMaterialEntity::getKbId, kbId)
                            .eq(WikiRawMaterialEntity::getContentHash, entity.getContentHash())
                            .last("LIMIT 1"));
            if (existing != null) {
                // Clean up the newly uploaded file — we won't use it
                cleanupFile(sourcePath);
                return handleDuplicate(existing);
            }
        }

        rawMapper.insert(entity);
        kbService.incrementRawCount(kbId);

        if (properties.isAutoProcessOnUpload()) {
            eventPublisher.publishEvent(new WikiProcessingEvent(this, entity.getId(), kbId));
        }

        log.info("[Wiki] Raw file added: id={}, kbId={}, type={}", entity.getId(), kbId, sourceType);
        return entity;
    }

    /**
     * CAS 式抢占：仅当当前状态为 pending 时才更新为 processing。
     *
     * @return true 表示抢占成功，false 表示已被其他线程处理
     */
    @Transactional
    public boolean claimForProcessing(Long id) {
        WikiRawMaterialEntity entity = rawMapper.selectById(id);
        if (entity == null || !"pending".equals(entity.getProcessingStatus())) {
            return false;
        }
        entity.setProcessingStatus("processing");
        entity.setErrorMessage(null);
        // RFC-012 M2 v2 UI：新一轮处理开始，清掉上次遗留的进度显示
        entity.setProgressPhase(null);
        entity.setProgressTotal(0);
        entity.setProgressDone(0);
        rawMapper.updateById(entity);
        return true;
    }

    /**
     * RFC-012 M2 v2 UI：更新 wiki 两阶段消化的进度字段。
     * <p>
     * 在 {@code WikiProcessingService.processChunkTwoPhase} 的四个节点被调用：
     * <ul>
     *   <li>方法开头 → {@code phase="route"}, done=0, total=0（进度条显示 indeterminate）</li>
     *   <li>route 返回后 → {@code phase="phase-b"}, done=0, total=N+M（切换到 determinate）</li>
     *   <li>每页 create/merge 成功 → done +1</li>
     *   <li>方法结束 → {@code phase="done"}（UI 会随 status 变成 completed 自动隐藏进度条）</li>
     * </ul>
     */
    @Transactional
    public void updateProgress(Long id, String phase, int done, int total) {
        WikiRawMaterialEntity entity = rawMapper.selectById(id);
        if (entity == null) return;
        entity.setProgressPhase(phase);
        entity.setProgressDone(done);
        entity.setProgressTotal(total);
        rawMapper.updateById(entity);
    }

    @Transactional
    public void updateProcessingStatus(Long id, String status, String errorMessage) {
        WikiRawMaterialEntity entity = rawMapper.selectById(id);
        if (entity == null) return;
        entity.setProcessingStatus(status);
        entity.setErrorMessage(errorMessage);
        if ("completed".equals(status)) {
            entity.setLastProcessedAt(java.time.LocalDateTime.now());
        }
        rawMapper.updateById(entity);
    }

    /**
     * Cache the extracted text for a raw material.
     * <p>
     * RFC-051: this method no longer touches {@code contentHash}. The previous
     * behavior overwrote the original-upload hash with an extracted-text hash,
     * which broke upload dedup (re-uploading the same file would compute a hash
     * over raw bytes but find a row whose hash had been replaced with extracted
     * text). The {@code contentHash} field is now an immutable identity for the
     * uploaded artifact; downstream short-circuiting uses {@code lastProcessedHash}.
     */
    @Transactional
    public void updateExtractedText(Long id, String extractedText) {
        WikiRawMaterialEntity entity = rawMapper.selectById(id);
        if (entity == null) return;
        entity.setExtractedText(extractedText);
        rawMapper.updateById(entity);
    }

    /**
     * 记录本次成功处理时的 content_hash（RFC-012 Change 5 的短路依据）。
     */
    @Transactional
    public void setLastProcessedHash(Long id, String hash) {
        WikiRawMaterialEntity entity = rawMapper.selectById(id);
        if (entity == null) return;
        entity.setLastProcessedHash(hash);
        rawMapper.updateById(entity);
    }

    /**
     * T2-4-6: Update material metadata JSON (e.g., after extracting contentsIndex).
     */
    @Transactional
    public void updateMaterialMetadataJson(Long id, String materialMetadataJson) {
        WikiRawMaterialEntity entity = rawMapper.selectById(id);
        if (entity == null) return;
        entity.setMaterialMetadataJson(materialMetadataJson);
        rawMapper.updateById(entity);
    }


    /**
     * 重新处理：重置状态为 pending 并发布事件。
     * <p>
     * 如果之前状态是 {@code partial}，把 rawId 加入 {@link #partialResumeIds}，
     * 下游的 WikiProcessingService 会据此决定是否保留已生成的 exclusive page（续传语义）。
     */
    @Transactional
    public void reprocess(Long id) {
        reprocess(id, false);
    }

    /**
     * Reprocess a raw material. When {@code forceClean} is true, clear the
     * generated pages, citations, relation cache rows and chunks that belong to
     * this raw material before enqueueing the processing event.
     */
    @Transactional
    public void reprocess(Long id, boolean forceClean) {
        WikiRawMaterialEntity entity = rawMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Raw material not found: " + id);
        }
        boolean wasPartial = "partial".equals(entity.getProcessingStatus());
        // Binary uploads cache extractedText after the first successful parse.
        // If that cached text came from an older buggy extractor (e.g. PDF raw bytes
        // mistaken as text), a plain reprocess would keep reusing the bad cache and
        // never hit the fixed extractor chain. Clear the cache for file-based sources
        // so reprocess truly re-extracts from the original artifact on disk.
        if (!"text".equalsIgnoreCase(entity.getSourceType())) {
            entity.setExtractedText(null);
        }
        if (forceClean) {
            try {
                int affectedPages = pageService.purgeGeneratedBySourceRawId(entity.getKbId(), id);
                log.info("[Wiki] Force reprocess cleaned {} wiki pages for raw={}", affectedPages, id);
            } catch (Exception e) {
                log.warn("[Wiki] Failed to purge wiki pages before force reprocess raw={}: {}", id, e.getMessage());
            }
            try {
                chunkService.deleteByRawId(id);
            } catch (Exception e) {
                log.warn("[Wiki] Failed to delete chunks before force reprocess raw={}: {}", id, e.getMessage());
            }
        }
        entity.setProcessingStatus("pending");
        entity.setErrorMessage(null);
        rawMapper.updateById(entity);

        if (wasPartial && !forceClean) {
            partialResumeIds.add(id);
            log.info("[Wiki] Raw material queued for PARTIAL RESUME: id={} (existing pages will be kept)", id);
        } else {
            log.info("[Wiki] Raw material queued for reprocessing: id={}, forceClean={}", id, forceClean);
        }
        eventPublisher.publishEvent(new WikiProcessingEvent(this, entity.getId(), entity.getKbId()));
    }

    /**
     * 消费 partial resume 标记：若存在则返回 true 并从 set 中移除（一次性）。
     * <p>
     * 必须在 {@link #claimForProcessing(Long)} 之前调用：claim 会把 status 改成 processing，
     * 此时已无法区分 raw 原本是从 partial 还是从 failed/pending 过来的。
     */
    public boolean consumePartialResumeFlag(Long id) {
        return partialResumeIds.remove(id);
    }

    @Transactional
    public void delete(Long id) {
        // T2-5-6: Cascade-delete exclusive wiki pages before deleting the raw material
        WikiRawMaterialEntity raw = rawMapper.selectById(id);
        if (raw != null) {
            try {
                if (pageService != null) {
                    int deletedPages = pageService.deleteExclusiveBySourceRawId(raw.getKbId(), id);
                    log.info("[Wiki] Cascade-deleted {} exclusive pages for raw={}", deletedPages, id);
                }
            } catch (Exception e) {
                log.warn("[Wiki] Failed to cascade-delete pages for raw={}: {}", id, e.getMessage());
            }
        }
        rawMapper.deleteById(id);
        // RFC-013：级联清理 chunk，避免语义搜索命中孤儿 chunk
        try {
            if (chunkService != null) {
                chunkService.deleteByRawId(id);
            }
        } catch (Exception e) {
            log.warn("[Wiki] Failed to cascade-delete chunks for raw={}: {}", id, e.getMessage());
        }
    }

    /**
     * 获取可用文本内容
     * <p>
     * 优先级：已缓存的 extractedText → 原始文本 → 调用 DocumentExtractTool 提取二进制文件
     */
    public String getTextContent(WikiRawMaterialEntity entity) {
        // 已有缓存的提取文本
        if (entity.getExtractedText() != null && !entity.getExtractedText().isBlank()) {
            return entity.getExtractedText();
        }
        // 文本类型直接返回原始内容
        if ("text".equals(entity.getSourceType())) {
            return entity.getOriginalContent();
        }
        // 二进制文件：调用 DocumentExtractTool 提取
        if (entity.getSourcePath() != null && !entity.getSourcePath().isBlank()) {
            try {
                String result = documentExtractTool.extract_document_text(entity.getSourcePath(), null);
                JSONObject json = JSONUtil.parseObj(result);
                if (json.getBool("success", false)) {
                    String text = json.getStr("text");
                    if (text != null && !text.isBlank()) {
                        boolean truncated = json.getBool("truncated", false);
                        if (truncated) {
                            // 截断的结果不缓存，避免永久丢失后半内容。返回文本供分块处理使用。
                            log.warn("[Wiki] Extracted text truncated at {} chars for: {} (full document may be larger)",
                                    text.length(), entity.getSourcePath());
                        } else {
                            // Full extraction: cache to avoid re-extracting on subsequent calls.
                            updateExtractedText(entity.getId(), text);
                        }
                        log.info("[Wiki] Extracted text from {}: {} chars, method={}, truncated={}",
                                entity.getSourcePath(), text.length(), json.getStr("method"), truncated);
                        return text;
                    }
                }
                log.warn("[Wiki] Document extraction returned no text for: {}", entity.getSourcePath());
            } catch (Exception e) {
                log.error("[Wiki] Document extraction failed for {}: {}", entity.getSourcePath(), e.getMessage());
            }
        }
        return entity.getOriginalContent();
    }

    /**
     * Recover raw materials stuck in 'processing' status after a server restart.
     * Resets them to 'pending', clears stale progress fields, and optionally
     * fires processing events so they get picked up automatically.
     *
     * @return number of recovered rows
     */
    @Transactional
    public int recoverStuckRawMaterialsOnStartup() {
        List<WikiRawMaterialEntity> stuck = rawMapper.selectList(
                new LambdaQueryWrapper<WikiRawMaterialEntity>()
                        .eq(WikiRawMaterialEntity::getProcessingStatus, "processing"));
        if (stuck.isEmpty()) return 0;

        for (WikiRawMaterialEntity raw : stuck) {
            raw.setProcessingStatus("pending");
            raw.setProgressPhase(null);
            raw.setProgressTotal(0);
            raw.setProgressDone(0);
            raw.setErrorMessage(null);
            rawMapper.updateById(raw);

            if (properties.isAutoProcessOnUpload()) {
                eventPublisher.publishEvent(new WikiProcessingEvent(this, raw.getId(), raw.getKbId()));
            }
            log.info("[Wiki] Recovered stuck processing raw material: id={}, kbId={}", raw.getId(), raw.getKbId());
        }
        return stuck.size();
    }

    /**
     * Handle a duplicate upload: decide what to do based on the existing row's status.
     * - completed → return as-is (no reprocessing needed)
     * - partial / failed → reprocess (partial enters resume branch)
     * - pending / processing → return as-is (already queued or running)
     */
    private WikiRawMaterialEntity handleDuplicate(WikiRawMaterialEntity existing) {
        String prevStatus = existing.getProcessingStatus();
        log.info("[Wiki] Duplicate file detected, reusing id={}, prevStatus={}", existing.getId(), prevStatus);

        if ("partial".equals(prevStatus) || "failed".equals(prevStatus)) {
            reprocess(existing.getId());
        }
        // completed / pending / processing → return as-is
        return existing;
    }

    /**
     * Delete a file from disk if it exists (cleanup for dedup-discarded uploads).
     */
    private void cleanupFile(String path) {
        if (path == null) return;
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path));
        } catch (Exception e) {
            log.warn("[Wiki] Failed to clean up duplicate upload file {}: {}", path, e.getMessage());
        }
    }

    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("[Wiki] Failed to compute content hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * SHA-256 over raw bytes. Used for file uploads so that PDF/Office binaries
     * produce a stable identity hash regardless of UTF-8 round-tripping.
     */
    private String computeHashOfBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("[Wiki] Failed to compute byte hash: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeMaterialType(String materialType) {
        String normalized = materialType == null ? "general" : materialType.trim().toLowerCase();
        return normalized.isBlank() ? "general" : normalized;
    }

    private String normalizeMaterialMetadataJson(String materialType, String materialMetadataJson) {
        if (!JSONUtil.isTypeJSON(materialMetadataJson)) {
            return null;
        }
        JSONObject source = JSONUtil.parseObj(materialMetadataJson);
        JSONObject normalized = new JSONObject();
        normalized.set("materialType", normalizeMaterialType(materialType));
        copyTrimmed(source, normalized, "edition");
        copyTrimmed(source, normalized, "source");
        copyTrimmed(source, normalized, "grade");
        copyTrimmed(source, normalized, "volume");
        copyTrimmed(source, normalized, "unit");
        copyTrimmed(source, normalized, "chapter");
        copyTrimmed(source, normalized, "classicName");

        LinkedHashSet<String> routeTags = new LinkedHashSet<>();
        routeTags.add(normalizeMaterialType(materialType));
        collectRouteTags(source.get("routeTags"), routeTags);
        collectRouteTags(source.getStr("routeTagsInput"), routeTags);
        addFieldValueTag(normalized, routeTags, "edition");
        addFieldValueTag(normalized, routeTags, "source");
        addFieldValueTag(normalized, routeTags, "grade");
        addFieldValueTag(normalized, routeTags, "volume");
        addFieldValueTag(normalized, routeTags, "unit");
        addFieldValueTag(normalized, routeTags, "chapter");
        addFieldValueTag(normalized, routeTags, "classicName");
        if (!routeTags.isEmpty()) {
            normalized.set("routeTags", new JSONArray(new ArrayList<>(routeTags)));
        }
        return normalized.size() <= 1 ? null : JSONUtil.toJsonStr(normalized);
    }

    private void copyTrimmed(JSONObject source, JSONObject target, String key) {
        String value = normalizeMetadataValue(source.getStr(key));
        if (value != null) {
            target.set(key, value);
        }
    }

    private void addFieldValueTag(JSONObject source, Set<String> routeTags, String key) {
        String value = normalizeMetadataValue(source.getStr(key));
        if (value != null) {
            routeTags.add(value);
        }
    }

    private void collectRouteTags(Object value, Set<String> routeTags) {
        if (value == null) {
            return;
        }
        if (value instanceof JSONArray array) {
            for (Object item : array) {
                String normalized = normalizeMetadataValue(item == null ? null : String.valueOf(item));
                if (normalized != null) {
                    routeTags.add(normalized);
                }
            }
            return;
        }
        String text = value instanceof String ? (String) value : String.valueOf(value);
        if (text == null) {
            return;
        }
        for (String item : text.split("[\\n,，;；|]")) {
            String normalized = normalizeMetadataValue(item);
            if (normalized != null) {
                routeTags.add(normalized);
            }
        }
    }

    private String normalizeMetadataValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT).startsWith("null") ? null : normalized;
    }

    // ---------- T2-4-1: Auto classification helpers ----------

    /**
     * Resolves material type and metadata using automatic classification
     * when the user has not explicitly provided them.
     * <p>
     * Only triggers for business KBs (domainProfileId != null).
     * General KBs keep the user-provided or default values.
     */
    private MaterialTypeAutoResult resolveMaterialTypeAuto(Long kbId, String title, String textPreview,
                                                            String explicitMaterialType, String explicitMetadataJson) {
        // User explicitly provided material type: respect user choice
        if (StringUtils.hasText(explicitMaterialType) && !"general".equalsIgnoreCase(explicitMaterialType.trim())) {
            return new MaterialTypeAutoResult(explicitMaterialType, explicitMetadataJson);
        }

        WikiKnowledgeBaseEntity kb = kbService.getById(kbId);
        if (kb == null || !StringUtils.hasText(kb.getDomainProfileId())) {
            // General KB or missing KB: keep explicit or default to general
            return new MaterialTypeAutoResult(explicitMaterialType, explicitMetadataJson);
        }

        try {
            vip.mate.wiki.classifier.MaterialTypeClassifyResult result =
                    materialTypeClassifier.classify(kb, title, textPreview);
            if (result != null && !"general".equals(result.materialType())) {
                log.info("[WikiRawMaterial] Auto-classified: kbId={}, title={}, type={}, confidence={}",
                        kbId, title, result.materialType(), result.confidence());
                // If user provided metadata but not type, merge auto-type with user metadata
                String mergedMetadata = result.materialMetadataJson();
                if (StringUtils.hasText(explicitMetadataJson) && JSONUtil.isTypeJSON(explicitMetadataJson)) {
                    mergedMetadata = mergeMetadataJson(result.materialMetadataJson(), explicitMetadataJson);
                }
                return new MaterialTypeAutoResult(result.materialType(), mergedMetadata,
                        result.confidence(), result.reason());
            }
        } catch (Exception e) {
            log.warn("[WikiRawMaterial] Auto-classification failed for kbId={}, title={}: {}", kbId, title, e.getMessage());
        }

        return new MaterialTypeAutoResult(explicitMaterialType, explicitMetadataJson);
    }


    private String mergeMetadataJson(String autoMetadataJson, String userMetadataJson) {
        if (!JSONUtil.isTypeJSON(autoMetadataJson)) return userMetadataJson;
        if (!JSONUtil.isTypeJSON(userMetadataJson)) return autoMetadataJson;

        JSONObject autoObj = JSONUtil.parseObj(autoMetadataJson);
        JSONObject userObj = JSONUtil.parseObj(userMetadataJson);
        // User fields override auto fields
        for (String key : userObj.keySet()) {
            String val = userObj.getStr(key);
            if (StringUtils.hasText(val)) {
                autoObj.set(key, val);
            }
        }
        return JSONUtil.toJsonStr(autoObj);
    }

    /**
     * Apply auto-detection transient flags to the entity for API response.
     */
    private void applyAutoDetectFlags(WikiRawMaterialEntity entity, MaterialTypeAutoResult autoResult,
                                       String explicitMaterialType) {
        boolean userProvidedType = StringUtils.hasText(explicitMaterialType)
                && !"general".equalsIgnoreCase(explicitMaterialType.trim());
        if (userProvidedType) {
            return; // User explicitly chose type: not auto-detected
        }
        boolean autoTriggered = autoResult.materialType() != null
                && !"general".equalsIgnoreCase(autoResult.materialType())
                && !autoResult.materialType().equalsIgnoreCase(explicitMaterialType == null ? "" : explicitMaterialType);
        if (autoTriggered) {
            entity.setAutoDetected(true);
            entity.setAutoDetectConfidence(autoResult.confidence());
            entity.setAutoDetectReason(autoResult.reason());
        }
    }


    private record MaterialTypeAutoResult(String materialType, String materialMetadataJson,
                                           String confidence, String reason) {
        MaterialTypeAutoResult(String materialType, String materialMetadataJson) {
            this(materialType, materialMetadataJson, null, null);
        }
    }
}
