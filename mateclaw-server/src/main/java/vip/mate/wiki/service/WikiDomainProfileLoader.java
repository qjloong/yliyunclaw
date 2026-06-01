package vip.mate.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vip.mate.wiki.dto.WikiDomainProfileMaterialType;
import vip.mate.wiki.dto.WikiDomainProfileMetadataField;
import vip.mate.wiki.dto.WikiDomainProfileOption;

import java.util.ArrayList;
import java.util.List;

/**
 * T2-4-8: Scans capability pack JSON templates at startup and registers
 * any declared {@code wikiDomainProfile} segments into the domain profile registry.
 * <p>
 * Searches classpath under {@code templates/} and {@code skills/} for JSON files
 * that contain a {@code capabilityPack.wikiDomainProfile} object.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikiDomainProfileLoader {

    private final WikiDomainProfileRegistryService registryService;
    private final ObjectMapper objectMapper;

    private static final String[] SCAN_PATTERNS = {
            "classpath:templates/*.json",
            "classpath:skills/**/template.json"
    };

    @PostConstruct
    public void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String pattern : SCAN_PATTERNS) {
            try {
                Resource[] resources = resolver.getResources(pattern);
                for (Resource resource : resources) {
                    if (!resource.exists() || !resource.isReadable()) {
                        continue;
                    }
                    try {
                        parseAndRegister(resource);
                    } catch (Exception e) {
                        log.debug("[WikiDomainProfileLoader] Failed to parse {}: {}", resource.getFilename(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.debug("[WikiDomainProfileLoader] Pattern scan failed for {}: {}", pattern, e.getMessage());
            }
        }
    }

    private void parseAndRegister(Resource resource) throws Exception {
        JsonNode root = objectMapper.readTree(resource.getInputStream());
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode capPack = root.get("capabilityPack");
        if (capPack == null || !capPack.isObject()) {
            return;
        }
        JsonNode wikiProfile = capPack.get("wikiDomainProfile");
        if (wikiProfile == null || !wikiProfile.isObject()) {
            return;
        }

        String id = text(wikiProfile, "id");
        String displayName = text(wikiProfile, "displayName");
        String description = text(wikiProfile, "description");
        if (!StringUtils.hasText(id) || !StringUtils.hasText(displayName)) {
            log.warn("[WikiDomainProfileLoader] Skipping incomplete profile in {}: id={}, displayName={}",
                    resource.getFilename(), id, displayName);
            return;
        }

        String pluginKey = text(capPack, "pluginKey");
        String packId = text(capPack, "packId");

        List<WikiDomainProfileMaterialType> materialTypes = parseMaterialTypes(wikiProfile.get("materialTypes"));

        WikiDomainProfileOption option = new WikiDomainProfileOption(
                id,
                displayName,
                description,
                "business",
                pluginKey,
                packId,
                materialTypes
        );
        registryService.registerProfile(option);
    }

    private List<WikiDomainProfileMaterialType> parseMaterialTypes(JsonNode node) {
        List<WikiDomainProfileMaterialType> types = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return types;
        }
        for (JsonNode item : node) {
            if (!item.isObject()) continue;
            String typeId = text(item, "id");
            String typeName = text(item, "name");
            if (!StringUtils.hasText(typeId)) continue;
            List<WikiDomainProfileMetadataField> fields = parseFields(item.get("fields"));
            types.add(new WikiDomainProfileMaterialType(typeId, typeName, fields));
        }
        return types;
    }

    private List<WikiDomainProfileMetadataField> parseFields(JsonNode node) {
        List<WikiDomainProfileMetadataField> fields = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return fields;
        }
        for (JsonNode item : node) {
            if (!item.isObject()) continue;
            String key = text(item, "key");
            String label = text(item, "label");
            String placeholder = text(item, "placeholder");
            boolean required = item.has("required") && item.get("required").asBoolean(false);
            int sort = item.has("sort") ? item.get("sort").asInt(0) : 0;
            if (StringUtils.hasText(key)) {
                fields.add(new WikiDomainProfileMetadataField(key, label, placeholder, required, sort));
            }
        }
        return fields;
    }

    private String text(JsonNode parent, String field) {
        if (parent == null || !parent.isObject()) return null;
        JsonNode node = parent.get(field);
        return node != null && !node.isNull() ? node.asText(null) : null;
    }
}
