# Meta Y 部署与升级文档

本文档说明新服务器如何部署 Meta Y、如何修改运行配置、如何使用离线镜像包安装，以及后续如何只替换后端 jar / 前端静态资源完成升级。

## 1. 部署模式

当前推荐两种部署模式：

| 模式 | 适用场景 | 特点 |
| --- | --- | --- |
| Runtime 免构建部署 | 测试服务器、客户现场、内网环境、发布前验证 | 服务器只运行容器，不在服务器上构建项目；后续替换 `app.jar` 和 `static/` 后重启即可。 |
| Build Compose 部署 | 开发/集成环境，服务器能访问 Docker Hub、Maven、npm | 每次更新源码后在服务器上 `up -d --build` 重新构建镜像。 |

后续交付优先使用 Runtime 免构建部署。

## 2. 服务器要求

- Linux x86_64，建议 Ubuntu 22.04/24.04 或 CentOS/Rocky/AlmaLinux 8+。
- Docker Engine 已安装。
- Docker Compose 可用，支持 `docker-compose` 或 `docker compose` 均可。
- 推荐配置：4C / 8GB 起步；如果并发较高或启用复杂文件处理，建议 8C / 16GB。
- 默认开放端口：`18080/tcp`。
- 如果使用域名和 HTTPS，建议用 Nginx 反向代理到 `127.0.0.1:18080`。

检查环境：

```bash
docker version
docker-compose version
```

如果系统使用新版 Compose，也可以检查：

```bash
docker compose version
```

## 3. 目录规划

推荐服务器目录：

```text
/opt/yliyunclaw/
  docker/
    .env
    docker-compose.runtime.yml
    Dockerfile.server-runtime
    load-offline-images.sh
    export-offline-images.sh
    runtime/
      app/
        app.jar
      static/
        index.html
        assets/
  backups/
  packages/
```

如果当前项目仍放在 `/opt/yliyunclaw`，也可以继续使用该目录。Compose 内部服务名已经使用 `yliyunclaw-*`，不会影响运行。

## 4. 新服务器离线部署

适用于客户服务器不能访问外网或不希望现场拉取镜像。

### 4.1 准备交付文件

需要准备以下文件：

```text
yliyunclaw-runtime-images.tar
yliyunclaw-runtime-images.tar.sha256
docker-compose.runtime.yml
Dockerfile.server-runtime
load-offline-images.sh
init-server.sh
.env.production.example
runtime/app/app.jar
runtime/static/
```

当前测试服务器已生成离线镜像包：

```text
/opt/yliyunclaw-offline/yliyunclaw-runtime-images.tar
/opt/yliyunclaw-offline/yliyunclaw-runtime-images.tar.sha256
```

### 4.2 上传到新服务器

示例：

```bash
mkdir -p /opt/yliyunclaw/docker
cd /opt/yliyunclaw/docker
```

将交付文件上传到该目录，最终结构至少包含：

```text
/opt/yliyunclaw/docker/docker-compose.runtime.yml
/opt/yliyunclaw/docker/load-offline-images.sh
/opt/yliyunclaw/docker/.env.production.example
/opt/yliyunclaw/docker/runtime/app/app.jar
/opt/yliyunclaw/docker/runtime/static/
/opt/yliyunclaw-offline/yliyunclaw-runtime-images.tar
```

### 4.3 校验离线包

```bash
cd /opt/yliyunclaw-offline
sha256sum -c yliyunclaw-runtime-images.tar.sha256
```

如果输出 `OK`，说明离线包没有损坏。

### 4.4 导入镜像

```bash
cd /opt/yliyunclaw/docker
chmod +x load-offline-images.sh
./load-offline-images.sh /opt/yliyunclaw-offline/yliyunclaw-runtime-images.tar
```

确认镜像存在：

```bash
docker images | grep -E 'yliyunclaw-server-runtime|mysql'
```

### 4.5 初始化配置

复制配置模板：

```bash
cd /opt/yliyunclaw/docker
cp .env.production.example .env
chmod 600 .env
```

编辑 `.env`：

```bash
vi .env
```

最少必须修改：

```env
MATECLAW_PUBLIC_URL=http://服务器IP:18080
MATECLAW_CORS_ALLOWED_ORIGINS=http://服务器IP:18080

DB_PASSWORD=强密码
DB_ROOT_PASSWORD=强密码
JWT_SECRET=长度足够的随机密钥
```

如果有域名：

```env
MATECLAW_PUBLIC_URL=https://metay.example.com
MATECLAW_CORS_ALLOWED_ORIGINS=https://metay.example.com
```

说明：`MATECLAW_*` 是当前后端兼容环境变量名，属于技术配置名，不影响产品显示为 Meta Y。

### 4.6 启动服务

如果服务器使用旧版 Compose：

```bash
cd /opt/yliyunclaw/docker
docker-compose --env-file .env -f docker-compose.runtime.yml up -d
```

如果服务器使用新版 Compose：

```bash
cd /opt/yliyunclaw/docker
docker compose --env-file .env -f docker-compose.runtime.yml up -d
```

查看状态：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml ps
```

健康检查：

```bash
curl http://127.0.0.1:18080/actuator/health
```

正常返回：

```json
{"status":"UP"}
```

访问：

```text
http://服务器IP:18080
```

## 5. 在线部署

适用于服务器可以访问镜像仓库，或者已经有 `mysql:8.0` 与 `eclipse-temurin:21-jre`。

### 5.1 上传运行文件

上传：

```text
docker/docker-compose.runtime.yml
docker/Dockerfile.server-runtime
docker/.env.production.example
docker/runtime/app/app.jar
docker/runtime/static/
```

### 5.2 构建 runtime 镜像

```bash
cd /opt/yliyunclaw/docker
docker build -f Dockerfile.server-runtime -t yliyunclaw-server-runtime:local .
```

如果服务器拉镜像慢，可以提前导入或拉取：

```bash
docker pull eclipse-temurin:21-jre
docker pull mysql:8.0
```

然后按第 4.5、4.6 节初始化并启动。

## 6. 配置说明

配置文件位置：

```text
/opt/yliyunclaw/docker/.env
```

常用配置：

| 配置项 | 说明 |
| --- | --- |
| `MATECLAW_PUBLIC_URL` | 对外访问地址，用于客户端、回调、邀请链接等场景。 |
| `MATECLAW_HTTP_PORT` | 宿主机暴露端口，默认 `18080`。 |
| `MATECLAW_CORS_ALLOWED_ORIGINS` | 允许访问后端的前端来源。内网测试可填 `http://IP:18080`。 |
| `DB_NAME` | 数据库名，默认 `mateclaw`。不建议随意修改。 |
| `DB_USERNAME` | 数据库用户，默认 `mateclaw`。 |
| `DB_PASSWORD` | 数据库用户密码，必须修改。 |
| `DB_ROOT_PASSWORD` | MySQL root 密码，必须修改。 |
| `JWT_SECRET` | 登录 Token 签名密钥，必须修改，且保持稳定。修改后所有用户需要重新登录。 |
| `JWT_EXPIRATION` | Token 有效期，默认 30 天。 |
| `JWT_RENEWAL_THRESHOLD` | Token 自动续期阈值，默认 7 天。 |
| `DASHSCOPE_API_KEY` | 可选。推荐在管理后台配置模型供应商，`.env` 中可留空或占位。 |
| `SERPER_API_KEY` | 可选。搜索能力所需 Key。 |
| `SEARXNG_BASE_URL` | Runtime 模式默认不启用 SearXNG，可留空。 |
| `JAVA_TOOL_OPTIONS` | JVM 参数，默认 `-Xms512m -Xmx2g`。 |

修改 `.env` 后重启：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml up -d
```

只重启后端：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
```

## 7. 后端升级

后端升级只需要替换 jar。

### 7.1 备份当前 jar

```bash
cd /opt/yliyunclaw/docker
mkdir -p ../backups
cp runtime/app/app.jar ../backups/app-$(date +%Y%m%d-%H%M%S).jar
```

### 7.2 上传新 jar

将新 jar 上传为：

```text
/opt/yliyunclaw/docker/runtime/app/app.jar
```

确保文件名固定为 `app.jar`。

### 7.3 重启后端

```bash
cd /opt/yliyunclaw/docker
docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
```

### 7.4 验证

```bash
curl http://127.0.0.1:18080/actuator/health
docker-compose --env-file .env -f docker-compose.runtime.yml logs -f --tail=200 yliyunclaw-server
```

服务启动时会自动执行 Flyway 数据库迁移。升级前建议先备份数据库。

## 8. 前端升级

前端升级只需要替换静态资源目录。

### 8.1 备份当前 static

```bash
cd /opt/yliyunclaw/docker
tar -czf ../backups/static-$(date +%Y%m%d-%H%M%S).tar.gz runtime/static
```

### 8.2 上传新 static

建议先上传到临时目录：

```text
/opt/yliyunclaw/packages/static-new/
```

确认里面包含：

```text
index.html
assets/
```

替换：

```bash
cd /opt/yliyunclaw/docker
rm -rf runtime/static/*
cp -a /opt/yliyunclaw/packages/static-new/. runtime/static/
```

### 8.3 重启或直接刷新

Spring 会从 `/app/static` 读取静态资源。为了避免缓存和文件句柄问题，建议重启后端：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
```

浏览器端如果仍显示旧页面，清理浏览器缓存或强制刷新。

## 9. 同时升级后端和前端

推荐顺序：

```bash
cd /opt/yliyunclaw/docker
mkdir -p ../backups
cp runtime/app/app.jar ../backups/app-$(date +%Y%m%d-%H%M%S).jar
tar -czf ../backups/static-$(date +%Y%m%d-%H%M%S).tar.gz runtime/static

# 替换 app.jar
cp /opt/yliyunclaw/packages/app.jar runtime/app/app.jar

# 替换 static
rm -rf runtime/static/*
cp -a /opt/yliyunclaw/packages/static/. runtime/static/

docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
curl http://127.0.0.1:18080/actuator/health
```

## 9.1 本地打包，手动上传更新

推荐日常发布使用 `docker/package-release.ps1`。该脚本只在本机生成发布产物，不连接服务器、不上传、不重启。

### 9.1.1 一键打包全部

包含：

- 后端 `app.jar`
- 前端 `static.tar.gz`
- 桌面客户端 `MetaY-Desktop.exe`
- 服务器更新脚本 `server-update.sh`
- 操作说明 `README.txt`

正式客户端默认服务地址使用：

```text
http://meta.ylicloud.com:8080
```

执行命令：

```powershell
cd D:\project\ai\mateclaw-dev
.\docker\package-release.ps1 `
  -Component all `
  -BackendUrl "http://meta.ylicloud.com:8080" `
  -DesktopTarget dist:portable
```

输出目录示例：

```text
D:\project\ai\mateclaw-dev\.release\manual-YYYYMMDD-HHMMSS
```

### 9.1.2 单独打包

只打包后端：

```powershell
.\docker\package-release.ps1 -Component backend
```

只打包前端：

```powershell
.\docker\package-release.ps1 -Component frontend
```

只打包客户端，并写入正式服务地址：

```powershell
.\docker\package-release.ps1 `
  -Component app `
  -BackendUrl "http://meta.ylicloud.com:8080" `
  -DesktopTarget dist:portable
```

### 9.1.3 上传到服务器

在本机把整个输出目录上传到服务器：

```powershell
scp -r D:\project\ai\mateclaw-dev\.release\manual-YYYYMMDD-HHMMSS root@192.168.0.50:/opt/yliyunclaw/packages/
```

如果使用其他工具上传，保持目录结构不变即可。上传后的服务器目录应类似：

```text
/opt/yliyunclaw/packages/manual-YYYYMMDD-HHMMSS/app.jar
/opt/yliyunclaw/packages/manual-YYYYMMDD-HHMMSS/static.tar.gz
/opt/yliyunclaw/packages/manual-YYYYMMDD-HHMMSS/MetaY-Desktop.exe
/opt/yliyunclaw/packages/manual-YYYYMMDD-HHMMSS/server-update.sh
```

### 9.1.4 服务器执行更新并重启

登录服务器：

```bash
ssh root@192.168.0.50
```

执行更新：

```bash
cd /opt/yliyunclaw/packages/manual-YYYYMMDD-HHMMSS
bash server-update.sh /opt/yliyunclaw/docker
```

当前 `192.168.0.50` 测试环境的真实 compose 运行目录是 `/opt/yliyunclaw/docker`。

脚本会自动：

1. 备份旧的 `runtime/app/app.jar`。
2. 备份旧的 `runtime/static`。
3. 替换后端 jar。
4. 替换前端静态资源，保留 `runtime/static/downloads` 目录。
5. 替换客户端下载文件为 `runtime/static/downloads/MetaY-Desktop.exe`。
6. 重启 `yliyunclaw-server`。
7. 执行本机健康检查。

更新脚本会同时打印：

- `release-manifest.json`：本次发布包时间、Git commit、文件 hash。
- `runtime/app/app.jar` 文件大小和时间。
- `runtime/static/index.html` 和 `runtime/static/release-manifest.json` 文件状态。
- `yliyunclaw-server` 容器状态。
- 后端健康检查结果。

客户端发布后下载地址：

```text
http://meta.ylicloud.com:8080/downloads/MetaY-Desktop.exe
```

### 9.1.5 常用参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `-Component` | `all` | `all` / `backend` / `frontend` / `app`。 |
| `-BackendUrl` | `http://meta.ylicloud.com:8080` | 写入桌面客户端的默认服务地址。 |
| `-DesktopTarget` | `dist:portable` | Electron 打包目标。常用 `dist:portable`、`dist:nsis`、`dist`。 |
| `-OutputDir` | `.release\manual-时间戳` | 本地发布产物输出目录。 |
| `-RemoteDockerDir` | `/opt/yliyunclaw/docker` | 生成到 `README.txt` 和 `server-update.sh` 的默认服务器运行目录。 |
| `-SkipBuild` | false | 复用已有构建产物时使用。 |
| `-MavenCommand` | 自动检测 | 指定 Maven 命令路径。 |
| `-MavenSettings` | 自动检测 `mateclaw-server/settings.xml` | 指定 Maven settings 文件。 |

### 9.1.6 更新后仍看到旧页面的排查

优先确认访问的域名是否真的打到当前服务器：

```bash
curl -I http://meta.ylicloud.com:8080/
curl -fsS http://meta.ylicloud.com:8080/release-manifest.json
curl -fsS http://127.0.0.1:18080/release-manifest.json
```

如果域名结果和服务器本机 `127.0.0.1:18080` 不一致，说明域名、端口映射或反向代理指向的不是当前运行目录。

确认服务器运行目录是否已替换：

```bash
cd /opt/yliyunclaw/docker
ls -lh runtime/app/app.jar
ls -lh runtime/static/index.html runtime/static/release-manifest.json
cat runtime/static/release-manifest.json
```

确认容器是否真的重启并挂载了这些文件：

```bash
docker compose --env-file .env -f docker-compose.runtime.yml ps
docker compose --env-file .env -f docker-compose.runtime.yml logs --tail=120 yliyunclaw-server
docker exec yliyunclaw-server ls -lh /app/app.jar /app/static/index.html /app/static/release-manifest.json
```

如果服务器文件已经是新的，但浏览器仍显示旧页面：

```bash
curl -I "http://meta.ylicloud.com:8080/index.html?ts=$(date +%s)"
curl -fsS "http://meta.ylicloud.com:8080/release-manifest.json?ts=$(date +%s)"
```

浏览器端执行强制刷新，或清理站点缓存后再访问。若前面经过 Nginx/CDN/网关，还需要清理代理缓存。

## 9.2 一键发布到测试环境

如需恢复自动上传发布，可使用 `docker/publish-test.ps1`。当前建议优先使用第 9.1 节的手动上传方式。

本地项目已提供测试环境发布脚本：

```text
docker/publish-test.ps1
```

默认测试环境：

```text
服务器：192.168.0.50
部署目录：/opt/yliyunclaw/docker
访问地址：http://192.168.0.50:18080
```

脚本使用本机 OpenSSH 的 `ssh` / `scp` 上传文件并在服务器执行替换和重启。建议为测试服务器配置 SSH key 免密；如果仍使用密码登录，需要在交互式终端按提示输入密码。

### 9.2.1 一键打包并发布全部

包含：

- 后端 jar
- 前端 static
- 桌面客户端下载包
- 重启后端容器
- 健康检查

```powershell
cd D:\project\ai\mateclaw-dev
.\docker\publish-test.ps1 -Component all -BackendUrl "http://192.168.0.50:18080"
```

如果本机尚未配置 SSH key，可临时使用密码模式：

```powershell
.\docker\publish-test.ps1 `
  -Component all `
  -ServerPassword "服务器密码" `
  -BackendUrl "http://meta.ylicloud.com:8080"
```

密码模式依赖本机 PowerShell 的 `Posh-SSH` 模块；长期发布建议配置 SSH key，避免在命令历史中留下明文密码。

### 9.2.2 只发布后端

```powershell
.\docker\publish-test.ps1 -Component backend
```

脚本会：

1. 执行 Maven package。
2. 上传为 `/opt/yliyunclaw/packages/app-时间戳.jar`。
3. 备份当前 `runtime/app/app.jar`。
4. 替换 `runtime/app/app.jar`。
5. 重启 `yliyunclaw-server`。

### 9.2.3 只发布前端

```powershell
.\docker\publish-test.ps1 -Component frontend
```

脚本会：

1. 执行 Vite build。
2. 打包静态资源为 `static-时间戳.tar.gz`。
3. 上传到 `/opt/yliyunclaw/packages/`。
4. 备份并替换 `runtime/static/`。
5. 重启 `yliyunclaw-server`。

### 9.2.4 只打包并发布客户端

```powershell
.\docker\publish-test.ps1 -Component app -BackendUrl "http://192.168.0.50:18080" -DesktopTarget dist:portable
```

`-BackendUrl` 会写入客户端默认服务地址。客户端首次启动时会默认连接该地址；用户仍可在登录页桌面配置入口修改。

发布后下载地址：

```text
http://192.168.0.50:18080/downloads/MetaY-Desktop.exe
```

常用客户端打包目标：

| 参数 | 说明 |
| --- | --- |
| `dist:portable` | 只生成免安装版，默认推荐用于内网测试。 |
| `dist:nsis` | 只生成安装包。 |
| `dist` | 同时生成安装包和免安装版。 |
| `pack` | 只打包目录，不生成安装器。 |

### 9.2.5 常用参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `-Component` | `all` | `all` / `backend` / `frontend` / `app`。 |
| `-ServerHost` | `192.168.0.50` | 测试服务器 IP。 |
| `-ServerUser` | `root` | SSH 用户。 |
| `-SshPort` | `22` | SSH 端口。 |
| `-RemoteDockerDir` | `/opt/yliyunclaw/docker` | 服务器 runtime compose 目录。 |
| `-BackendUrl` | `http://192.168.0.50:18080` | 客户端默认连接地址，也是健康检查地址。 |
| `-ServerPassword` | 空 | 可选。配置后使用 Posh-SSH 密码模式上传和执行远程命令；为空时使用系统 SSH/SCP。 |
| `-DesktopTarget` | `dist:portable` | Electron 打包目标。 |
| `-SkipBuild` | false | 复用已有 jar/static/exe，仅执行上传替换。 |
| `-NoRestart` | false | 替换后不重启后端。 |
| `-SkipHealthCheck` | false | 跳过健康检查。 |

示例：发布到其他服务器或域名：

```powershell
.\docker\publish-test.ps1 `
  -Component all `
  -ServerHost "10.0.0.12" `
  -RemoteDockerDir "/opt/yliyunclaw/docker" `
  -BackendUrl "https://metay.example.com"
```

## 10. 数据库备份与恢复

### 10.1 备份

```bash
cd /opt/yliyunclaw/docker
source .env
mkdir -p ../backups
docker exec yliyunclaw-mysql mysqldump -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" > ../backups/db-$(date +%Y%m%d-%H%M%S).sql
```

### 10.2 恢复

恢复前先停止后端，避免写入：

```bash
cd /opt/yliyunclaw/docker
docker-compose --env-file .env -f docker-compose.runtime.yml stop yliyunclaw-server
source .env
docker exec -i yliyunclaw-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" < ../backups/db-YYYYMMDD-HHMMSS.sql
docker-compose --env-file .env -f docker-compose.runtime.yml start yliyunclaw-server
```

注意：数据库迁移通常不可自动降级。回滚版本时，应恢复对应版本升级前的数据库备份。

## 11. 回滚

### 11.1 回滚后端 jar

```bash
cd /opt/yliyunclaw/docker
cp ../backups/app-YYYYMMDD-HHMMSS.jar runtime/app/app.jar
docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
```

### 11.2 回滚前端 static

```bash
cd /opt/yliyunclaw/docker
rm -rf runtime/static/*
tar -xzf ../backups/static-YYYYMMDD-HHMMSS.tar.gz -C /tmp/yliyunclaw-static-rollback
cp -a /tmp/yliyunclaw-static-rollback/runtime/static/. runtime/static/
docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
```

### 11.3 回滚数据库

按第 10.2 节恢复升级前数据库备份。

## 12. 客户端更新

桌面客户端是 Electron 包，客户机器运行后连接服务器。

### 12.1 重新打包客户端

在开发/打包机器执行：

```powershell
cd D:\project\ai\mateclaw-dev
.\docker\package-client.ps1 -BackendUrl "https://metay.example.com" -Target dist
```

如果要直接发布到测试服务器，可以使用：

```powershell
.\docker\publish-test.ps1 -Component app -BackendUrl "http://192.168.0.50:18080"
```

输出目录：

```text
mateclaw-desktop\release
```

### 12.2 客户端连接地址

打包时会把默认后端地址写入桌面端默认配置。客户也可以在登录页的桌面配置入口修改后端地址。

桌面端配置保存位置：

```text
~/.metay-desktop/config.json
```

## 13. 生成新的离线镜像包

在已有 Docker 环境的机器上：

```bash
cd /opt/yliyunclaw/docker
chmod +x export-offline-images.sh
./export-offline-images.sh /opt/yliyunclaw-offline
```

输出：

```text
/opt/yliyunclaw-offline/yliyunclaw-runtime-images.tar
/opt/yliyunclaw-offline/yliyunclaw-runtime-images.tar.sha256
```

离线包包含：

- `yliyunclaw-server-runtime:local`
- `mysql:8.0`

注意：离线镜像包不包含业务 jar 和前端 static。它们通过宿主机目录挂载，单独交付和替换。

## 14. 常用运维命令

查看容器：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml ps
```

查看后端日志：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml logs -f --tail=200 yliyunclaw-server
```

重启后端：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server
```

重启全部服务：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml restart
```

停止服务：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml down
```

停止并删除数据卷，慎用：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml down -v
```

## 15. Nginx HTTPS 示例

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

启用 HTTPS 后，`.env` 推荐：

```env
MATECLAW_PUBLIC_URL=https://metay.example.com
MATECLAW_CORS_ALLOWED_ORIGINS=https://metay.example.com
```

然后重启：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml up -d
```

## 16. 常见问题

### 16.1 `actuator/health` 不是 UP

查看日志：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml logs -f --tail=300 yliyunclaw-server
```

重点检查：

- 数据库密码是否和 MySQL 容器一致。
- `JWT_SECRET` 是否为空。
- `runtime/app/app.jar` 是否存在。
- `runtime/static/index.html` 是否存在。
- 端口 `18080` 是否被占用。

### 16.2 页面 404 或空白

检查前端静态资源：

```bash
ls -lah runtime/static
ls -lah runtime/static/assets
```

确认 `docker-compose.runtime.yml` 中有：

```yaml
SPRING_WEB_RESOURCES_STATIC_LOCATIONS: file:/app/static/,classpath:/static/
```

然后重启后端。

### 16.3 替换 jar 后容器反复重启

检查 jar 是否完整：

```bash
ls -lh runtime/app/app.jar
```

查看启动错误：

```bash
docker-compose --env-file .env -f docker-compose.runtime.yml logs --tail=300 yliyunclaw-server
```

如果是数据库迁移失败，不要反复重启，先恢复数据库备份或修复迁移问题。

### 16.4 离线导入镜像后仍提示找不到镜像

确认镜像名：

```bash
docker images | grep yliyunclaw-server-runtime
```

`docker-compose.runtime.yml` 默认使用：

```env
YLIYUNCLAW_SERVER_IMAGE=yliyunclaw-server-runtime:local
```

如果镜像名不同，可以在 `.env` 增加：

```env
YLIYUNCLAW_SERVER_IMAGE=你的镜像名:标签
```

## 17. 发布检查清单

部署或升级完成后检查：

- `docker-compose --env-file .env -f docker-compose.runtime.yml ps` 中 MySQL healthy，Server running。
- `curl http://127.0.0.1:18080/actuator/health` 返回 `UP`。
- 浏览器能打开首页。
- 管理员能登录。
- 普通用户权限和菜单符合预期。
- 内置智能体、知识库、Skills 能正常显示。
- Chat 能创建新会话并收到模型回复。
- 备份目录已生成本次升级前的 jar、static、数据库备份。
