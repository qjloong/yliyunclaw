package vip.mate.approval;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 用户在批准工具调用时可选择的授权范围。
 */
public enum ApprovalGrantScope {

    ONCE,
    CONVERSATION,
    PROJECT;

    public boolean isReusable() {
        return this == CONVERSATION || this == PROJECT;
    }

    public static ApprovalGrantScope from(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ONCE;
        }
        try {
            return ApprovalGrantScope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ONCE;
        }
    }
}