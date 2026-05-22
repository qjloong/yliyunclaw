package vip.mate.hook.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vip.mate.hook.event.MateHookEvent;

import java.net.URI;
import java.util.List;

/**
 * HTTP 动作：向指定 URL 发 POST/GET 请求，可带模板化 body。
 *
 * <p><b>安全约束（强制）</b>：
 * <ul>
 *   <li>目标 URL 的 host 必须在 {@code HookProperties.trustedDomains} 白名单内（精确或后缀匹配）</li>
 *   <li>禁止私网地址（10/8, 172.16/12, 192.168/16, 127/8, ::1） — 防 SSRF（构造期校验，不等运行时）</li>
 *   <li>连接 / 读超时由 {@code HookProperties.http} 配置，不可无限等待</li>
 *   <li>响应体不回读业务，仅记 status code</li>
 * </ul></p>
 *
 * <p>实现注意：RestClient 由 {@link HttpActionFactory} 单例构造并复用连接池，
 * 避免每次调用新建 HttpClient。</p>
 */
@Slf4j
public final class HttpAction implements HookAction {

    private final RestClient restClient;
    private final String method;          // GET | POST
    private final URI url;
    private final String bodyTemplate;    // 可含 {{event.xxx}} 占位
    private final List<String> trustedDomains;
    private final long timeoutMs;

    public HttpAction(RestClient restClient, String method, URI url, String bodyTemplate,
                      List<String> trustedDomains, long timeoutMs) {
        this.restClient = restClient;
        this.method = (method == null) ? "POST" : method.toUpperCase();
        this.url = url;
        this.bodyTemplate = bodyTemplate;
        this.trustedDomains = List.copyOf(trustedDomains == null ? List.of() : trustedDomains);
        this.timeoutMs = Math.max(100L, timeoutMs);
    }

    @Override
    public Kind kind() { return Kind.HTTP; }

    @Override
    public long timeoutMillis() { return timeoutMs; }

    @Override
    public void validate() {
        if (url == null) throw new IllegalArgumentException("HttpAction.url must not be null");
        if (!isAllowedHost(url.getHost())) {
            throw new IllegalArgumentException("host not in trusted-domains: " + url.getHost());
        }
        if (isPrivateAddress(url.getHost())) {
            throw new IllegalArgumentException("private/loopback host is forbidden: " + url.getHost());
        }
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("unsupported method: " + method);
        }
    }

    @Override
    public HookResult execute(MateHookEvent event, HookContext ctx) {
        long start = System.nanoTime();
        try {
            String body = renderBody(event, ctx);
            HttpStatusCode status = switch (method) {
                case "GET" -> restClient.get().uri(url).retrieve().toBodilessEntity().getStatusCode();
                case "POST" -> restClient.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? "" : body)
                        .retrieve().toBodilessEntity().getStatusCode();
                default -> throw new IllegalStateException("unreachable");
            };
            long ms = (System.nanoTime() - start) / 1_000_000L;
            if (status.is2xxSuccessful()) return HookResult.success("status=" + status.value(), ms);
            return HookResult.failed("http status=" + status.value(), ms);
        } catch (RestClientException e) {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return HookResult.failed(e.getMessage(), ms);
        }
    }

    private String renderBody(MateHookEvent event, HookContext ctx) {
        if (bodyTemplate == null || bodyTemplate.isEmpty()) return null;
        // 极简占位替换：仅支持 {{event.type}} / {{event.timestamp}} + ctx.templateVars
        // 保持零依赖；复杂模板后续可接 SpEL 或 Mustache
        String rendered = bodyTemplate
                .replace("{{event.type}}", event.type())
                .replace("{{event.timestamp}}", String.valueOf(event.timestamp()));
        for (var e : ctx.templateVars().entrySet()) {
            rendered = rendered.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
        }
        return rendered;
    }

    private boolean isAllowedHost(String host) {
        if (host == null || host.isEmpty()) return false;
        for (String d : trustedDomains) {
            if (host.equalsIgnoreCase(d)) return true;
            if (host.toLowerCase().endsWith("." + d.toLowerCase())) return true;
        }
        return false;
    }

    /** 快速 SSRF 防护：按字符串形态过滤常见私网地址。DNS rebinding 超出本层范围，由网络层处置。 */
    private static boolean isPrivateAddress(String host) {
        if (host == null) return true;
        String h = host.toLowerCase();
        if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1")) return true;
        if (h.startsWith("10.") || h.startsWith("192.168.")) return true;
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignore) { }
            }
        }
        if (h.startsWith("169.254.")) return true;  // link-local
        if (h.startsWith("fd") || h.startsWith("fe80:")) return true;  // ipv6 ula / link-local
        return false;
    }
}
