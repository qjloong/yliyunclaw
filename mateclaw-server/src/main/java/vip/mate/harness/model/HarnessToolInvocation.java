package vip.mate.harness.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class HarnessToolInvocation {
    private String id;
    private String stepId;
    private String toolName;
    private String status;
    private String riskLevel;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
