package vip.mate.agent.model;

import lombok.Data;

import java.util.List;

/**
 * Agent 模板 DTO
 *
 * @author MateClaw Team
 */
@Data
public class TemplateDTO {

    private String id;
    private String name;
    private String nameZh;
    private String description;
    private String descriptionZh;
    private String icon;
    private String agentType;
    private String tags;
    private Integer maxIterations;
    /**
     * Optional pre-rendered system prompt seeded into the new agent. Templates
     * use H2 sections (## Role / ## Goal / ## Backstory / ## Additional
     * Instructions) so the editor UI can split the prompt into structured
     * fields and derive a one-line tagline for the agent card.
     */
    private String systemPrompt;
    private List<WorkspaceFileTemplate> workspaceFiles;

    /**
     * Skill slugs (matching {@code mate_skill.name}) to pre-bind to the newly
     * hired agent. Resolved against the target workspace at apply time; any
     * slug whose row is missing in that workspace is logged and skipped so a
     * partially-installed environment can still hire the agent. Templates ship
     * with classpath-stable slugs, not numeric IDs, because skill ids vary per
     * install.
     */
    private List<String> defaultSkillSlugs;

    /**
     * Tool names to pre-bind directly (bypassing the skill layer). Filtered
     * against {@code AvailableToolService.listAvailable()} at apply time —
     * names the picker can't resolve are dropped with a warning rather than
     * aborting the hire. Use for capabilities that aren't owned by any skill,
     * not for system-level tools that are already universally available.
     */
    private List<String> defaultToolNames;

    /**
     * 聊天首页副标题（可选，hire 时拷贝到新 agent）。
     */
    private String homeSubtitle;

    /**
     * 聊天首页快捷聊天入口（可选）。每项含 title 与 prompt，hire 时序列化为
     * JSON 存入 agent 的 home_quick_starts_json。
     */
    private List<HomeQuickStart> homeQuickStarts;

    /**
     * 模板版本号（可选，hire 时拷贝到新 agent.template_version）。
     */
    private String templateVersion;

    /**
     * 模板分类（可选，hire 时拷贝到新 agent.template_category）。
     */
    private String templateCategory;

    /**
     * 模板域（可选，hire 时拷贝到新 agent.template_domain）。
     */
    private String templateDomain;

    /**
     * Agent profile 标识（可选）。教师出题助手等专用 agent 需要以此启用
     * 出题规则、出题扩展拦截器。
     */
    private String profileId;

    /**
     * 能力包标识（可选），例如 capability.education.junior_chinese_exam。
     * 决定 Teacher 等 extension 是否接管此 agent。
     */
    private String capabilityPackId;

    /**
     * 插件标识（可选），例如 builtin.teacher_exam。Extension 优先按 pluginKey
     * 识别（如 TeacherAgentExtension），缺失时按 templateId/profileId 兜底。
     */
    private String pluginKey;

    /**
     * 模板元数据 JSON（可选，hire 时原样拷贝到 agent.template_metadata_json）。
     */
    private String templateMetadataJson;

    @Data
    public static class HomeQuickStart {
        private String title;
        private String prompt;
    }

    @Data
    public static class WorkspaceFileTemplate {
        private String filename;
        private String content;
        private Boolean enabled;
        private Integer sortOrder;
    }
}
