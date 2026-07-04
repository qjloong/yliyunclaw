package vip.mate.teacher.model;

/**
 * Lightweight per-turn context for the built-in Teacher agent.
 *
 * <p>This is intentionally small for v2 phase 1: it centralizes agent identity,
 * runtime mode, material availability, and coarse task classification before
 * later phases add durable workflow state and rule-pack scoring.</p>
 */
public record TeacherTurnContext(
        String agentId,
        String agentName,
        String templateId,
        String profileId,
        String capabilityPackId,
        String runtimeMode,
        String businessModule,
        String taskType,
        boolean hasBoundKnowledgeBases,
        boolean explicitLocalScope,
        boolean awaitingConfirmation
) {
}
