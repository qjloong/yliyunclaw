package vip.mate.wiki.dto;

import vip.mate.wiki.model.WikiPageEntity;

import java.util.List;

/**
 * Canonical-source derived view built from stable page structure metadata.
 */
public record WikiDerivedView(
        String viewType,
        String viewKey,
        String title,
        String subtitle,
        String sliceType,
        String grade,
        String volume,
        String unit,
        String chapter,
        String classicName,
        String source,
        List<String> materialTypes,
        List<String> routeTags,
        int pageCount,
        List<WikiPageEntity> pages
) {
}