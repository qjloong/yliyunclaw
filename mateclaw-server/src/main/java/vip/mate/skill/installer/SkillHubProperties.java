package vip.mate.skill.installer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ClawHub 市场连接配置
 *
 * @author MateClaw Team
 */
@Data
@ConfigurationProperties(prefix = "mateclaw.skill.hub")
public class SkillHubProperties {

    /** Hub 基础 URL */
    private String baseUrl = "https://clawhub.ai";

    /** 搜索 API 路径 */
    private String searchPath = "/api/v1/search";

    /** HTTP 请求超时（秒） */
    private int httpTimeout = 15;

    /** HTTP 重试次数 */
    private int httpRetries = 3;
}
