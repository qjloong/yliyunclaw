-- V9015: 修复 yliyun-mcp 连接配置 — 使用 SSE 传输协议
-- FastMCP httpStream 模式兼容 SSE 协议：
--   GET  /mcp → SSE stream (server→client notifications)
--   POST /mcp → JSON-RPC with SSE response (client→server)
-- Java HttpClientSseClientTransport 原生处理此模式，无兼容性问题。

UPDATE mate_mcp_server
SET transport = 'sse',
    url = 'http://localhost:18100/mcp',
    command = NULL,
    args_json = NULL,
    cwd = NULL,
    headers_json = '{"X-Internal-Service":"mateclaw"}',
    connect_timeout_seconds = 10,
    read_timeout_seconds = 30,
    update_time = CURRENT_TIMESTAMP
WHERE name = 'yliyun-mcp';
