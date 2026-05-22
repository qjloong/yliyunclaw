package vip.mate.context.contract;

/**
 * Compaction levels for context blocks under token pressure (WP-4).
 *
 * <p>These levels mirror the implicit behavior already present in
 * {@link vip.mate.agent.context.ConversationWindowManager} but make the
 * state transitions explicit and testable.
 *
 * <p>State machine (for history window):
 * <pre>
 * FULL → TRIM → COMPACT → COLLAPSE → SUMMARY_FALLBACK
 * </pre>
 *
 * @author MateClaw Team
 */
public enum ContextCompactionLevel {

    /** No compaction applied; the block is used at full length. */
    FULL,

    /** Minor trimming: whitespace, redundant formatting, or low-priority messages removed. */
    TRIM,

    /** Structural compaction: message content preserved but verbose metadata collapsed. */
    COMPACT,

    /** Heavy compaction: only essential assertions and key facts retained. */
    COLLAPSE,

    /** Complete replacement with a generated summary; original content is no longer present. */
    SUMMARY_FALLBACK
}
