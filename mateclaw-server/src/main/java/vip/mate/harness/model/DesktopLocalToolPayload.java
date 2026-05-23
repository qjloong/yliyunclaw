package vip.mate.harness.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DesktopLocalToolPayload {
    private String schemaVersion;
    private String source;
    private String timestamp;
    private String toolName;
    private String mode;
    private String status;
    private String riskLevel;
    private Boolean requiresApproval;
    private String workspaceRoot;
    private String cwd;
    private String summary;
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private Map<String, Object> approval = new LinkedHashMap<>();
    private Map<String, Object> truncation = new LinkedHashMap<>();
}
