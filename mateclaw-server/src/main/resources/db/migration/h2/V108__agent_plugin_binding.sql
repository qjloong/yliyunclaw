-- V108: Persist per-agent plugin bindings and backfill legacy Teacher agents.

CREATE TABLE IF NOT EXISTS mate_agent_plugin (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    agent_id           BIGINT       NOT NULL,
    plugin_key         VARCHAR(128) NOT NULL,
    capability_pack_id VARCHAR(128),
    stage              VARCHAR(64),
    subject            VARCHAR(64),
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    config_json        CLOB,
    create_time        DATETIME     NOT NULL,
    update_time        DATETIME     NOT NULL,
    deleted            INT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_plugin
    ON mate_agent_plugin(agent_id, plugin_key, capability_pack_id);
CREATE INDEX IF NOT EXISTS idx_agent_plugin_agent
    ON mate_agent_plugin(agent_id, enabled, create_time);

INSERT INTO mate_agent_plugin (
    id,
    agent_id,
    plugin_key,
    capability_pack_id,
    stage,
    subject,
    enabled,
    config_json,
    create_time,
    update_time,
    deleted
)
SELECT
    900000000000000000 + a.id,
    a.id,
    'builtin.teacher_exam',
    CASE
        WHEN COALESCE(a.capability_pack_id, '') <> '' THEN a.capability_pack_id
        ELSE 'capability.education.junior_chinese_exam'
    END,
    'junior',
    'chinese',
    TRUE,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM mate_agent a
WHERE COALESCE(a.deleted, 0) = 0
  AND (
      a.template_id = 'builtin.teacher_exam_assistant'
      OR a.profile_id = 'teacher_exam_assistant_profile'
      OR a.capability_pack_id IN ('capability.education.junior_chinese_exam', 'capability.education.junior_classics_exam')
  )
  AND NOT EXISTS (
      SELECT 1
      FROM mate_agent_plugin p
      WHERE p.agent_id = a.id
        AND COALESCE(p.deleted, 0) = 0
  );