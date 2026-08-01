package vip.mate.llm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.exception.MateClawException;
import vip.mate.llm.event.ModelConfigChangedEvent;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.repository.ModelConfigMapper;
import vip.mate.llm.workspace.WorkspaceModelConfigEntity;
import vip.mate.llm.workspace.repository.WorkspaceModelConfigMapper;
import vip.mate.llm.workspace.WorkspaceModelScope;

import java.util.Comparator;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 模型配置服务
 */
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;
    private final WorkspaceModelConfigMapper workspaceModelConfigMapper;
    private final WorkspaceModelScope workspaceModelScope;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelCapabilityService modelCapabilityService;

    /**
     * Lazy to break circular dependency: ModelProviderService → ModelConfigService.
     * Used only in getDefaultModel() to skip models whose provider is unconfigured.
     */
    @Lazy
    @Autowired
    private ModelProviderService modelProviderService;

    public List<ModelConfigEntity> listModels() {
        if (isWorkspaceScoped()) {
            return workspaceModelConfigMapper.selectList(
                            workspaceQuery()
                                    .orderByDesc(WorkspaceModelConfigEntity::getIsDefault)
                                    .orderByAsc(WorkspaceModelConfigEntity::getProvider)
                                    .orderByAsc(WorkspaceModelConfigEntity::getName))
                    .stream().map(this::toModelConfig).toList();
        }
        return modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                .orderByDesc(ModelConfigEntity::getIsDefault)
                .orderByAsc(ModelConfigEntity::getProvider)
                .orderByAsc(ModelConfigEntity::getName));
    }

    public List<ModelConfigEntity> listEnabledModels() {
        if (isWorkspaceScoped()) {
            return workspaceModelConfigMapper.selectList(
                            workspaceQuery()
                                    .eq(WorkspaceModelConfigEntity::getEnabled, true)
                                    .and(w -> w.isNull(WorkspaceModelConfigEntity::getModelType)
                                            .or().eq(WorkspaceModelConfigEntity::getModelType, "chat"))
                                    .orderByAsc(WorkspaceModelConfigEntity::getProvider)
                                    .orderByDesc(WorkspaceModelConfigEntity::getIsDefault)
                                    .orderByAsc(WorkspaceModelConfigEntity::getName))
                    .stream().map(this::toModelConfig).toList();
        }
        return modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getEnabled, true)
                // 仅 chat 类型（排除 embedding），NULL 兼容老数据
                .and(w -> w.isNull(ModelConfigEntity::getModelType)
                           .or().eq(ModelConfigEntity::getModelType, "chat"))
                .orderByAsc(ModelConfigEntity::getProvider)
                .orderByDesc(ModelConfigEntity::getIsDefault)
                .orderByAsc(ModelConfigEntity::getName));
    }

    public List<ModelConfigEntity> listModelsByProvider(String providerId) {
        if (isWorkspaceScoped()) {
            ensureWorkspaceProviderModels(providerId);
            return workspaceModelConfigMapper.selectList(
                            workspaceQuery()
                                    .eq(WorkspaceModelConfigEntity::getProvider, providerId)
                                    .orderByDesc(WorkspaceModelConfigEntity::getBuiltin)
                                    .orderByAsc(WorkspaceModelConfigEntity::getName))
                    .stream().map(this::toModelConfig).toList();
        }
        return modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getProvider, providerId)
                .orderByDesc(ModelConfigEntity::getBuiltin)
                .orderByAsc(ModelConfigEntity::getName));
    }

    /**
     * 按模型类型筛选（RFC: embedding UI 配置）。
     * <p>
     * modelType 参数：
     * <ul>
     *   <li>{@code "chat"} — 对话模型（默认，包括老数据 modelType IS NULL）</li>
     *   <li>{@code "embedding"} — 文本向量化模型</li>
     * </ul>
     */
    public List<ModelConfigEntity> listByType(String modelType) {
        return listByType(modelType, null);
    }

    /**
     * Optional modality filter (case-insensitive: {@code "vision" / "video" / "audio"}).
     * Used by the multimodal sidecar settings UI to populate "default vision model" /
     * "default video model" dropdowns.
     * <p>
     * The filter does <b>not</b> hide models the built-in heuristics fail to recognize:
     * a provider-compatible model (e.g. a DashScope OpenAI-compatible vision model with
     * a custom name) is vision-capable in practice even though its name matches no
     * built-in prefix and it carries no declared {@code modalities}. Hard-filtering
     * those out left them un-selectable as a sidecar. Instead every <em>enabled</em>
     * chat model is returned; each row's transient {@link ModelConfigEntity#getModalityCapable()}
     * flag records whether its declared / heuristic capabilities already cover the
     * requested modality, and known-capable rows are sorted to the top so the UI can
     * highlight them while still letting the user pick any model.
     */
    public List<ModelConfigEntity> listByType(String modelType, String modality) {
        List<ModelConfigEntity> rows;
        if (isWorkspaceScoped()) {
            rows = listModels().stream()
                    .filter(model -> "chat".equals(modelType)
                            ? model.getModelType() == null || "chat".equals(model.getModelType())
                            : modelType.equals(model.getModelType()))
                    .sorted(Comparator
                            .comparing((ModelConfigEntity m) -> Boolean.TRUE.equals(m.getIsDefault())).reversed()
                            .thenComparing(ModelConfigEntity::getName,
                                    Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        } else if ("chat".equals(modelType)) {
            rows = modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                    .and(w -> w.isNull(ModelConfigEntity::getModelType)
                               .or().eq(ModelConfigEntity::getModelType, "chat"))
                    .orderByDesc(ModelConfigEntity::getIsDefault)
                    .orderByAsc(ModelConfigEntity::getName));
        } else {
            rows = modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                    .eq(ModelConfigEntity::getModelType, modelType)
                    .orderByDesc(ModelConfigEntity::getIsDefault)
                    .orderByAsc(ModelConfigEntity::getName));
        }
        if (modality == null || modality.isBlank()) return rows;
        ModelCapabilityService.Modality required;
        try {
            required = ModelCapabilityService.Modality.valueOf(modality.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return rows;
        }
        return rows.stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                .peek(m -> m.setModalityCapable(
                        modelCapabilityService.supports(m.getModelName(), m.getModalities(), required)))
                // Known-capable models first; preserve the existing default-then-name
                // order within each group.
                .sorted(Comparator.comparing(
                        (ModelConfigEntity m) -> Boolean.TRUE.equals(m.getModalityCapable())).reversed())
                .toList();
    }

    /**
     * 查找第一个 enabled 的 embedding 模型（WikiEmbeddingService 的 fallback 路径）
     */
    public ModelConfigEntity findFirstEnabledEmbedding() {
        if (isWorkspaceScoped()) {
            return workspaceModelConfigMapper.selectList(
                            workspaceQuery()
                                    .eq(WorkspaceModelConfigEntity::getModelType, "embedding")
                                    .eq(WorkspaceModelConfigEntity::getEnabled, true)
                                    .orderByDesc(WorkspaceModelConfigEntity::getIsDefault)
                                    .orderByAsc(WorkspaceModelConfigEntity::getName)
                                    .last("LIMIT 1"))
                    .stream().findFirst().map(this::toModelConfig).orElse(null);
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getModelType, "embedding")
                .eq(ModelConfigEntity::getEnabled, true)
                .orderByDesc(ModelConfigEntity::getIsDefault)
                .orderByAsc(ModelConfigEntity::getName)
                .last("LIMIT 1"));
    }

    public ModelConfigEntity getModel(Long id) {
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scoped = workspaceModelConfigMapper.selectOne(
                    workspaceQuery().eq(WorkspaceModelConfigEntity::getId, id).last("LIMIT 1"));
            if (scoped == null) {
                throw new MateClawException("err.llm.model_config_not_found",
                        "当前工作区的模型配置不存在: " + id);
            }
            return toModelConfig(scoped);
        }
        ModelConfigEntity entity = modelConfigMapper.selectById(id);
        if (entity == null) {
            throw new MateClawException("err.llm.model_config_not_found", "模型配置不存在: " + id);
        }
        return entity;
    }

    public ModelConfigEntity getDefaultModel() {
        if (isWorkspaceScoped()) {
            List<ModelConfigEntity> candidates = listEnabledModels();
            for (ModelConfigEntity candidate : candidates) {
                if (Boolean.TRUE.equals(candidate.getIsDefault())
                        && isProviderEnabledAndConfigured(candidate.getProvider())) {
                    return candidate;
                }
            }
            for (ModelConfigEntity candidate : candidates) {
                if (isProviderEnabledAndConfigured(candidate.getProvider())) {
                    return candidate;
                }
            }
            if (!candidates.isEmpty()) {
                throw new MateClawException("err.llm.no_configured_provider",
                        "当前工作区的模型 Provider 尚未完成配置，请联系租户管理员");
            }
            throw new MateClawException("err.llm.no_available_model",
                    "当前工作区没有可用的模型配置");
        }
        // Prefer the explicitly marked default chat model when its provider is configured.
        ModelConfigEntity defaultMarked = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getIsDefault, true)
                .and(w -> w.isNull(ModelConfigEntity::getModelType)
                           .or().eq(ModelConfigEntity::getModelType, "chat"))
                .last("LIMIT 1"));
        if (defaultMarked != null && isProviderEnabledAndConfigured(defaultMarked.getProvider())) {
            return defaultMarked;
        }

        // Default model's provider is unavailable (or no default set) — scan all enabled
        // chat models and pick the first one whose provider is actually configured.
        List<ModelConfigEntity> candidates = modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getEnabled, true)
                .and(w -> w.isNull(ModelConfigEntity::getModelType)
                           .or().eq(ModelConfigEntity::getModelType, "chat"))
                .orderByDesc(ModelConfigEntity::getIsDefault)
                .orderByAsc(ModelConfigEntity::getName));
        for (ModelConfigEntity candidate : candidates) {
            if (isProviderEnabledAndConfigured(candidate.getProvider())) {
                return candidate;
            }
        }

        // No configured provider found — give a clearer error than the generic one.
        if (!candidates.isEmpty()) {
            String unconfiguredProvider = candidates.get(0).getProvider();
            throw new MateClawException("err.llm.no_configured_provider",
                    "所有已启用的模型 Provider 均未完成配置（默认 Provider: " + unconfiguredProvider
                    + "），请在「设置 → 模型」中填写 API Key");
        }
        throw new MateClawException("err.llm.no_available_model", "没有可用的模型配置");
    }

    /**
     * Checks whether a provider is fully configured (API key / credentials present).
     * Delegates to ModelProviderService which is injected lazily to avoid a circular
     * dependency. Falls back to {@code true} when the service is not yet available
     * (e.g., during early bootstrap) so we don't accidentally block startup.
     */
    private boolean isProviderEnabledAndConfigured(String providerId) {
        if (modelProviderService == null || providerId == null) {
            return true;
        }
        try {
            return modelProviderService.isProviderEnabledAndConfigured(providerId);
        } catch (Exception e) {
            return true; // conservative: don't filter if lookup fails
        }
    }

    public ModelConfigEntity getDefaultModelByProvider(String providerId) {
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity entity = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, providerId)
                            .eq(WorkspaceModelConfigEntity::getIsDefault, true)
                            .last("LIMIT 1"));
            return entity == null ? null : toModelConfig(entity);
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getProvider, providerId)
                .eq(ModelConfigEntity::getIsDefault, true)
                .last("LIMIT 1"));
    }

    /**
     * Resolve a provider's primary chat model for routing.
     *
     * <p>Prefers the row carrying the system default flag when it happens to
     * belong to this provider; otherwise falls back to the provider's
     * earliest-configured enabled chat model. The {@code is_default} flag is a
     * single system-wide marker (see {@link #clearDefaultFlag}), so a provider
     * that does not own it has no row matching {@link #getDefaultModelByProvider}.
     * Without this fallback a preferred provider could never contribute a
     * primary model unless it already held the global default.
     *
     * @return the provider's primary chat model, or {@code null} when the
     *         provider has no enabled chat model configured
     */
    public ModelConfigEntity getPrimaryChatModelByProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) return null;
        ModelConfigEntity def = getDefaultModelByProvider(providerId);
        if (def != null) return def;
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity entity = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, providerId)
                            .eq(WorkspaceModelConfigEntity::getEnabled, true)
                            .and(w -> w.isNull(WorkspaceModelConfigEntity::getModelType)
                                    .or().eq(WorkspaceModelConfigEntity::getModelType, "chat"))
                            .orderByAsc(WorkspaceModelConfigEntity::getId)
                            .last("LIMIT 1"));
            return entity == null ? null : toModelConfig(entity);
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getProvider, providerId)
                .eq(ModelConfigEntity::getEnabled, true)
                .eq(ModelConfigEntity::getModelType, "chat")
                .orderByAsc(ModelConfigEntity::getId)
                .last("LIMIT 1"));
    }

    public ModelConfigEntity createModel(ModelConfigEntity entity) {
        validateModel(entity, null);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearDefaultFlag();
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        if (entity.getBuiltin() == null) {
            entity.setBuiltin(true);
        }
        if (entity.getIsDefault() == null) {
            entity.setIsDefault(false);
        }
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scoped = toWorkspaceModelConfig(
                    entity, workspaceModelScope.currentWorkspaceId());
            scoped.setId(null);
            scoped.setDeleted(0);
            workspaceModelConfigMapper.insert(scoped);
            ensureDefaultExists();
            publishConfigChanged("workspace-model-created");
            return toModelConfig(scoped);
        }
        modelConfigMapper.insert(entity);
        ensureDefaultExists();
        publishConfigChanged("model-created");
        return entity;
    }

    public ModelConfigEntity updateModel(ModelConfigEntity entity) {
        ModelConfigEntity existing = getModel(entity.getId());
        validateModel(entity, existing.getId());
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearDefaultFlag();
        }
        if (existing.getIsDefault() && Boolean.FALSE.equals(entity.getEnabled())) {
            throw new MateClawException("err.llm.cannot_disable_default", "默认模型不能被禁用，请先切换默认模型");
        }
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scoped = toWorkspaceModelConfig(
                    entity, workspaceModelScope.currentWorkspaceId());
            workspaceModelConfigMapper.updateById(scoped);
            ensureDefaultExists();
            publishConfigChanged("workspace-model-updated");
            return getModel(entity.getId());
        }
        modelConfigMapper.updateById(entity);
        ensureDefaultExists();
        publishConfigChanged("model-updated");
        return getModel(entity.getId());
    }

    public void deleteModel(Long id) {
        ModelConfigEntity entity = getModel(id);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            throw new MateClawException("err.llm.cannot_delete_default", "默认模型不能删除，请先切换默认模型");
        }
        if (isWorkspaceScoped()) {
            workspaceModelConfigMapper.deleteById(id);
            ensureDefaultExists();
            publishConfigChanged("workspace-model-deleted");
            return;
        }
        modelConfigMapper.deleteById(id);
        ensureDefaultExists();
        publishConfigChanged("model-deleted");
    }

    public ModelConfigEntity addModelToProvider(String providerId, String modelId, String displayName, boolean builtin) {
        if (!StringUtils.hasText(providerId) || !StringUtils.hasText(modelId)) {
            throw new MateClawException("err.llm.provider_model_required", "Provider 和模型标识不能为空");
        }
        ModelConfigEntity existing;
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scopedExisting = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, providerId)
                            .eq(WorkspaceModelConfigEntity::getModelName, modelId)
                            .last("LIMIT 1"));
            existing = scopedExisting == null ? null : toModelConfig(scopedExisting);
        } else {
            existing = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                    .eq(ModelConfigEntity::getProvider, providerId)
                    .eq(ModelConfigEntity::getModelName, modelId)
                    .last("LIMIT 1"));
        }
        if (existing != null) {
            throw new MateClawException("err.llm.model_exists", "模型已存在: " + modelId);
        }
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setName(StringUtils.hasText(displayName) ? displayName : modelId);
        entity.setProvider(providerId);
        entity.setModelName(modelId);
        entity.setDescription("");
        entity.setTemperature(0.7);
        entity.setMaxTokens(4096);
        entity.setTopP(0.8);
        entity.setBuiltin(builtin);
        entity.setEnabled(true);
        entity.setIsDefault(false);
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scoped = toWorkspaceModelConfig(
                    entity, workspaceModelScope.currentWorkspaceId());
            scoped.setDeleted(0);
            workspaceModelConfigMapper.insert(scoped);
            ensureDefaultExists();
            publishConfigChanged("workspace-provider-model-added");
            return toModelConfig(scoped);
        }
        modelConfigMapper.insert(entity);
        ensureDefaultExists();
        publishConfigChanged("provider-model-added");
        return entity;
    }

    public void removeModelFromProvider(String providerId, String modelId) {
        ModelConfigEntity entity;
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scoped = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, providerId)
                            .eq(WorkspaceModelConfigEntity::getModelName, modelId)
                            .last("LIMIT 1"));
            entity = scoped == null ? null : toModelConfig(scoped);
        } else {
            entity = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                    .eq(ModelConfigEntity::getProvider, providerId)
                    .eq(ModelConfigEntity::getModelName, modelId)
                    .last("LIMIT 1"));
        }
        if (entity == null) {
            throw new MateClawException("err.llm.model_not_found", "模型不存在: " + modelId);
        }
        if (Boolean.TRUE.equals(entity.getBuiltin())) {
            throw new MateClawException("err.llm.builtin_readonly", "内置模型不支持删除");
        }
        deleteModel(entity.getId());
    }

    public void deleteModelsByProvider(String providerId) {
        List<ModelConfigEntity> entities = listModelsByProvider(providerId);
        for (ModelConfigEntity entity : entities) {
            if (isWorkspaceScoped()) {
                workspaceModelConfigMapper.deleteById(entity.getId());
            } else {
                modelConfigMapper.deleteById(entity.getId());
            }
        }
        ensureDefaultExists();
        publishConfigChanged("provider-models-deleted");
    }

    public ModelConfigEntity setDefaultModel(Long id) {
        ModelConfigEntity entity = getModel(id);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new MateClawException("err.llm.only_enabled_default", "只有启用状态的模型才能设为默认");
        }
        clearDefaultFlag();
        entity.setIsDefault(true);
        if (isWorkspaceScoped()) {
            workspaceModelConfigMapper.updateById(toWorkspaceModelConfig(
                    entity, workspaceModelScope.currentWorkspaceId()));
            publishConfigChanged("workspace-default-model-updated");
            return entity;
        }
        modelConfigMapper.updateById(entity);
        publishConfigChanged("default-model-updated");
        return entity;
    }

    public ModelConfigEntity setDefaultModel(String providerId, String modelName) {
        ModelConfigEntity entity;
        if (isWorkspaceScoped()) {
            ensureWorkspaceProviderModels(providerId);
            WorkspaceModelConfigEntity scoped = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, providerId)
                            .eq(WorkspaceModelConfigEntity::getModelName, modelName)
                            .last("LIMIT 1"));
            entity = scoped == null ? null : toModelConfig(scoped);
        } else {
            entity = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                    .eq(ModelConfigEntity::getProvider, providerId)
                    .eq(ModelConfigEntity::getModelName, modelName)
                    .last("LIMIT 1"));
        }
        if (entity == null) {
            throw new MateClawException("err.llm.model_not_found", "模型不存在: " + providerId + "/" + modelName);
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            // Auto-enable when setting as default (e.g. local Ollama models)
            entity.setEnabled(true);
        }
        clearDefaultFlag();
        entity.setIsDefault(true);
        if (isWorkspaceScoped()) {
            workspaceModelConfigMapper.updateById(toWorkspaceModelConfig(
                    entity, workspaceModelScope.currentWorkspaceId()));
            publishConfigChanged("workspace-default-model-updated");
            return entity;
        }
        modelConfigMapper.updateById(entity);
        publishConfigChanged("default-model-updated");
        return entity;
    }

    public ModelConfigEntity resolveModel(String agentModelName) {
        if (StringUtils.hasText(agentModelName)) {
            ModelConfigEntity entity;
            if (isWorkspaceScoped()) {
                WorkspaceModelConfigEntity scoped = workspaceModelConfigMapper.selectOne(
                        workspaceQuery()
                                .eq(WorkspaceModelConfigEntity::getModelName, agentModelName)
                                .eq(WorkspaceModelConfigEntity::getEnabled, true)
                                .last("LIMIT 1"));
                entity = scoped == null ? null : toModelConfig(scoped);
            } else {
                entity = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                        .eq(ModelConfigEntity::getModelName, agentModelName)
                        .eq(ModelConfigEntity::getEnabled, true)
                        .last("LIMIT 1"));
            }
            if (entity != null) {
                return entity;
            }
        }
        return getDefaultModel();
    }

    /**
     * Resolve an enabled model by its exact (provider, modelName) pair. Unlike
     * {@link #resolveModel(String)} this does NOT fall back to the default —
     * it returns {@code null} when nothing matches, leaving the fallback
     * decision to the caller. Used to honour a per-conversation model pin while
     * still degrading gracefully when that model was later disabled or deleted.
     */
    public ModelConfigEntity findEnabledModel(String provider, String modelName) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(modelName)) {
            return null;
        }
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scoped = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, provider)
                            .eq(WorkspaceModelConfigEntity::getModelName, modelName)
                            .eq(WorkspaceModelConfigEntity::getEnabled, true)
                            .last("LIMIT 1"));
            return scoped == null ? null : toModelConfig(scoped);
        }
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getProvider, provider)
                .eq(ModelConfigEntity::getModelName, modelName)
                .eq(ModelConfigEntity::getEnabled, true)
                .last("LIMIT 1"));
    }

    private void validateModel(ModelConfigEntity entity, Long currentId) {
        if (!StringUtils.hasText(entity.getName())) {
            throw new MateClawException("err.llm.name_required", "模型名称不能为空");
        }
        if (!StringUtils.hasText(entity.getProvider())) {
            entity.setProvider("dashscope");
        }
        if (!StringUtils.hasText(entity.getModelName())) {
            throw new MateClawException("err.llm.id_required", "模型标识不能为空");
        }
        ModelConfigEntity duplicate;
        if (isWorkspaceScoped()) {
            WorkspaceModelConfigEntity scoped = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, entity.getProvider())
                            .eq(WorkspaceModelConfigEntity::getModelName, entity.getModelName())
                            .eq(WorkspaceModelConfigEntity::getDeleted, 0)
                            .ne(currentId != null, WorkspaceModelConfigEntity::getId, currentId)
                            .last("LIMIT 1"));
            duplicate = scoped == null ? null : toModelConfig(scoped);
        } else {
            duplicate = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                    .eq(ModelConfigEntity::getProvider, entity.getProvider())
                    .eq(ModelConfigEntity::getModelName, entity.getModelName())
                    .eq(ModelConfigEntity::getDeleted, 0)
                    .ne(currentId != null, ModelConfigEntity::getId, currentId)
                    .last("LIMIT 1"));
        }
        if (duplicate != null) {
            throw new MateClawException("err.llm.id_exists", "模型标识已存在: " + entity.getProvider() + "/" + entity.getModelName());
        }
    }

    private void clearDefaultFlag() {
        if (isWorkspaceScoped()) {
            List<WorkspaceModelConfigEntity> defaults = workspaceModelConfigMapper.selectList(
                    workspaceQuery().eq(WorkspaceModelConfigEntity::getIsDefault, true));
            for (WorkspaceModelConfigEntity item : defaults) {
                item.setIsDefault(false);
                workspaceModelConfigMapper.updateById(item);
            }
            return;
        }
        List<ModelConfigEntity> defaults = modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getIsDefault, true));
        for (ModelConfigEntity item : defaults) {
            item.setIsDefault(false);
            modelConfigMapper.updateById(item);
        }
    }

    private void ensureDefaultExists() {
        if (isWorkspaceScoped()) {
            long workspaceId = workspaceModelScope.currentWorkspaceId();
            long defaultCount = workspaceModelConfigMapper.selectCount(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getIsDefault, true)
                            .eq(WorkspaceModelConfigEntity::getEnabled, true));
            if (defaultCount > 0) {
                return;
            }
            WorkspaceModelConfigEntity firstEnabled = workspaceModelConfigMapper.selectOne(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getEnabled, true)
                            .orderByAsc(WorkspaceModelConfigEntity::getName)
                            .last("LIMIT 1"));
            if (firstEnabled != null) {
                firstEnabled.setWorkspaceId(workspaceId);
                firstEnabled.setIsDefault(true);
                workspaceModelConfigMapper.updateById(firstEnabled);
            }
            return;
        }
        long defaultCount = modelConfigMapper.selectCount(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getIsDefault, true)
                .eq(ModelConfigEntity::getEnabled, true));
        if (defaultCount > 0) {
            return;
        }
        ModelConfigEntity firstEnabled = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getEnabled, true)
                .orderByAsc(ModelConfigEntity::getName)
                .last("LIMIT 1"));
        if (firstEnabled != null) {
            firstEnabled.setIsDefault(true);
            modelConfigMapper.updateById(firstEnabled);
        }
    }

    private void publishConfigChanged(String reason) {
        long workspaceId = workspaceModelScope.currentWorkspaceId();
        eventPublisher.publishEvent(new ModelConfigChangedEvent(
                workspaceId == WorkspaceModelScope.DEFAULT_WORKSPACE_ID
                        ? reason
                        : reason + ":workspace=" + workspaceId));
    }

    public void ensureWorkspaceProviderModels(String providerId) {
        if (!isWorkspaceScoped() || !StringUtils.hasText(providerId)) {
            return;
        }
        long workspaceId = workspaceModelScope.currentWorkspaceId();
        List<ModelConfigEntity> catalogModels = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigEntity>()
                        .eq(ModelConfigEntity::getProvider, providerId)
                        .eq(ModelConfigEntity::getDeleted, 0));
        for (ModelConfigEntity catalogModel : catalogModels) {
            Long count = workspaceModelConfigMapper.selectCount(
                    workspaceQuery()
                            .eq(WorkspaceModelConfigEntity::getProvider, providerId)
                            .eq(WorkspaceModelConfigEntity::getModelName,
                                    catalogModel.getModelName()));
            if (count != null && count > 0) {
                continue;
            }
            WorkspaceModelConfigEntity scoped =
                    toWorkspaceModelConfig(catalogModel, workspaceId);
            scoped.setId(null);
            scoped.setIsDefault(false);
            scoped.setDeleted(0);
            try {
                workspaceModelConfigMapper.insert(scoped);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // Concurrent first access copied the same catalog row.
            }
        }
    }

    private boolean isWorkspaceScoped() {
        return workspaceModelScope.currentWorkspaceId()
                != WorkspaceModelScope.DEFAULT_WORKSPACE_ID;
    }

    private LambdaQueryWrapper<WorkspaceModelConfigEntity> workspaceQuery() {
        return new LambdaQueryWrapper<WorkspaceModelConfigEntity>()
                .eq(WorkspaceModelConfigEntity::getWorkspaceId,
                        workspaceModelScope.currentWorkspaceId())
                .eq(WorkspaceModelConfigEntity::getDeleted, 0);
    }

    private ModelConfigEntity toModelConfig(WorkspaceModelConfigEntity scoped) {
        if (scoped == null) {
            return null;
        }
        ModelConfigEntity target = new ModelConfigEntity();
        target.setId(scoped.getId());
        target.setName(scoped.getName());
        target.setProvider(scoped.getProvider());
        target.setModelName(scoped.getModelName());
        target.setDescription(scoped.getDescription());
        target.setTemperature(scoped.getTemperature());
        target.setMaxTokens(scoped.getMaxTokens());
        target.setMaxInputTokens(scoped.getMaxInputTokens());
        target.setRequestTimeoutSeconds(scoped.getRequestTimeoutSeconds());
        target.setTopP(scoped.getTopP());
        target.setEnableSearch(scoped.getEnableSearch());
        target.setSearchStrategy(scoped.getSearchStrategy());
        target.setBuiltin(scoped.getBuiltin());
        target.setEnabled(scoped.getEnabled());
        target.setIsDefault(scoped.getIsDefault());
        target.setModelType(scoped.getModelType());
        target.setModalities(scoped.getModalities());
        target.setCreateTime(scoped.getCreateTime());
        target.setUpdateTime(scoped.getUpdateTime());
        target.setDeleted(scoped.getDeleted());
        return target;
    }

    private WorkspaceModelConfigEntity toWorkspaceModelConfig(
            ModelConfigEntity source, long workspaceId) {
        WorkspaceModelConfigEntity target = new WorkspaceModelConfigEntity();
        target.setId(source.getId());
        target.setWorkspaceId(workspaceId);
        target.setName(source.getName());
        target.setProvider(source.getProvider());
        target.setModelName(source.getModelName());
        target.setDescription(source.getDescription());
        target.setTemperature(source.getTemperature());
        target.setMaxTokens(source.getMaxTokens());
        target.setMaxInputTokens(source.getMaxInputTokens());
        target.setRequestTimeoutSeconds(source.getRequestTimeoutSeconds());
        target.setTopP(source.getTopP());
        target.setEnableSearch(source.getEnableSearch());
        target.setSearchStrategy(source.getSearchStrategy());
        target.setBuiltin(source.getBuiltin());
        target.setEnabled(source.getEnabled());
        target.setIsDefault(source.getIsDefault());
        target.setModelType(source.getModelType());
        target.setModalities(source.getModalities());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setDeleted(source.getDeleted() == null ? 0 : source.getDeleted());
        return target;
    }
}
