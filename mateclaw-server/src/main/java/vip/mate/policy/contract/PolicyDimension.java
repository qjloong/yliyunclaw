package vip.mate.policy.contract;

/**
 * Policy dimensions that are enforceable across the platform (WP-2).
 *
 * <p>Each dimension maps to one or more fields in {@code WorkspacePolicy} and has
 * deterministic restrictive-merge semantics so that "more restrictive wins" is
 * predictable and testable.
 *
 * @author MateClaw Team
 * @see PolicyScope
 * @see PolicyApplicationMode
 */
public enum PolicyDimension {

    /** File-system sandbox level: read-only, workspace-write, full-access. */
    SANDBOX,

    /** Approval strictness: default vs strict. */
    APPROVAL,

    /** Network behavior: inherit, restricted, disabled. */
    NETWORK,

    /** Explicitly allowed file paths (additive). */
    ALLOWED_PATHS,

    /** Explicitly denied file paths (additive/union). */
    DENIED_PATHS,

    /** Risk-category overrides that map tool families to severity levels. */
    RISK_OVERRIDES,

    /** Project permission mode: limited vs full (behaves like policy but stored via settings). */
    PROJECT_PERMISSION
}
