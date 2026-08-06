package vip.mate.auth.yliyun.runtime;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/** Request-scoped access to the verified trusted context. */
public final class YliyunTrustedContextHolder {

    public static final String REQUEST_ATTRIBUTE =
            YliyunTrustedContextHolder.class.getName() + ".context";
    private static final ThreadLocal<YliyunTrustedContext> CURRENT = new ThreadLocal<>();

    private YliyunTrustedContextHolder() {
    }

    static void set(HttpServletRequest request, YliyunTrustedContext context) {
        CURRENT.set(context);
        request.setAttribute(REQUEST_ATTRIBUTE, context);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static Optional<YliyunTrustedContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Optional<YliyunTrustedContext> from(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof YliyunTrustedContext context
                ? Optional.of(context) : Optional.empty();
    }

    public static YliyunTrustedContext requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException(
                "No verified Yliyun Trusted Context is bound to this thread"));
    }
}
