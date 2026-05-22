package vip.mate.workspace.core.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Workspace-level execution policy for coding and tool operations.
 */
@Data
public class WorkspacePolicy {

    public static final String SANDBOX_WORKSPACE_WRITE = "workspace-write";
    public static final String SANDBOX_READ_ONLY = "read-only";
    public static final String SANDBOX_FULL_ACCESS = "full-access";

    public static final String APPROVAL_DEFAULT = "default";
    public static final String APPROVAL_STRICT = "strict";

    public static final String NETWORK_INHERIT = "inherit";
    public static final String NETWORK_RESTRICTED = "restricted";
    public static final String NETWORK_DISABLED = "disabled";

    private String sandboxMode;

    private String approvalPolicy;

    private String networkPolicy;

    private List<String> allowedPaths;

    private List<String> deniedPaths;

    private Map<String, String> riskOverrides;
}