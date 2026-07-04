package vip.mate.wiki.dto;

/**
 * Lightweight raw-material projection for retrieval reranking.
 */
public record RawSearchRef(
        Long id,
        String title,
        String materialType,
        String materialMetadataJson
) {
}
