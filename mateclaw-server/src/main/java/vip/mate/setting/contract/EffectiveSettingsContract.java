package vip.mate.setting.contract;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only contract for resolving the effective value of a setting key across
 * all known sources (WP-1).
 *
 * <p>This contract does not mutate any configuration store. It provides a unified
 * query surface so that consumers can ask "what is the effective value of key X?"
 * without knowing which subsystem owns that key.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>All methods are idempotent and side-effect-free.</li>
 *   <li>Resolution traces are optional but recommended for diagnostics.</li>
 *   <li>Policy-like fields should be classified {@link SettingFieldClass#SAFETY_POLICY}
 *       and resolved with {@link SettingMergeStrategy#RESTRICTIVE_MERGE}.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see SettingSource
 * @see SettingFieldClass
 * @see SettingMergeStrategy
 */
public interface EffectiveSettingsContract {

    /**
     * Resolve the effective scalar value for a key in a given scope.
     *
     * @param scope       the scope identifier (e.g., workspace id, agent id, or "global")
     * @param key         the setting key
     * @param defaultValue fallback when no source provides the key
     * @return the resolved value and its provenance
     */
    ResolutionResult<String> resolveString(String scope, String key, String defaultValue);

    /**
     * Resolve the effective boolean value for a key in a given scope.
     */
    ResolutionResult<Boolean> resolveBoolean(String scope, String key, boolean defaultValue);

    /**
     * Return a read-only snapshot of all known keys and their effective values
     * for the given scope, together with classification metadata.
     */
    Map<String, ResolutionResult<?>> resolveAll(String scope);

    /**
     * Result of a single resolution, carrying both the value and its provenance.
     */
    record ResolutionResult<T>(T value, SettingSource source, SettingFieldClass fieldClass,
                                SettingMergeStrategy mergeStrategy, String provenanceNote) {

        public static <T> ResolutionResult<T> of(T value, SettingSource source,
                                                  SettingFieldClass fieldClass,
                                                  SettingMergeStrategy mergeStrategy) {
            return new ResolutionResult<>(value, source, fieldClass, mergeStrategy, null);
        }
    }
}
