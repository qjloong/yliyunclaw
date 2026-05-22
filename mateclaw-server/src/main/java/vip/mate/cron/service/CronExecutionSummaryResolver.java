package vip.mate.cron.service;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Derives a user-facing cron execution summary from the persisted run state
 * and the final assistant text / error message.
 */
public final class CronExecutionSummaryResolver {

    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_AWAITING_APPROVAL = "AWAITING_APPROVAL";
    public static final String STATUS_PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String STATUS_EXECUTION_FAILED = "EXECUTION_FAILED";
    public static final String STATUS_EXECUTION_SUCCEEDED = "EXECUTION_SUCCEEDED";

    private static final int MAX_SUMMARY_TEXT = 280;

    private CronExecutionSummaryResolver() {
    }

    public static Summary running() {
        return new Summary(STATUS_RUNNING, defaultText(STATUS_RUNNING));
    }

    public static Summary fromAssistantText(String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return new Summary(STATUS_EXECUTION_SUCCEEDED, defaultText(STATUS_EXECUTION_SUCCEEDED));
        }
        if (isApprovalPending(normalized)) {
            return new Summary(STATUS_AWAITING_APPROVAL, defaultText(STATUS_AWAITING_APPROVAL));
        }
        if (isPermissionDenied(normalized)) {
            return new Summary(STATUS_PERMISSION_DENIED, abbreviate(normalized));
        }
        return new Summary(STATUS_EXECUTION_SUCCEEDED, abbreviate(normalized));
    }

    public static Summary fromFailure(Throwable error) {
        return fromFailureMessage(error != null ? error.getMessage() : null);
    }

    public static Summary fromFailureMessage(String message) {
        String normalized = normalize(message);
        if (!StringUtils.hasText(normalized)) {
            normalized = defaultText(STATUS_EXECUTION_FAILED);
        }
        if (isPermissionDenied(normalized)) {
            return new Summary(STATUS_PERMISSION_DENIED, abbreviate(normalized));
        }
        return new Summary(STATUS_EXECUTION_FAILED, abbreviate(normalized));
    }

    public static Summary fromPersistedOrFallback(String runStatus,
                                                  String summaryStatus,
                                                  String summaryText,
                                                  String errorMessage) {
        if (StringUtils.hasText(summaryStatus)) {
            return new Summary(summaryStatus, textOrDefault(summaryStatus, summaryText, errorMessage));
        }
        if (!StringUtils.hasText(runStatus)) {
            return new Summary(STATUS_NONE, defaultText(STATUS_NONE));
        }
        return switch (runStatus.toLowerCase(Locale.ROOT)) {
            case "running" -> running();
            case "failed" -> fromFailureMessage(errorMessage);
            case "completed", "succeeded" -> new Summary(
                    STATUS_EXECUTION_SUCCEEDED,
                    textOrDefault(STATUS_EXECUTION_SUCCEEDED, summaryText, null));
            default -> new Summary(STATUS_NONE, defaultText(STATUS_NONE));
        };
    }

    public static String toConversationMessageStatus(String summaryStatus) {
        if (STATUS_AWAITING_APPROVAL.equals(summaryStatus)) {
            return "awaiting_approval";
        }
        if (STATUS_PERMISSION_DENIED.equals(summaryStatus) || STATUS_EXECUTION_FAILED.equals(summaryStatus)) {
            return "failed";
        }
        if (STATUS_RUNNING.equals(summaryStatus)) {
            return "generating";
        }
        return "completed";
    }

    public static String defaultText(String summaryStatus) {
        if (STATUS_RUNNING.equals(summaryStatus)) {
            return "任务执行中";
        }
        if (STATUS_AWAITING_APPROVAL.equals(summaryStatus)) {
            return "任务触发了受控操作，正在等待审批";
        }
        if (STATUS_PERMISSION_DENIED.equals(summaryStatus)) {
            return "任务因权限限制未完成执行";
        }
        if (STATUS_EXECUTION_FAILED.equals(summaryStatus)) {
            return "任务执行失败";
        }
        if (STATUS_EXECUTION_SUCCEEDED.equals(summaryStatus)) {
            return "任务执行成功";
        }
        return "尚无执行记录";
    }

    private static boolean isApprovalPending(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("[approval_pending]")
                || lower.contains("awaiting user decision")
                || text.contains("等待审批");
    }

    private static boolean isPermissionDenied(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return text.contains("[安全拦截]")
                || text.contains("权限不足")
                || text.contains("[已拒绝]")
                || lower.contains("permission denied")
                || lower.contains("access denied")
                || lower.contains("outside the allowed workspace directory")
                || lower.contains("insufficient permission");
    }

    private static String textOrDefault(String status, String primary, String secondary) {
        String text = normalize(primary);
        if (!StringUtils.hasText(text)) {
            text = normalize(secondary);
        }
        return StringUtils.hasText(text) ? abbreviate(text) : defaultText(status);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private static String abbreviate(String text) {
        if (!StringUtils.hasText(text) || text.length() <= MAX_SUMMARY_TEXT) {
            return text;
        }
        return text.substring(0, MAX_SUMMARY_TEXT - 1) + "…";
    }

    public record Summary(String status, String text) {
    }
}
