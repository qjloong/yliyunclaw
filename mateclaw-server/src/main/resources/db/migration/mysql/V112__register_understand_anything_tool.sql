-- V112: Register Understand-Anything tool methods (MySQL)
-- 4-method design: understand_scan = primary entry (single entry point design)
-- understand_diff / explain / knowledge = clearly differentiated tools
-- ToolRegistry.syncBuiltinToolEntities auto-inserts missing entries at startup.
INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, builtin, enabled, bindable, create_time, update_time)
VALUES
    ('understand_scan',     'Understand Scan',     'Full multi-agent pipeline analysis — primary entry for architecture, domain, onboarding', 'builtin', 'understandAnythingTool', 1, 1, 1, NOW(), NOW()),
    ('understand_diff',     'Understand Diff',     'Analyze impact of uncommitted changes only (diff analysis)',                           'builtin', 'understandAnythingTool', 1, 1, 1, NOW(), NOW()),
    ('understand_explain',  'Understand Explain',  'Deep-dive explain a single source file',                                              'builtin', 'understandAnythingTool', 1, 1, 1, NOW(), NOW()),
    ('understand_knowledge','Understand Knowledge','Analyze documentation/wiki directory (Karpathy-style)',                               'builtin', 'understandAnythingTool', 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    enabled = 1,
    update_time = NOW();
