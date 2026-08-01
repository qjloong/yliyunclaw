-- P0 convergence: one general runtime + Streamable HTTP /mcp.
UPDATE mate_mcp_server
SET transport = 'streamable_http',
    url = 'http://127.0.0.1:18100/mcp',
    command = NULL,
    args_json = NULL,
    cwd = NULL,
    headers_json = '{"X-Internal-Service":"mateclaw","X-Internal-Token":"${MCP_INTERNAL_SERVICE_TOKEN}"}',
    connect_timeout_seconds = 10,
    read_timeout_seconds = 60,
    update_time = CURRENT_TIMESTAMP
WHERE name = 'yliyun-mcp';
