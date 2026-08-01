# Local Agent Architecture for Desktop Coding

This document defines the canonical boundary for MateClaw's Codex/OpenClaw-style local coding workflow.

The core rule is simple:

> local execution, remote reasoning, minimal context bridge

A desktop coding task must not require full repository traversal and raw file upload to a remote model. The desktop app keeps execution authority over the local workspace, while the server keeps orchestration, policy, approval, and audit authority.

## Goals

- Support local coding workflows from the desktop app without full-repo upload.
- Reuse existing Workspace Policy, Tool Guard, HarnessRun, and Project Cache foundations.
- Keep remote-model context bounded to task-relevant summaries, snippets, diff evidence, and command results.
- Make local read/write/test flows observable in Chat and Harness timelines.

## Non-goals

- Do not create a second disconnected agent stack just for desktop.
- Do not mirror the full local repository into the server database.
- Do not treat `skills` or `MCP` as the primary substrate for core local coding operations.
- Do not bypass current workspace policy, approval, or audit requirements.

## Canonical topology

```text
Desktop App
  ├─ Local Tool Host
  │   ├─ workspace tree / glob / grep / symbol shortlist
  │   ├─ file read / patch / write
  │   ├─ git status / diff
  │   └─ command / test execution
  ├─ Local Project Cache
  │   ├─ repo map
  │   ├─ key files
  │   ├─ changed files
  │   └─ snippet shortlist
  └─ Desktop Bridge Payloads
          ↓
Server Harness / Policy Layer
  ├─ Workspace Policy
  ├─ Tool Guard + Approval
  ├─ HarnessRun / step timeline / audit
  ├─ Agent runtime orchestration
  └─ Context router / project summary reuse
          ↓
Remote Model
  ├─ user instruction
  ├─ project summary
  ├─ selected snippets
  ├─ diff / validation evidence
  └─ tool results
```

## Responsibility split

| Layer | Owns | Must not own |
| --- | --- | --- |
| Desktop/App | Local filesystem access, local git access, local command execution, local project indexing, directory/file pickers | Long-term audit authority, policy source of truth, multi-user workspace state |
| Server/Harness | Workspace policy, approval decisions, tool metadata, run state, trace persistence, shared workspace semantics | Blind full local file crawling, direct assumption that every client path is server-visible |
| Remote model | Reasoning, planning, file-selection decisions, patch planning, summary generation | Raw full-repository storage, unrestricted local execution authority |

## Minimal context bridge contract

The desktop bridge may send only task-relevant evidence.

### Allowed payload classes

- `ProjectSummary`
  - project root
  - manifests / stack markers
  - key directories
  - likely entry files
  - changed file summary
- `SearchHitSet`
  - grep/glob/symbol hits
  - filename/path shortlist
- `SnippetBundle`
  - only the selected file ranges needed for the task
- `PatchEvidence`
  - touched files
  - diff stats
  - patch summary
- `CommandEvidence`
  - executed command
  - cwd
  - exit code
  - bounded stdout/stderr
- `ApprovalFinding`
  - denied path
  - risk level
  - approval requirement
  - recovery hint

### Shared desktop payload contract

Every desktop-local tool result should be able to emit one normalized payload that can later be mapped into Harness / audit records.

Current schema name:

- `desktop-local-tool-result.v1`

Current required top-level fields:

- `schemaVersion`
- `source`
- `timestamp`
- `toolName`
- `mode`
- `status`
- `riskLevel`
- `requiresApproval`
- `workspaceRoot`
- `cwd`
- `summary`
- `evidence`
- `approval`
- `truncation`

This payload is not yet the final server-side Harness DTO, but it is the compatibility bridge that lets desktop-only local execution converge toward the same audit/evidence semantics.

The current bridge stack now has four concrete layers:

- Electron local tool execution produces `sharedPayload`
- Server Harness exposes an ingest endpoint that maps `sharedPayload` into tool-invocation / approval state
- Renderer-side desktop helpers can auto-forward `sharedPayload` into Harness when the current `harnessRunId` is known
- Chat desktop project inspection now uses those helpers by default for a first real local-read evidence path
- That default desktop evidence path now also ingests a bounded local `git diff` snapshot whenever the workspace is dirty, so recent diff evidence is captured without uploading the whole repo
- Chat desktop directory browsing now also uses the same local wrapper path, reducing dependence on server-visible local paths for basic project navigation
- Chat desktop project insight now auto-captures a bounded set of key-file snippets through the same wrapper path
- Chat desktop project inspection now also emits a query-aware local grep pass from the latest user task and a bounded readonly toolchain inspection pass inferred from project hints, moving the first default evidence chain toward `tree -> git status -> git diff -> readonly toolchain -> grep -> snippet`
- Chat review summaries and the Project panel now also treat `workspace.write_patch` as first-class changed-file evidence, so desktop-local patch writes show up in the same visible diff/file surfaces as server-side writes
- Chat desktop Project panel command hints can now trigger the approval-aware local validation flow for eligible commands, so `prepare -> approve -> deny -> execute` has a first real default UI entry instead of remaining only an exposed wrapper
- Pending desktop-local validation requests are now rehydrated from Harness tool/approval state when Chat reloads or the Project panel reopens, and explicit denial now resolves that shared approval state too instead of only hiding the local card in renderer memory
- Harness-derived local validation results are now folded back into the latest assistant review summary, so message-level review cards can show compact test/lint/build evidence next to changed-file evidence instead of forcing users to inspect only the execution timeline
- The Project panel now mirrors the same validation evidence in its latest-reply and recent-review summaries, so local validation outcomes are visible in both message-level and project-level acceptance surfaces

### Forbidden by default

- Full recursive file-content upload for the whole project
- Shipping arbitrary files outside workspace roots
- Hidden-path / secret-path reads without policy check
- Sending raw `.git`, secret files, keys, environment files, or credential stores unless explicitly allowed and approved

## Local coding request lifecycle

### 1. User intent enters Chat

Example: "Read the local project, fix the login redirect bug, and run the frontend check."

### 2. Desktop resolves local workspace boundary

The app selects or confirms the workspace root and applies allowed / denied path rules.

### 3. Desktop builds a lightweight project map

Before any remote reasoning turn, gather only:

- repo root
- manifests (`package.json`, `pom.xml`, `README.md`, `docs/**`, etc.)
- changed files
- top-level directory tree
- relevant search hits for the query

### 4. Server/Harness evaluates policy

The same Workspace Policy and Tool Guard model must classify the intended operation:

- read-only inspection
- local write
- git write
- command execution
- network-sensitive action
- cross-boundary access

### 5. Remote model receives bounded context

The model sees:

- user task
- project summary
- selected file shortlist
- only the needed snippets
- prior tool results

The model does **not** see the whole repository unless the user explicitly forces that path and policy permits it.

### 6. Desktop executes selected local tools

Examples:

- list files in `src/auth`
- grep for `logoutAndRedirect`
- read two target files
- apply a patch
- run `pnpm test` in workspace root

### 7. Server persists run evidence

Harness steps, approvals, diffs, and command summaries are stored the same way as server-side tool runs.

### 8. Chat renders observable evidence

The user should see:

- plan
- selected files
- approvals/denials
- changed files
- validation output
- final summary

## Trust and approval model

Desktop-local execution is not automatically trusted more than server-side execution.

The same trust model applies:

- read-only local file listing/search: low risk
- local file write/patch: medium or high risk depending on path and scope
- destructive shell commands: high risk
- install/build/network actions: high risk or approval-bound
- reads outside workspace root: denied or approval-gated

This keeps one consistent mental model across web, server-run, and desktop-local workflows.

## Mapping to current codebase

## Step 2 rollout split

To avoid breaking the existing Web path, Step 2 is intentionally split into three buckets.

### Desktop-only

- Electron IPC endpoints for local workspace tree, glob, grep, and snippet reads
- Electron IPC endpoints for readonly `git status` and `git diff`
- Electron IPC endpoint for conservative readonly `command.run` (`--version` / `-v` style environment inspection only in the first landing)
- Electron IPC endpoint for minimal safe `workspace.write_patch` with workspace-root enforcement and optimistic concurrency checks
- Electron IPC endpoints for approval-aware validation command execution using `prepare -> approve -> execute` tokens for a narrow allowlist (`test` / `lint` / `build` style commands)
- local workspace-root boundary enforcement in the desktop process
- future local git / command / patch execution

### Server shared

- policy vocabulary
- approval / audit shape
- Harness step model
- project summary / snippet / diff payload contracts
- shared desktop local tool result payload normalization
- shared ingest endpoint for desktop-local payload -> Harness tool invocation / approval mapping

### Web unchanged

- Web continues to use the current remote/server-visible workspace flow
- Web does not gain implicit access to arbitrary client-local directories
- Web keeps reusing the same policy / approval wording, but not the desktop-only local executor

The first landing of Step 2 should therefore prefer desktop-only readonly capabilities before any mutating local tools are added.

### Current desktop foundation

- [mateclaw-desktop/electron/main.cjs](../mateclaw-desktop/electron/main.cjs)
  - already hosts IPC handlers for file pickers, basic file read/write, and desktop runtime info
- [mateclaw-desktop/electron/localToolHost.cjs](../mateclaw-desktop/electron/localToolHost.cjs)
  - hosts the desktop-only readonly local tool subset for workspace tree, glob, grep, snippet reads, git status, and git diff
- [mateclaw-desktop/electron/preload.cjs](../mateclaw-desktop/electron/preload.cjs)
  - already exposes the desktop bridge to the renderer
- [mateclaw-desktop/electron/localServer.cjs](../mateclaw-desktop/electron/localServer.cjs)
  - already proxies `/api` traffic and serves the renderer shell
- [mateclaw-desktop/electron/desktopConfig.cjs](../mateclaw-desktop/electron/desktopConfig.cjs)
  - already persists desktop-local runtime settings

### Current server foundation to reuse

- [docs/agent-harness-implementation.md](agent-harness-implementation.md#L79-L80)
  - Phase 4 and Phase 5 already define Workspace Policy + Coding Agent + Project Cache / Context Router
- Existing `WorkspacePolicy`, `ToolPolicyResolver`, `ToolExecutionExecutor`, `WorkspaceService`, `ContextRouterService`, and `HarnessRun` surfaces remain the source of truth for policy and audit semantics.

## Implementation slices after this document

### Slice A — Desktop local tool host

Add desktop-only tool endpoints for:

- `workspace.tree`
- `workspace.glob`
- `workspace.grep`
- `workspace.read_snippet`
- `git.status`
- `git.diff`
- `workspace.write_patch`
- `command.run` (readonly subset)

All of them must be workspace-scoped and pass policy metadata.

The first writable landing should stay narrow:

- workspace-scoped only
- no shell involved
- optimistic concurrency via expected content hash
- explicit replace/append/prepend/set operations only
- no implicit cross-file or cross-root mutations

The first approval-aware command landing should also stay narrow:

- no arbitrary shell execution
- explicit allowlist only
- approval token required before execution
- short-lived in-memory approval state in the desktop process until the server-side approval/audit loop is wired in fully

### Slice B — Structured bridge payloads

Define the JSON contracts between Desktop, Server, and model for:

- project summary
- snippet bundle
- diff summary
- command summary
- approval finding

### Slice C — Query-aware context reduction

The desktop bridge should prefer:

1. repo map
2. changed files
3. search hits
4. selected snippets
5. command evidence

and escalate to more reads only when the task requires it.

### Slice D — End-to-end coding acceptance

Use the built-in Coding Agent sample task to validate:

- locate project
- narrow candidate files
- read snippets
- patch files
- run validation
- show diff/test evidence

without full-repo upload.

## Acceptance for Step 1

Step 1 is complete when:

- one canonical topology exists
- Desktop / Server / model responsibilities are frozen
- the minimal context bridge contract is explicit
- future local coding work references this document instead of inventing ad-hoc behavior

This document is that canonical baseline.
