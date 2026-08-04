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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    static final int MAX_FOLDER_ITEMS = 100;
    static final int MAX_FOLDER_SUMMARIES = 12;
    static final int MAX_FOLDER_READS = 5;
    static final int MAX_FOLDER_LISTING_CHARS = 8_000;

    private static final Pattern FILE_REF = Pattern.compile("^yliyun://file/(\\d+)$");
    private static final Pattern FOLDER_REF = Pattern.compile("^yliyun://folder/(\\d+)$");

    private final McpServerService mcpServerService;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;
    private final YliyunCloudResourceRefService resourceRefService;
    private final YliyunCloudResourceStatusService resourceStatusService;

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
        String userPrompt = extractUserPrompt(parts);
        Set<String> resolvedReferences = new HashSet<>();
        for (MessageContentPart part : cloudParts) {
            String reference = part.getPath();
            part.setCaption(null); // never trust stale/client-supplied extracted content
            if (!resolvedReferences.add(reference)) {
                continue;
            }

            Matcher fileMatcher = FILE_REF.matcher(reference);
            Matcher folderMatcher = FOLDER_REF.matcher(reference);
            YliyunCloudResourceRefService.ResolvedResourceRef signedRef =
                    resourceRefService.supports(reference)
                            ? resourceRefService.resolvePath(reference, origin)
                            : null;
            boolean signedFile = signedRef != null && "file".equals(signedRef.resourceType());
            boolean signedFolder = signedRef != null && "folder".equals(signedRef.resourceType());
            if (signedRef != null) {
                resourceStatusService.requireCurrent(signedRef, origin);
                if ((part.getFileName() == null || part.getFileName().isBlank())
                        && signedRef.displayName() != null) {
                    part.setFileName(signedRef.displayName());
                }
                if ((part.getContentType() == null || part.getContentType().isBlank())
                        && signedRef.mimeType() != null) {
                    part.setContentType(signedRef.mimeType());
                }
            }
            ToolCallResult result;
            if (fileMatcher.matches() || signedFile) {
                long fileId = Long.parseLong(signedFile
                        ? signedRef.resourceId() : fileMatcher.group(1));
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
            } else if (folderMatcher.matches() || signedFolder) {
                long folderId = Long.parseLong(signedFolder
                        ? signedRef.resourceId() : folderMatcher.group(1));
                String folderContent = enrichFolder(
                        server, folderId, part, userPrompt, remainingChars, origin, trace);
                part.setCaption(folderContent);
                remainingChars -= folderContent.length();
            }
        }
    }

    private String enrichFolder(
            McpServerEntity server,
            long folderId,
            MessageContentPart folderPart,
            String userPrompt,
            int remainingChars,
            ChatOrigin origin,
            TraceListener trace) {
        if (remainingChars < 100) {
            throw new IllegalStateException(
                    "云盘附件内容总量超过 " + MAX_TOTAL_CHARS + " 字符，请减少文件后重试");
        }

        String listCallId = "cloud-attachment-" + UUID.randomUUID();
        String listToolName = "yliyun_attachment_list";
        Map<String, Object> listArguments = Map.of(
                "folderId", folderId,
                "folderName", displayName(folderPart),
                "maxResults", MAX_FOLDER_ITEMS);
        notifyStarted(trace, listCallId, listToolName, serialize(listArguments));

        JsonNode listingPayload;
        try {
            ToolCallResult listResult = mcpClientManager.callTool(
                    server.getId(),
                    "file.list",
                    Map.of("parentId", folderId, "maxResults", MAX_FOLDER_ITEMS),
                    origin.toToolContext());
            listingPayload = successfulPayload(listResult, folderPart);
            notifyCompleted(trace, listCallId, listToolName,
                    "已列出云盘文件夹“" + displayName(folderPart) + "”",
                    true);
        } catch (RuntimeException e) {
            notifyCompleted(trace, listCallId, listToolName, sanitizeDetail(e.getMessage()), false);
            throw e;
        }

        List<FolderCandidate> candidates = folderCandidates(listingPayload, userPrompt);
        String listing = renderFolderListing(listingPayload, candidates);
        List<FolderCandidate> readable = candidates.stream()
                .filter(this::isReadableFolderCandidate)
                .sorted(folderCandidateComparator())
                .limit(MAX_FOLDER_SUMMARIES)
                .toList();
        if (readable.isEmpty()) {
            return trimToLength(listing, remainingChars);
        }

        String summaryCallId = "cloud-attachment-" + UUID.randomUUID();
        String summaryToolName = "yliyun_attachment_summarize";
        notifyStarted(trace, summaryCallId, summaryToolName, serialize(Map.of(
                "folderId", folderId,
                "candidateCount", readable.size(),
                "fileIds", readable.stream().map(FolderCandidate::id).toList())));

        List<FolderCandidate> summarized = new ArrayList<>();
        int summarySuccesses = 0;
        for (FolderCandidate candidate : readable) {
            String summaryText = "";
            try {
                ToolCallResult summaryResult = mcpClientManager.callTool(
                        server.getId(),
                        "file.summarize",
                        Map.of("fileId", candidate.id()),
                        origin.toToolContext());
                JsonNode summary = successfulPayload(summaryResult, filePart(candidate));
                summaryText = summarySearchText(summary);
                summarySuccesses++;
            } catch (RuntimeException ex) {
                log.debug("[Yliyun Attachment] file.summarize skipped fileId={}: {}",
                        candidate.id(), sanitizeDetail(ex.getMessage()));
            }
            summarized.add(candidate.withSummary(
                    summaryText,
                    candidate.score() + relevanceScore(userPrompt, summaryText, 2)));
        }
        notifyCompleted(trace, summaryCallId, summaryToolName,
                "已概览 " + summarySuccesses + "/" + readable.size() + " 个候选文件并计算相关性",
                summarySuccesses > 0);

        List<FolderCandidate> selected = summarized.stream()
                .sorted(folderCandidateComparator())
                .limit(MAX_FOLDER_READS)
                .toList();
        int listingBudget = Math.min(MAX_FOLDER_LISTING_CHARS, Math.max(1_000, remainingChars / 5));
        String boundedListing = trimToLength(listing, Math.min(listing.length(), listingBudget));
        int contentBudget = Math.max(0, remainingChars - boundedListing.length() - 400);
        if (selected.isEmpty() || contentBudget < 100) {
            return trimToLength(boundedListing, remainingChars);
        }

        String readCallId = "cloud-attachment-" + UUID.randomUUID();
        String readToolName = "yliyun_attachment_read_selected";
        notifyStarted(trace, readCallId, readToolName, serialize(Map.of(
                "folderId", folderId,
                "selectedCount", selected.size(),
                "files", selected.stream().map(candidate -> Map.of(
                        "fileId", candidate.id(), "fileName", candidate.name())).toList())));

        StringBuilder sections = new StringBuilder();
        int readSuccesses = 0;
        int budgetLeft = contentBudget;
        for (int index = 0; index < selected.size() && budgetLeft >= 100; index++) {
            FolderCandidate candidate = selected.get(index);
            int filesLeft = selected.size() - index;
            int maxChars = Math.min(MAX_CHARS_PER_FILE, Math.max(100, budgetLeft / filesLeft));
            try {
                ToolCallResult readResult = mcpClientManager.callTool(
                        server.getId(),
                        "file.read",
                        Map.of("fileId", candidate.id(), "maxChars", maxChars, "format", "markdown"),
                        origin.toToolContext());
                String content = extractFileContent(readResult, filePart(candidate));
                String heading = "\n\n### " + candidate.name() + "\n\n";
                int availableContent = Math.max(0, budgetLeft - heading.length());
                String boundedContent = trimToLength(content, availableContent);
                if (!boundedContent.isBlank()) {
                    sections.append(heading).append(boundedContent);
                    budgetLeft -= heading.length() + boundedContent.length();
                    readSuccesses++;
                }
            } catch (RuntimeException ex) {
                String failure = "\n\n### " + candidate.name()
                        + "\n\n> 读取失败：" + sanitizeDetail(ex.getMessage());
                String boundedFailure = trimToLength(failure, budgetLeft);
                sections.append(boundedFailure);
                budgetLeft -= boundedFailure.length();
                log.debug("[Yliyun Attachment] folder file.read skipped fileId={}: {}",
                        candidate.id(), sanitizeDetail(ex.getMessage()));
            }
        }
        notifyCompleted(trace, readCallId, readToolName,
                "已按相关性读取 " + readSuccesses + "/" + selected.size() + " 个文件",
                readSuccesses > 0);

        String selection = "\n\n## 本轮按提问相关性读取的文件"
                + sections;
        String result = boundedListing + selection;
        log.info("[Yliyun Attachment] progressive folder resolved folderId={}, candidates={}, "
                        + "summarized={}, selected={}, read={}, chars={}, requester={}",
                folderId, candidates.size(), summarySuccesses, selected.size(), readSuccesses,
                result.length(), origin.requesterId());
        return trimToLength(result, remainingChars);
    }

    private List<FolderCandidate> folderCandidates(JsonNode payload, String userPrompt) {
        List<FolderCandidate> candidates = new ArrayList<>();
        JsonNode items = payload != null ? payload.get("items") : null;
        if (items == null || !items.isArray()) return candidates;
        for (JsonNode item : items) {
            long id = item.path("id").asLong(0);
            String name = item.path("name").asText("").replaceAll("[\\r\\n]+", " ").trim();
            if (id <= 0 || name.isBlank()) continue;
            String type = item.path("type").asText("file");
            String mimeType = item.path("mimeType").asText("");
            String modifiedAt = item.path("modifiedAt").asText("");
            long size = Math.max(0, item.path("size").asLong(0));
            int score = relevanceScore(userPrompt, name, 5);
            candidates.add(new FolderCandidate(
                    id, name, type, mimeType, modifiedAt, size, "", score));
        }
        return candidates;
    }

    private boolean isReadableFolderCandidate(FolderCandidate candidate) {
        if ("directory".equalsIgnoreCase(candidate.type())) return false;
        String mime = candidate.mimeType().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/")) {
            return false;
        }
        String name = candidate.name().toLowerCase(Locale.ROOT);
        return !(name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")
                || name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".exe")
                || name.endsWith(".bin"));
    }

    private Comparator<FolderCandidate> folderCandidateComparator() {
        return Comparator.comparingInt(FolderCandidate::score).reversed()
                .thenComparing(FolderCandidate::modifiedAt, Comparator.reverseOrder())
                .thenComparing(FolderCandidate::name);
    }

    private String renderFolderListing(JsonNode payload, List<FolderCandidate> candidates) {
        int total = payload != null ? payload.path("total").asInt(candidates.size()) : candidates.size();
        StringBuilder out = new StringBuilder();
        out.append("## 文件夹目录（共 ").append(total).append(" 项）\n");
        for (FolderCandidate candidate : candidates) {
            out.append("- ")
                    .append("directory".equalsIgnoreCase(candidate.type()) ? "[目录] " : "[文件] ")
                    .append(candidate.name());
            if (!"directory".equalsIgnoreCase(candidate.type())) {
                out.append("（").append(candidate.mimeType().isBlank()
                        ? "未知类型" : candidate.mimeType());
                if (candidate.size() > 0) out.append("，").append(candidate.size()).append(" bytes");
                out.append("）");
            }
            out.append('\n');
            if (out.length() >= MAX_FOLDER_LISTING_CHARS) {
                out.append("- …目录较大，其余项目未展开\n");
                break;
            }
        }
        return out.toString();
    }

    private String summarySearchText(JsonNode summary) {
        if (summary == null || summary.isNull()) return "";
        StringBuilder text = new StringBuilder();
        for (String field : List.of("fileName", "preview")) {
            JsonNode value = summary.get(field);
            if (value != null && !value.isNull()) text.append(value.asText()).append(' ');
        }
        for (String field : List.of("headings", "keywords")) {
            JsonNode values = summary.get(field);
            if (values != null && values.isArray()) {
                values.forEach(value -> text.append(value.asText()).append(' '));
            }
        }
        return text.toString();
    }

    private int relevanceScore(String query, String source, int weight) {
        if (source == null || source.isBlank()) return 0;
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : queryTerms(query)) {
            if (normalizedSource.contains(term)) score += weight;
        }
        return score;
    }

    private Set<String> queryTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        if (query == null || query.isBlank()) return terms;
        Set<String> stopWords = Set.of(
                "这个", "文件", "云盘", "帮我", "进行", "分析", "总结", "内容", "关键", "要点",
                "读取", "please", "file", "folder", "cloud", "read", "analyze", "summarize");
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() < 2) continue;
            if (!stopWords.contains(token)) terms.add(token);
            boolean containsHan = token.codePoints()
                    .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                            == Character.UnicodeScript.HAN);
            if (containsHan && token.length() > 2) {
                for (int i = 0; i < token.length() - 1 && terms.size() < 64; i++) {
                    String pair = token.substring(i, i + 2);
                    if (!stopWords.contains(pair)) terms.add(pair);
                }
            }
            if (terms.size() >= 64) break;
        }
        return terms;
    }

    private String extractUserPrompt(List<MessageContentPart> parts) {
        StringBuilder prompt = new StringBuilder();
        for (MessageContentPart part : parts) {
            if (part != null && "text".equals(part.getType())
                    && part.getText() != null && !part.getText().isBlank()) {
                if (!prompt.isEmpty()) prompt.append('\n');
                prompt.append(part.getText());
                if (prompt.length() >= 2_000) break;
            }
        }
        return trimToLength(prompt.toString(), 2_000);
    }

    private MessageContentPart filePart(FolderCandidate candidate) {
        MessageContentPart part = new MessageContentPart();
        part.setType("file");
        part.setFileName(candidate.name());
        part.setContentType(candidate.mimeType());
        part.setPath("yliyun://file/" + candidate.id());
        return part;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || maxLength <= 0) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record FolderCandidate(
            long id,
            String name,
            String type,
            String mimeType,
            String modifiedAt,
            long size,
            String summary,
            int score) {
        FolderCandidate withSummary(String value, int relevance) {
            return new FolderCandidate(
                    id, name, type, mimeType, modifiedAt, size, value, relevance);
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
                || FOLDER_REF.matcher(part.getPath()).matches()
                || resourceRefService.supports(part.getPath());
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
