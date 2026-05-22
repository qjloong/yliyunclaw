package vip.mate.llm.model;

/**
 * Embedding 模型协议。
 * <p>
 * 与 {@link ModelProtocol}（Chat 协议）分离——chat 和 embedding 在同一个 Provider 下
 * 可能走不同的请求格式：
 * <ul>
 *   <li>DashScope 的 embedding endpoint 是专用 path（/api/v1/services/embeddings/text-embedding/text-embedding）</li>
 *   <li>OpenAI 兼容协议的 embedding 统一走 /v1/embeddings</li>
 * </ul>
 * <p>
 * 通过 {@link #fromProviderId} 从 providerId 推断协议，新增 provider 时只需扩展这里。
 *
 * @author MateClaw Team
 */
public enum EmbeddingProtocol {

    DASHSCOPE_EMBEDDING("dashscope-embedding"),
    OPENAI_EMBEDDING("openai-embedding");

    private final String id;

    EmbeddingProtocol(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * 从 providerId 推断 embedding 协议。
     * <ul>
     *   <li>dashscope / 任何包含 "dashscope" / "qwen" / "aliyun" 的 → DASHSCOPE_EMBEDDING</li>
     *   <li>其他（openai / deepseek / kimi / zhipu / moonshot / 任何 OpenAI 兼容） → OPENAI_EMBEDDING</li>
     * </ul>
     */
    public static EmbeddingProtocol fromProviderId(String providerId) {
        if (providerId == null) return OPENAI_EMBEDDING;
        String p = providerId.toLowerCase().trim();
        if (p.contains("dashscope") || p.contains("qwen") || p.contains("aliyun")) {
            return DASHSCOPE_EMBEDDING;
        }
        return OPENAI_EMBEDDING;
    }
}
