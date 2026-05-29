package vip.mate.wiki.dto;

/**
 * Controlled domain profile option exposed to Wiki UI / APIs.
 */
public record WikiDomainProfileOption(
        String id,
        String displayName,
        String description,
        String kbKind,
        String sourcePluginKey,
        String capabilityPackId
) {
}
