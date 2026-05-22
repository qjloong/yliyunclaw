package vip.mate.memory.event;

import vip.mate.memory.contract.MemoryWriteProvenance;

/**
 * Published when canonical memory files are written (MEMORY.md, structured/*.md).
 * Used by SoulSummarizerService for K-accumulate SOUL.md evolution.
 *
 * @param agentId the agent ID
 * @param target  which file was written (e.g. "MEMORY.md", "structured/user.md")
 * @param action  what happened ("remember", "consolidate", "update")
 * @param content the written content (may be truncated for large writes)
 * @param provenance standardized write provenance when the writer surface requires it
 * @author MateClaw Team
 */
public record MemoryWriteEvent(
        Long agentId,
        String target,
        String action,
                String content,
                MemoryWriteProvenance provenance
) {

        public MemoryWriteEvent(Long agentId, String target, String action, String content) {
                this(agentId, target, action, content, null);
        }
}
