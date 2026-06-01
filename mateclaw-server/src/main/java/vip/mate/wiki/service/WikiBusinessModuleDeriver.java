package vip.mate.wiki.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Derives business module tags (e.g., classic_reading, classical_chinese)
 * from page title, purpose hint, and source material metadata.
 * <p>
 * This is a domain-agnostic heuristic service. Mappings are seeded with the
 * education.exam.junior_chinese sample; additional domains can register
 * custom mappings via {@link #registerDeriver}.
 */
@Component
public class WikiBusinessModuleDeriver {

    @FunctionalInterface
    public interface Deriver {
        List<String> derive(String title, String purposeHint, Set<String> materialTypes);
    }

    private final List<Deriver> derivers = new ArrayList<>();

    public WikiBusinessModuleDeriver() {
        // Default sample deriver for education domains
        derivers.add(this::defaultDerive);
    }

    public void registerDeriver(Deriver deriver) {
        derivers.add(deriver);
    }

    public List<String> derive(String title, String purposeHint, Set<String> materialTypes) {
        Set<String> modules = new LinkedHashSet<>();
        for (Deriver d : derivers) {
            modules.addAll(d.derive(title, purposeHint, materialTypes));
        }
        return new ArrayList<>(modules);
    }

    private List<String> defaultDerive(String title, String purposeHint, Set<String> materialTypes) {
        Set<String> modules = new LinkedHashSet<>();
        String probe = ((title == null ? "" : title) + " " + (purposeHint == null ? "" : purposeHint))
                .toLowerCase(Locale.ROOT);

        // Material-type driven tags
        if (materialTypes != null) {
            if (materialTypes.contains("classic_manuscript")) {
                modules.add("classic_reading");
            }
            if (materialTypes.contains("curriculum_standard")) {
                modules.add("basic_knowledge");
                modules.add("modern_reading");
                modules.add("classical_chinese");
                modules.add("ancient_poetry");
            }
        }

        // Text heuristic for textbook / sample / rule materials
        if (probe.contains("文言") || probe.contains("古文") || probe.contains("实词") || probe.contains("虚词") || probe.contains("翻译") || probe.contains("断句")) {
            modules.add("classical_chinese");
        }
        if (probe.contains("古诗") || probe.contains("诗词") || probe.contains("诗歌") || probe.contains("默写") || probe.contains("炼字") || probe.contains("意象") || probe.contains("唐诗") || probe.contains("宋词")) {
            modules.add("ancient_poetry");
        }
        if (probe.contains("名著") || probe.contains("导读") || probe.contains("整本书") || probe.contains("西游记") || probe.contains("水浒传") || probe.contains("骆驼祥子") || probe.contains("朝花夕拾") || probe.contains("昆虫记") || probe.contains("经典常谈")) {
            modules.add("classic_reading");
        }
        if (probe.contains("写作") || probe.contains("作文") || probe.contains("审题") || probe.contains("立意") || probe.contains("素材") || probe.contains("范文")) {
            modules.add("writing");
        }
        if (probe.contains("基础") || probe.contains("字音") || probe.contains("字形") || probe.contains("成语") || probe.contains("病句") || probe.contains("文学常识") || probe.contains("文化常识") || probe.contains("修辞")) {
            modules.add("basic_knowledge");
        }

        // Default fallback for textbook lessons without specific signals
        if (modules.isEmpty() && materialTypes != null && materialTypes.contains("textbook_latest")) {
            modules.add("modern_reading");
        }

        return new ArrayList<>(modules);
    }
}
