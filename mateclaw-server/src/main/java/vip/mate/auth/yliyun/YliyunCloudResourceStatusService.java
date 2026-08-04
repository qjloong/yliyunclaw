package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.exception.MateClawException;
import vip.mate.tool.mcp.model.McpServerEntity;
import vip.mate.tool.mcp.runtime.McpClientManager;
import vip.mate.tool.mcp.runtime.McpClientManager.ToolCallResult;
import vip.mate.tool.mcp.service.McpServerService;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Verifies the live state of a signed cloud resource through the same MCP/OBO
 * path used by Agent tools. The browser never decides whether a resource still
 * exists, is readable, or which version is current.
 */
@Service
@RequiredArgsConstructor
public class YliyunCloudResourceStatusService {

    private final YliyunCloudResourceRefService resourceRefService;
    private final McpServerService mcpServerService;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;

    /**
     * Issue a reference only after the resource has been checked with the
     * authenticated user's cloud identity. Client-provided version/name/mime
     * values are treated as hints and replaced by server metadata when present.
     */
    public YliyunCloudResourceRefService.IssuedResourceRef issueVerified(
            YliyunCloudResourceRefService.IssueRequest request,
            Long mateUserId) {
        YliyunCloudResourceRefService.IssueRequest unversioned = request == null ? null
                : new YliyunCloudResourceRefService.IssueRequest(
                        request.resourceType(), request.resourceId(), request.displayName(),
                        null, request.binding(), request.mimeType());
        var provisional = resourceRefService.issue(unversioned, mateUserId);
        ChatOrigin origin = userOrigin(mateUserId);
        var resolved = resourceRefService.resolvePath(provisional.path(), origin);
        ResourceStatus status = inspect(resolved, origin);
        requireAvailable(status);
        return resourceRefService.issue(new YliyunCloudResourceRefService.IssueRequest(
                resolved.resourceType(),
                resolved.resourceId(),
                firstNonBlank(status.displayName(), resolved.displayName()),
                status.currentVersionId(),
                resolved.binding(),
                firstNonBlank(status.mimeType(), resolved.mimeType())), mateUserId);
    }

    public ResourceStatus status(StatusRequest request, Long mateUserId) {
        if (request == null || request.refId() == null || request.refId().isBlank()) {
            throw new MateClawException("err.auth.yliyun.invalid_resource_ref", 403,
                    "refId 格式无效");
        }
        ChatOrigin origin = userOrigin(mateUserId);
        var resolved = resourceRefService.resolvePath(toPath(request.refId()), origin);
        return inspect(resolved, origin);
    }

    /** Refresh an existing signed reference to the currently visible version. */
    public YliyunCloudResourceRefService.IssuedResourceRef refresh(
            StatusRequest request,
            Long mateUserId) {
        if (request == null || request.refId() == null || request.refId().isBlank()) {
            throw new MateClawException("err.auth.yliyun.invalid_resource_ref", 403,
                    "refId 格式无效");
        }
        ChatOrigin origin = userOrigin(mateUserId);
        var resolved = resourceRefService.resolvePath(toPath(request.refId()), origin);
        ResourceStatus status = inspect(resolved, origin);
        requireAvailable(status);
        return resourceRefService.issue(new YliyunCloudResourceRefService.IssueRequest(
                resolved.resourceType(),
                resolved.resourceId(),
                firstNonBlank(status.displayName(), resolved.displayName()),
                status.currentVersionId(),
                resolved.binding(),
                firstNonBlank(status.mimeType(), resolved.mimeType())), mateUserId);
    }

    /**
     * Chat-time guard. It prevents an old signed version from silently reading
     * newer content if the file changed after the UI's last status check.
     */
    void requireCurrent(
            YliyunCloudResourceRefService.ResolvedResourceRef resolved,
            ChatOrigin origin) {
        ResourceStatus status = inspect(resolved, origin);
        switch (status.state()) {
            case CURRENT -> { }
            case UPDATED -> throw new IllegalStateException(
                    "云盘文件已有新版本，请点击“使用最新版本”后重试");
            case DELETED -> throw new IllegalStateException(
                    "云盘文件已被删除或移入回收站，请重新选择文件");
            case PERMISSION_REVOKED -> throw new IllegalStateException(
                    "当前账号对云盘文件的访问权限已失效");
            case UNAVAILABLE -> throw new IllegalStateException(
                    "暂时无法验证云盘文件状态，请稍后重试");
        }
    }

    ResourceStatus inspect(
            YliyunCloudResourceRefService.ResolvedResourceRef resolved,
            ChatOrigin origin) {
        long checkedAt = Instant.now().getEpochSecond();
        McpServerEntity server;
        try {
            server = mcpServerService.getByName(YliyunCloudAttachmentService.SERVER_NAME);
        } catch (Exception ex) {
            return unavailable(checkedAt, "云盘 MCP 尚未配置");
        }

        ToolCallResult result = mcpClientManager.callTool(
                server.getId(),
                "file.versions",
                Map.of("fileId", Long.parseLong(resolved.resourceId())),
                origin.toToolContext());
        if (result == null || !result.success()) {
            String code = result != null ? result.code() : "MCP_EMPTY_RESULT";
            if ("FILE_NOT_FOUND".equals(code)) {
                return new ResourceStatus(ResourceState.DELETED, resolved.versionId(), null,
                        resolved.displayName(), resolved.mimeType(), checkedAt,
                        "资源已删除或移入回收站", false);
            }
            if ("PERMISSION_DENIED".equals(code)) {
                return new ResourceStatus(ResourceState.PERMISSION_REVOKED, resolved.versionId(), null,
                        resolved.displayName(), resolved.mimeType(), checkedAt,
                        "当前账号已无权访问该资源", false);
            }
            return unavailable(checkedAt, result != null ? result.content() : "MCP 未返回结果");
        }

        try {
            JsonNode payload = objectMapper.readTree(result.content());
            String currentVersionId = text(payload, "currentVersionId");
            JsonNode resource = payload != null ? payload.get("resource") : null;
            String displayName = text(resource, "name");
            String mimeType = text(resource, "mimeType");
            ResourceState state = resolved.versionId() != null
                    && currentVersionId != null
                    && !Objects.equals(resolved.versionId(), currentVersionId)
                    ? ResourceState.UPDATED : ResourceState.CURRENT;
            String message = state == ResourceState.UPDATED
                    ? "资源已有新版本" : "资源为最新状态";
            return new ResourceStatus(state, resolved.versionId(), currentVersionId,
                    displayName, mimeType, checkedAt, message, false);
        } catch (Exception ex) {
            return unavailable(checkedAt, "MCP 状态响应无法解析");
        }
    }

    private void requireAvailable(ResourceStatus status) {
        switch (status.state()) {
            case CURRENT, UPDATED -> { }
            case DELETED -> throw new MateClawException(
                    "err.auth.yliyun.resource_deleted", 404, status.message());
            case PERMISSION_REVOKED -> throw new MateClawException(
                    "err.auth.yliyun.resource_permission_revoked", 403, status.message());
            case UNAVAILABLE -> throw new MateClawException(
                    "err.auth.yliyun.resource_status_unavailable", 503, status.message());
        }
    }

    private ResourceStatus unavailable(long checkedAt, String detail) {
        String message = detail == null || detail.isBlank()
                ? "暂时无法验证资源状态" : detail.replaceAll("\\s+", " ").trim();
        if (message.length() > 200) message = message.substring(0, 200) + "…";
        return new ResourceStatus(ResourceState.UNAVAILABLE, null, null,
                null, null, checkedAt, message, true);
    }

    private ChatOrigin userOrigin(Long mateUserId) {
        if (mateUserId == null) {
            throw new MateClawException("err.auth.unauthenticated", 401, "Not authenticated");
        }
        return ChatOrigin.web(null, null, null, null, null, mateUserId);
    }

    private String toPath(String refId) {
        return refId.startsWith(YliyunCloudResourceRefService.PATH_PREFIX)
                ? refId : YliyunCloudResourceRefService.PATH_PREFIX + refId;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value == null || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    public enum ResourceState {
        CURRENT,
        UPDATED,
        DELETED,
        PERMISSION_REVOKED,
        UNAVAILABLE
    }

    public record StatusRequest(String refId) { }

    public record ResourceStatus(
            ResourceState state,
            String referencedVersionId,
            String currentVersionId,
            String displayName,
            String mimeType,
            long checkedAt,
            String message,
            boolean retryable) { }
}
