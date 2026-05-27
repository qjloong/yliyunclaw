package vip.mate.teacher.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reviewable Teacher self-improvement proposal.
 *
 * <p>The agent may create drafts, but drafts never change production behavior
 * until an administrator explicitly accepts and publishes them.</p>
 */
@Data
public class TeacherImprovementDraft {
    private String id;
    private String status;
    private String targetType;
    private String targetArea;
    private String proposalType;
    private String riskLevel;
    private String title;
    private String summary;
    private String sourceRunId;
    private String sourceConversationId;
    private String rulePackId;
    private Long workspaceId;
    private String createdAt;
    private String reviewedAt;
    private String reviewNote;
    private Map<String, Object> diagnosis = new LinkedHashMap<>();
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private Map<String, Object> proposedPatch = new LinkedHashMap<>();
    private Map<String, Object> proposedSkillPatch = new LinkedHashMap<>();
    private Map<String, Object> proposedAcceptanceCase = new LinkedHashMap<>();
    private TeacherRulePack proposedRulePack;
}
