package vip.mate.setting.contract;

/**
 * Merge semantics for settings resolution, aligned with the observed behavior
 * in {@code WorkspaceService.mergeSettingsJson()} and executor policy merging (WP-1).
 *
 * <p>Each strategy defines how a higher-precedence source interacts with a lower-precedence
 * source when both provide a value for the same key.
 *
 * @author MateClaw Team
 */
public enum SettingMergeStrategy {

    /** Higher-precedence value completely replaces the lower-precedence value. */
    SCALAR_OVERRIDE,

    /** Higher-precedence list is appended to the lower-precedence list (e.g., allowed paths). */
    ADDITIVE_MERGE,

    /** The more restrictive of the two values wins (e.g., sandbox mode, approval policy). */
    RESTRICTIVE_MERGE,

    /** Both values are preserved in a composite structure (e.g., risk overrides with domain scoping). */
    COMPOSITE_MERGE,

    /** Lower-precedence value is ignored; only the highest-precedence source matters. */
    SINGLE_SOURCE
}
