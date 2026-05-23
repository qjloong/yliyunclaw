package vip.mate.memory.contract;

/**
 * Types of memory operations that can be performed across surfaces (WP-3).
 *
 * <p>This enum supports governance decisions such as:
 * <ul>
 *   <li>Should this surface be allowed to write canonical memory files?</li>
 *   <li>Should this operation emit a provenance event?</li>
 *   <li>Does this operation affect daily notes, long-term files, or both?</li>
 * </ul>
 *
 * @author MateClaw Team
 */
public enum MemoryOperation {

    /** Read durable memory for injection into current-turn context. */
    READ,

    /** Write or append to durable memory (canonical files, daily notes, lessons). */
    WRITE,

    /** Summarize a conversation and promote findings to long-term memory. */
    SUMMARIZE,

    /** Consolidate multiple memory sources into canonical form (dream/emergence). */
    CONSOLIDATE,

    /** Score or track recall metrics without changing memory content. */
    SCORE_RECALL,

    /** Delete or archive memory content. */
    DELETE
}
