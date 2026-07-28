-- V9015: 修复 yliyun-mcp 连接配置（MySQL）
UPDATE mate_mcp_server
SET headers_json = '{"X-Internal-Service":"mateclaw"}',
    transport = 'sse',
    url = 'http://yliyun-mcp:18100/sse',
    update_time = NOW()
WHERE name = 'yliyun-mcp'
  AND (headers_json LIKE '%${currentUserId}%' OR transport = 'streamable_http');
