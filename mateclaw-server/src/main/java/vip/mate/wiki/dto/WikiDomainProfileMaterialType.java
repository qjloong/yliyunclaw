package vip.mate.wiki.dto;

import java.util.List;

/**
 * Material type configuration for a domain profile.
 *
 * <p>Each profile declares the material types it supports and the metadata
 * fields that should be rendered for that type.
 */
public record WikiDomainProfileMaterialType(
        String value,
        String label,
        List<WikiDomainProfileMetadataField> fields
) {
}
