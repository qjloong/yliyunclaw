package vip.mate.memory.contract;

/**
 * Canonical taxonomy of memory surfaces, distinguishing durable-memory authorities
 * from access surfaces and from metrics-only surfaces (WP-3).
 *
 * <p>This enum addresses WP-0 ambiguity {@code A05} (memory authority overlap) by making
 * the <em>role</em> of each memory-touching component explicit.
 *
 * @author MateClaw Team
 * @see MemoryOperation
 * @see MemoryGovernanceContract
 */
public enum MemorySurfaceType {

    /** Orchestrator that dispatches to providers but does not own memory truth. */
    ORCHESTRATOR,

    /** Built-in provider that injects memory into the system prompt path. */
    BUILTIN_PROVIDER,

    /** Lifecycle mediator that joins memory with context before model invocation. */
    LIFECYCLE_MEDIATOR,

    /** Service that writes daily notes and long-term summaries after conversation ends. */
    SUMMARIZATION_WRITER,

    /** Service that performs dream/emergence consolidation and writes canonical memory files. */
    EMERGENCE_WRITER,

    /** Service that refreshes derived summaries like SOUL.md from canonical memory state. */
    DERIVED_SUMMARY_WRITER,

    /** Service that tracks recall metrics and scoring; does not own memory content. */
    RECALL_SCORING,

    /** Tool that exposes direct read/write access to memory files. */
    DIRECT_FILE_TOOL,

    /** Lightweight durable-memory append tool with event emission. */
    UNIVERSAL_TOOL,

    /** Controller/service layer that writes fact-related metadata back to canonical files. */
    FACT_MAINTENANCE,

    /** Archive rotation that moves DREAMS.md history into monthly files. */
    ARCHIVE_MAINTENANCE
}
