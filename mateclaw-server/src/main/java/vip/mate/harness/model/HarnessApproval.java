package vip.mate.harness.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class HarnessApproval {
    private String id;
    private String toolInvocationId;
    private String status;
    private String scope;
    private String requestedBy;
    private String resolvedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
