package vip.mate.policy.contract;

/**
 * Canonical policy-scope taxonomy for the Core Systems Consolidation (WP-2).
 *
 * <p>This enum makes the authority layer of each policy producer explicit.
 * Higher-scoped policy does not automatically override lower-scoped policy;
 * the override rule depends on the {@link PolicyApplicationMode} and the
 * restrictive-merge semantics of each {@link PolicyDimension}.
 *
 * @author MateClaw Team
 * @see PolicyDimension
 * @see PolicyApplicationMode
 */
public enum PolicyScope {

    /** Product-wide hard constraints (e.g., global enable/disable switches). */
    GLOBAL,

    /** Workspace-level policy persisted in {@code WorkspaceEntity.settingsJson}. */
    WORKSPACE,

    /** Template-provided safety defaults applied at agent bootstrap. */
    TEMPLATE,

    /** Per-agent runtime constraints (e.g., per-turn overrides, emergency stops). */
    RUNTIME
}
