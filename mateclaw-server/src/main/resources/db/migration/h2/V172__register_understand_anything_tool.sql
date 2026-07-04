-- V112: Register Understand-Anything tool methods (H2)
-- Uses explicit IDs + WHERE NOT EXISTS pattern matching V107 convention.
INSERT INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, bindable, builtin, create_time, update_time, deleted)
SELECT 1000000030, 'understand_scan', 'Understand Scan',
       'Full multi-agent pipeline analysis — primary entry for architecture, domain, onboarding',
       'builtin', 'understandAnythingTool', '🧠', TRUE, TRUE, TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_scan');

INSERT INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, bindable, builtin, create_time, update_time, deleted)
SELECT 1000000031, 'understand_diff', 'Understand Diff',
       'Analyze impact of uncommitted changes only (diff analysis)',
       'builtin', 'understandAnythingTool', '🔍', TRUE, TRUE, TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_diff');

INSERT INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, bindable, builtin, create_time, update_time, deleted)
SELECT 1000000032, 'understand_explain', 'Understand Explain',
       'Deep-dive explain a single source file',
       'builtin', 'understandAnythingTool', '📄', TRUE, TRUE, TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_explain');

INSERT INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, bindable, builtin, create_time, update_time, deleted)
SELECT 1000000033, 'understand_knowledge', 'Understand Knowledge',
       'Analyze documentation/wiki directory (Karpathy-style)',
       'builtin', 'understandAnythingTool', '📚', TRUE, TRUE, TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_knowledge');
