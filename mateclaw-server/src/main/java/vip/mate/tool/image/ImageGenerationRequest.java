package vip.mate.tool.image;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 图片生成统一请求
 *
 * @author MateClaw Team
 */
@Data
@Builder
public class ImageGenerationRequest {

    /** 图片内容描述 */
    private String prompt;

    /** 生成模式（由 runtime 自动推断） */
    private ImageCapability mode;

    /** 指定模型名称（可选，provider 有默认值） */
    private String model;

    /** 图片尺寸：1024x1024 / 1024x1792 / 1792x1024 等 */
    @Builder.Default
    private String size = "1024x1024";

    /** 画面比例：1:1 / 16:9 / 9:16 */
    @Builder.Default
    private String aspectRatio = "1:1";

    /** 生成数量 */
    @Builder.Default
    private Integer count = 1;

    /** 参考图片 URL（IMAGE_EDIT 模式） */
    private String referenceImageUrl;

    /** provider 特有的额外参数 */
    private Map<String, Object> extraParams;
}
