package vip.mate.setting.contract;

/**
 * Field classification for settings entries, distinguishing ordinary preferences
 * from safety authority and runtime-local state (WP-1).
 *
 * <p>This classification addresses blocker {@code A01} (mixed policy/settings semantics)
 * from the WP-0 ambiguity register by making the <em>intent</em> of each field explicit
 * before any storage separation is attempted.
 *
 * @author MateClaw Team
 * @see SettingSource
 */
public enum SettingFieldClass {

    /** Ordinary user preference or product default with no safety implications. */
    PRODUCT_SETTING,

    /** Safety authority field that can tighten, constrain, or veto behavior. */
    SAFETY_POLICY,

    /** Machine-local operational state that must never become shared truth. */
    LOCAL_RUNTIME_STATE,

    /** Template-provided default that may influence bootstrap but is not itself authority. */
    TEMPLATE_DEFAULT,

    /** Field whose classification is still ambiguous and needs later-package review. */
    AMBIGUOUS
}
