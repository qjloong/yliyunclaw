package vip.mate.llm.model;

import java.util.Set;

/**
 * 模型能力判断工具类。
 * <p>
 * 集中管理各 provider / 模型家族的能力检测（如是否支持 function calling / tools），
 * 供 {@link vip.mate.agent.AgentGraphBuilder}、{@link vip.mate.llm.config.OllamaAutoDiscoveryRunner}
 * 等调用方统一使用，避免逻辑分散。
 *
 * @author MateClaw Team
 */
public final class ModelCapability {

    private ModelCapability() {
        // utility class
    }

    /**
     * Ollama 模型族里<strong>已知不支持 function calling / tools</strong>的家族名（冒号前半段）。
     * <p>
     * 参考：https://ollama.com/search?c=tools （标了 "Tools" 标签的才支持）。
     * 清单随 Ollama 生态演进，必要时手工维护。
     */
    private static final Set<String> KNOWN_NO_TOOL_FAMILIES = Set.of(
            "deepseek-r1",    // reasoning model, no tool calling
            "gemma", "gemma2", "gemma3",
            "phi3", "phi4",
            "codellama",
            "llama3.2",       // 1b/3b variants lack tools（llama3.1 / llama3.3 支持）
            "qwen2",          // 旧 qwen2 无 tool 支持（qwen2.5 / qwen3 支持）
            "mistral"         // 原始 mistral 无 tools（mistral-nemo / mixtral 支持）
    );

    /**
     * 判断指定模型是否支持工具调用（function calling / tools）。
     * <p>
     * 当前仅对 Ollama provider 做家族级检测；其他 provider 默认返回 {@code true}
     *（由 provider 自身在调用时报错，或已有其他机制处理）。
     *
     * @param model 模型配置；若为 null 则返回 false
     * @return true 当且仅当模型非 null 且已知支持工具调用
     */
    public static boolean supportsTools(ModelConfigEntity model) {
        if (model == null) {
            return false;
        }
        String provider = model.getProvider();
        if (!"ollama".equals(provider)) {
            // 非 Ollama provider：默认假设支持 tools（由远端报错兜底）
            return true;
        }
        String modelTag = model.getModelName();
        if (modelTag == null || modelTag.isEmpty()) {
            return false;
        }
        int idx = modelTag.indexOf(':');
        String base = idx > 0 ? modelTag.substring(0, idx) : modelTag;
        return !KNOWN_NO_TOOL_FAMILIES.contains(base);
    }

    /**
     * 基于原始模型 tag 判断 Ollama 模型是否支持 tools（供非 {@link ModelConfigEntity} 场景使用）。
     *
     * @param modelTag Ollama 模型 tag，如 "deepseek-r1:latest"
     * @return true 当且仅当 tag 非空且已知支持工具调用
     */
    public static boolean ollamaSupportsTools(String modelTag) {
        if (modelTag == null || modelTag.isEmpty()) {
            return false;
        }
        int idx = modelTag.indexOf(':');
        String base = idx > 0 ? modelTag.substring(0, idx) : modelTag;
        return !KNOWN_NO_TOOL_FAMILIES.contains(base);
    }
}
