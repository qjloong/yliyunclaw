package vip.mate.tool.video.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.system.model.SystemSettingsDTO;
import vip.mate.task.AsyncTaskService.TaskPollResult;
import vip.mate.tool.video.*;

import java.util.List;
import java.util.Set;

/**
 * DashScope 视频生成 Provider — 支持通义万相 Wan 2.5 / Wanx 2.1
 * <p>
 * 复用已有的 DashScope LLM provider 的 API Key。
 * API 文档: https://help.aliyun.com/zh/model-studio/developer-reference/video-generation
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeVideoProvider implements VideoGenerationProvider {

    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "https://dashscope.aliyuncs.com/api/v1";
    private static final String DEFAULT_T2V_MODEL = "wan2.5-t2v-turbo";
    private static final String DEFAULT_I2V_MODEL = "wan2.5-i2v-turbo";

    @Override
    public String id() {
        return "dashscope";
    }

    @Override
    public String label() {
        return "DashScope (通义万相)";
    }

    @Override
    public boolean requiresCredential() {
        return true;
    }

    @Override
    public int autoDetectOrder() {
        return 100;
    }

    @Override
    public Set<VideoCapability> capabilities() {
        return Set.of(VideoCapability.GENERATE, VideoCapability.IMAGE_TO_VIDEO);
    }

    @Override
    public VideoProviderCapabilities detailedCapabilities() {
        return VideoProviderCapabilities.builder()
                .modes(capabilities())
                .aspectRatios(List.of("16:9", "9:16", "1:1"))
                .supportedDurations(List.of(5, 10))
                .maxDurationSeconds(10)
                .defaultModel(DEFAULT_T2V_MODEL)
                .models(List.of("wan2.5-t2v-turbo", "wan2.5-i2v-turbo", "wanx2.1-t2v-turbo", "wanx2.1-i2v-turbo"))
                .build();
    }

    @Override
    public boolean isAvailable(SystemSettingsDTO config) {
        try {
            return modelProviderService.isProviderConfigured("dashscope");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public VideoSubmitResult submit(VideoGenerationRequest request, SystemSettingsDTO config) {
        String apiKey = getDashScopeApiKey();
        if (apiKey == null) {
            return VideoSubmitResult.failure(id(), "DashScope API Key 未配置");
        }

        try {
            String model = resolveModel(request);
            ObjectNode body = buildRequestBody(request, model);

            HttpResponse response = HttpRequest.post(BASE_URL + "/services/aigc/video-generation/generation")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("X-DashScope-Async", "enable")
                    .body(body.toString())
                    .timeout(30_000)
                    .execute();

            JsonNode result = objectMapper.readTree(response.body());

            if (response.getStatus() == 200 && result.has("output")) {
                String taskId = result.path("output").path("task_id").asText();
                log.info("[DashScope Video] Submitted task: {} (model={})", taskId, model);
                return VideoSubmitResult.success(taskId, id());
            } else {
                String errMsg = result.has("message") ? result.get("message").asText()
                        : "HTTP " + response.getStatus();
                log.warn("[DashScope Video] Submit failed: {}", errMsg);
                return VideoSubmitResult.failure(id(), errMsg);
            }
        } catch (Exception e) {
            log.error("[DashScope Video] Submit error: {}", e.getMessage(), e);
            return VideoSubmitResult.failure(id(), e.getMessage());
        }
    }

    @Override
    public TaskPollResult checkStatus(String providerTaskId, SystemSettingsDTO config) {
        String apiKey = getDashScopeApiKey();
        if (apiKey == null) {
            return TaskPollResult.failed("DashScope API Key 未配置");
        }

        try {
            HttpResponse response = HttpRequest.get(BASE_URL + "/tasks/" + providerTaskId)
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(15_000)
                    .execute();

            JsonNode result = objectMapper.readTree(response.body());
            JsonNode output = result.path("output");
            String taskStatus = output.path("task_status").asText();

            return switch (taskStatus) {
                case "SUCCEEDED" -> {
                    String videoUrl = extractVideoUrl(output);
                    yield TaskPollResult.succeeded(videoUrl, null, output.toString());
                }
                case "FAILED" -> {
                    String errMsg = output.has("message") ? output.get("message").asText() : "任务失败";
                    yield TaskPollResult.failed(errMsg);
                }
                case "RUNNING" -> TaskPollResult.running(null);
                default -> TaskPollResult.pending(null);
            };
        } catch (Exception e) {
            log.error("[DashScope Video] Poll error for task {}: {}", providerTaskId, e.getMessage());
            return null; // 轮询异常不终止，等下次重试
        }
    }

    // ==================== 内部方法 ====================

    private String getDashScopeApiKey() {
        try {
            var providerEntity = modelProviderService.getProviderConfig("dashscope");
            return providerEntity.getApiKey();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveModel(VideoGenerationRequest request) {
        if (request.getModel() != null && !request.getModel().isBlank()) {
            return request.getModel();
        }
        return request.getMode() == VideoCapability.IMAGE_TO_VIDEO
                ? DEFAULT_I2V_MODEL : DEFAULT_T2V_MODEL;
    }

    private ObjectNode buildRequestBody(VideoGenerationRequest request, String model) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        ObjectNode input = body.putObject("input");
        input.put("prompt", request.getPrompt());

        if (request.getMode() == VideoCapability.IMAGE_TO_VIDEO && request.getImageUrl() != null) {
            input.put("img_url", request.getImageUrl());
        }

        ObjectNode parameters = body.putObject("parameters");
        if (request.getAspectRatio() != null) {
            // DashScope 使用 size 参数，如 "1280*720"
            String size = aspectRatioToSize(request.getAspectRatio());
            if (size != null) {
                parameters.put("size", size);
            }
        }
        if (request.getDurationSeconds() != null) {
            parameters.put("duration", String.valueOf(request.getDurationSeconds()));
        }

        return body;
    }

    private String aspectRatioToSize(String aspectRatio) {
        return switch (aspectRatio) {
            case "16:9" -> "1280*720";
            case "9:16" -> "720*1280";
            case "1:1" -> "720*720";
            default -> null;
        };
    }

    private String extractVideoUrl(JsonNode output) {
        JsonNode results = output.path("results");
        if (results.isArray() && !results.isEmpty()) {
            return results.get(0).path("url").asText(null);
        }
        // 有些模型返回 video_url
        if (output.has("video_url")) {
            return output.get("video_url").asText();
        }
        return null;
    }
}
