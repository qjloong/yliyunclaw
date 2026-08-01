package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.tool.mcp.model.McpServerEntity;
import vip.mate.tool.mcp.runtime.McpClientManager;
import vip.mate.tool.mcp.runtime.McpClientManager.ToolCallResult;
import vip.mate.tool.mcp.service.McpServerService;
import vip.mate.workspace.conversation.model.MessageContentPart;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code yliyun://} chat attachments through the shared MCP runtime
 * before the LLM sees the current turn.
 *
 * <p>A cloud attachment is a typed reference, not a local filesystem path.
 * Leaving it to the model to infer that it should call {@code file.read} is
 * unreliable and also makes unbound-agent configurations fail closed. This
 * service performs the read deterministically with the authenticated user's
 * OBO identity, then stores the extracted text on the message part so follow-up
 * turns retain the same context.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YliyunCloudAttachmentService {

    static final String SERVER_NAME = "yliyun-mcp";
    public static final String EVENT_ATTACHMENT_STARTED = "cloud_attachment_started";
    public static final String EVENT_ATTACHMENT_COMPLETED = "cloud_attachment_completed";
    static final int MAX_ATTACHMENTS = 5;
    static final int MAX_CHARS_PER_FILE = 30_000;
    static final int MAX_TOTAL_CHARS = 60_000;

    private static final Pattern FILE_REF = Pattern.compile("^yliyun://file/(\\d+)$");
    private static final Pattern FOLDER_REF = Pattern.compile("^yliyun://folder/(\\d+)$");

    private final McpServerService mcpServerService;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;

    /**
     * UI-only trace for deterministic attachment preparation.
     *
     * <p>These events deliberately do not become provider tool-call messages:
     * the read happens before the Agent graph starts, so replaying it as an
     * assistant tool call would create an invalid tool-call/tool-response pair
     * in later LLM history. The web accumulator stores it as a visual segment
     * instead.</p>
     */
    public interface TraceListener {
        void onStarted(String toolCallId, String toolName, String arguments);

        void onCompleted(String toolCallId, String toolName, String result, boolean success);
    }

    private static final TraceListener NOOP_TRACE = new TraceListener() {
        @Override
        public void onStarted(String toolCallId, String toolName, String arguments) {
        }

        @Override
        public void onCompleted(String toolCallId, String toolName, String result, boolean success) {
        }
    };

    /**
     * Mutates cloud message parts by attaching server-verified text in
     * {@link MessageContentPart#setCaption(String)}.
     */
    public void enrich(List<MessageContentPart> parts, ChatOrigin origin) {
        enrich(parts, origin, NOOP_TRACE);
    }

    /**
     * Resolve attachments and report the preparation timeline to the caller.
     */
    public void enrich(List<MessageContentPart> parts, ChatOrigin origin, TraceListener traceListener) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        TraceListener trace = traceListener != null ? traceListener : NOOP_TRACE;

        List<MessageContentPart> cloudParts = parts.stream()
                .filter(this::isCloudPart)
                .toList();
        if (cloudParts.isEmpty()) {
            return;
        }
        if (cloudParts.size() > MAX_ATTACHMENTS) {
            throw new IllegalStateException("一次最多读取 " + MAX_ATTACHMENTS + " 个云盘附件，请减少选择后重试");
        }

        McpServerEntity server;
        try {
            server = mcpServerService.getByName(SERVER_NAME);
        } catch (Exception e) {
            throw new IllegalStateException("云盘 MCP 尚未配置，无法读取已选择的云盘附件", e);
        }

        int remainingChars = MAX_TOTAL_CHARS;
        Set<String> resolvedReferences = new HashSet<>();
        for (MessageContentPart part : cloudParts) {
            String reference = part.getPath();
            part.setCaption(null); // never trust stale/client-supplied extracted content
            if (!resolvedReferences.add(reference)) {
                continue;
            }

            Matcher fileMatcher = FILE_REF.matcher(reference);
            Matcher folderMatcher = FOLDER_REF.matcher(reference);
            ToolCallResult result;
            if (fileMatcher.matches()) {
                long fileId = Long.parseLong(fileMatcher.group(1));
                int maxChars = Math.min(MAX_CHARS_PER_FILE, remainingChars);
                if (maxChars < 100) {
                    throw new IllegalStateException("云盘附件内容总量超过 " + MAX_TOTAL_CHARS + " 字符，请减少文件后重试");
                }
                String toolCallId = "cloud-attachment-" + UUID.randomUUID();
                String toolName = "yliyun_attachment_read";
                Map<String, Object> arguments = Map.of(
                        "fileId", fileId,
                        "fileName", displayName(part),
                        "maxChars", maxChars);
                notifyStarted(trace, toolCallId, toolName, serialize(arguments));
                try {
                    result = mcpClientManager.callTool(
                            server.getId(),
                            "file.read",
                            Map.of("fileId", fileId, "maxChars", maxChars, "format", "markdown"),
                            origin.toToolContext());
                    String content = extractFileContent(result, part);
                    if (content.length() > remainingChars) {
                        content = content.substring(0, remainingChars);
                    }
                    part.setCaption(content);
                    remainingChars -= content.length();
                    notifyCompleted(trace, toolCallId, toolName,
                            "已读取“" + displayName(part) + "”，提取 " + content.length() + " 个字符",
                            true);
                    log.info("[Yliyun Attachment] file.read resolved fileId={}, chars={}, requester={}",
                            fileId, content.length(), origin.requesterId());
                } catch (RuntimeException e) {
                    notifyCompleted(trace, toolCallId, toolName, sanitizeDetail(e.getMessage()), false);
                    throw e;
                }
            } else if (folderMatcher.matches()) {
                long folderId = Long.parseLong(folderMatcher.group(1));
                String toolCallId = "cloud-attachment-" + UUID.randomUUID();
                String toolName = "yliyun_attachment_list";
                Map<String, Object> arguments = Map.of(
                        "folderId", folderId,
                        "folderName", displayName(part),
                        "maxResults", 100);
                notifyStarted(trace, toolCallId, toolName, serialize(arguments));
                try {
                    result = mcpClientManager.callTool(
                            server.getId(),
                            "file.list",
                            Map.of("parentId", folderId, "maxResults", 100),
                            origin.toToolContext());
                    String listing = extractFolderListing(result, part);
                    if (listing.length() > remainingChars) {
                        listing = listing.substring(0, remainingChars);
                    }
                    part.setCaption(listing);
                    remainingChars -= listing.length();
                    notifyCompleted(trace, toolCallId, toolName,
                            "已读取云盘文件夹“" + displayName(part) + "”的文件列表",
                            true);
                    log.info("[Yliyun Attachment] file.list resolved folderId={}, chars={}, requester={}",
                            folderId, listing.length(), origin.requesterId());
                } catch (RuntimeException e) {
                    notifyCompleted(trace, toolCallId, toolName, sanitizeDetail(e.getMessage()), false);
                    throw e;
                }
            }
        }
    }

    private String serialize(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void notifyStarted(TraceListener trace, String toolCallId, String toolName, String arguments) {
        try {
            trace.onStarted(toolCallId, toolName, arguments);
        } catch (Exception e) {
            log.debug("[Yliyun Attachment] trace start notification failed: {}", e.getMessage());
        }
    }

    private void notifyCompleted(
            TraceListener trace,
            String toolCallId,
            String toolName,
            String result,
            boolean success) {
        try {
            trace.onCompleted(toolCallId, toolName, result, success);
        } catch (Exception e) {
            log.debug("[Yliyun Attachment] trace completion notification failed: {}", e.getMessage());
        }
    }

    private boolean isCloudPart(MessageContentPart part) {
        if (part == null || part.getPath() == null) {
            return false;
        }
        return FILE_REF.matcher(part.getPath()).matches()
                || FOLDER_REF.matcher(part.getPath()).matches();
    }

    private String extractFileContent(ToolCallResult result, MessageContentPart part) {
        JsonNode payload = successfulPayload(result, part);
        JsonNode error = payload.get("error");
        if (error != null && !error.asText().isBlank()) {
            throw readFailure(part, error.asText());
        }
        JsonNode content = payload.get("content");
        if (content == null || content.asText().isBlank()) {
            throw readFailure(part, "MCP 未返回可分析的文本内容");
        }
        JsonNode fileName = payload.get("fileName");
        if ((part.getFileName() == null || part.getFileName().isBlank())
                && fileName != null && !fileName.asText().isBlank()) {
            part.setFileName(fileName.asText());
        }
        return content.asText();
    }

    private String extractFolderListing(ToolCallResult result, MessageContentPart part) {
        JsonNode payload = successfulPayload(result, part);
        JsonNode error = payload.get("error");
        if (error != null && !error.asText().isBlank()) {
            throw readFailure(part, error.asText());
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            return result.content();
        }
    }

    private JsonNode successfulPayload(ToolCallResult result, MessageContentPart part) {
        if (result == null || !result.success()) {
            String detail = result != null ? result.content() : "MCP 未返回结果";
            throw readFailure(part, detail);
        }
        try {
            JsonNode payload = objectMapper.readTree(result.content());
            if (payload == null || payload.isNull()) {
                throw readFailure(part, "MCP 返回了空结果");
            }
            return payload;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("file", displayName(part));
            detail.put("response", result.content());
            log.warn("[Yliyun Attachment] unparseable MCP response: {}", detail);
            throw readFailure(part, "MCP 返回格式无法解析");
        }
    }

    private IllegalStateException readFailure(MessageContentPart part, String detail) {
        return new IllegalStateException(
                "读取云盘附件“" + displayName(part) + "”失败：" + sanitizeDetail(detail));
    }

    private String displayName(MessageContentPart part) {
        return part.getFileName() != null && !part.getFileName().isBlank()
                ? part.getFileName()
                : part.getPath();
    }

    private String sanitizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "未知错误";
        }
        String singleLine = detail.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300) + "…";
    }
}
