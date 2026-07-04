-- V172: Register Understand-Anything tool methods (H2)
INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, enabled, create_time, update_time, deleted)
SELECT 'understand_scan', 'Understand Scan',
       'Full multi-agent pipeline analysis — primary entry for architecture, domain, onboarding',
       'builtin', 'understandAnythingTool', TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_scan');

INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, enabled, create_time, update_time, deleted)
SELECT 'understand_diff', 'Understand Diff',
       'Analyze impact of uncommitted changes only (diff analysis)',
       'builtin', 'understandAnythingTool', TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_diff');

INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, enabled, create_time, update_time, deleted)
SELECT 'understand_explain', 'Understand Explain',
       'Deep-dive explain a single source file',
       'builtin', 'understandAnythingTool', TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_explain');

INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, enabled, create_time, update_time, deleted)
SELECT 'understand_knowledge', 'Understand Knowledge',
       'Analyze documentation/wiki directory (Karpathy-style)',
       'builtin', 'understandAnythingTool', TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'understand_knowledge');
