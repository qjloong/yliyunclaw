# upstream/dev → dev-v2 完整合并执行方案

> 2026-08-03 实际执行状态：本轮同步已完成，`dev-v2` 已快进到合并提交
> `fa6920c1`。合并前本地提交为 `dbb62646`，上游目标为 `bfd84fd5`，共同基点为
> `e294b325`。当前仓库的业务远程名是 `dev`，官方远程名是 `upstream`；官方仓库为
> `matevip/mateclaw`，不是旧文档中的 `mateaix/mateclaw`。
>
> 本轮合并后的运行故障也已纳入本文检查项：上游发布版把 Maven revision 从
> `2.0.0-SNAPSHOT` 改为 `2.0.0`，硬编码旧 JAR 名会误启动缓存产物；此外，编译通过后仍发现
> 一粒云 provision 链路的 Spring Bean 环，以及上游新增测试未同步本地
> `ModelProviderService` 构造参数。以后必须同时通过“测试源码编译 + 可执行 JAR 重建 +
> Spring 健康启动”，不能只以 Git 无冲突或 `mvn package -DskipTests` 作为完成标准。
> 前端也必须执行真实的 `pnpm build`：本轮发现构建脚本引用了不存在的 Shell 校验文件、
> Windows `bash` 误走 WSL、定制组件依赖的类型/桌面桥接文件漏合，以及 dev/build 并发改写
> `components.d.ts`。这些问题不会由 Vite dev 热更新完整暴露。
>
> 下文带 `20260803` 的分支名和 SHA 是本次执行记录，不是每次同步都可原样复制的常量。
> 当前 `backup/dev-v2-before-upstream-20260803` 与 `sync/upstream-dev-20260803` 已存在；后续同步应先
> `git fetch`，重新计算 merge-base，并使用新的日期/批次名，不能重复创建或把旧 SHA 当成新基线。

## 一、合并目标

将官方上游：

```text
matevip/mateclaw:dev
```

的最新功能、缺陷修复和基础能力合并到本地开发分支：

```text
qjloong/yliyunclaw:dev-v2
```

本次合并遵循：

```text
本地 dev-v2 为业务主线
+ 上游新增能力增量吸收
+ 本地一粒云定制必须保留
+ 共享文件人工整合
+ 合并完成后再开始 Goal Agent 和 AI 微站编码
```

本次不处理：

```text
qjloong/yliyunclaw:dev
```

该分支属于旧快照和历史发布分支，不是本次需要同步的官方主线。

---

# 二、分支和远程定义

本仓库当前远程名称：

```text
dev       = qjloong/yliyunclaw
upstream  = matevip/mateclaw
```

检查：

```bash
git remote -v
```

预期至少包含：

```text
dev       https://github.com/qjloong/yliyunclaw.git
upstream  https://github.com/matevip/mateclaw.git
```

缺少官方上游时执行：

```bash
git remote add upstream https://github.com/matevip/mateclaw.git
```

本次基准关系：

```text
本地业务分支：dev-v2
官方主分支：upstream/dev
临时合并分支：sync/upstream-dev-20260803
安全备份分支：backup/dev-v2-before-upstream-20260803
```

---

# 三、总体合并原则

## 3.1 `ours` 和 `theirs` 的含义

在以下操作中：

```bash
git checkout dev-v2
git merge upstream/dev
```

Git 中：

```text
ours   = 当前本地 dev-v2
theirs = 官方 upstream/dev
```

因此：

```bash
git checkout --ours -- <file>
```

表示保留本地 `dev-v2` 文件。

```bash
git checkout --theirs -- <file>
```

表示采用官方 `upstream/dev` 文件。

禁止执行：

```bash
git checkout --theirs .
```

也禁止执行：

```bash
git checkout --ours .
```

因为两种整树操作都会造成大量功能丢失。

## 3.2 文件分类原则

所有变更分为四类：

| 类型                   | 处理方式     |
| -------------------- | -------- |
| 上游新增、dev-v2 不存在的独立文件 | 优先采用上游   |
| dev-v2 独有的一粒云业务文件    | 保留本地     |
| 双方都修改的共享核心文件         | 人工合并     |
| 构建产物、旧快照、运行时文件       | 不合并或删除跟踪 |

## 3.3 不在本次合并中夹带业务开发

本次只完成上游同步，不同时实现：

```text
Goal Agent
AI 微站
隐藏企业场景
隐藏内容日历
菜单重构
业务模块注册
新数据库领域表
```

这些应在上游合并完成、测试通过后，以独立提交实现。

这样才能区分：

```text
上游同步造成的问题
和
新业务代码造成的问题
```

---

# 四、阶段一：合并前检查

## 4.1 确认当前分支

```bash
git branch --show-current
```

应为：

```text
dev-v2
```

不是时执行：

```bash
git switch dev-v2
```

## 4.2 检查工作区

```bash
git status
```

要求：

```text
nothing to commit, working tree clean
```

如果存在未提交修改，不建议直接 `stash` 后合并，也不能用 `git add -A` 不经审查地把日志、
密钥、数据库或构建产物一起提交。先查看每个变更，再只暂存确认属于源码的路径：

```bash
git status --short
git diff
git add -- <reviewed-source-paths>
git diff --cached --name-status
git diff --cached --check
git commit -m "chore: snapshot dev-v2 before upstream sync"
```

不希望保留临时修改时，先人工备份后清理。

## 4.3 获取远程最新状态

```bash
git fetch dev --prune
git fetch upstream dev --prune
```

确认：

```bash
git log --oneline --decorate -n 5 dev-v2
git log --oneline --decorate -n 10 upstream/dev
```

## 4.4 确认共同基点

```bash
git merge-base dev-v2 upstream/dev
```

当前分析时共同基点为：

```text
e294b3254280ad38a7d83f2d502307ee1bebeda7
```

实际执行时以命令结果为准。

## 4.5 查看两边提交

查看上游新增提交：

```bash
git log --oneline --decorate dev-v2..upstream/dev
```

查看本地独有提交：

```bash
git log --oneline --decorate upstream/dev..dev-v2
```

查看统计：

```bash
git diff --stat dev-v2...upstream/dev
```

---

# 五、阶段二：建立安全备份

## 5.1 创建备份分支

```bash
git switch dev-v2

git branch backup/dev-v2-before-upstream-20260803
```

确认备份指向当前提交：

```bash
git rev-parse dev-v2
git rev-parse backup/dev-v2-before-upstream-20260803
```

两个 SHA 应一致。

## 5.2 可选：创建本地标签

```bash
git tag -a backup-dev-v2-20260803 \
  -m "Backup dev-v2 before upstream/dev merge"
```

## 5.3 创建临时同步分支

不要直接在 `dev-v2` 上第一次执行合并。

```bash
git switch -c sync/upstream-dev-20260803 dev-v2
```

确认：

```bash
git branch --show-current
```

应输出：

```text
sync/upstream-dev-20260803
```

这种方式的好处是，即使整个合并失败，正式 `dev-v2` 仍然保持不变。

---

# 六、阶段三：生成差异清单

建议使用 Git Bash 或 WSL 执行以下命令。

## 6.1 保存共同基点

```bash
BASE=$(git merge-base dev-v2 upstream/dev)
echo "$BASE"
```

## 6.2 本地修改文件

```bash
git diff --name-only "$BASE"..dev-v2 \
  | sort > /tmp/dev-v2-files.txt
```

## 6.3 上游修改文件

```bash
git diff --name-only "$BASE"..upstream/dev \
  | sort > /tmp/upstream-files.txt
```

## 6.4 双方共同修改文件

```bash
comm -12 /tmp/dev-v2-files.txt /tmp/upstream-files.txt \
  > /tmp/shared-files.txt

cat /tmp/shared-files.txt
```

这些文件是：

```text
即使 Git 自动合并成功
也必须人工检查的文件
```

## 6.5 仅上游修改文件

```bash
comm -13 /tmp/dev-v2-files.txt /tmp/upstream-files.txt \
  > /tmp/upstream-only-files.txt
```

## 6.6 仅本地修改文件

```bash
comm -23 /tmp/dev-v2-files.txt /tmp/upstream-files.txt \
  > /tmp/dev-v2-only-files.txt
```

---

# 七、阶段四：执行不提交合并

```bash
git merge --no-commit --no-ff upstream/dev
```

可能出现两种结果。

## 7.1 出现文本冲突

查看：

```bash
git status
git diff --name-only --diff-filter=U
```

## 7.2 没有文本冲突

即使没有出现冲突，也不要立即提交。

继续查看：

```bash
git status
git diff --cached --stat
git diff --cached
```

因为此前已经发生过：

```text
Git 自动合并成功
但本地 MetaY 定制代码被静默删除
```

---

# 八、阶段五：按文件类型处理

## 8.1 应优先采用上游的新增文件

以下内容原则上采用 `upstream/dev`。

### Agent Teams 独立模块

```text
mateclaw-server/src/main/java/vip/mate/team/controller/**
mateclaw-server/src/main/java/vip/mate/team/event/**
mateclaw-server/src/main/java/vip/mate/team/model/**
mateclaw-server/src/main/java/vip/mate/team/repository/**
mateclaw-server/src/main/java/vip/mate/team/service/**
mateclaw-server/src/main/java/vip/mate/team/tool/**
```

前端：

```text
mateclaw-ui/src/views/Teams.vue
mateclaw-ui/src/stores/useTeamStore.ts
mateclaw-ui/src/composables/useTeamEvents.ts
```

数据库：

```text
V172__agent_team_foundation.sql
V173__register_team_tasks_tool.sql
V174__team_task_event_timeline.sql
```

H2、MySQL、Kingbase 三套迁移都要保留。

Agent Teams 已经包含共享任务板、角色、任务依赖、分派、事件和 Plan-Execute 桥接，对 Goal Agent 后续 Worker 执行层有直接复用价值。

### Skill Workspace 隔离新增文件

```text
mateclaw-server/src/main/java/vip/mate/agent/context/AgentWorkspaceResolver.java
```

以及上游新增的 Skill Workspace 隔离测试。

该能力保证运行时 Skill 只能在当前会话 Workspace 内解析，避免读取其他 Workspace 的同名 Skill。

### Channel 去重新增文件

```text
ChannelDedupProperties.java
InboundMessageDeduplicator.java
```

以及相应测试。

该能力在消息进入 Agent 前完成一次性 Claim，避免企微、钉钉、飞书消息重投造成重复回复。

### Mem0 独立插件

```text
mateclaw-plugin-mem0/**
```

Mem0 属于可选插件，不作为 Goal/Website 事实源，但独立模块本身可以合并。

### 新增 DTO

```text
mateclaw-server/src/main/java/vip/mate/llm/model/ProviderOptionDTO.java
```

### 上游测试和产品文档

```text
mateclaw-server/src/test/java/vip/mate/team/**
mateclaw-server/src/test/java/vip/mate/channel/**
mateclaw-server/src/test/java/vip/mate/skill/**
mateclaw-server/src/main/resources/docs/en/teams.md
mateclaw-server/src/main/resources/docs/zh/teams.md
```

如果这些文件出现冲突，可使用：

```bash
git checkout --theirs -- <file>
git add <file>
```

前提是确认文件确实是上游新增、dev-v2 没有本地定制。

---

## 8.2 必须保留本地 dev-v2 的内容

以下内容以本地版本为主。

### 一粒云认证与附件

```text
vip/mate/auth/yliyun/**
YliyunAuthController
YliyunAuthCookieService
YliyunUserMappingService
YliyunAssistantProvisioningService
YliyunCloudAttachmentService
YliyunTicketReplayService
```

### 一粒云 MCP

```text
McpProxyController
McpIdentityForwardService 中的一粒云 OBO 逻辑
McpClientManager 中的一粒云连接逻辑
Yliyun MCP 数据库种子和修复迁移
```

### Workspace 模型隔离

```text
WorkspaceModelScope
WorkspaceModelProviderEntity
WorkspaceModelConfigEntity
WorkspaceModelProviderMapper
WorkspaceModelConfigMapper
V9019__workspace_model_provider_isolation.sql
```

### Teacher Agent

```text
vip/mate/teacher/**
teacher-rules/**
teacher-exam-assistant.json
```

### 本地 Agent SPI

```text
AgentContext
AgentPromptAugmenter
AgentExecutionInterceptor
TeacherAgentExtension
```

### 本地工具和导出能力

```text
FilterDirectoryMaterialsTool
ListDirectoryTool
UnderstandAnythingTool
HtmlRenderTool
DocxExportService
HtmlExportService
GeneratedDiskFileRegistry
GeneratedFileDiskTokenService
```

### 本地 Wiki 扩展

```text
vip/mate/wiki/classifier/**
vip/mate/wiki/dto/**
本地新增的 Wiki 业务派生、规则和材料处理服务
```

### 本地数据库迁移

全部保留；当前最高版本是 `V9020__yliyun_app_entitlement.sql`：

```text
V9001-V9020 中仓库实际存在的迁移文件
```

禁止：

```text
删除
重命名
重新编号
覆盖已发布迁移
```

### 本地前端定制

```text
CloudAgentEmbed.vue
CloudFilePicker.vue
SaveToCloudDialog.vue
ChatHome.vue
ChatInputWorkspaceBar.vue
TeacherOps.vue
TeacherExamResultBlock.vue
ProjectChangesPanel.vue
cloudContextPolicy.ts
一粒云 Logo 和品牌主题
```

如果本地独有文件出现误冲突，可使用：

```bash
git checkout --ours -- <file>
git add <file>
```

---

# 九、共享核心文件人工合并方案

以下文件禁止简单选择 `ours` 或 `theirs`。

---

## 9.1 `AgentGraphBuilder.java`

路径：

```text
mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java
```

### 必须保留本地能力

```text
AgentPromptAugmenter 注入和调用
Template 元数据字段
pluginKey
profileId
capabilityPackId
knowledgeBaseIdsJson
runtimeMode
WorkspaceModelScope
本地模型隔离
GoalService 现有基础逻辑
本地 Tool/Guard/MCP 扩展
```

### 必须吸收上游能力

```text
TeamContextBuilder
TeamPlanBridge
Agent Teams Plan-Execute 桥接
Workspace Skill 运行时解析
团队步骤依赖
团队任务完成后的恢复和汇总
```

### 合并结果要求

最终文件中同时存在：

```bash
git grep -n "AgentPromptAugmenter" -- \
  mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java

git grep -n "TeamPlanBridge" -- \
  mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java

git grep -n "WorkspaceModelScope" -- \
  mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java
```

当前 dev-v2 的 `AgentGraphBuilder` 已包含 Workspace 模型隔离、Goal 依赖和本地多项扩展，上游不能整文件覆盖。

---

## 9.2 `ToolExecutionExecutor.java`

### 保留本地

```text
本地 ToolExecutionContext
一粒云附件和 MCP 上下文
Tool Guard 定制
生成文件处理
现有审批和身份传播
```

### 吸收上游

```text
根据 ChatOrigin 解析 Workspace
Skill 自动重定向按 Workspace 隔离
Skill not found 提示按 Workspace 查询
load_skill/read_file 等结果不再错误截断
审批恢复路径的 Workspace 解析
```

### 特别检查

不能因为上游改造而丢失：

```text
workspaceId
userId
conversationId
agentId
一粒云身份 Header
MCP OBO 信息
```

---

## 9.3 `ChatController.java`

### 保留本地

```text
一粒云登录会话
云盘附件
云盘文件选择
附件预览
保存到云盘
Cloud Agent Embed
一粒云业务路由
AgentStreamAccumulator 本地扩展
```

### 吸收上游

```text
附件按 yyyy-MM-dd 目录写入
新旧附件目录兼容读取
storedName basename 校验
路径穿越保护
日期目录配额统计
会话文件定位兼容
重复生成和回退相关修复
```

上游附件目录变化为：

```text
{conversationDir}/yyyy-MM-dd/{storedName}
```

但 URL 仍保持原有形式。

### 处理原则

以本地 Controller 业务逻辑为骨架，将上游文件定位和日期目录 API 手工移植进来。

禁止整文件采用上游版本。

---

## 9.4 `ModelProviderService.java`

### 保留本地

```text
WorkspaceModelScope
WorkspaceModelProviderMapper
Workspace Provider Catalog
Workspace OAuth 凭据
Workspace Provider 启用状态
Workspace 模型隔离
```

dev-v2 已经实现 Workspace 级 Provider 处理，因此上游服务不能覆盖。

### 吸收上游

增加：

```java
public List<ProviderOptionDTO> listProviderOptions() {
    return listProviders().stream()
        .filter(p -> Boolean.TRUE.equals(p.getConfigured()))
        .map(p -> new ProviderOptionDTO(p.getId(), p.getName()))
        .toList();
}
```

这里的：

```java
listProviders()
```

必须继续运行在当前 Workspace Scope 中。

---

## 9.5 `ModelConfigController.java`

保留本地权限、Workspace 和模型接口。

手工增加：

```java
@GetMapping("/options")
@RequireWorkspaceRole("viewer")
public R<List<ProviderOptionDTO>> options() {
    return R.ok(modelProviderService.listProviderOptions());
}
```

上游新增接口只返回 Provider ID 和名称，不暴露 API Key、Base URL 和连接诊断。

---

## 9.6 `mateclaw-ui/src/api/index.ts`

### 保留本地

```text
Teacher API
Yliyun API
Cloud Attachment API
MCP API
Workspace Policy API
本地模型 API
本地导出 API
```

### 增加上游

```text
Team API
Team Task API
Team Event API
modelApi.listProviderOptions()
```

最终必须同时检查：

```bash
git grep -n "teacherRulePackApi" mateclaw-ui/src/api/index.ts
git grep -n "listProviderOptions" mateclaw-ui/src/api/index.ts
git grep -n "teamApi" mateclaw-ui/src/api/index.ts
```

---

## 9.7 `Agents.vue`

保留本地 Agent 编辑、模板字段、插件字段和知识库绑定。

将 Provider 加载改为：

```ts
modelApi.listProviderOptions()
```

不要恢复为：

```ts
modelApi.listProviders()
```

普通 Workspace 成员无权读取包含连接配置的完整 Provider 列表。

---

## 9.8 `router/index.ts`

### 保留本地

```text
CloudAgentEmbed
TeacherOps
一粒云相关路由
安全管理相关路由
已有 ChatConsole 路由
```

### 增加上游

```text
Teams
```

### 不在此次合并中处理

```text
隐藏企业场景
隐藏内容日历
Goal Agent 路由
Website Agent 路由
```

这些另开功能提交。

---

## 9.9 `MainLayout.vue`

### 保留本地

```text
MetaY / 一粒云品牌
Workspace 切换
本地导航结构
本地权限显示
Cloud Agent 入口
```

### 增加上游

```text
Agent Teams 菜单入口
对应权限和图标
```

不要直接采用上游 MainLayout，否则可能丢失品牌和业务菜单。

---

## 9.10 `application.yml`

保留本地：

```text
一粒云认证
MCP
OBO
云盘附件
Workspace 模型
本地上传目录
本地安全配置
```

增加上游：

```text
mate.channel.dedup.enabled
mate.channel.dedup.ttl
mate.channel.dedup.max-size
mateclaw.chat.upload.date-folders
Mem0 需要的可选配置
Agent Teams 配置
```

完成后检查重复配置键，避免 YAML 同一路径定义两次。

可使用：

```bash
grep -n "channel:" \
  mateclaw-server/src/main/resources/application.yml

grep -n "date-folders" \
  mateclaw-server/src/main/resources/application.yml
```

---

## 9.11 `pom.xml`

保留全部现有模块，增加：

```xml
<module>mateclaw-plugin-mem0</module>
```

后续 Goal 和 Website 模块不在本次增加。

检查：

```bash
mvn help:effective-pom -DskipTests
```

---

## 9.12 `mateclaw-server/Dockerfile`

保留本地 Docker 构建逻辑。

增加 Mem0 POM 复制，使 Maven Reactor 能识别新模块：

```dockerfile
COPY mateclaw-plugin-mem0/pom.xml mateclaw-plugin-mem0/pom.xml
```

是否将 Mem0 JAR 放入最终镜像，应按当前插件打包方式处理；Mem0 是可选插件，不应强制默认启用。

---

## 9.13 i18n 文件

共享文件：

```text
mateclaw-ui/src/i18n/locales/zh-CN.ts
mateclaw-ui/src/i18n/locales/en-US.ts
```

必须同时保留：

```text
本地 Teacher/Yliyun/Cloud 文案
上游 Teams 文案
上游模型权限文案
上游 Skill/Memory 文案
```

检查重复 Key。

前端构建通常能发现部分语法错误，但发现不了同名 Key 被后一个值覆盖的问题，因此需要人工搜索。

---

# 十、自动合并后语义检查

所有冲突解决完成后，先执行：

```bash
git status
git diff --name-only --diff-filter=U
```

第二条命令必须无输出。

## 10.1 检查本地定制是否仍存在

```bash
git grep -n "MetaY custom"
git grep -n "AgentPromptAugmenter"
git grep -n "AgentExecutionInterceptor"
git grep -n "WorkspaceModelScope"
git grep -n "YliyunAuthController"
git grep -n "YliyunCloudAttachmentService"
git grep -n "YliyunUserMappingService"
git grep -n "McpIdentityForwardService"
git grep -n "TeacherAgentExtension"
git grep -n "pluginKey"
git grep -n "knowledgeBaseIdsJson"
```

## 10.2 检查上游能力是否已吸收

```bash
git grep -n "TeamPlanBridge"
git grep -n "TeamTaskService"
git grep -n "AgentWorkspaceResolver"
git grep -n "InboundMessageDeduplicator"
git grep -n "listProviderOptions"
git grep -n "date-folders"
git grep -n "mateclaw-plugin-mem0"
```

## 10.3 检查冲突标记

```bash
git grep -n "<<<<<<<"
git grep -n "======="
git grep -n ">>>>>>>"
```

这些命令应无输出。

注意：部分文档可能正常包含连续等号。发现结果时人工确认，不要直接批量删除。

## 10.4 检查空白和补丁问题

```bash
git diff --check
```

必须处理：

```text
尾随空格
非法冲突标记
错误换行
```

---

# 十一、数据库迁移检查

## 11.1 确认上游迁移

```text
V172
V173
V174
```

三种数据库应齐全：

```text
h2
mysql
kingbase
```

## 11.2 确认本地迁移

```text
V9001-V9020 中仓库实际存在的迁移文件
```

必须全部存在。

## 11.3 禁止操作

生产和现有测试数据库禁止执行：

```bash
flyway clean
```

禁止删除：

```text
flyway_schema_history
```

禁止修改已执行迁移的内容。不要假设三个数据库目录中的编号完全连续或文件集合完全相同；
应逐目录比较合并前后的已跟踪文件，不能用“范围内每个编号都必须存在”作为判断条件。

## 11.4 H2 环境

仅本地临时开发环境，在确认不需要保留数据时，可以删除 H2 数据库重新生成。

但正式 MySQL、Kingbase 必须验证增量迁移。

## 11.5 MySQL 验证

至少验证：

```text
旧数据库升级成功
V172-V174 正常执行
既有 V9001-V9020 迁移不被重复执行
不存在重复版本
Agent Team 表成功建立
本地一粒云表仍然存在
Workspace 模型表仍然存在
```

---

# 十二、后端构建和测试

## 12.0 当前 Windows 开发机前置条件

MateClaw 2.0.0 要求 Java 21。当前电脑没有保证全局 `mvn` 和 `JAVA_HOME` 正确，PowerShell
应在单条命令作用域内显式指定，不修改系统全局环境：

```powershell
$env:JAVA_HOME = 'C:\Users\loong\.jdks\ms-21.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$Maven = 'D:\program\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd'
& $Maven -version
```

输出必须同时确认 Maven 使用 Java 21。不能只检查 `java -version`，因为 Maven 可能仍引用另一套
JDK。

PowerShell 调用包含 `-D...` 的 Maven 参数时，推荐使用字符串数组展开，避免宿主把属性参数错误
拆成生命周期名称：

```powershell
$BuildArgs = @('-pl', 'mateclaw-server', '-am', '-Dmaven.test.skip=true', 'clean', 'install')
& $Maven @BuildArgs
```

## 12.1 完整测试

在仓库根目录执行：

```bash
mvn -pl mateclaw-server -am clean test
```

由于增加了 Mem0 模块，还应执行：

```bash
mvn -pl mateclaw-plugin-mem0 -am test
```

或者执行全仓测试：

```bash
mvn clean test
```

在上述 Windows 环境中，把 `mvn` 替换为 `& $Maven`。如果正在运行的后端占用
`mateclaw-server/target/*.jar`，`clean` 或 Spring Boot `repackage` 会失败；应先通过
`Get-NetTCPConnection -LocalPort 18088` 和 `Win32_Process.CommandLine` 精确确认 PID 与 JAR
路径，再停止该 MateClaw 进程。禁止按名称批量终止全部 `java.exe`。

`-DskipTests` 只跳过执行，仍会编译测试源码；`-Dmaven.test.skip=true` 连测试编译也跳过。
合并验收必须至少有一次不带这两个参数的 `test`，否则本轮出现的
`ModelProviderServiceOptionsTest` 构造器不匹配不会被发现。

## 12.2 后端重点测试范围

必须验证：

```text
AgentGraphBuilder 可以正常创建 Spring Bean
不存在构造器循环依赖
通用 Agent 正常对话
Plan-Execute 正常执行
Agent Teams 可以创建和分派任务
Team Lead 多步骤计划可以挂入任务板
Team Task 完成后可以恢复和汇总
Teacher Agent 扩展正常
Yliyun Assistant 正常
Workspace Model 隔离正常
Workspace Skill 隔离正常
Tool Guard 正常
审批恢复正常
MCP 身份转发正常
云盘附件正常
渠道消息去重正常
```

## 12.3 特别关注 Spring 构造器变化

上游可能给以下类增加依赖：

```text
AgentGraphBuilder
ToolExecutionExecutor
ChannelMessageRouter
CodeExecuteTool
SkillFileTool
SkillLoadTool
SkillScriptTool
ModelProviderService
```

测试代码中手工 `new` 这些类的地方可能编译失败。

优先采用上游已同步修改的测试构造参数，不要为了快速通过而删除正式依赖。

本轮实际发现：`ModelProviderService` 的本地 Workspace 隔离增加了
`WorkspaceModelProviderMapper`、`WorkspaceModelScope` 和 `SettingCrypto`，而上游新增的
`ModelProviderServiceOptionsTest` 仍按上游较短构造器创建服务。正确修复是补齐 mock，并把测试
scope 固定为默认 Workspace；不能删除本地隔离依赖，也不能排除该测试。

## 12.4 可执行 JAR 与 Spring 启动验收

上游 `bdfa8a6f` 发布后根 POM 的 revision 是 `2.0.0`，产物为：

```text
mateclaw-server/target/mateclaw-server-2.0.0.jar
```

不要在启动脚本、IDE 配置或文档中硬编码 `2.0.0-SNAPSHOT.jar`。本地启动脚本应从
`target` 中解析当前构建生成的非 `sources`/`javadoc` 可执行 JAR，并优先最新构建时间。

完成测试后执行：

```powershell
.\scripts\yliyun-dev\start-mateclaw.ps1
Invoke-RestMethod http://127.0.0.1:18088/actuator/health
```

随后核对监听进程的 `CommandLine` 确实指向刚生成的 JAR，而不是旧 SNAPSHOT 缓存。启动失败时
检查：

```powershell
Get-Content .\data\yliyun-dev\logs\mateclaw.out.log -Tail 300
Get-Content .\data\yliyun-dev\logs\mateclaw.err.log -Tail 100
```

本轮实际启动发现的 Bean 环为：

```text
YliyunUserMappingService
→ YliyunAssistantProvisioningService
→ AgentService
→ AgentGraphBuilder
→ MCP runtime
→ McpIdentityForwardService
→ YliyunUserMappingService
```

这是运行期 Spring 装配错误，普通单元测试和跳过测试的 package 都可能漏掉。修复应在集成边界
延迟注入 `AgentService`，保留 `AgentService.updateAgent` 的缓存失效和生命周期事件；不要打开
`spring.main.allow-circular-references` 掩盖结构问题，也不要改为直接写 Agent 表绕过缓存。

---

# 十三、前端构建和测试

进入前端目录：

```bash
cd mateclaw-ui
```

安装依赖：

```bash
pnpm install --frozen-lockfile
```

如果失败，不要直接删除 lock 文件。

先检查：

```text
package.json
pnpm-lock.yaml
pnpm-workspace.yaml
```

之前 `pnpm-workspace.yaml` 曾缺少：

```yaml
packages:
  - '.'
```

确认该配置仍然存在。

执行：

```bash
pnpm build
pnpm test
```

项目存在 lint 脚本时再执行：

```bash
pnpm lint
```

## 13.0 Windows 与合并后构建约束

Snowflake/Long ID 精度检查使用跨平台 Node 脚本：

```bash
pnpm lint:precision
# 实际执行 scripts/check-snowflake-precision.mjs
```

不要在 `package.json` 中写成：

```text
bash ../scripts/check-snowflake-precision.sh
```

原因有两点：仓库可能并不存在该 `.sh` 文件；Windows PATH 中的 `bash.exe` 也可能是 WSL
启动器而不是 Git Bash。业务实体的后端 `Long` ID 必须在前端全链路保留为字符串；确实不是
实体 ID 的序号或时间戳，才允许使用带原因的 `snowflake-precision-ok` 标记。

如果 `pnpm build` 在类型检查阶段出现下列错误，不应以跳过 `vue-tsc` 处理：

```text
ProjectChangesPanel.vue 引用的 ContextRouter/Review/Harness 类型不存在
@/utils/desktop 不存在
cloud_attachment_* 不属于 SSEEventType
组件可选 prop 没有 withDefaults 默认值
```

这说明定制组件被保留，但其配套契约没有一起合入。应从本地业务历史和当前后端事件定义中恢复
最小、准确的类型与桥接能力，不能把整个组件删除，也不能用全局 `any` 掩盖。

开发服务器与生产构建可能并行运行。`unplugin-vue-components` 的声明文件只允许 dev server
生成；production build 应设置 `dts: false`，否则 Windows 上两个进程可能同时写
`src/types/components.d.ts`，报 `UNKNOWN`/`EPERM` 并留下不完整声明文件。

本轮验证结果：

```text
pnpm lint:precision：通过
pnpm build：通过（6276 modules transformed）
pnpm test：13 files / 111 tests 全部通过
```

## 13.1 前端重点验证

```text
登录页
Workspace 切换
首页
ChatConsole
Agent 列表
Agent 编辑弹窗
普通成员加载 Provider 选项
Team 管理页
Team Kanban
TeacherOps
Cloud Agent Embed
云盘文件选择
云盘附件预览
保存到云盘
MCP 管理
安全管理
本地 Logo 和主题
```

## 13.2 路由测试

验证：

```text
/chat
/agents
/teams
/embed/cloud-agent
/teacher-ops
/settings/*
```

不应出现：

```text
路由重复
导航重复
权限判断不一致
404
页面无限重定向
```

---

# 十四、运行时冒烟测试

## 14.1 普通 Web 对话

1. 使用管理员登录；
2. 创建 Agent；
3. 发起普通对话；
4. 调用一个 Tool；
5. 验证流式输出没有重复；
6. 验证消息正常保存。

## 14.2 普通 Workspace 成员

1. 使用 member/viewer 角色登录；
2. 打开 Agent 编辑；
3. Provider 选项正常加载；
4. 页面不请求完整 `/api/v1/models`；
5. 不暴露 API Key 和 Base URL。

## 14.3 Agent Teams

1. 创建 Team；
2. 添加 Lead；
3. 添加两个成员 Agent；
4. 创建有前置依赖的多个任务；
5. 验证独立任务可以并行；
6. 验证依赖任务等待；
7. 验证 Task Timeline；
8. 验证完成后 Lead 汇总结果。

## 14.4 Workspace Skill 隔离

准备两个 Workspace，各自创建同名 Skill：

```text
company-writer
```

验证：

```text
Workspace A 只能读取 A 的 Skill
Workspace B 只能读取 B 的 Skill
审批恢复后仍保持正确 Workspace
Tool 自动重定向不会跨 Workspace
```

## 14.5 一粒云认证

验证：

```text
Ticket 登录
用户映射
Workspace 映射
Yliyun Assistant 自动创建
Cookie
Ticket 防重放
```

## 14.6 云盘附件

验证：

```text
选择云盘文件
上传聊天附件
预览附件
Agent 读取附件
生成文件
保存到云盘
日期目录写入
旧平铺目录文件仍能读取
Windows 路径正常
Linux 路径正常
```

## 14.7 MCP

验证：

```text
当前用户身份透传
Workspace 身份
一粒云 OBO Token
MCP Tool 查询
MCP Tool 写入
审批
Token 失效后的失败关闭
```

## 14.8 渠道去重

在企微、飞书或测试入口模拟同一个 Message ID 重投两次。

预期：

```text
只产生一个 Agent 回合
只保存一条用户消息
只回复一次
不重复消耗 Token
```

---

# 十五、合并提交前审查

查看最终差异：

```bash
git status
git diff --cached --stat
git diff --cached
```

建议重点审查：

```bash
git diff --cached -- \
  mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java

git diff --cached -- \
  mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java

git diff --cached -- \
  mateclaw-server/src/main/java/vip/mate/channel/web/ChatController.java

git diff --cached -- \
  mateclaw-server/src/main/java/vip/mate/llm/service/ModelProviderService.java

git diff --cached -- \
  mateclaw-ui/src/api/index.ts

git diff --cached -- \
  mateclaw-ui/src/router/index.ts

git diff --cached -- \
  mateclaw-ui/src/views/layout/MainLayout.vue
```

确认没有误加入：

```text
mateclaw-ui/data/*.db
docker/runtime/static/**
target/**
node_modules/**
dist/**
日志
临时下载文件
密钥
.env
```

发现已跟踪运行时文件时：

```bash
git rm --cached <path>
```

同时补充 `.gitignore`。

---

# 十六、提交合并

测试全部通过后，只暂存已审查的合并结果：

```bash
git add -- <reviewed-merge-paths>
```

再次确认：

```bash
git status
git diff --cached --check
```

创建合并提交：

```bash
git commit -m "merge: sync upstream/dev into dev-v2 (2026-08-03)"
```

建议提交说明包含：

```text
- merge Agent Teams and task board runtime
- merge workspace-scoped skill resolution
- merge channel inbound deduplication
- merge attachment date-folder support
- merge provider option permission fix
- preserve Yliyun auth, MCP, cloud attachment and workspace model customizations
- preserve Teacher Agent and MetaY extensions
```

---

# 十七、更新合并记录

在测试通过后更新：

```text
docs/git-merge-v2.md
```

记录：

```text
上游起始 SHA
上游目标 SHA
本地 dev-v2 起始 SHA
最终 Merge Commit SHA
文本冲突文件
人工语义合并文件
新增迁移
保留的 MetaY 定制
构建结果
测试结果
未完成问题
```

建议单独提交：

```bash
git add docs/git-merge-v2.md
git commit -m "docs: record upstream dev sync for 2026-08-03"
```

不要把尚未验证的结论写成“已通过”。

---

# 十八、将结果合入正式 dev-v2

确认临时分支正确：

```bash
git log --oneline --decorate -n 10
```

切回正式分支：

```bash
git switch dev-v2
```

使用快进合并：

```bash
git merge --ff-only sync/upstream-dev-20260803
```

这样 `dev-v2` 只会快进到已经验证过的同步结果，不会再次产生不同内容。

再次检查：

```bash
git status
git log --oneline --decorate -n 10
```

---

# 十九、推送策略

本方案默认不自动推送。

本地验收完成后，才执行：

```bash
git push dev dev-v2
```

禁止未经确认执行：

```bash
git push --force
```

如果远端 `dev-v2` 在合并期间产生了新提交：

```bash
git fetch dev
git log --oneline dev-v2..dev/dev-v2
```

先分析新提交，再进行普通 merge。

不要直接强推覆盖。

---

# 二十、回滚方案

## 20.1 合并尚未提交

```bash
git merge --abort
```

临时分支恢复到合并前状态。

## 20.2 已在临时分支提交，但未合入 dev-v2

直接切回：

```bash
git switch dev-v2
```

删除临时同步分支：

```bash
git branch -D sync/upstream-dev-20260803
```

正式 `dev-v2` 不受影响。

## 20.3 已快进到 dev-v2，但尚未推送

确认没有未提交修改后：

```bash
git switch dev-v2
git reset --hard backup/dev-v2-before-upstream-20260803
```

`reset --hard` 会删除当前未提交修改，只能在工作区干净时使用。

## 20.4 已经推送到共享远程

不要重写共享历史。

使用：

```bash
git log --oneline --merges
```

找到合并提交，然后：

```bash
git revert -m 1 <merge-commit-sha>
```

提交并正常推送。

---

# 二十一、明确禁止事项

本次合并禁止：

```text
1. merge qjloong/yliyunclaw:dev 到 dev-v2
2. git checkout --theirs .
3. git checkout --ours .
4. 直接覆盖 AgentGraphBuilder
5. 直接覆盖 ChatController
6. 直接覆盖 ModelProviderService
7. 删除、覆盖或重编号既有 V9001-V9020 迁移
8. 在生产数据库执行 Flyway clean
9. 把 Goal/Website 新业务开发夹带进同步提交
10. 未测试直接 push
11. 使用普通 force push
12. 把 docker/runtime/static 构建产物重新提交
13. 认为“没有 Git 冲突”就代表合并正确
```

---

# 二十二、完成标准

只有同时满足以下条件，才算本次合并完成。

## Git

```text
无未解决冲突
无冲突标记
工作区干净
有明确 Merge Commit
保留备份分支
```

## 本地定制

```text
一粒云认证存在
云盘附件存在
MCP 身份透传存在
Workspace Model 隔离存在
Teacher Agent 存在
Agent SPI 存在
MetaY 品牌存在
V9001-V9020 中已跟踪的一粒云迁移均存在
```

## 上游能力

```text
Agent Teams 存在
Team Task Board 存在
TeamPlanBridge 存在
Workspace Skill 隔离存在
Channel 去重存在
Tool Result 完整性修复存在
Provider Options 存在
附件日期目录存在
Mem0 模块可编译
V172-V174 存在
```

## 构建

```text
后端测试通过
前端构建通过
前端测试通过
Flyway 增量迁移通过
Docker 构建不因新增模块失败
```

## 业务冒烟

```text
普通聊天正常
Agent 编辑正常
Provider 选择正常
Teams 正常
Teacher Agent 正常
一粒云登录正常
云盘附件正常
MCP 正常
Workspace 隔离正常
渠道不重复回复
```

---

# 二十三、合并后的后续开发顺序

本次合并完成后，建议顺序为：

```text
1. 根据上游 Agent Teams 修订 Goal Agent Worker 设计
2. 建设 Business Runtime
3. 实现 Goal Agent P0
4. 实现 AI 微站 P0
5. Goal/Website 通过 Port 复用 Agent Teams
6. P1 再接入 Workflow 和 Mem0
```

Goal Agent 不再重复建设一套平台级 Agent Team 和共享任务板。

建议关系：

```text
Goal Agent：
Project / Goal / Milestone / Proposal / Evidence / Acceptance
        ↓
GoalWorkerRuntimePort
        ↓
MateClaw Agent Teams：
Team / Member / Team Task / Dispatch / Event
```

AI 微站仍然拥有独立：

```text
Site
Source
Generation Job
Page Revision
Publish Revision
Public Projection
Form
Lead
Analytics
```

Agent Teams 仅作为后续多 Agent 生成执行能力，不作为网站领域事实源。

---

# 二十四、最简执行命令清单

```bash
# 1. 准备
git switch dev-v2
git status
git fetch dev --prune
git fetch upstream dev --prune

# 2. 备份
git branch backup/dev-v2-before-upstream-20260803
git switch -c sync/upstream-dev-20260803 dev-v2

# 3. 合并
git merge --no-commit --no-ff upstream/dev

# 4. 检查冲突
git status
git diff --name-only --diff-filter=U

# 5. 人工解决共享核心文件
# 禁止 checkout --theirs .
# 禁止 checkout --ours .

# 6. 语义检查
git grep -n "AgentPromptAugmenter"
git grep -n "TeamPlanBridge"
git grep -n "WorkspaceModelScope"
git grep -n "AgentWorkspaceResolver"
git grep -n "YliyunAuthController"
git grep -n "InboundMessageDeduplicator"
git grep -n "listProviderOptions"
git diff --check

# 7. 后端测试/打包前，先精确确认并停止占用 18088 和 target JAR 的 MateClaw 进程
# 禁止按名称批量终止全部 java.exe；参见 12.1 节
mvn -pl mateclaw-server -am clean test
mvn -pl mateclaw-plugin-mem0 -am test

# 8. 前端测试
cd mateclaw-ui
pnpm install --frozen-lockfile
pnpm build
pnpm test
cd ..

# 9. 运行时验收（第 7 步已经释放旧 JAR 文件锁）
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/yliyun-dev/start-mateclaw.ps1
curl --fail http://127.0.0.1:18088/actuator/health

# 10. 提交（只暂存已审查的源码和文档，不使用 git add -A）
git add -- <reviewed-merge-paths>
git diff --cached --check
git commit -m "merge: sync upstream/dev into dev-v2 (2026-08-03)"

# 11. 合入正式分支
git switch dev-v2
git merge --ff-only sync/upstream-dev-20260803

# 12. 最终检查
git status
git log --oneline --decorate -n 10
```
