package vip.mate.llm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import vip.mate.llm.model.DiscoverResult;
import vip.mate.llm.model.ModelCapability;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.model.ModelInfoDTO;
import vip.mate.llm.model.TestResult;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.llm.service.ModelDiscoveryService;

import vip.mate.llm.service.ModelProviderService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Startup runner: auto-detect running Ollama instance and register discovered models.
 * <p>
 * On application startup, pings the Ollama endpoint (default http://127.0.0.1:11434).
 * If Ollama is online, discovers all pulled models and auto-enables matching pre-configured models.
 * If offline, silently skips (debug log only).
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class OllamaAutoDiscoveryRunner implements ApplicationRunner {

    private static final String OLLAMA_PROVIDER_ID = "ollama";

    // 模型能力判断已迁移至 ModelCapability，本类直接复用。

    private final ModelDiscoveryService modelDiscoveryService;
    private final ModelConfigService modelConfigService;
    private final ModelProviderService modelProviderService;

    @Async
    @Override
    public void run(ApplicationArguments args) {
        try {
            // Test connection first
            TestResult result = modelDiscoveryService.testConnection(OLLAMA_PROVIDER_ID);
            if (!result.isSuccess()) {
                log.debug("Ollama not running, skipping auto-discovery: {}", result.getErrorMessage());
                return;
            }

            log.info("Ollama detected, discovering models...");

            // Discover and register new models
            DiscoverResult discovered = modelDiscoveryService.discoverModels(OLLAMA_PROVIDER_ID);
            if (discovered.getNewCount() > 0) {
                var newModelIds = discovered.getNewModels().stream()
                        .map(ModelInfoDTO::getId)
                        .toList();
                int added = modelDiscoveryService.batchAddModels(OLLAMA_PROVIDER_ID, newModelIds);
                log.info("Ollama auto-discovery: found {} models, registered {} new", discovered.getTotalDiscovered(), added);
            } else {
                log.info("Ollama auto-discovery: {} models found, all already registered", discovered.getTotalDiscovered());
            }

            // Enable pre-configured Ollama models that match discovered models
            Set<String> discoveredIds = discovered.getDiscoveredModels().stream()
                    .map(ModelInfoDTO::getId)
                    .collect(Collectors.toSet());

            for (ModelConfigEntity model : modelConfigService.listModelsByProvider(OLLAMA_PROVIDER_ID)) {
                if (Boolean.TRUE.equals(model.getEnabled())) {
                    continue;
                }
                String modelTag = model.getModelName();
                String modelBase = modelTag.contains(":") ? modelTag.substring(0, modelTag.indexOf(":")) : modelTag;

                // 精确匹配 / 裸名匹配 —— Ollama 能按原 tag 调用，model_name 无需改写
                if (discoveredIds.contains(modelTag) || discoveredIds.contains(modelBase)) {
                    model.setEnabled(true);
                    modelConfigService.updateModel(model);
                    log.info("Ollama: auto-enabled model '{}'", modelTag);
                    continue;
                }

                // Fuzzy 前缀匹配：种子里的 tag（例如 deepseek-r1:latest）Ollama 没装，
                // 但 base 相同的另一个 tag 存在（例如 deepseek-r1:7b）。
                // 这里必须把 model_name 改写成实际存在的 tag，否则 /v1/chat/completions
                // 会用原 tag 发请求，Ollama 返回 404 "model not found"。
                String actualTag = discoveredIds.stream()
                        .filter(id -> id.startsWith(modelBase + ":"))
                        .findFirst()
                        .orElse(null);
                if (actualTag != null) {
                    log.info("Ollama: rewriting seed tag '{}' → '{}' to match installed model",
                            modelTag, actualTag);
                    model.setModelName(actualTag);
                    model.setEnabled(true);
                    modelConfigService.updateModel(model);
                    log.info("Ollama: auto-enabled model '{}'", actualTag);
                }
            }
            // Auto-activate first Ollama model if no valid default exists
            tryAutoActivateOllamaModel(discoveredIds);
        } catch (Exception e) {
            log.debug("Ollama auto-discovery skipped: {}", e.getMessage());
        }
    }

    private void tryAutoActivateOllamaModel(Set<String> discoveredIds) {
        List<ModelConfigEntity> ollamaModels = modelConfigService.listModelsByProvider(OLLAMA_PROVIDER_ID);
        List<ModelConfigEntity> enabledModels = ollamaModels.stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                .toList();
        if (enabledModels.isEmpty()) {
            return;
        }
        boolean hasValidDefault;
        try {
            ModelConfigEntity current = modelConfigService.getDefaultModel();
            boolean providerOk = modelProviderService.isProviderAvailable(current.getProvider());
            // 对 Ollama provider 额外要求：当前默认的 tag 必须实际存在于 Ollama，
            // 否则视为"历史遗留的无效默认"（如之前 auto-activate 把 :latest 设为默认，
            // 但 Ollama 只有 :7b），需要在本轮重新挑一个可用的默认。
            boolean tagOk = !OLLAMA_PROVIDER_ID.equals(current.getProvider())
                    || discoveredIds.contains(current.getModelName());
            hasValidDefault = providerOk && tagOk;
        } catch (Exception e) {
            hasValidDefault = false;
        }
        if (!hasValidDefault) {
            // 挑默认的优先级（逐级 fallback）：
            //   1) tag 实际存在于 Ollama **且** 家族支持 tools（agent 能正常用工具）
            //   2) tag 实际存在于 Ollama（哪怕不支持 tools，总比设个根本不存在的 tag 强）
            //   3) 兜底：enabledModels 第一条（保留原来的行为）
            ModelConfigEntity pick = enabledModels.stream()
                    .filter(m -> discoveredIds.contains(m.getModelName()))
                    .filter(m -> ModelCapability.ollamaSupportsTools(m.getModelName()))
                    .findFirst()
                    .orElseGet(() -> enabledModels.stream()
                            .filter(m -> discoveredIds.contains(m.getModelName()))
                            .findFirst()
                            .orElse(enabledModels.get(0)));
            modelConfigService.setDefaultModel(OLLAMA_PROVIDER_ID, pick.getModelName());
            if (!ModelCapability.ollamaSupportsTools(pick.getModelName())) {
                log.warn("Ollama: auto-activated default model '{}' but its family does not support tool calling; "
                        + "agents that require tools will fail. Consider pulling a tool-capable model "
                        + "(qwen3 / qwen2.5 / llama3.1:8b+ / mistral-nemo).", pick.getModelName());
            } else {
                log.info("Ollama: auto-activated default model '{}'", pick.getModelName());
            }
        }
    }


}
