# MateClaw Local Git Workflow

本文档用于固化当前仓库的本地开发 / 上游同步策略，目标是：

- 可以持续从上游 `matevip/mateclaw` 拉取最新 bugfix 和功能
- 不影响本地正在开发的功能
- 不自动推送任何本地分支到远端

---

## 1. 当前分支约定

- `dev-local`
  - 日常开发分支
  - 基于 `upstream/main`
  - 叠加本地开发功能
- `local-snapshot-20260513`
  - 本地安全快照分支
  - 用于回滚和兜底
- `upstream-main`
  - 上游主线的本地参考分支

推荐你后续始终在 `dev-local` 上开发：

```bash
git checkout dev-local
```

---

## 2. 上游仓库配置

官方上游仓库：

```bash
https://github.com/matevip/mateclaw
```

检查远程：

```bash
git remote -v
```

如果还没有 `upstream`，执行：

```bash
git remote add upstream https://github.com/matevip/mateclaw.git
```

---

## 3. 日常同步上游最新修改

当云端有新的 bugfix 或功能更新时：

```bash
git checkout dev-local
git fetch upstream
git merge upstream/main
```

这套流程适合大多数场景，优点是历史清晰、回退方便。

如果你更喜欢线性历史，也可以使用：

```bash
git checkout dev-local
git fetch upstream
git rebase upstream/main
```

---

## 4. 开发前的安全习惯

开始做较大改动前，建议先看一下状态：

```bash
git status
git branch
```

如果是重要阶段，可额外打一个本地快照分支：

```bash
git checkout dev-local
git checkout -b local-snapshot-YYYYMMDD
```

或者直接提交一个本地快照：

```bash
git add -A
git commit -m "chore: local snapshot before upstream sync"
```

---

## 5. 如果同步后有问题，怎么回退

如果你发现同步后的 `dev-local` 不符合预期，可以先切回快照分支：

```bash
git checkout local-snapshot-20260513
```

如果只是想回退最近一次同步提交，也可以：

```bash
git checkout dev-local
git log --oneline --decorate --max-count=10
git reset --hard <commit>
```

> 注意：`reset --hard` 会丢弃当前未提交修改，执行前先确认工作区已备份。

---

## 6. 不推送到云端的原则

当前流程默认只做本地操作：

- 本地建分支
- 本地提交
- 本地合并 / rebase
- 本地回滚

不会自动推送到 GitHub。

只有你显式执行下面命令时，才会推送：

```bash
git push
```

如果你只想长期本地开发，不执行 `git push` 即可。

---

## 7. 推荐的最小工作流

### 日常开发

```bash
git checkout dev-local
git status
```

### 同步上游最新代码

```bash
git checkout dev-local
git fetch upstream
git merge upstream/main
```

### 出问题时回到安全快照

```bash
git checkout local-snapshot-20260513
```

---

## 8. English summary

- Use `dev-local` for daily work.
- Pull latest upstream changes with:

```bash
git checkout dev-local
git fetch upstream
git merge upstream/main
```

- Use `local-snapshot-20260513` as the rollback branch.
- Nothing is pushed unless you explicitly run `git push`.