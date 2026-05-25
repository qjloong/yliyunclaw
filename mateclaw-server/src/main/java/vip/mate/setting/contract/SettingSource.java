package vip.mate.setting.contract;

/**
 * Canonical settings-source taxonomy for the Core Systems Consolidation (WP-1).
 *
 * <p>This enum unifies all configuration origins into one explicit precedence model.
 * It does not change current resolution behavior; it makes the existing behavior
 * inspectable and contract-bound for later convergence.
 *
 * <p>Precedence order (highest to lowest) as observed in the current codebase:
 * <ol>
 *   <li>{@link #RUNTIME_OVERRIDE} — request-scoped or test-scoped temporary values</li>
 *   <li>{@link #DESKTOP_LOCAL_CONFIG} — machine-local persisted config and env overrides</li>
 *   <li>{@link #WORKSPACE_SHARED_SETTINGS} — workspace-level JSON settings stored in DB</li>
 *   <li>{@link #TEMPLATE_DEFAULTS} — built-in template JSON defaults applied at agent creation</li>
 *   <li>{@link #MANAGED_SYSTEM_SETTINGS} — server-global DB-managed settings</li>
 *   <li>{@link #PLATFORM_DEFAULTS} — hardcoded product defaults in Java/Electron source</li>
 * </ol>
 *
 * @author MateClaw Team
 * @see SettingFieldClass
 * @see EffectiveSettingsContract
 */
public enum SettingSource {

    /** Hardcoded product defaults in server Java code or desktop packaged source. */
    PLATFORM_DEFAULTS,

    /** DB-managed server-global settings surfaced through {@code SystemSettingService}. */
    MANAGED_SYSTEM_SETTINGS,

    /** Built-in template JSON values that seed agent/workspace defaults at bootstrap time. */
    TEMPLATE_DEFAULTS,

    /** Workspace-level settings persisted in {@code WorkspaceEntity.settingsJson}. */
    WORKSPACE_SHARED_SETTINGS,

    /** Desktop-local config file and environment overrides; strictly local runtime state. */
    DESKTOP_LOCAL_CONFIG,

    /** Request-scoped or test-scoped overrides (e.g., IPC test payload, CLI flags). */
    RUNTIME_OVERRIDE
}
