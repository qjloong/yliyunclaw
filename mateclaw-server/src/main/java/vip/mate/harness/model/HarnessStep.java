package vip.mate.harness.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class HarnessStep {
    private String id;
    private String name;
    private String phase;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
