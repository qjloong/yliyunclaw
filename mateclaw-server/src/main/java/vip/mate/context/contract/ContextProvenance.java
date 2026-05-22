package vip.mate.context.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Provenance metadata for a single context block, making "where did this text come from?"
 * answerable for diagnostics and compliance (WP-4).
 *
 * <p>Each context block injected into the model path should carry one
 * {@link ContextProvenance} so that later compaction, fallback, and audit logic
 * can reason about freshness, authority, and replacement rules.
 *
 * @param sourceType     the kind of context source
 * @param sourceId       an opaque identifier for the specific source instance
 *                       (e.g., file name, KB id, conversation id, tool call id)
 * @param freshness      when this block was produced or last refreshed
 * @param budgetChars    the character budget assigned to this block at injection time
 * @param budgetTokens   the estimated token budget; may be null if not yet computed
 * @param metadata       source-specific key/value metadata (e.g., {"kb.externalKey":"java-spec"})
 * @author MateClaw Team
 */
public record ContextProvenance(ContextSourceType sourceType,
                                 String sourceId,
                                 Instant freshness,
                                 Integer budgetChars,
                                 Integer budgetTokens,
                                 Map<String, String> metadata) {

    public ContextProvenance {
        metadata = metadata != null ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata)) : Collections.emptyMap();
    }

    public static ContextProvenance of(ContextSourceType sourceType, String sourceId) {
        return new ContextProvenance(sourceType, sourceId, Instant.now(), null, null, Collections.emptyMap());
    }

    public static ContextProvenance of(ContextSourceType sourceType, String sourceId, int budgetChars) {
        return new ContextProvenance(sourceType, sourceId, Instant.now(), budgetChars, null, Collections.emptyMap());
    }
}
