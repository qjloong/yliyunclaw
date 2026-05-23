package vip.mate.context.contract;

/**
 * Budget classes that determine how context sources compete for space (WP-4).
 *
 * <p>The current system mixes character budgets (in {@code ContextRouterService})
 * and token estimates (in {@code ConversationWindowManager}). WP-4 does not yet
 * converge them; it makes the distinction explicit so that later convergence
 * (WP-4 second pass or WP-5 diagnostics) has a stable vocabulary.
 *
 * @author MateClaw Team
 */
public enum ContextBudgetClass {

    /** Fixed small hint block produced by the router (e.g., project cues, memory hints). */
    ROUTER_HINT,

    /** Token-estimated variable block managed by the window manager (conversation history). */
    HISTORY_WINDOW,

    /** External evidence block with its own provenance (attachments, tool results). */
    EVIDENCE_BLOCK,

    /** Pre-fetched durable memory block merged before the model call. */
    MEMORY_BLOCK,

    /** System-level reserved budget that cannot be compressed (directives, safety instructions). */
    RESERVED
}
