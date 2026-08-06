package vip.mate.auth.yliyun.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Servlet boundary for all /api/internal/v1 Yliyun runtime requests. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class YliyunRuntimeSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(
            YliyunRuntimeSecurityFilter.class);

    private final YliyunRuntimeSecurityProperties properties;
    private final YliyunRuntimeRequestVerifier verifier;
    private final ObjectMapper objectMapper;

    public YliyunRuntimeSecurityFilter(
            YliyunRuntimeSecurityProperties properties,
            YliyunRuntimeRequestVerifier verifier,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String prefix = properties.getPathPrefix();
        String uri = request.getRequestURI();
        return prefix == null || prefix.isBlank() || uri == null
                || !(uri.equals(prefix) || uri.startsWith(prefix + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest wrapped = null;
        YliyunTrustedContext context;
        try {
            wrapped = new CachedBodyHttpServletRequest(
                    request, properties.getMaxBodyBytes());
            context = verifier.verify(wrapped);
        } catch (YliyunRuntimeAuthException ex) {
            writeFailure(wrapped != null ? wrapped : request, response, ex);
            return;
        }

        YliyunTrustedContextHolder.set(wrapped, context);
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            YliyunTrustedContextHolder.clear();
        }
    }

    private void writeFailure(HttpServletRequest request, HttpServletResponse response,
                              YliyunRuntimeAuthException ex) throws IOException {
        String traceId = safeTraceId(request.getHeader(YliyunRuntimeHeaders.TRACE_ID));
        log.warn("Yliyun runtime request rejected: code={}, stage={}, traceId={}, path={}",
                ex.getErrorCode(), ex.getStage(), traceId, request.getRequestURI());
        response.setStatus(ex.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("stage", ex.getStage());
        body.put("suggestion", ex.getSuggestion());
        body.put("traceId", traceId);
        body.put("retryable", ex.isRetryable());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String safeTraceId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,128}") ? value : null;
    }
}
