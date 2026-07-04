package vip.mate.wiki.dto;

/**
 * Metadata form field configuration for a material type within a domain profile.
 */
public record WikiDomainProfileMetadataField(
        String key,
        String label,
        String placeholder,
        boolean visible,
        int order
) {
}
