-- V178: Register Understand-Anything tool methods
MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000040, 'understand_scan', 'Understand Scan', 'Full multi-agent pipeline analysis', 'builtin', 'understandAnythingTool', '🧠', TRUE, TRUE, NOW(), NOW(), 0);

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000041, 'understand_diff', 'Understand Diff', 'Analyze impact of uncommitted changes', 'builtin', 'understandAnythingTool', '🔍', TRUE, TRUE, NOW(), NOW(), 0);

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000042, 'understand_explain', 'Understand Explain', 'Deep-dive explain a single source file', 'builtin', 'understandAnythingTool', '📄', TRUE, TRUE, NOW(), NOW(), 0);

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000043, 'understand_knowledge', 'Understand Knowledge', 'Analyze documentation/wiki directory', 'builtin', 'understandAnythingTool', '📚', TRUE, TRUE, NOW(), NOW(), 0);
