package vip.mate.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.approval.model.ToolApprovalEntity;
import vip.mate.approval.repository.ToolApprovalMapper;
import vip.mate.tool.guard.model.GuardEvaluation;
import vip.mate.tool.guard.model.GuardFinding;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.model.ConversationEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 负责“允许同类权限”的审批指纹生成与复用匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalGrantService {

    private final ToolApprovalMapper approvalMapper;
    private final ConversationService conversationService;

    public ApprovalGrantContext buildContext(String conversationId,
                                             ChatOrigin origin,
                                             String toolName,
                                             GuardEvaluation evaluation) {
        Long workspaceId = origin != null ? origin.workspaceId() : null;
        String projectPath = origin != null ? origin.workspaceBasePath() : null;

        if (workspaceId == null || !StringUtils.hasText(projectPath)) {
            ConversationEntity conversation = conversationService.getConversation(conversationId);
            if (conversation != null) {
                if (workspaceId == null) {
                    workspaceId = conversation.getWorkspaceId();
                }
                if (!StringUtils.hasText(projectPath)) {
                    projectPath = conversationService.resolveEffectiveWorkingDirectory(
                            conversation,
                            workspaceId,
                            null);
                }
            }
        }

        return new ApprovalGrantContext(workspaceId, projectPath, buildApprovalKey(toolName, evaluation));
    }

    public String buildApprovalKey(String toolName, GuardEvaluation evaluation) {
        if (!StringUtils.hasText(toolName) || evaluation == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(toolName.trim().toLowerCase(Locale.ROOT));
        parts.add(evaluation.maxSeverity() != null ? evaluation.maxSeverity().name() : "NONE");

        List<GuardFinding> findings = evaluation.findings() != null ? evaluation.findings() : List.of();
        findings.stream()
                .map(finding -> String.join("|",
                        normalizePart(finding.ruleId()),
                        finding.category() != null ? finding.category().name() : "",
                        normalizePart(finding.paramName())))
                .sorted()
                .forEach(parts::add);

        return sha256Short(String.join("\n", parts));
    }

    public boolean hasReusableGrant(String requesterId,
                                    String conversationId,
                                    Long workspaceId,
                                    String projectPath,
                                    String toolName,
                                    GuardEvaluation evaluation) {
        if (!StringUtils.hasText(requesterId) || !StringUtils.hasText(toolName) || evaluation == null) {
            return false;
        }

        String approvalKey = buildApprovalKey(toolName, evaluation);
        if (!StringUtils.hasText(approvalKey)) {
            return false;
        }

        List<ToolApprovalEntity> grants = approvalMapper.selectList(
                new LambdaQueryWrapper<ToolApprovalEntity>()
                        .eq(ToolApprovalEntity::getDeleted, 0)
                        .eq(ToolApprovalEntity::getUserId, requesterId)
                        .eq(ToolApprovalEntity::getToolName, toolName)
                        .eq(ToolApprovalEntity::getApprovalKey, approvalKey)
                        .in(ToolApprovalEntity::getStatus, List.of("APPROVED", "CONSUMED"))
                        .in(ToolApprovalEntity::getGrantScope, List.of(
                                ApprovalGrantScope.CONVERSATION.name(),
                                ApprovalGrantScope.PROJECT.name()))
                        .orderByDesc(ToolApprovalEntity::getResolvedAt, ToolApprovalEntity::getCreateTime)
        );

        String normalizedProjectPath = normalizePath(projectPath);
        for (ToolApprovalEntity grant : grants) {
            ApprovalGrantScope scope = ApprovalGrantScope.from(grant.getGrantScope());
            if (scope == ApprovalGrantScope.CONVERSATION
                    && Objects.equals(grant.getConversationId(), conversationId)) {
                log.info("[ApprovalGrant] Reusing conversation-scoped grant: conversation={}, tool={}",
                        conversationId, toolName);
                return true;
            }
            if (scope == ApprovalGrantScope.PROJECT
                    && Objects.equals(grant.getWorkspaceId(), workspaceId)
                    && Objects.equals(normalizePath(grant.getProjectPath()), normalizedProjectPath)) {
                log.info("[ApprovalGrant] Reusing project-scoped grant: workspaceId={}, project={}, tool={}",
                        workspaceId, projectPath, toolName);
                return true;
            }
        }
        return false;
    }

    private String normalizePart(String value) {
        return value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        return path.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
    }

    private String sha256Short(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public record ApprovalGrantContext(Long workspaceId, String projectPath, String approvalKey) {
    }
}