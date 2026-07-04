package vip.mate.teacher.model;

import java.util.List;
import java.util.Map;

/**
 * Read-only Teacher skill contract.
 *
 * <p>RulePack defines hard rules. TeacherSkillDefinition defines the
 * executable workflow that applies those rules to a teaching task.</p>
 */
public record TeacherSkillDefinition(
        String id,
        String name,
        String version,
        String module,
        String purpose,
        List<String> supportedRulePackIds,
        List<String> workflowSteps,
        Map<String, Object> inputContract,
        Map<String, Object> outputContract,
        List<Map<String, String>> acceptanceSignals
) {
}
