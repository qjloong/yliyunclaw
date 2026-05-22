# Meta Y 服务端容器部署与客户端打包

本文档面向“后台部署到服务器，前端客户端打包给客户使用”的交付方式。

推荐结构：

- 服务器：`MySQL 8` + `SearXNG` + `Meta Y Server`，由 Docker Compose 管理。
- 客户端：Electron 桌面客户端，安装在客户电脑上，通过公网/内网地址连接服务器。
- 管理员：首次登录后在管理界面配置模型供应商、API Key、内置 Agent、知识库等业务数据。

## 1. 服务器要求

- Linux x86_64，建议 Ubuntu 22.04/24.04。
- Docker Engine 24+，Docker Compose v2。
- 至少 4C / 8GB 内存；如果启用浏览器自动化、PDF/OCR、较多并发，建议 8C / 16GB。
- 开放端口：默认 `18080/tcp`。如果使用 Nginx/HTTPS，只对外开放 `443`，内部反代到 `127.0.0.1:18080`。
- 构建镜像需要访问 npm、Maven、Docker Hub / Microsoft Container Registry。国内环境建议在 `.env` 打开 `MAVEN_FLAGS=-Paliyun-first`，并配置 Docker 镜像加速。

## 2. 初始化部署配置

把项目上传到服务器后进入仓库目录：

```bash
cd /opt/yliyunclaw/docker
chmod +x init-server.sh
./init-server.sh https://metay.example.com
```

如果暂时没有域名，也可以使用服务器 IP：

```bash
./init-server.sh http://203.0.113.10:18080
```

脚本会创建 `docker/.env`，并自动生成：

- `DB_PASSWORD`
- `DB_ROOT_PASSWORD`
- `JWT_SECRET`
- `SEARXNG_SECRET`
- `MATECLAW_PUBLIC_URL`
- `MATECLAW_CORS_ALLOWED_ORIGINS`

Windows 管理机也可以执行：

```powershell
cd D:\project\ai\mateclaw-dev\docker
.\init-server.ps1 -PublicUrl "https://metay.example.com"
```

## 3. 启动服务

```bash
cd /opt/yliyunclaw/docker
docker compose --env-file .env -f docker-compose.server.yml up -d --build
```

查看日志：

```bash
docker compose --env-file .env -f docker-compose.server.yml logs -f yliyunclaw-server
```

查看服务状态：

```bash
docker compose --env-file .env -f docker-compose.server.yml ps
```

健康检查：

```bash
curl http://127.0.0.1:18080/actuator/health
```

首次启动会自动执行 Flyway 迁移并初始化数据库表。不要手动挂载旧版 `schema.sql` 到 MySQL 初始化目录，避免和迁移冲突。

## 4. HTTPS / Nginx 反向代理

生产环境建议使用 HTTPS。Nginx 示例：

```nginx
server {
    listen 80;
    server_name metay.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name metay.example.com;

    ssl_certificate /etc/letsencrypt/live/metay.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/metay.example.com/privkey.pem;

    client_max_body_size 200m;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;
    }
}
```

使用 HTTPS 后，确认 `docker/.env`：

```env
MATECLAW_PUBLIC_URL=https://metay.example.com
MATECLAW_CORS_ALLOWED_ORIGINS=https://metay.example.com
```

然后重启：

```bash
docker compose --env-file .env -f docker-compose.server.yml up -d
```

## 5. 管理员初始化

服务启动后访问：

```text
https://metay.example.com
```

建议首次进入后完成：

1. 登录/创建管理员账号，按当前项目已有认证逻辑操作。
2. 在管理界面配置模型供应商和模型。
3. 配置系统默认 Workspace、内置 Agent 模板、知识库、Skills/Tools。
4. 创建普通用户或通过工作区邀请链接邀请成员。
5. 普通用户只分配到对应 Workspace，避免授予系统配置权限。

## 6. 客户端打包

当前项目有两类前端：

- Web 前端：已随 Meta Y Server Docker 镜像构建并由后端服务直接提供。
- 桌面客户端：`mateclaw-desktop` Electron 包，适合发给客户安装。

### 6.1 使用客户服务器地址打包

在开发/打包机器上执行：

```powershell
cd D:\project\ai\mateclaw-dev
.\docker\package-client.ps1 -BackendUrl "https://metay.example.com" -Target dist
```

输出目录：

```text
mateclaw-desktop\release
```

可选目标：

- `dist`：同时构建 NSIS 安装包和 portable 包。
- `dist:nsis`：只构建安装包。
- `dist:portable`：只构建免安装包。
- `pack`：只打目录包，便于本地验证。

脚本会在打包前临时把桌面端“打包模式默认后端地址”改成你的 `BackendUrl`，打包结束后自动恢复源码文件。

### 6.2 客户端首次运行

客户打开桌面端后：

- 默认连接打包时注入的服务器地址。
- 如果客户环境变更，可在登录页/桌面配置入口修改后端地址。
- 配置文件保存到客户电脑用户目录：`~/.metay-desktop/config.json`；旧版 `~/.yliyunclaw-desktop/config.json` 会在首次启动时自动迁移。

## 7. 免构建运行时部署

如果后续不希望每次都在服务器上构建镜像，使用 `docker-compose.runtime.yml`。它只依赖已加载的基础运行镜像，并把后端 jar 和前端静态资源映射到宿主机：

```text
docker/runtime/app/app.jar
docker/runtime/static/
```

首次准备运行时镜像和离线包：

```bash
cd /opt/yliyunclaw/docker
chmod +x export-offline-images.sh load-offline-images.sh
./export-offline-images.sh /opt/yliyunclaw-offline
```

离线安装到新服务器时：

```bash
cd /opt/yliyunclaw/docker
./load-offline-images.sh /opt/yliyunclaw-offline/yliyunclaw-runtime-images.tar
docker compose --env-file .env -f docker-compose.runtime.yml up -d
```

以后升级时只需要替换：

```text
docker/runtime/app/app.jar
docker/runtime/static/
```

然后重启后端：

```bash
docker compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
```

Windows 打包机可以用：

```powershell
.\docker\package-runtime.ps1
```

如果 jar 和前端静态文件由 CI 产出，也可以直接传入路径：

```powershell
.\docker\package-runtime.ps1 -JarPath D:\build\app.jar -StaticPath D:\build\static
```

## 8. 升级与回滚

升级：

```bash
cd /opt/yliyunclaw
git pull
cd docker
docker compose --env-file .env -f docker-compose.server.yml up -d --build
```

数据库由 Flyway 自动迁移。升级前建议备份：

```bash
docker exec yliyunclaw-mysql mysqldump -uroot -p"$DB_ROOT_PASSWORD" mateclaw > yliyunclaw-backup.sql
```

回滚代码版本后重新构建即可，但数据库迁移通常不可自动降级。正式环境回滚前应先恢复对应备份。

## 9. 数据与备份

Compose 使用命名卷：

- `yliyunclaw_mysql_data`：MySQL 数据。
- `yliyunclaw_server_data`：服务端运行数据。
- `yliyunclaw_server_logs`：服务端日志。

建议每日备份 MySQL，并保留至少 7 到 30 天。

## 10. 常用运维命令

```bash
# 重启后端
docker compose --env-file .env -f docker-compose.server.yml restart yliyunclaw-server

# 查看后端日志
docker compose --env-file .env -f docker-compose.server.yml logs -f --tail=200 yliyunclaw-server

# 停止全部服务
docker compose --env-file .env -f docker-compose.server.yml down

# 停止并删除数据卷，慎用
docker compose --env-file .env -f docker-compose.server.yml down -v
```

## 11. 注意事项

- 生产环境必须修改 `JWT_SECRET`、数据库密码、`SEARXNG_SECRET`。
- 不建议对公网暴露 MySQL 端口；当前生产 Compose 只暴露 Meta Y HTTP 端口。
- 普通模型 API Key 推荐登录后在管理界面配置，不建议写入 `.env` 并随包分发。
- 如果客户只使用桌面端，服务器仍然建议启用 HTTPS，避免 Token 在网络中明文传输。
- 如果需要多租户/多客户部署，建议每个客户独立一套 Compose、独立数据库、独立域名和独立客户端包。
