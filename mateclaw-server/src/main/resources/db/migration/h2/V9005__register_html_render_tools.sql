-- V177: Register HtmlRenderTool methods (MERGE INTO pattern for idempotency)
MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000030, 'renderHtml', 'HTML 报表', '将 Markdown 直接渲染为 HTML 报表', 'builtin', 'htmlRenderTool', '🧾', TRUE, TRUE, NOW(), NOW(), 0);

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000031, 'renderHtmlFromFile', 'HTML 报表（文件）', '从 Markdown 文件生成 HTML 报表', 'builtin', 'htmlRenderTool', '🧾', TRUE, TRUE, NOW(), NOW(), 0);

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000032, 'renderHtmlFromFiles', 'HTML 报表（多文件）', '合并多个 Markdown 文件生成 HTML 报表', 'builtin', 'htmlRenderTool', '🧾', TRUE, TRUE, NOW(), NOW(), 0);
