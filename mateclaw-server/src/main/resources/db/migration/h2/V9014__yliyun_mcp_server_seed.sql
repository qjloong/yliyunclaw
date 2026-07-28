-- V9014: 云盘 yliyun-mcp MCP Server 默认配置（种子数据）
-- 使用 stdio 传输模式：MateClaw 启动子进程，无协议兼容性问题。
-- cwd 需按实际部署路径调整（生产环境建议用环境变量 ${MCP_YLIYUN_HOME}）。

MERGE INTO mate_mcp_server (id, name, description, transport, url, command, args_json, cwd,
    headers_json, enabled, builtin, connect_timeout_seconds, read_timeout_seconds,
    disclosure_tier, create_time, update_time, deleted)
KEY (name)
VALUES (
    9001,
    'yliyun-mcp',
    '一粒云云盘 MCP Server（V1 — 文件搜索、读写、分享、标签，stdio 模式）',
    'stdio',
    NULL,
    'npx',
    '["tsx", "src/index.ts"]',
    '/Users/qinjinlong/Documents/projects/ai/yliyun-mcp-server',
    NULL,
    TRUE,
    TRUE,
    30,
    60,
    'core',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
