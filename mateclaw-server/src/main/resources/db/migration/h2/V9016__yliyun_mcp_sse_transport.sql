-- V9016: yliyun-mcp 最终正确配置 — SSE 传输 + /mcp 端点
-- 参考 V85 ckjia-shopping 的 SSE 模式，与 FastMCP httpStream 兼容。

MERGE INTO mate_mcp_server (id, name, description, transport, url,
    headers_json, enabled, builtin, connect_timeout_seconds, read_timeout_seconds,
    disclosure_tier, command, args_json, cwd,
    create_time, update_time, deleted)
KEY (name)
VALUES (
    9001,
    'yliyun-mcp',
    '一粒云云盘 MCP Server（V1 — 文件搜索、读写、分享、标签）',
    'sse',
    'http://localhost:18100/mcp',
    '{"X-Internal-Service":"mateclaw"}',
    TRUE,
    TRUE,
    10,
    30,
    'core',
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
