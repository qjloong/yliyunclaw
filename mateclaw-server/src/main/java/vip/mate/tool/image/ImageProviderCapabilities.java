package vip.mate.tool.image;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 图片生成 Provider 细粒度能力声明
 *
 * @author MateClaw Team
 */
@Data
@Builder
public class ImageProviderCapabilities {

    /** 支持的生成模式 */
    @Builder.Default
    private Set<ImageCapability> modes = Set.of(ImageCapability.TEXT_TO_IMAGE);

    /** 支持的图片尺寸，如 ["1024x1024", "1024x1792"] */
    @Builder.Default
    private List<String> supportedSizes = List.of("1024x1024");

    /** 支持的画面比例 */
    @Builder.Default
    private List<String> aspectRatios = List.of("1:1", "16:9", "9:16");

    /** 最大生成数量 */
    @Builder.Default
    private int maxCount = 1;

    /** 默认模型 */
    private String defaultModel;

    /** 可用模型列表 */
    @Builder.Default
    private List<String> models = List.of();

    /**
     * 将请求的 size 就近匹配到 provider 支持的值
     */
    public String normalizeSize(String requested) {
        if (requested == null || requested.isBlank()) {
            return supportedSizes.isEmpty() ? "1024x1024" : supportedSizes.get(0);
        }
        if (supportedSizes.contains(requested)) {
            return requested;
        }
        // 就近匹配：解析面积，找最接近的
        long reqArea = parseArea(requested);
        String closest = supportedSizes.get(0);
        long minDiff = Math.abs(reqArea - parseArea(closest));
        for (String s : supportedSizes) {
            long diff = Math.abs(reqArea - parseArea(s));
            if (diff < minDiff) {
                minDiff = diff;
                closest = s;
            }
        }
        return closest;
    }

    /**
     * 将请求的 aspectRatio 就近匹配或回退到默认
     */
    public String normalizeAspectRatio(String requested) {
        if (aspectRatios.contains(requested)) {
            return requested;
        }
        return aspectRatios.isEmpty() ? "1:1" : aspectRatios.get(0);
    }

    /**
     * 将请求的 count 限制在 provider 支持范围内
     */
    public int normalizeCount(int requested) {
        return Math.min(Math.max(requested, 1), maxCount);
    }

    private long parseArea(String size) {
        try {
            String[] parts = size.toLowerCase().split("x");
            return Long.parseLong(parts[0].trim()) * Long.parseLong(parts[1].trim());
        } catch (Exception e) {
            return 1024L * 1024L;
        }
    }
}
