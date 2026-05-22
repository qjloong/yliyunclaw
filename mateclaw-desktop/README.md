# Meta Y Desktop Test Shell

这个目录提供一个可打包的 Electron 桌面壳，用来本地测试 Meta Y 的桌面客户端行为。

## 当前实现

- Electron 启动本地页面，而不是直接把窗口指向远端登录页
- 本地 `/api` 代理会转发到可配置的云端或本地 backend
- 登录页与应用设置页不再暴露桌面端后端配置
- 顶部菜单提供单独的“配置代理地址”入口；配置后 `/api` 请求优先转发到代理地址
- 通过 preload bridge 暴露基础桌面能力：目录选择、文件读写、打开路径、系统信息

## 架构说明

- `dev:ui`：Electron 本地代理前端 Vite dev server，并把 `/api` 转发给 `MATECLAW_BACKEND_URL`
- `mateclaw-ui dev:desktop`：Vite 开发服务把 `/api` 转发到 Electron 本地代理，避免绕过桌面端代理配置
- `pack/dist`：Electron 加载本地 `renderer-dist/`，并把 `/api` 转发给已保存的 backend 地址

- 修改地址： D:\project\ai\mateclaw-dev\mateclaw-desktop\electron\desktopConfig.cjs
默认行为：

- 开发模式默认连接：`http://127.0.0.1:18088`
- 打包模式默认连接：`https://claw-demo.mate.vip`
- 默认包只包含 Electron 客户端，不内置 JRE / 本地后端 JAR；如需联调代理链路，请在顶部菜单里配置代理地址

## 命令

```powershell
cd mateclaw-desktop
pnpm install
pnpm dev
```

如果你要连接 Vite 前端开发服务：

```powershell
cd mateclaw-ui
pnpm dev:desktop

cd ../mateclaw-desktop
pnpm dev:ui
```

打包桌面客户端：

```powershell
cd mateclaw-desktop
pnpm dist
```

输出目录：`mateclaw-desktop/release/`

## 说明

当前仓库里的 `mateclaw-desktop` 仍是本地测试壳，但现在已经补齐了本地页面加载、代理转发配置和基础桌面桥接能力；默认分发形态也调整为“仅客户端壳 + 可切换远端/代理服务”，更适合继续联调与验证。
