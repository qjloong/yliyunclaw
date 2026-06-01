package vip.mate.workspace.conversation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.common.result.R;
import vip.mate.channel.web.ChatStreamTracker;
import vip.mate.tool.document.DocxExportService;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.model.ConversationEntity;
import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 会话管理接口
 *
 * @author MateClaw Team
 */
@Tag(name = "会话管理")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ChatStreamTracker streamTracker;
    private final DocxExportService docxExportService;

    /**
     * 获取当前用户的会话列表
     * 返回 ConversationVO，包含 agentName / agentIcon / status 等前端展示字段
     */
    @Operation(summary = "获取会话列表")
    @GetMapping
    public R<List<ConversationVO>> list(
            Authentication auth,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        return R.ok(conversationService.listConversations(username, workspaceId));
    }

    /**
     * 获取指定会话的消息历史（支持分页）。
     * <p>
     * 不传 limit 时返回全部消息（向后兼容）。
     * 传 limit 时返回最新 limit 条 + hasMore 标志。
     * 传 beforeId + limit 时返回该 ID 之前的 limit 条（上拉加载更早消息）。
     */
    @Operation(summary = "获取会话消息历史（支持分页）")
    @GetMapping("/{conversationId}/messages")
    public R<?> listMessages(@PathVariable String conversationId,
                             @RequestParam(required = false) Long beforeId,
                             @RequestParam(required = false) Integer limit,
                             Authentication auth,
                             @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权访问该会话");
        }

        // 向后兼容：不传 limit 则返回全部消息（旧前端行为）
        if (limit == null || limit <= 0) {
            return R.ok(conversationService.listMessageViews(conversationId));
        }

        // 分页模式
        java.util.List<vip.mate.workspace.conversation.model.MessageEntity> messages;
        boolean hasMore;

        if (beforeId != null) {
            // 上拉加载：取 beforeId 之前的 limit+1 条，多取一条用于判断 hasMore
            messages = conversationService.listMessagesBefore(conversationId, beforeId, limit + 1);
            hasMore = messages.size() > limit;
            if (hasMore) {
                messages = messages.subList(messages.size() - limit, messages.size());
            }
        } else {
            // 初始加载：最新 limit 条
            long total = conversationService.countMessages(conversationId);
            messages = conversationService.listRecentMessages(conversationId, limit);
            hasMore = total > limit;
        }

        java.util.List<vip.mate.workspace.conversation.vo.MessageVO> views = messages.stream()
                .map(m -> vip.mate.workspace.conversation.vo.MessageVO.from(
                        m, conversationService.parseMessageParts(m), conversationService.renderMessageContent(m)))
                .toList();

        return R.ok(java.util.Map.of(
                "messages", views,
                "hasMore", hasMore
        ));
    }

    /**
     * 删除会话（同时删除消息）
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/{conversationId}")
    public R<Void> delete(@PathVariable String conversationId,
                          Authentication auth,
                          @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权操作该会话");
        }
        conversationService.deleteConversation(conversationId);
        return R.ok();
    }

    /**
     * 重命名会话
     */
    @Operation(summary = "重命名会话")
    @PutMapping("/{conversationId}/title")
    public R<Void> rename(@PathVariable String conversationId,
                          @RequestBody Map<String, String> body,
                          Authentication auth,
                          @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权操作该会话");
        }
        String title = body.getOrDefault("title", "").trim();
        if (title.isEmpty() || title.length() > 100) {
            return R.fail("标题不合法");
        }
        conversationService.renameConversation(conversationId, title);
        return R.ok();
    }

    @Operation(summary = "更新会话工作目录")
    @PutMapping("/{conversationId}/working-directory")
    public R<Map<String, String>> updateWorkingDirectory(@PathVariable String conversationId,
                                                         @RequestBody Map<String, String> body,
                                                         Authentication auth,
                                                         @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权操作该会话");
        }
        try {
            String effective = conversationService.updateWorkingDirectory(
                    conversationId,
                    body.get("workingDirectory"));
            return R.ok(Map.of("workingDirectory", effective != null ? effective : ""));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "更新会话运行模式")
    @PutMapping("/{conversationId}/runtime-mode")
    public R<Map<String, String>> updateRuntimeMode(@PathVariable String conversationId,
                                                    @RequestBody Map<String, String> body,
                                                    Authentication auth,
                                                    @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权操作该会话");
        }
        try {
            String effective = conversationService.updateRuntimeMode(
                    conversationId,
                    body.get("runtimeMode"));
            return R.ok(Map.of("runtimeMode", effective));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "更新会话运行模型")
    @PutMapping("/{conversationId}/runtime-model")
    public R<Map<String, Object>> updateRuntimeModel(@PathVariable String conversationId,
                                                     @RequestBody Map<String, String> body,
                                                     Authentication auth,
                                                     @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权操作该会话");
        }
        try {
            ConversationService.RuntimeModelResolution resolution = conversationService.updateRuntimeModelSelection(
                    conversationId,
                    body.get("runtimeProviderId"),
                    body.get("runtimeModelName"));
            ConversationService.RuntimeModelSelection effective = resolution.effectiveSelection();
            return R.ok(Map.of(
                    "requestedRuntimeProviderId", resolution.requestedRuntimeProviderId() != null ? resolution.requestedRuntimeProviderId() : "",
                    "requestedRuntimeModelName", resolution.requestedRuntimeModelName() != null ? resolution.requestedRuntimeModelName() : "",
                    "runtimeProviderId", effective.runtimeProviderId() != null ? effective.runtimeProviderId() : "",
                    "runtimeModelName", effective.runtimeModelName() != null ? effective.runtimeModelName() : "",
                    "fallbackApplied", resolution.fallbackApplied(),
                    "reason", resolution.reason() != null ? resolution.reason() : ""));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "导出 Teacher 试题结果为 Word")
    @PostMapping("/{conversationId}/teacher-export")
    public R<Map<String, String>> exportTeacherPaper(@PathVariable String conversationId,
                                                     @RequestBody Map<String, Object> body,
                                                     Authentication auth,
                                                     @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权操作该会话");
        }
        ConversationEntity conversation = conversationService.getConversation(conversationId);
        if (conversation == null) {
            return R.fail(404, "会话不存在");
        }

        String markdown = blankToNull(asString(body.get("markdown")));
        if (!StringUtils.hasText(markdown)) {
            return R.fail("导出内容不能为空");
        }
        String mode = asString(body.getOrDefault("mode", "full")).trim().toLowerCase(Locale.ROOT);
        if (!"questions".equals(mode) && !"full".equals(mode)) {
            return R.fail("导出模式不合法");
        }
        String sectionKeys = String.valueOf(body.getOrDefault("sectionKeys", ""));
        if ("questions".equals(mode)) {
            if (!sectionKeys.contains("questions") || containsTeacherAnswerSections(markdown)) {
                return R.fail("仅试题版 Word 不能包含答案、采分点、来源或审核内容");
            }
        } else if (!containsTeacherFullSections(markdown)) {
            return R.fail("完整版 Word 必须包含试题、参考答案、采分点和来源区块");
        }
        String format = asString(body.getOrDefault("format", "docx")).trim().toLowerCase(Locale.ROOT);
        if (!"docx".equals(format)) {
            return R.fail("当前仅支持导出为 Word(docx)");
        }

        try {
            String effectiveWorkingDirectory = conversationService.resolveEffectiveWorkingDirectory(
                    conversation, workspaceId, null);
            Path projectDir = StringUtils.hasText(effectiveWorkingDirectory)
                    ? Paths.get(effectiveWorkingDirectory).toAbsolutePath().normalize()
                    : null;
            String outputPath = asString(body.get("outputPath"));
            String filename = asString(body.get("filename"));
            ChatOrigin origin = ChatOrigin.web(conversationId, username, workspaceId, effectiveWorkingDirectory);
            Path target = StringUtils.hasText(outputPath)
                    ? docxExportService.resolveOutputPath(projectDir, outputPath, filename)
                    : (projectDir != null ? docxExportService.buildDefaultOutputPath(projectDir, filename, origin) : null);
            if (projectDir != null && target != null && !target.toAbsolutePath().normalize().startsWith(projectDir)) {
                return R.fail("导出路径必须位于当前项目绑定目录内");
            }

            DocxExportService.ExportedDocx exported = docxExportService.exportMarkdown(
                    markdown,
                    filename,
                    asString(body.get("pageSize")),
                    target,
                    origin.toToolContext());
            return R.ok(Map.of(
                    "format", format,
                    "fileName", exported.fileName(),
                    "downloadUrl", exported.downloadUrl(),
                    "savedPath", exported.savedPath() != null ? exported.savedPath() : ""
            ));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            return R.fail("导出失败: " + e.getMessage());
        }
    }

    /**
     * 清空会话消息（保留会话记录）
     */
    @Operation(summary = "清空会话消息")
    @DeleteMapping("/{conversationId}/messages")
    public R<Void> clearMessages(@PathVariable String conversationId,
                                 Authentication auth,
                                 @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权操作该会话");
        }
        conversationService.clearMessages(conversationId);
        return R.ok();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean containsTeacherQuestionSection(String markdown) {
        return markdown != null
                && markdown.matches("(?s).*#{1,6}\\s*(试题|试题内容|正式试题|题目|试卷)(?:\\s|[:：#]|$).*");
    }

    private boolean containsTeacherAnswerSections(String markdown) {
        return markdown != null
                && markdown.matches("(?s).*#{1,6}\\s*(参考答案|答案|采分点|评分标准|评分细则|来源依据|来源|命题质量审核|质量审核)(?:\\s|[:：#]|$).*");
    }

    private boolean containsTeacherFullSections(String markdown) {
        return containsTeacherQuestionSection(markdown)
                && containsTeacherHeading(markdown, "参考答案", "答案")
                && containsTeacherHeading(markdown, "采分点", "评分标准", "评分细则")
                && containsTeacherHeading(markdown, "来源依据", "来源");
    }

    private boolean containsTeacherHeading(String markdown, String... headings) {
        if (markdown == null || headings == null) {
            return false;
        }
        for (String heading : headings) {
            if (heading != null && markdown.matches("(?s).*#{1,6}\\s*" + java.util.regex.Pattern.quote(heading) + "(?:\\s|[:：#]|$).*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取会话的流状态
     * 优先使用内存中的 StreamTracker，若无数据则回退到数据库持久化的 stream_status
     */
    @Operation(summary = "获取会话流状态")
    @GetMapping("/{conversationId}/status")
    public R<Map<String, String>> getStreamStatus(@PathVariable String conversationId,
                                                  Authentication auth,
                                                  @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        String username = auth != null ? auth.getName() : "anonymous";
        if (!conversationService.isConversationOwner(conversationId, username, workspaceId)) {
            return R.fail(403, "无权访问该会话");
        }
        if (streamTracker.isRunning(conversationId)) {
            return R.ok(Map.of("streamStatus", "running"));
        }
        // 回退到数据库持久化的 stream_status（处理服务重启/节点切换场景）
        String dbStatus = conversationService.getStreamStatus(conversationId);
        return R.ok(Map.of("streamStatus", dbStatus != null ? dbStatus : "idle"));
    }
}
