-- V9014: 云盘 yliyun-mcp MCP Server 默认配置（种子数据，Kingbase）
INSERT INTO mate_mcp_server (id, name, description, transport, url, headers_json,
    enabled, builtin, connect_timeout_seconds, read_timeout_seconds,
    disclosure_tier, create_time, update_time, deleted)
VALUES (
    9001,
    'yliyun-mcp',
    '一粒云云盘 MCP Server（V1 — 文件搜索、读写、分享、标签）',
    'sse',
    'http://yliyun-mcp:18100/sse',
    '{"X-Internal-Service":"mateclaw"}',
    TRUE,
    TRUE,
    10,
    30,
    'core',
    NOW(),
    NOW(),
    0
) ON CONFLICT (name) DO NOTHING;
