UPDATE mate_tool
SET display_name = 'HTML 报表（文本生成）',
    description = '将 Markdown 文本直接渲染为 HTML 报表并返回预览/下载链接。适合新建报告、方案稿和打印页。',
    tool_type = 'builtin',
    bean_name = 'htmlRenderTool',
    update_time = NOW()
WHERE name = 'renderHtml';

INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, enabled, create_time, update_time, deleted)
SELECT 'renderHtml', 'HTML 报表（文本生成）', '将 Markdown 文本直接渲染为 HTML 报表并返回预览/下载链接。适合新建报告、方案稿和打印页。', 'builtin', 'htmlRenderTool', TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'renderHtml');

UPDATE mate_tool
SET display_name = 'HTML 报表（单文件转换）',
    description = '从单个 Markdown 文件生成 HTML 报表并返回预览/下载链接。适合长文档、正文文件或先写后导出的场景。',
    tool_type = 'builtin',
    bean_name = 'htmlRenderTool',
    update_time = NOW()
WHERE name = 'renderHtmlFromFile';

INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, enabled, create_time, update_time, deleted)
SELECT 'renderHtmlFromFile', 'HTML 报表（单文件转换）', '从单个 Markdown 文件生成 HTML 报表并返回预览/下载链接。适合长文档、正文文件或先写后导出的场景。', 'builtin', 'htmlRenderTool', TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'renderHtmlFromFile');

UPDATE mate_tool
SET display_name = 'HTML 报表（多文件汇总）',
    description = '按顺序合并多个 Markdown 文件并生成 HTML 报表，返回预览/下载链接。适合章节式报告和多材料汇总导出。',
    tool_type = 'builtin',
    bean_name = 'htmlRenderTool',
    update_time = NOW()
WHERE name = 'renderHtmlFromFiles';

INSERT INTO mate_tool (name, display_name, description, tool_type, bean_name, enabled, create_time, update_time, deleted)
SELECT 'renderHtmlFromFiles', 'HTML 报表（多文件汇总）', '按顺序合并多个 Markdown 文件并生成 HTML 报表，返回预览/下载链接。适合章节式报告和多材料汇总导出。', 'builtin', 'htmlRenderTool', TRUE, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mate_tool WHERE name = 'renderHtmlFromFiles');
