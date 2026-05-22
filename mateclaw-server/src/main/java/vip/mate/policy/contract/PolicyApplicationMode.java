package vip.mate.policy.contract;

/**
 * How a policy value from a given scope interacts with the current effective value (WP-2).
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code CONSTRAIN} — the policy sets an upper bound; stronger scopes can still tighten it.</li>
 *   <li>{@code TIGHTEN} — the policy may only make the effective value more restrictive,
 *       never less restrictive. This is the default for template defaults.</li>
 *   <li>{@code VETO} — the policy overrides all weaker scopes unconditionally.
 *       Reserved for runtime emergency stops and global locks.</li>
 * </ul>
 *
 * @author MateClaw Team
 */
public enum PolicyApplicationMode {

    /** Sets a boundary that stronger scopes may still tighten. */
    CONSTRAIN,

    /** May only increase restrictiveness; weakening is ignored. */
    TIGHTEN,

    /** Unconditional override; used sparingly for emergency/runtime stops. */
    VETO
}
