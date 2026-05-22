package vip.mate.context.contract;

/**
 * Canonical taxonomy of context contributors for the Core Systems Consolidation (WP-4).
 *
 * <p>This enum names every major source that can contribute evidence to the current
 * model call. It makes the previously implicit source classification in
 * {@link vip.mate.agent.context.ContextRouterService} explicit and reusable.
 *
 * @author MateClaw Team
 * @see ContextProvenance
 * @see ContextLifecycleContract
 */
public enum ContextSourceType {

    /** Project insight cache: stack hints, key files, build system, material index. */
    PROJECT_CACHE,

    /** Workspace memory files (e.g., MEMORY.md, profile files) enabled for this agent. */
    WORKSPACE_MEMORY,

    /** Wiki / knowledge-base route hints and grounding evidence. */
    WIKI,

    /** Recent session recall via {@code SessionSearchService}. */
    SESSION_SEARCH,

    /** Template-declared context sources (e.g., knowledge bindings, project-derived flags). */
    TEMPLATE_DECLARATION,

    /** Conversation history messages before window fitting and compaction. */
    CONVERSATION_HISTORY,

    /** Memory prefetch block produced by {@code MemoryManager} and injected via {@code MemoryLifecycleMediator}. */
    MEMORY_PREFETCH,

    /** Attachment-derived evidence (documents, images, etc.) extracted and summarized. */
    ATTACHMENT,

    /** Tool-result evidence from the current turn (e.g., file read output, search results). */
    TOOL_RESULT,

    /** Explicit user instruction or system prompt preamble. */
    DIRECTIVE
}
