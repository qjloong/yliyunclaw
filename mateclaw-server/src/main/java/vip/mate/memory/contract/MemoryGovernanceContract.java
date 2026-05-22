package vip.mate.memory.contract;

/**
 * Governance contract for durable-memory operations (WP-3).
 *
 * <p>This contract defines the rules that determine whether a given memory operation
 * is permissible for a given surface. It does not replace the existing memory SPI;
 * it adds a <strong>classification and filtering layer</strong> on top of it so that
 * governance rules become explicit rather than implicit.
 *
 * <p>Example rules:
 * <ul>
 *   <li>{@code DIRECT_FILE_TOOL} may write canonical memory files, but must emit
 *       {@link MemoryWriteProvenance}.</li>
 *   <li>{@code RECALL_SCORING} must not write canonical memory files; it only writes metrics.</li>
 *   <li>{@code SUMMARIZATION_WRITER} may write both daily notes and long-term files,
 *       but promotion rules must be explicit.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see MemorySurfaceType
 * @see MemoryOperation
 */
public interface MemoryGovernanceContract {

    /**
     * Check whether the operation is allowed for the given surface.
     *
     * @param surfaceType the surface requesting the operation
     * @param operation   the operation requested
     * @param targetPath  the memory target (e.g., file path, note category); may be null
     * @return true if allowed under current governance rules
     */
    boolean isAllowed(MemorySurfaceType surfaceType, MemoryOperation operation, String targetPath);

    /**
     * Determine whether the operation must emit a {@link MemoryWriteProvenance} record.
     *
     * @param surfaceType the surface performing the operation
     * @param operation   the operation being performed
     * @return true if provenance is mandatory
     */
    boolean requiresProvenance(MemorySurfaceType surfaceType, MemoryOperation operation);

    /**
     * Classify whether the target is daily-notes, long-term canonical, or metrics-only.
     *
     * @param targetPath the memory target
     * @return the classification; never null
     */
    TargetClass classifyTarget(String targetPath);

    enum TargetClass {
        DAILY_NOTES,
        DERIVED_SUMMARY,
        LONG_TERM_CANONICAL,
        METRICS_ONLY,
        UNKNOWN
    }
}
