package vip.mate.harness.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class HarnessRun {
    private String id;
    private String conversationId;
    private String agentId;
    private String agentName;
    private String mode;
    private HarnessRunStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<HarnessStep> steps = new ArrayList<>();
    private List<HarnessToolInvocation> toolInvocations = new ArrayList<>();
    private List<HarnessApproval> approvals = new ArrayList<>();
    private HarnessExecutionSummary summary = new HarnessExecutionSummary();
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
