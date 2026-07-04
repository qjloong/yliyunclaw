-- V171: Register HtmlRenderTool methods.
-- Idempotent: MERGE INTO updates existing rows when id matches.

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000030, 'renderHtml', 'HTML 报表（文本生成）', '将 Markdown 文本直接渲染为 HTML 报表并返回预览/下载链接。适合新建报告、方案稿和打印页。', 'builtin', 'htmlRenderTool', '🧾', TRUE, TRUE, NOW(), NOW(), 0);

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000031, 'renderHtmlFromFile', 'HTML 报表（单文件转换）', '从单个 Markdown 文件生成 HTML 报表并返回预览/下载链接。适合长文档、正文文件或先写后导出的场景。', 'builtin', 'htmlRenderTool', '🧾', TRUE, TRUE, NOW(), NOW(), 0);

MERGE INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
KEY (id)
VALUES (1000000032, 'renderHtmlFromFiles', 'HTML 报表（多文件汇总）', '按顺序合并多个 Markdown 文件并生成 HTML 报表，返回预览/下载链接。适合章节式报告和多材料汇总导出。', 'builtin', 'htmlRenderTool', '🧾', TRUE, TRUE, NOW(), NOW(), 0);
