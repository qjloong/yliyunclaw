# WenShu 应用中心导航串线记录（2026-08-04）

## 结论

云盘前端的 `wenshu` 路由、workspace tab 与 AI 助手 Drawer 分支互相独立。本次点击“AI问数”却加载 MateClaw，并非前端路由串线，而是应用中心残留配置造成：

```text
app.wenshu_integration.wenshu_embed_url=http://127.0.0.1:5173
```

本机 5173 端口正是 MateClaw 前端，因此问数 iframe 加载了错误应用。

## 已处理

应用中心中的下列平台连接项已经统一纠正为 `http://192.168.0.135`：

- `app.wenshu_integration.wenshu_base_url`
- `app.wenshu_integration.wenshu_embed_url`
- `app.wenshu_integration.allowed_origin`

接口复核结果：

- `/cloud-drive/wenshu/capability` 动态返回 `http://192.168.0.135/embed/cloud-drive`；
- `/yliyun/ai/capability` 仍独立返回 `http://localhost:5173`；
- 应用中心配置查询与 capability 返回一致；
- 云盘前端未增加 135 硬编码，`pnpm build:local` 已通过。

应用地址必须继续以 `cloud_app_config_item` 为事实源，经 capability 下发；前端菜单不得直接拼接 WenShu 或 MateClaw 地址。

## 部署环境待办

135 上 WenShu 容器还有两项部署配置未与应用中心同步：

1. 集成 `/health` 使用应用中心当前 `X-Integration-Key` 返回 401，说明 `CLOUD_DRIVE_CLIENT_SECRET` / `CLOUD_DRIVE_INBOUND_SECRET` 与应用中心密钥不一致。
2. `/embed/cloud-drive` 当前返回 `Content-Security-Policy: frame-ancestors 'self'`，会阻止本地云盘页面 iframe 嵌入。

部署侧需使用同一套配置重新注入：

```text
CLOUD_DRIVE_CLIENT_SECRET
CLOUD_DRIVE_INBOUND_SECRET
VITE_CLOUD_DRIVE_ORIGINS
CLOUD_DRIVE_ALLOWED_ORIGINS
CLOUD_DRIVE_FRAME_ANCESTORS
```

其中三个 origin 白名单必须包含实际云盘 Web origin，并保持精确一致；禁止使用 `*`。

## 验收顺序

1. `http://192.168.0.135/health` 返回成功。
2. 携带应用中心密钥调用 `/api/v1/integrations/cloud-drive/health` 返回成功。
3. 在云盘应用中心执行“测试连接”。
4. 刷新 `/cloud-drive/wenshu/capability`，确认 URL、origin、健康状态正确。
5. 点击云盘左侧“AI问数”，确认加载 WenShu 而非 MateClaw。
6. 验证 iframe `READY → 出票 → bootstrap` 完整链路。
