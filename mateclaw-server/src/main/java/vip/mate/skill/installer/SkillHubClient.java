package vip.mate.skill.installer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.skill.installer.model.HubSkillInfo;
import vip.mate.skill.installer.model.SkillBundle;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ClawHub 市场 API 客户端
 * <p>
 * 提供 skill 搜索和 bundle 获取能力。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
public class SkillHubClient {

    private final SkillHubProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SkillHubClient(SkillHubProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getHttpTimeout()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 搜索 ClawHub 市场
     */
    public List<HubSkillInfo> search(String query, int limit) {
        String url = properties.getBaseUrl() + properties.getSearchPath()
                + "?q=" + encodeParam(query) + "&limit=" + limit;

        for (int attempt = 0; attempt <= properties.getHttpRetries(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(properties.getHttpTimeout()))
                        .GET()
                        .header("Accept", "application/json")
                        .header("User-Agent", "MateClaw/1.0")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseSearchResponse(response.body());
                }

                if (isRetryable(response.statusCode()) && attempt < properties.getHttpRetries()) {
                    log.warn("Hub search attempt {} failed with status {}, retrying...", attempt + 1, response.statusCode());
                    Thread.sleep(backoffMs(attempt));
                    continue;
                }

                log.warn("Hub search failed with status {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Collections.emptyList();
            } catch (Exception e) {
                if (attempt < properties.getHttpRetries()) {
                    log.warn("Hub search attempt {} error: {}, retrying...", attempt + 1, e.getMessage());
                    try {
                        Thread.sleep(backoffMs(attempt));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Collections.emptyList();
                    }
                } else {
                    log.error("Hub search failed after {} attempts: {}", properties.getHttpRetries() + 1, e.getMessage());
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取 skill bundle 详情
     * <p>
     * 与 {@link #search} 共享同一套重试策略：408/429/5xx 状态码或 IO 异常时按
     * 指数退避（800/1600/3200ms）重试，最多 {@code httpRetries} 次。
     * 此外当 bundle 内容（{@code content}）为空时直接返回 null —— 空 bundle
     * 重装会清空用户的 SKILL.md，是不可接受的"成功"。
     */
    public SkillBundle fetchBundle(String slug, String version) {
        String path = version != null && !version.isBlank()
                ? "/api/v1/skills/" + slug + "/versions/" + encodeParam(version)
                : "/api/v1/skills/" + slug;
        String url = properties.getBaseUrl() + path;

        for (int attempt = 0; attempt <= properties.getHttpRetries(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(properties.getHttpTimeout()))
                        .GET()
                        .header("Accept", "application/json")
                        .header("User-Agent", "MateClaw/1.0")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    SkillBundle bundle = parseBundleResponse(response.body(), slug);
                    if (bundle == null || bundle.content() == null || bundle.content().isBlank()) {
                        log.warn("Hub fetchBundle returned empty content for '{}'; treat as failure to avoid wiping local SKILL.md", slug);
                        return null;
                    }
                    return bundle;
                }

                if (isRetryable(status) && attempt < properties.getHttpRetries()) {
                    log.warn("Hub fetchBundle attempt {} for '{}' failed with status {}, retrying...", attempt + 1, slug, status);
                    Thread.sleep(backoffMs(attempt));
                    continue;
                }

                log.warn("Hub fetchBundle failed for '{}': status {}", slug, status);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                if (attempt < properties.getHttpRetries()) {
                    log.warn("Hub fetchBundle attempt {} for '{}' error: {}, retrying...", attempt + 1, slug, e.getMessage());
                    try {
                        Thread.sleep(backoffMs(attempt));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    log.error("Hub fetchBundle failed after {} attempts for '{}': {}", properties.getHttpRetries() + 1, slug, e.getMessage());
                }
            }
        }
        return null;
    }

    // ==================== 内部方法 ====================

    @SuppressWarnings("unchecked")
    private List<HubSkillInfo> parseSearchResponse(String body) {
        try {
            Map<String, Object> json = objectMapper.readValue(body, new TypeReference<>() {});
            Object data = json.get("data");
            if (data == null) {
                data = json.get("results");
            }
            if (data == null) {
                data = json.get("skills");
            }
            if (data instanceof List<?> list) {
                String jsonStr = objectMapper.writeValueAsString(list);
                return objectMapper.readValue(jsonStr, new TypeReference<>() {});
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to parse hub search response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private SkillBundle parseBundleResponse(String body, String slug) {
        try {
            Map<String, Object> json = objectMapper.readValue(body, new TypeReference<>() {});
            String name = getStr(json, "name", slug);
            String content = getStr(json, "content", "");
            String description = getStr(json, "description", "");
            String author = getStr(json, "author", "");
            String version = getStr(json, "version", "1.0.0");
            String icon = getStr(json, "icon", "");

            Map<String, String> references = json.containsKey("references")
                    ? objectMapper.convertValue(json.get("references"), new TypeReference<>() {})
                    : Map.of();
            Map<String, String> scripts = json.containsKey("scripts")
                    ? objectMapper.convertValue(json.get("scripts"), new TypeReference<>() {})
                    : Map.of();

            return new SkillBundle(name, content, references, scripts,
                    "clawhub", properties.getBaseUrl() + "/skills/" + slug,
                    version, description, author, icon);
        } catch (Exception e) {
            log.warn("Failed to parse hub bundle response: {}", e.getMessage());
            return null;
        }
    }

    private String getStr(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultVal;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private long backoffMs(int attempt) {
        return (long) (800 * Math.pow(2, attempt));
    }

    private String encodeParam(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
