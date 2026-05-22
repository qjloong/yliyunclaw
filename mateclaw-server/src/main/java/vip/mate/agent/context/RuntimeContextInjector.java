package vip.mate.agent.context;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 运行时上下文注入器 — 在 LLM 消息列表中预注入当前时间等运行时信息。
 * <p>
 * 参考 Claude Code 的 prependUserContext 模式：将时间信息作为首条 meta UserMessage 注入，
 * 而非修改 System Prompt，以保持 prompt cache 命中率。
 * <p>
 * <b>RFC-014 协同要点</b>：本类返回的内容必须始终包装为 {@code UserMessage} 注入，
 * 严禁拼接进 SystemMessage —— 否则每次时间变化都会击穿 spring-ai 的
 * {@code AnthropicCacheStrategy.SYSTEM_AND_TOOLS / CONVERSATION_HISTORY} system cache。
 * 内容长度（约 70 字符）远小于 spring-ai 的 USER min-content-length 阈值（≥1024 字符），
 * 因此不会被错误纳入对话 cache 块。新增调用点时请保持此约束。
 */
public final class RuntimeContextInjector {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private RuntimeContextInjector() {
    }

    /**
     * 构建运行时上下文消息，包含当前日期和时间。
     */
    public static String buildContextMessage() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return "[system-context] Current time: " + now.format(DATE_FMT)
                + " " + now.format(TIME_FMT) + " (Asia/Shanghai)";
    }

    /**
     * 构建运行时上下文消息，包含当前日期、时间和工作目录。
     * 使用 I18nService 解析本地化消息。
     */
    public static String buildContextMessage(String workspaceBasePath) {
        return buildContextMessage(workspaceBasePath, null);
    }

    /**
     * 构建运行时上下文消息（i18n 版本）。
     */
    public static String buildContextMessage(String workspaceBasePath, vip.mate.i18n.I18nService i18n) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        String dateStr = now.format(DATE_FMT);
        String timeStr = now.format(TIME_FMT);

        StringBuilder sb = new StringBuilder();
        if (i18n != null) {
            sb.append(i18n.msg("context.current_time", dateStr, timeStr));
        } else {
            sb.append("[system-context] Current time: ").append(dateStr)
              .append(" ").append(timeStr).append(" (Asia/Shanghai)");
        }

        if (workspaceBasePath != null && !workspaceBasePath.isBlank()) {
            if (i18n != null) {
                sb.append("\n").append(i18n.msg("context.working_dir", workspaceBasePath));
                sb.append("\n").append(i18n.msg("context.working_dir_hint"));
            } else {
                sb.append("\n[system-context] Working directory: ").append(workspaceBasePath);
                sb.append("\nYou can only read/write files and execute commands within this directory and its subdirectories.");
            }
        }
        return sb.toString();
    }
}
