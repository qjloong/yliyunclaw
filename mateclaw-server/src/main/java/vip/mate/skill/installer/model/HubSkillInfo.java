package vip.mate.skill.installer.model;

import lombok.Data;

import java.util.List;

/**
 * ClawHub 市场 skill 信息
 *
 * @author MateClaw Team
 */
@Data
public class HubSkillInfo {

    private String name;
    private String slug;
    private String description;
    private String author;
    private String version;
    private String icon;
    private List<String> tags;
    private Integer downloads;
    private String bundleUrl;
}
