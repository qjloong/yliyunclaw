# Meta Y Core Systems Consolidation Plan

> Status: **WP-0 Baseline Frozen** — Approved for WP-1 start  
> Frozen at: 2026-05-20  
> Goal: Consolidate the project’s core systems model by drawing on the advanced patterns in `docs/claude-code-book-main/第二部分-核心系统篇`, without changing existing core business scenarios or breaking current business logic.  
> Next: WP-1 Settings Resolution Consolidation (see §6 and §14).

---

## 1. Background

After reviewing the core modules in `docs/claude-code-book-main/第二部分-核心系统篇`, it is clear that the design ideas in those materials are advanced, systematic, and highly relevant to the current project.

The current Meta Y project already has many strong capabilities in place:

- unified harness run / step / tool / approval surfaces
- workspace policy and tool guard enforcement
- project insight cache and context routing
- memory SPI and lifecycle mediation
- hook registry, dispatch, and audit support
- local-first desktop execution with minimal context bridging
- template-driven built-in business agents

The current problem is **not a lack of capability**, but rather that these capabilities are distributed across modules and have not yet been fully consolidated into a single explicit core-systems model.

This document therefore does **not** propose a disruptive rewrite. Instead, it proposes a **compatibility-first consolidation plan**:

- keep existing core business scenarios unchanged
- keep existing user-facing main flows unchanged
- improve architecture consistency, system boundaries, terminology, and future extensibility
- later merge these conclusions back into the main execution ledger and checklist

---

## 2. Scope and Constraints

### 2.1 In Scope

This plan covers the core-system modules inspired by the Part 2 materials:

1. Settings and configuration
2. Policy and safety authority relationships
3. Long-term memory model
4. Working-context management
5. Hook and lifecycle extensibility
6. Template contract integration
7. Cross-cutting terminology and system boundaries

### 2.2 Out of Scope

This plan does **not** directly:

- change the existing product’s core business scenarios
- replace the current HarnessRun / Approval / WorkspacePolicy mainline
- redesign the UI navigation structure
- introduce a second independent runtime stack
- add new industry packs first
- perform code refactors immediately

### 2.3 Hard Constraints

All future implementation derived from this plan must preserve:

- Coding Agent core workflow
- Teacher Agent core workflow
- Workspace / Wiki / Chat / Approval main business logic
- Desktop local-first execution principle
- minimal context bridge to remote reasoning
- compatibility with the existing ledger in `docs/agent-harness-implementation.md`

---

## 3. Guiding Principles

### 3.1 Compatibility-first, not rewrite-first

The project already has meaningful implementations. The right strategy is:

- first unify the model
- then unify the terminology
- then tighten the boundaries
- finally converge scattered logic incrementally

### 3.2 Policy is above settings

A core lesson from the reference materials is that settings and extensions are useful only when constrained by a clear safety authority.

In this project, ordinary settings should represent:

- preferences
- defaults
- local runtime choices
- template suggestions

But policy should remain the top authority over:

- tool permissions
- workspace boundaries
- network behavior
- write scopes
- approval requirements
- hook execution eligibility
- context escalation
- memory write constraints

### 3.3 Local-first and minimal-context bridging remain non-negotiable

The architecture in `docs/local-agent-architecture.md` must remain a hard invariant.

That means:

- local file/git/search/run stays local whenever possible
- server remains policy/audit/orchestration authority
- remote models receive only task-relevant summarized evidence
- no return to broad full-repo upload behavior

### 3.4 Core systems are shared infrastructure, not per-template hacks

The Coding Agent and Teacher Agent are valid business exemplars, but they should not each independently define their own implicit system contracts forever.

The core systems model must become explicit and reusable.

---

## 4. Current System Assessment

### 4.1 Current Strengths

The project already aligns strongly with advanced harness thinking in several areas:

- **Run model**: a unified run/step/tool/approval execution surface already exists
- **Safety model**: workspace policy and tool guard are already first-class
- **Project understanding**: project cache and context routing are already present
- **Memory extensibility**: provider-based memory architecture already exists
- **Hooks**: registry, dispatcher, and audit mechanisms already exist
- **Template packaging**: built-in templates already carry runtime and quality metadata
- **Desktop local bridge**: local agent execution is already separated from remote reasoning

### 4.2 Current Weaknesses

The project’s main weakness is architectural fragmentation:

- configuration exists, but no single resolution model exists
- policy exists, but is not yet the visibly universal authority layer
- memory exists, but its taxonomy is not yet strict enough
- context routing exists, but context lifecycle is still only partially formalized
- hooks exist, but lifecycle events are still fragmented across subsystems
- templates already behave like system contracts, but are not yet treated as such explicitly

### 4.3 Core Consolidation Thesis

The consolidation target is therefore:

> Turn distributed capabilities into one explicit Core Systems Architecture, without changing the current core business scenarios.

---

## 5. Consolidated Core Systems Model

This plan proposes six unified layers:

1. **Policy Layer**
2. **Settings Layer**
3. **Memory Layer**
4. **Context Layer**
5. **Lifecycle Layer**
6. **Template Contract Layer**

These are **not** six totally new subsystems. They are six architectural views that will later absorb and align the project’s current implementations.

---

## 6. Settings and Configuration Consolidation

Reference inspiration: `05-设置与配置-Agent的基因.md`

### 6.1 Problem Statement

The current project has multiple configuration sources and resolution paths, including:

- platform/app defaults
- Spring/application config
- DB system settings
- workspace settings and workspace policy
- desktop local config
- template defaults
- provider-specific fallback logic
- runtime request/CLI/UI state

The problem is not that these are wrong. The problem is that:

- their boundaries are not yet formally unified
- precedence is not consistently documented
- merge rules differ across modules without one common model
- policy and ordinary settings are not always clearly separated conceptually

### 6.2 Proposed Configuration Source Model

For this project, the future normalized configuration source model should be:

1. **platform defaults**  
   low-level product defaults from application code and packaged runtime defaults
2. **system managed settings**  
   admin/system/operator-managed settings, DB-backed or environment-backed
3. **template defaults**  
   starter defaults embedded in built-in templates
4. **workspace shared settings**  
   shared workspace-level behavior and preferences
5. **desktop local settings**  
   local machine runtime settings for desktop behavior only
6. **runtime flags / request-scoped overrides**  
   ephemeral overrides for a specific invocation/session
7. **policy enforcement layer**  
   not just another source, but the final authoritative constraint layer

### 6.3 Why This Order Fits the Current Project

This ordering preserves current behavior while making the model understandable:

- template defaults should not override shared workspace decisions
- desktop local settings should not become team-shared truth
- runtime flags are temporary and must not redefine durable config
- policy remains capable of constraining all lower layers

### 6.4 Proposed Merge Semantics

The project should classify configuration fields into three merge classes.

#### A. Scalar override

Examples:

- `backendUrl`
- `model`
- `theme`
- `language`

Rule:

- higher-priority source overrides lower-priority source directly

#### B. Additive / mergeable collections

Examples:

- allowed path lists
- capability tags
- some tool allowlists
- hook registration lists where appropriate

Rule:

- merge and deduplicate only if the field is semantically cumulative

#### C. Restrictive policy values

Examples:

- `approvalMode`
- `sandboxMode`
- `networkPolicy`
- write permissions
- dangerous tool risk overrides

Rule:

- do **not** treat these as ordinary “last writer wins” values
- use “more restrictive wins” semantics where applicable

### 6.5 Proposed Configuration Domains

To reduce conceptual confusion, future work should separate config into four conceptual domains.

#### 1. Product settings

Purpose:

- affect standard product behavior and defaults
- UI preferences, service-level defaults, non-sensitive runtime behavior

#### 2. Safety policy

Purpose:

- define non-bypassable guardrails
- tool restrictions, network restrictions, approval requirements, workspace boundaries

#### 3. Local runtime state

Purpose:

- local-only operational state
- desktop backend/proxy endpoints, local convenience state, ephemeral machine-specific config

#### 4. Business template defaults

Purpose:

- initialize agent/business workflows with sensible defaults
- never become the top authority layer

### 6.6 Configuration Safety Boundaries

A key refinement inspired by the reference materials is explicit trust classification.

Future config resolution should distinguish:

- **managed / trusted** config
- **shared workspace** config
- **template embedded defaults**
- **project-derived hints**
- **local runtime-only config**

And explicitly state what each source may influence.

For example:

- project-derived hints may influence context and suggestions
- they must not silently elevate safety permissions
- local desktop config may influence routing targets
- it must not redefine shared policy authority

### 6.7 Immediate Optimization Direction

Before any code refactor, the first deliverable should be a **canonical config resolution contract** covering:

- source types
- precedence
- merge classes
- trust levels
- policy override rules
- field classification examples

This can later guide backend, desktop, template, and UI normalization.

---

## 7. Policy Authority Consolidation

### 7.1 Problem Statement

The current project already has strong tool and workspace safety logic, but policy is not yet consistently framed as the single highest authority across all core systems.

Today, policy is strongest in:

- tool execution
- workspace boundary enforcement
- approval behavior

But the same principle should later govern:

- configuration resolution
- hook eligibility
- memory persistence boundaries
- context escalation
- desktop/server bridge behavior

### 7.2 Proposed Policy Model

Policy should be treated as a cross-cutting authority layer with clearly defined scopes.

Suggested scopes:

1. **global/system policy**
2. **workspace policy**
3. **template policy defaults**
4. **runtime constraints**

### 7.3 Core Rules

#### Rule 1 — Policy is not just another setting

Policy should not be resolved using ordinary preference merge rules.

#### Rule 2 — Template policy may tighten but should not loosen stronger shared policy

Templates can express safer defaults. They should not silently weaken stronger workspace or system constraints.

#### Rule 3 — Project-derived information is never authority

Project-local files, repo-scanned material, or ad hoc instructions may shape suggestions and context, but must not gain the same authority level as managed policy.

#### Rule 4 — Local desktop settings cannot redefine shared boundaries

Desktop config may shape local runtime plumbing, not shared safety truth.

### 7.4 Trust Classification Proposal

Future system design should explicitly classify sources as:

- **managed**
- **trusted shared**
- **local trusted**
- **project-derived / untrusted for safety authority**

This classification should eventually apply consistently to:

- settings
- hooks
- injected instructions
- memory write candidates
- context source selection

### 7.5 Policy as a Universal Constraint Surface

Every future subsystem should define how it is constrained by policy.

That includes:

- configuration values becoming effective
- hook registration becoming active
- context sources becoming eligible for injection
- memory extraction becoming persistable
- local desktop actions becoming executable
- template-provided defaults becoming runnable

### 7.6 Immediate Optimization Direction

The next architectural step is to define a **unified policy authority matrix** describing:

- which subsystem consumes policy
- which fields are policy-governed
- whether policy constrains, overrides, or vetoes lower-layer settings

---

## 8. Memory System Consolidation

Reference inspiration: `06-记忆系统-Agent的长期记忆.md`

### 8.1 Problem Statement

The project has a memory architecture, but the conceptual boundary between memory and adjacent systems is not yet strict enough.

There is a risk of long-term memory becoming mixed with:

- project understanding cache
- routed context snippets
- transient session artifacts
- KB grounding results
- one-off file or attachment summaries

### 8.2 Proposed Information Taxonomy

Future consolidation should explicitly distinguish the following information classes.

#### 1. Long-term memory

Characteristics:

- non-derivable from current project state
- durable across sessions
- low-change
- high future behavioral value

Examples:

- user preferences
- validated workflow rules
- non-obvious project decisions
- durable collaboration conventions

#### 2. Project cache

Characteristics:

- derivable from the workspace/repo
- can be recomputed
- may use invalidation/fingerprints/watchers
- optimized for reuse, not permanence

Examples:

- project root detection
- key files
- tech-stack summary
- command hints
- changed-file summaries

#### 3. Working context

Characteristics:

- request-scoped or short-lived
- selected for the current task
- budget-sensitive
- frequently discarded or compacted

Examples:

- local tool result summary
- recent diff evidence
- selected KB snippets
- recent conversation-relevant messages

#### 4. Session ephemeral material

Characteristics:

- high priority for the active conversation
- not yet validated for permanence
- often temporary or task-specific

Examples:

- current task scaffolding
- recently uploaded user materials
- temporary analysis state

#### 5. Knowledge grounding

Characteristics:

- fact-bearing external or curated knowledge source
- must preserve provenance
- remains conceptually separate from memory

Examples:

- wiki pages
- KB pages
- source extracts
- retrieved reference passages

### 8.3 Long-term Memory Write Criteria

A candidate should enter long-term memory only if it satisfies all or most of the following:

- not cheaply derivable from repo or tools
- expected to matter in future sessions
- reasonably stable
- validated by user action, repeated observation, or explicit confirmation
- useful for collaboration quality, not just historical completeness

### 8.4 Explicit Exclusions

The following should generally not be written into long-term memory:

- file tree snapshots
- code structure summaries
- route lists
- single-use debug trails
- temporary attachment digests
- unverified intermediate hypotheses
- information already better represented by project cache

### 8.5 Role of `MEMORY.md`

Future consolidation should define `MEMORY.md` as:

- a stable memory entrypoint
- a compact index/summary surface
- a durable collaboration memory layer

It should **not** become:

- a scratchpad
- a raw transcript dump
- a replacement for project cache
- a substitute for KB grounding

### 8.6 Memory Governance Proposal

Long-term memory should eventually be governed by:

- write eligibility rules
- source provenance
- policy constraints
- retention / review rules
- conflict resolution rules

### 8.7 Immediate Optimization Direction

Before changing implementation behavior, the project should define a **memory governance document** describing:

- what memory is
- what memory is not
- what can be promoted from session/context into memory
- what should remain cache/context/grounding only

---

## 9. Context Management Consolidation

Reference inspiration: `07-上下文管理-Agent的工作记忆.md`

### 9.1 Problem Statement

The project already has query-aware and budget-aware routing, but it is still best understood as an advanced routing layer rather than a full context operating model.

The next maturity step is to define the **full lifecycle of working context**.

### 9.2 Existing Strengths

Current strengths already include:

- project insight caching
- query-aware relevance routing
- budget-aware injection
- workspace and agent-aware context selection
- local-first minimal context bridge

These are strong foundations and should remain.

### 9.3 Proposed Working Context Lifecycle

Future context handling should be modeled as a lifecycle:

1. **source collection**
2. **relevance scoring**
3. **budget allocation**
4. **compaction**
5. **provenance tagging**
6. **injection**
7. **post-run retention or discard**

### 9.4 Unified Context Source Categories

All context sources should eventually be normalized into a common taxonomy.

Suggested categories:

- current user intent
- recent conversation evidence
- long-term memory snippets
- project cache summary
- local tool evidence
- KB / wiki grounding
- template-required context
- attachment-derived summaries
- runtime validation evidence

### 9.5 Budgeting Proposal

Current character-aware budgeting should later evolve toward token-aware budgeting, but the model should be formalized first.

Future budget handling should define:

- global injection budget
- per-source budget heuristics
- source ranking
- min/max reserved budget classes
- degrade strategy under pressure

### 9.6 Provenance Requirement

Every context block injected into the model path should ideally have provenance dimensions such as:

- source type
- source owner/scope
- derivable vs durable
- confidence
- freshness
- policy eligibility

This is especially important when mixing:

- local repo facts
- workspace memory
- KB grounding
- template defaults
- attachment-derived summaries

### 9.7 Compaction Strategy Proposal

Without changing behavior yet, the project should formally define context compaction levels, for example:

- **trim**: remove low-value or expired details
- **compact**: shorten source blocks while preserving structured meaning
- **collapse**: convert larger groups into summarized structured evidence
- **full summary fallback**: final safety fallback under extreme pressure

### 9.8 Circuit Breaker and Fallback Proposal

Like the reference design, the project should later define explicit fallback behaviors for context pressure and compaction failures.

Potential future needs:

- token pressure threshold
- source suppression rules
- compaction failure handling
- repeated failure circuit breaker
- guaranteed preserve list

### 9.9 Attachments and Large Context Convergence

A major refinement beyond the current backlog outline is this:

attachments should not remain an adjacent feature track forever.

They should enter the same context lifecycle as other sources.

That means:

- images
- extracted documents
- large files
- structured file summaries
- indexing outputs

should all eventually be routed through the same source classification, budgeting, provenance, and compaction rules.

### 9.10 Immediate Optimization Direction

The next architectural deliverable should be a **context lifecycle contract** describing:

- source taxonomy
- budget semantics
- provenance metadata
- compaction levels
- fallback rules
- attachment convergence rules

---

## 10. Hook and Lifecycle Consolidation

Reference inspiration: `08-钩子系统-Agent的生命周期扩展点.md`

### 10.1 Problem Statement

The project already has meaningful hook machinery, but the broader lifecycle model is distributed across:

- hook registry and hook dispatch
- graph lifecycle listeners
- memory lifecycle mediation
- approval flow handling
- validation and export paths

This makes the project extensible, but not yet governed by a single explicit lifecycle contract.

### 10.2 Proposed Lifecycle Categories

Future unification should classify lifecycle events into seven categories.

#### 1. Session lifecycle

Examples:

- session start
- session restore
- session end
- desktop bridge attach/detach

#### 2. Prompt / plan lifecycle

Examples:

- user message accepted
- plan draft created
- plan revised
- plan confirmed
- plan restored

#### 3. Tool lifecycle

Examples:

- tool requested
- pre-tool guard evaluation
- tool executed
- tool result normalized
- tool result ingested

#### 4. Approval lifecycle

Examples:

- approval required
- approval pending
- approval granted/denied
- approval expired/canceled
- approval ingested from desktop side

#### 5. Context lifecycle

Examples:

- context route computed
- context compacted
- context pressure reached
- context fallback triggered

#### 6. Memory lifecycle

Examples:

- memory candidate extracted
- memory candidate filtered
- memory persisted
- memory recalled into context

#### 7. Completion / validation / export lifecycle

Examples:

- run completed
- validation executed
- acceptance evaluated
- export generated
- final answer summarized

### 10.3 Hook vs Listener Relationship

The future unified model should distinguish but align two concepts.

#### Listener

- internal runtime observer
- often built into core execution flow
- closer to system internals

#### Hook

- configurable extension surface
- externally attached behavior
- policy-governed and source-trusted

They do not need to become identical, but they should share a common event vocabulary.

### 10.4 Hook Trust Model

Inspired by the reference system, hook execution should later become more clearly governed by trust classification.

Possible source classes:

- managed hooks
- shared trusted hooks
- local trusted hooks
- project-derived hooks or project-influenced behaviors

Not all classes should carry equal authority.

### 10.5 Event Contract Proposal

Each lifecycle event should eventually be able to carry a consistent minimal envelope, such as:

- `runId`
- `workspaceId`
- `conversationId`
- `agentId`
- `eventType`
- `sourceClass`
- `policyScope`
- `traceContext`
- `timestamp`

This will later support better observability, hook governance, and audit consistency.

### 10.6 Why This Matters

A unified lifecycle fabric will make later work easier in:

- observability and tracing
- local tool evidence ingestion
- subagent/fork/coordinator orchestration
- approval continuity across desktop/server
- consistent business-template instrumentation

### 10.7 Immediate Optimization Direction

The next design deliverable should be a **lifecycle event map** that reorganizes existing hooks/listeners/mediators under one event taxonomy without breaking current implementations.

---

## 11. Template Contract Consolidation

### 11.1 Problem Statement

The built-in templates already carry much more than prompt text.

They already encode:

- default runtime identity
- capability expectations
- policy defaults
- context requirements
- workspace seed files
- quality and acceptance intent

This means templates are already acting like business-layer contracts.

### 11.2 Proposed Template Role

Templates should be understood as:

- business-facing initialization contracts
- not top-level authority sources
- not substitutes for policy
- not substitutes for memory or project cache

### 11.3 Suggested Contract Sections

Future normalization should group template metadata into stable contract sections.

Suggested groups:

1. identity
2. runtime profile
3. capability defaults
4. safety defaults
5. context declarations
6. knowledge declarations
7. workspace materialization
8. acceptance declarations

### 11.4 Behavioral Principle

Templates may:

- define business defaults
- define expected context sources
- define expected quality checks
- define starter workspace material

Templates must not:

- silently override stronger policy constraints
- act as safety authorities on their own
- collapse long-term memory and knowledge-grounding into one concept

### 11.5 Immediate Optimization Direction

The next architectural step is to formally document template fields as part of the core systems contract, so templates stop being the place where system semantics quietly drift.

---

## 12. Terminology Normalization

A major consolidation benefit will come from a stable shared vocabulary.

### 12.1 Settings Vocabulary

Use distinct terms for:

- `settings` — configurable defaults or preferences
- `defaults` — low-priority initial values
- `overrides` — higher-priority effective replacement
- `policy` — non-bypassable authority constraints

### 12.2 Information Vocabulary

Use distinct terms for:

- `memory` — durable non-derivable knowledge
- `cache` — recomputable reusable summary
- `context` — task-scoped injected working evidence
- `grounding` — external factual source backing

### 12.3 Runtime Boundary Vocabulary

Normalize:

- workspace boundary
- local execution
- remote reasoning
- approval gate
- sandbox mode
- network policy

### 12.4 Template Vocabulary

Normalize:

- template defaults
- template manifest
- runtime contract
- materialized files

### 12.5 Why Terminology Matters

Without vocabulary normalization:

- UI labels diverge from backend semantics
- ledger items become harder to classify
- templates evolve their own dialect
- future contributors duplicate concepts accidentally

---

## 13. Compatibility Statement

This plan is designed to leave the project’s existing business logic intact.

### 13.1 Explicitly Preserved

The following remain preserved:

- Coding Agent workflow remains local-first and project-aware
- Teacher Agent workflow remains structured, grounded, and approval-aware
- Workspace permissions remain centered on the existing guard/policy path
- HarnessRun, Approval, and Audit remain the core execution evidence path
- built-in templates remain the main business entrypoint for starter agents
- desktop remains the local capability host

### 13.2 What Changes Later

Only the underlying coherence improves:

- clearer source precedence
- clearer policy authority
- clearer memory/context separation
- clearer lifecycle model
- clearer template contract boundaries

### 13.3 What Should Not Happen

The plan explicitly rejects:

- breaking current user flows in order to “match Claude” mechanically
- duplicating a second runtime model beside the existing harness
- turning project-local content into a silent authority source
- weakening local-first constraints for convenience

---

## 14. Recommended Consolidation Sequence

### Phase A — Architecture normalization first

Before updating checklists, define the architecture model explicitly:

- unified config model
- policy authority model
- memory taxonomy
- context lifecycle
- lifecycle event map
- template contract model

### Phase B — Vocabulary normalization

Before more features are added, normalize names and semantics across:

- backend
- desktop
- template JSON
- UI copy
- ledger language

### Phase C — Memory / context boundary tightening

After the model is agreed:

- separate durable memory from derivable project knowledge
- unify attachment-derived evidence with context routing
- define compaction/fallback semantics

### Phase D — Lifecycle and hook convergence

After event categories are explicit:

- align hooks, listeners, approval lifecycle, and local bridge traces under the shared event model

### Phase E — Merge back into the execution ledger

Only after the above model is accepted should the current ledger be updated in a clean way.

---

## 15. Mapping Back to the Existing Ledger Later

After approval, this plan can later be mapped into `docs/agent-harness-implementation.md` as:

- a new core-systems consolidation track
- Phase 4/5/6 refinement tasks
- Local Agent bridge refinement tasks
- P1 acceptance hardening tasks
- P2 attachment/context convergence tasks

The mapping should be organized by system consolidation themes, not scattered one-off items.

---

## 16. Final Conclusion

The project is already advanced in core capability. The key next step is not “add more systems”, but “make the existing systems explicit, consistent, and governable under one shared architecture model”.

The final intended consolidated architecture is:

- **Policy** as the authority layer
- **Settings** as layered defaults and overrides
- **Memory** as long-term non-derivable knowledge
- **Context** as budgeted working evidence with provenance
- **Lifecycle** as the shared event fabric
- **Templates** as business contract packaging, not top-level authority

This gives the project a stronger architectural spine while preserving the current business logic and core product scenarios.

---

## 17. Approval Request

This document is intended to be reviewed and confirmed first.

After confirmation, the next step will be:

- map the above structure back into `docs/agent-harness-implementation.md`
- merge the optimization directions into the existing phases and priorities
- keep compatibility with the current business flows as a hard constraint

---

## 18. Detailed Requirement Breakdown and Executable Change Plan

This section converts the architecture proposal into a **bounded execution plan**.

Core objective:

- split the plan into independently executable work packages
- define exact functional points for each package
- define expected modification scope by module and file family
- prevent “while touching this, also refactor everything” expansion

### 18.1 Execution Control Rules

All implementation tasks derived from this document must obey the following rules.

#### Rule A — One package solves one kind of problem

Each package should solve only one primary architectural problem:

- config normalization
- policy authority normalization
- memory taxonomy normalization
- context lifecycle normalization
- lifecycle event normalization
- template contract normalization

No package should mix multiple primary goals unless the dependency is explicit and unavoidable.

#### Rule B — Prefer extraction over rewrite

When existing logic already works, implementation should prefer:

- extracting contracts
- wrapping existing behavior
- centralizing merge rules
- normalizing names and metadata

Instead of:

- rewriting stable core flows
- changing business workflow sequencing
- replacing execution pipelines wholesale

#### Rule C — Default first batch is compatibility batch

The first implementation batch should prioritize:

- contract definitions
- resolver centralization
- metadata alignment
- audit/provenance enrichment

It should avoid:

- large persistence model migrations
- UI workflow redesign
- cross-module rewrites of already stable flows

#### Rule D — Every task must have explicit no-change boundaries

Each task must define:

- what is allowed to change
- what must not change
- which modules are in scope
- which adjacent modules are explicitly out of scope

#### Rule E — No hidden product expansion

This plan is for **core-systems consolidation**, not for introducing new product tracks.

The following are not implicitly included unless separately approved:

- new agent categories
- new approval UX flows
- new desktop runtime channels
- new memory backends
- new template marketplace behaviors

---

## 19. Work Package Structure

The recommended execution order is split into seven work packages.

1. WP-0 — Baseline contracts and inventory locking
2. WP-1 — Settings resolution consolidation
3. WP-2 — Policy authority matrix consolidation
4. WP-3 — Memory taxonomy and governance consolidation
5. WP-4 — Context lifecycle consolidation
6. WP-5 — Lifecycle event / hook contract consolidation
7. WP-6 — Template contract normalization and ledger merge preparation

Each work package below includes:

- target outcome
- functional points
- exact scope boundaries
- likely touched files/modules
- explicit exclusions
- acceptance checkpoint

---

## 20. Work Package Details

### WP-0 — Baseline Contracts and Inventory Locking

#### Objective

Create the execution baseline so later implementation stays scoped and measurable.

#### Functional Points

1. Create a core-systems inventory table:
   - current config sources
   - current policy entry points
   - current memory entry points
   - current context routing entry points
   - current lifecycle/hook entry points
   - current template metadata entry points
2. For each area, mark:
   - source of truth
   - merge owner
   - enforcement owner
   - audit/provenance owner
3. Define the canonical terminology list used by follow-up packages.
4. Freeze a first-pass scope map for the six core layers.

#### Modification Scope

In scope:

- documentation only
- contract tables
- glossary normalization
- system inventory notes

Likely files:

- [docs/core-systems-consolidation-plan.md](docs/core-systems-consolidation-plan.md)
- [docs/agent-harness-implementation.md](docs/agent-harness-implementation.md)
- [docs/harness.md](docs/harness.md)
- [docs/local-agent-architecture.md](docs/local-agent-architecture.md)

Out of scope:

- Java code changes
- desktop runtime changes
- database schema changes
- UI changes

#### Acceptance Checkpoint

WP-0 is done when:

- the team can point to one inventory table per core layer
- the source-of-truth owner is documented for each layer
- follow-up packages can name exact target files before coding starts

#### WP-0 Directly Executable Task Sheet

The goal of this task sheet is to make WP-0 executable without interpretation drift.

Execution rules for this sheet:

- output is documentation/inventory only
- no runtime behavior changes
- no schema changes
- no UI changes
- if a task uncovers missing ownership, record it as a finding rather than solving it in WP-0

##### WP-0 Deliverable Package

WP-0 should produce four concrete artifacts:

1. a **Core Systems Inventory Table**
2. a **Source-of-Truth / Merge / Enforcement / Audit ownership table**
3. a **Canonical Terminology Glossary**
4. a **WP-1 to WP-6 scope lock table**

Preferred artifact landing:

- keep the master plan in [docs/core-systems-consolidation-plan.md](docs/core-systems-consolidation-plan.md)
- add summarized execution/ledger mapping into [docs/agent-harness-implementation.md](docs/agent-harness-implementation.md) only after WP-0 review
- if the inventory becomes too large, split into a dedicated annex doc under `docs/`

##### WP-0 Task List

| Task ID | Task Name | Core Layer | Classes / Files to Inspect | Expected Output | Acceptance Point |
| --- | --- | --- | --- | --- | --- |
| WP0-01 | Inventory settings sources | Settings | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java), [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs), [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs) | One table listing every current settings source, its scope, and whether it is shared, local, template, or runtime-only | No active settings source used by server/template/desktop runtime is missing from the inventory |
| WP0-02 | Inventory policy entry points | Policy | [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java), [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java), [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java), [mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java) | One table of current policy producers, policy consumers, restrictive merge points, and approval-coupled enforcement points | Tool guard, workspace policy, template policy merge, and approval-linked enforcement are all explicitly mapped |
| WP0-03 | Inventory memory surfaces | Memory | [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java), [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java), [mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java](mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java), [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java), [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java), [mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java](mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java), [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java) | One table separating memory manager, memory file tools, emergence/summarization flow, recall flow, and workspace file backed memory surfaces | The inventory can distinguish who reads memory, who writes memory, who consolidates memory, and who only exposes memory as a tool |
| WP0-04 | Inventory context assembly surfaces | Context | [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java), [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java), [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java), [mateclaw-server/src/main/java/vip/mate/agent/graph/StateGraphReActAgent.java](mateclaw-server/src/main/java/vip/mate/agent/graph/StateGraphReActAgent.java), [mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java](mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java), [mateclaw-server/src/main/java/vip/mate/agent/graph/node/ReasoningNode.java](mateclaw-server/src/main/java/vip/mate/agent/graph/node/ReasoningNode.java) | One table of context sources, routing points, window/compaction points, and final injection points | The inventory shows where context is selected, where it is compacted, and where it enters the model path |
| WP0-05 | Inventory lifecycle and harness events | Lifecycle | [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java), [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java), [mateclaw-server/src/main/java/vip/mate/harness/model/HarnessRun.java](mateclaw-server/src/main/java/vip/mate/harness/model/HarnessRun.java), [mateclaw-server/src/main/java/vip/mate/harness/controller/HarnessRunController.java](mateclaw-server/src/main/java/vip/mate/harness/controller/HarnessRunController.java) | One table mapping current graph events to harness run state, approval state, and tool invocation state | At least phase, plan, tool, approval, and perf events are mapped end-to-end from publication to harness ingestion |
| WP0-06 | Inventory hook surfaces | Lifecycle / Hook | [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java), [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java), [mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java](mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java), hook event classes under `mateclaw-server/src/main/java/vip/mate/hook/event/`, [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java) | One table listing current hook event families, dispatch path, action types, and existing trust assumptions | The inventory clearly separates hook event publication, hook matching, and hook action execution |
| WP0-07 | Inventory template contract entry points | Template | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java), [mateclaw-server/src/main/java/vip/mate/agent/controller/TemplateController.java](mateclaw-server/src/main/java/vip/mate/agent/controller/TemplateController.java), [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json), [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json), representative built-in template JSON files under `mateclaw-server/src/main/resources/templates/` | One table of template metadata categories, current consumers, and which fields already behave as runtime contract fields | The inventory can state which template fields affect runtime, workspace seeding, quality expectations, and policy-tightening behavior |
| WP0-08 | Build ownership matrix | Cross-cutting | Outputs of WP0-01 to WP0-07 | One ownership matrix with columns: source of truth, merge owner, enforcement owner, audit/provenance owner, unresolved owner | Every inventoried subsystem row has either a named owner or an explicit unresolved-owner note |
| WP0-09 | Build canonical glossary | Cross-cutting | [docs/core-systems-consolidation-plan.md](docs/core-systems-consolidation-plan.md), [docs/harness.md](docs/harness.md), [docs/local-agent-architecture.md](docs/local-agent-architecture.md), [docs/agent-harness-implementation.md](docs/agent-harness-implementation.md) | One glossary section fixing terms such as settings, defaults, overrides, policy, memory, cache, context, grounding, hook, listener, lifecycle event, template contract | The same term is not defined inconsistently across the core planning docs |
| WP0-10 | Build scope lock map | Cross-cutting | Outputs of WP0-01 to WP0-09 | One matrix that maps WP-1 to WP-6 to exact likely classes/files and explicit out-of-scope neighbors | Each later work package has a bounded start scope and at least one explicit non-goal |
| WP0-11 | Record unresolved ambiguities | Cross-cutting | Findings from all WP-0 tasks | One issue list of boundary ambiguities, but documented as later-package input rather than solved immediately | Open questions are captured without expanding WP-0 into design implementation |
| WP0-12 | Review and freeze WP-0 baseline | Cross-cutting | All WP-0 artifacts | One review-ready summary stating “approved to start WP-1” or “inventory gaps remain” | A reviewer can decide whether WP-1 can start without needing fresh discovery work |

##### WP-0 Recommended Execution Sequence

The recommended order is:

1. `WP0-01` settings
2. `WP0-02` policy
3. `WP0-04` context
4. `WP0-03` memory
5. `WP0-05` lifecycle / harness events
6. `WP0-06` hook surfaces
7. `WP0-07` template contract entry points
8. `WP0-08` ownership matrix
9. `WP0-09` glossary
10. `WP0-10` scope lock map
11. `WP0-11` unresolved ambiguities
12. `WP0-12` final review and freeze

This order is intentional:

- settings and policy define authority boundaries first
- context and memory need those boundaries to be inventoried correctly
- lifecycle and hooks are easier to classify after runtime ownership is visible
- template contract mapping is more accurate after the previous layers are named

##### WP-0 Task-Level Execution Notes

###### Task `WP0-01` — Inventory settings sources

Expected notes to capture:

- whether the source is platform, managed system, workspace, template, desktop-local, or runtime-only
- whether the source is durable or ephemeral
- whether the source is team-shared or machine-local
- whether the source can affect policy, or only regular settings

Do not do in this task:

- no resolver extraction
- no precedence rewrite
- no desktop config behavior changes

###### Task `WP0-02` — Inventory policy entry points

Expected notes to capture:

- where policy is defined
- where policy is merged
- where policy is enforced
- where policy is surfaced to approvals/audit

Special focus:

- current `WorkspacePolicy` fields
- template-policy merge inside [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)
- approval-linked guard flow

###### Task `WP0-03` — Inventory memory surfaces

Expected notes to capture:

- file-backed memory vs service-level memory orchestration
- user-triggered memory writes vs automatic consolidation writes
- memory recall vs memory promotion vs memory summarization
- whether a surface owns durable knowledge or just exposes access to files

Special caution:

- do not treat every `MEMORY.md` touchpoint as a memory authority surface automatically

###### Task `WP0-04` — Inventory context assembly surfaces

Expected notes to capture:

- raw source providers
- routing logic
- window fitting
- retry compaction
- final model injection points

Special focus:

- current role split between `ContextRouterService` and `ConversationWindowManager`
- how graph nodes trigger prompt-too-long fallback behavior

###### Task `WP0-05` — Inventory lifecycle and harness events

Expected notes to capture:

- canonical event name as currently emitted
- producer class
- consumer class
- whether the event is user-visible, audit-visible, or internal only
- whether the event already has enough metadata for later lifecycle normalization

###### Task `WP0-06` — Inventory hook surfaces

Expected notes to capture:

- hook event families already present
- event adaptation path from existing Spring events
- current action execution types
- rate limit / concurrency / audit surfaces already provided by hook dispatch

Do not do in this task:

- no hook trust redesign
- no plugin architecture expansion

###### Task `WP0-07` — Inventory template contract entry points

Expected notes to capture:

- which fields are pure descriptive metadata
- which fields seed workspace files
- which fields influence runtime behavior
- which fields behave like quality/acceptance declarations
- which fields may currently leak into policy semantics

###### Task `WP0-08` — Build ownership matrix

Required columns:

- subsystem / surface
- source of truth
- merge owner
- enforcement owner
- audit / provenance owner
- unresolved notes

Completion rule:

- no row should be left ownerless without an explicit unresolved marker

###### Task `WP0-09` — Build canonical glossary

Required minimum terms:

- settings
- defaults
- overrides
- policy
- memory
- cache
- context
- grounding
- hook
- listener
- lifecycle event
- template contract
- local runtime state

###### Task `WP0-10` — Build scope lock map

Required output shape:

- package name
- in-scope classes/files
- adjacent but excluded classes/files
- allowed change types
- forbidden change types

###### Task `WP0-11` — Record unresolved ambiguities

Typical ambiguity categories:

- unclear owner
- duplicate source of truth
- mixed policy/settings semantics
- memory/context overlap
- hook/lifecycle naming mismatch
- template metadata fields with unclear authority level

###### Task `WP0-12` — Review and freeze WP-0 baseline

Review questions:

1. Can WP-1 start without additional discovery?
2. Are settings sources and policy sources clearly separated?
3. Are memory, cache, context, and grounding no longer being mixed in the inventory?
4. Are lifecycle and hook entry points documented enough for later normalization?
5. Are template contract fields bounded enough to avoid schema sprawl in WP-6?

##### WP-0 Expanded Execution Worksheets

The following worksheets convert `WP0-01 ~ WP0-12` into directly executable documentation tasks.

Usage rule:

- one worksheet can be executed independently
- each worksheet should produce a visible markdown artifact section
- if evidence is uncertain, capture it under “Open Questions” rather than inferring ownership

###### Worksheet `WP0-01` — Inventory Settings Sources

**Task goal**

Build the first authoritative inventory of all current settings sources and classify them by scope, persistence, trust, and effect surface.

**Files / classes to inspect**

- [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)
- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)
- [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs)
- representative template files under `mateclaw-server/src/main/resources/templates/`

**Execution steps**

1. List every settings-bearing source discovered in the inspected files.
2. For each source, classify:
   - source type
   - persistence type
   - scope
   - trust level
   - whether it affects regular behavior, safety behavior, or both
3. Mark whether the source is read directly, merged indirectly, or used only as fallback.
4. Mark any settings source that is machine-local but currently capable of influencing shared behavior.
5. Record unresolved precedence ambiguity if the source order is implicit rather than explicit.

**Required output table template**

| Source ID | Source Name | Backing Class/File | Storage Medium | Scope | Shared or Local | Durable or Ephemeral | Trust Class | Affects Settings | Affects Policy | Known Consumers | Precedence Notes | Open Question |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one completed settings-source inventory table
- one short summary paragraph naming the current de facto precedence chain
- one list of unclear precedence points

**Acceptance checks**

- system settings, workspace settings, template defaults, desktop-local config, and runtime-time overrides are all represented
- every row has a scope classification
- every row has a consumer or explicit “consumer unknown” note

**Explicit non-goals**

- no new resolver
- no code extraction
- no changing desktop or server config behavior

**WP0-01 first-pass filled inventory**

| Source ID | Source Name | Backing Class/File | Storage Medium | Scope | Shared or Local | Durable or Ephemeral | Trust Class | Affects Settings | Affects Policy | Known Consumers | Precedence Notes | Open Question |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S01 | Server hardcoded defaults | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java) | Java constants and `getValue(key, default)` fallback | Server-global | Shared | Durable in code, used as runtime fallback | managed | yes | indirect only | `getSettings()`, `getAllSettings()`, search/media provider services | Lowest visible server-layer fallback when DB row missing | Whether other server modules implement separate unmanaged defaults outside `SystemSettingService` |
| S02 | System managed settings rows | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java) | DB table via `SystemSettingMapper` | Server-global | Shared | Durable | managed | yes | sometimes, depending on field semantics | settings UI/API, provider selection, feature switches, model/media/search services | Overrides server hardcoded defaults when row exists | Need later field classification to distinguish regular settings from policy-like switches |
| S03 | Environment fallback for server integration defaults | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java#L77-L81) | Process environment variables | Server-global deployment | Shared at deployment level | Ephemeral per deployment, effectively durable until env changes | managed | yes | no | currently visible for `SEARXNG_BASE_URL` resolution | Used only when DB value is blank; currently narrower than DB-backed settings | Need inventory pass later to verify whether other services use direct env fallbacks outside `SystemSettingService` |
| S04 | Workspace settings JSON | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java#L1424-L1455) | `WorkspaceEntity.settingsJson` JSON blob | Workspace | Shared | Durable | trusted shared | yes | yes, because `workspacePolicy` and `projectPermissionMode` are embedded inside settings JSON | workspace CRUD, policy resolution, project permission resolution | Merged from existing JSON + incoming JSON, then forced keys are rewritten | Needs later separation between ordinary workspace settings and safety authority fields |
| S05 | Workspace direct fields mirrored into settings JSON | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java#L1395-L1488) | Entity fields + normalized JSON serialization | Workspace | Shared | Durable | trusted shared | yes | yes | `resolveProjectPermissionMode()`, `resolveWorkspacePolicy()`, tool guard context building | `projectPermissionMode` and `workspacePolicy` are treated as forced effective values during merge | Whether any additional workspace-level settings live outside `settingsJson` and should enter the same source model |
| S06 | Built-in template defaults | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java), [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json), [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json) | Classpath JSON templates | Template / agent bootstrap | Shared | Durable | managed | yes | yes for template-provided default workspace policy and runtime constraints | `applyTemplate()`, template health checks, agent creation, workspace file seeding | Applies at agent/template bootstrap time, not as a universal global override chain | Need later classification of which template fields are defaults vs advisory metadata vs policy-tightening hints |
| S07 | Template metadata snapshot persisted onto agent | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java#L570-L583) | `AgentEntity.templateMetadataJson` | Agent | Shared within workspace/agent record | Durable | trusted shared | yes | potentially, because downstream runtime may read template-provided policy/runtime declarations | agent runtime construction, later policy merge in execution path | Snapshot is derived from template file, excludes `systemPrompt` and `workspaceFiles`, and becomes per-agent persisted state | Need later inventory of downstream consumers that read `templateMetadataJson` directly rather than the original template file |
| S08 | Desktop packaged defaults | [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs) | hardcoded defaults via `createDefaultConfig()` and packaged-aware backend URL resolution | Desktop machine | Local | Durable in code, used as runtime fallback | local trusted | yes | no | desktop startup, local server target resolution, proxy config dialog | Used when no persisted config exists; packaged mode switches default backend target | Need later check whether packaged defaults should remain purely local runtime state and never appear as shared truth |
| S09 | Desktop environment overrides | [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs) | Process environment variables such as `METAY_BACKEND_URL`, `MATECLAW_DEFAULT_BACKEND_URL` | Desktop machine / packaging environment | Local | Ephemeral per environment, effectively durable until process/env changes | local trusted | yes | no | desktop config load path, packaged builds, runtime backend selection | Forced backend env has higher precedence than persisted config; default backend env overrides hardcoded packaged/dev default | Need explicit documentation later for forced override vs default override semantics |
| S10 | Desktop persisted local config | [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs), [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs#L324-L329) | user config file under `.metay-desktop/config.json` | Desktop machine | Local | Durable | local trusted | yes | no | desktop proxy settings, backend target resolution, auto-start backend behavior, renderer IPC config screens | Overrides packaged defaults but can itself be overridden by forced backend env | Already proven to influence packaged runtime strongly; must stay classified as local runtime state only |
| S11 | Desktop runtime request-scoped test override | [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs#L334-L340) | IPC payload only | Desktop current session | Local | Ephemeral | local trusted | yes | no | proxy/backend connectivity test flow | Used only inside `test-server`; does not persist | Need later confirmation whether any other desktop IPC path allows request-scoped config override |

**WP0-01 current de facto precedence summary**

First-pass observation shows that the project already has multiple independent settings chains rather than one unified chain.

Current visible chains are:

1. **server managed settings chain**  
   DB system settings → server hardcoded fallback → limited env fallback in specific integrations such as `SEARXNG_BASE_URL`
2. **workspace shared settings chain**  
   existing `settingsJson` → incoming workspace payload merge → forced rewrite of `projectPermissionMode` and `workspacePolicy`
3. **template bootstrap chain**  
   built-in template JSON → agent fields / template metadata snapshot / workspace seed files / default knowledge bases
4. **desktop local runtime chain**  
   forced backend env → persisted local config → packaged/dev defaults
5. **desktop request-scoped temporary override chain**  
   IPC payload override → current effective API target, but only for connectivity test flow

This confirms that the current system does **not** yet expose one canonical “effective settings” model. It already operates through several subsystem-specific precedence chains.

**WP0-01 open questions and follow-up notes**

1. `SystemSettingService` clearly exposes DB + code defaults + one env fallback, but later passes should verify whether other provider services implement direct env/config fallbacks outside this service.
2. `WorkspaceService` stores both ordinary settings and authority-bearing fields inside `settingsJson`, which is the main reason `WP-1` and `WP-2` must stay adjacent.
3. Template JSON currently acts as both descriptive metadata and runtime default source; later work must separate “template defaults” from “template contract” more explicitly.
4. Desktop config has a strict local-runtime role today, but because it determines the active backend target it has high operational impact and must never be mistaken for shared settings truth.
5. The current inventory still needs one later cross-check against agent runtime consumers to confirm exactly where `templateMetadataJson` is read back as effective configuration.

###### Worksheet `WP0-02` — Inventory Policy Entry Points

**Task goal**

Identify where policy is defined, merged, enforced, and coupled to approvals.

**Files / classes to inspect**

- [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)
- [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java)
- [mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java)

**Execution steps**

1. Enumerate all current policy fields visible through `WorkspacePolicy` and related merge helpers.
2. Identify where each policy field is:
   - created or loaded
   - merged
   - interpreted
   - enforced
3. Identify where approval workflow uses policy outcomes rather than independent logic.
4. Identify whether template metadata participates in policy tightening.
5. Record cases where policy semantics are mixed with ordinary settings semantics.

**Required output table template**

| Policy Dimension | Defining Type/File | Load Owner | Merge Owner | Enforcement Owner | Approval Coupling | Template Tightening? | Runtime Metadata Surface | Audit Surface | Ambiguity |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one policy authority inventory table
- one restrictive-merge observation note
- one list of mixed policy/settings semantics to revisit in `WP-2`

**Acceptance checks**

- tool permissions, sandbox, approval, network, and path restrictions are all mapped if present
- template-policy merge location is explicitly named
- approval-linked enforcement path is documented

**Explicit non-goals**

- no change to guard outcomes
- no rewrite of approval flow
- no new policy schema

**WP0-02 first-pass filled inventory**

| Policy ID | Policy Dimension | Defining Type/File | Load Owner | Merge Owner | Enforcement Owner | Approval Coupling | Template Tightening? | Runtime Metadata Surface | Audit Surface | Ambiguity |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P01 | Sandbox mode | [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java) | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) via normalized workspace policy resolution | [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java) via `mergeTemplatePolicy()` and restrictive sandbox merge | [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java) through `ToolInvocationContext` evaluation | indirect, because sandbox/risk decisions can require approval creation | yes, template `defaultWorkspacePolicy.sandboxMode` tightens workspace policy when not full access | `ToolInvocationContext.workspacePolicy`, `workspacePolicyMode` | guard audit recording | exact engine interpretation still needs later pass through guard engine internals |
| P02 | Approval policy | [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java) | `WorkspaceService` | `ToolExecutionExecutor` via `moreRestrictiveApproval()` | `ToolGuardService` and then [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java) | direct, because policy outcomes create pending approvals and later reusable grants | yes | pending approval metadata, approval key, grant scope | approval DB + guard audit | later needs explicit split between approval policy and per-tool risk-triggered approval |
| P03 | Network policy | `WorkspacePolicy.networkPolicy` | `WorkspaceService` | `ToolExecutionExecutor` via `moreRestrictiveNetwork()` | `ToolGuardService` / guard engine | indirect | yes | workspace policy in invocation context | guard audit | need later check whether non-tool network paths bypass this policy surface |
| P04 | Allowed paths | `WorkspacePolicy.allowedPaths` | `WorkspaceService` JSON hydration | `ToolExecutionExecutor` via `mergeAllowedPaths()` | `ToolGuardService` / guard engine | indirect | yes | workspace policy payload | guard audit | merge is additive, but final restriction semantics still need formal contract |
| P05 | Denied paths | `WorkspacePolicy.deniedPaths` | `WorkspaceService` | `ToolExecutionExecutor` via union merge | `ToolGuardService` / guard engine | indirect | yes | workspace policy payload | guard audit | later confirm whether denied paths always dominate allowed paths in engine logic |
| P06 | Risk overrides | `WorkspacePolicy.riskOverrides` | `WorkspaceService` normalization | `PolicyMergeHelper.mergeRiskOverrides()` with workspace-first collision lock | `ToolGuardService` / guard engine and approval key builder | direct, because severity/findings drive approval creation and approval reuse | yes | `GuardEvaluation`, approval key in [mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java) | guard audit + approval DB persistence | workspace keys currently win on collision; stronger per-decision severity lattice is still a later refinement |
| P07 | Project permission mode | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) | `WorkspaceService.resolveProjectPermissionMode()` | `WorkspaceService.mergeSettingsJson()` forces it into settings JSON and `ToolExecutionExecutor` converts it to `fullAccess` behavior | executor decides whether template tightening is bypassed when full access | indirect | template tightening disabled in full-access path | `workspacePolicyMode` in invocation context | weak / runtime-only | behaves like policy authority but is stored through settings mechanics |

**WP0-02 current policy flow summary**

Current first-pass policy flow is:

1. workspace policy is persisted and normalized in [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
2. runtime tool execution builds a `ToolInvocationContext` in [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java#L827-L856)
3. template policy is merged at execution time inside the executor
4. [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java) evaluates and audits the call
5. [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java) persists pending approvals and [mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java) builds reusable grant identity

This means policy is already cross-cutting, but its load, merge, enforcement, and approval coupling are still distributed across multiple modules.

**WP0-02 open questions and follow-up notes**

1. `projectPermissionMode` behaves like policy but is stored through a settings-oriented path.
2. `riskOverrides` same-key collisions are now locked to workspace-first precedence; a later pass can still introduce a stronger per-decision severity lattice if the guard engine needs it.
3. Template policy merge lives in execution code rather than in a shared effective-policy resolver.
4. A later pass should inspect guard-engine internals to complete field-by-field enforcement mapping.

###### Worksheet `WP0-03` — Inventory Memory Surfaces

**Task goal**

Separate memory ownership surfaces from file access surfaces and from consolidation/summarization surfaces.

**Files / classes to inspect**

- [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java)
- [mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java](mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java)
- [mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java](mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java)
- [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java)

**Execution steps**

1. Identify all surfaces that read or write `MEMORY.md`, structured memory files, or daily memory notes.
2. Separate them into categories:
   - manager/orchestrator
   - provider/storage abstraction
   - consolidation/summarization
   - recall/retrieval
   - direct tool/file access
3. Mark whether each surface creates durable memory, consumes durable memory, or only exposes low-level access.
4. Record whether provenance is captured when memory changes.
5. Record places where project cache, facts, or daily notes may be mixed conceptually with long-term memory.

**Required output table template**

| Surface ID | Class/File | Surface Type | Reads Durable Memory | Writes Durable Memory | Uses Daily Notes | Uses Structured Files | Exposes Tool Access | Emits Provenance/Event | Candidate Owner | Open Boundary Question |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one memory surfaces inventory table
- one “what is not memory authority” note
- one list of memory-vs-cache-vs-context confusion points

**Acceptance checks**

- `MemoryManager`, provider, emergence, recall, summarization, and tool access are not conflated into one role
- `MEMORY.md` file-touchpoints are classified rather than assumed to be authority surfaces
- every writer has an identified write reason or open question note

**Explicit non-goals**

- no change to memory write rules
- no format migration for `MEMORY.md`
- no recall algorithm changes

**WP0-03 first-pass filled inventory**

| Surface ID | Class/File | Surface Type | Reads Durable Memory | Writes Durable Memory | Uses Daily Notes | Uses Structured Files | Exposes Tool Access | Emits Provenance/Event | Candidate Owner | Open Boundary Question |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M01 | [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java) | orchestrator / integration point | yes, via providers | indirect via provider sync/lifecycle hooks | indirect | indirect | yes, collects provider tool beans | no unified provenance surface | Memory SPI layer | should remain orchestration-only rather than governance owner |
| M02 | [mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java](mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java) | built-in provider backed by workspace files | yes | no direct writes in provider | yes, if daily notes are enabled workspace files | not explicit | no direct extra tools | no direct event in shown code | built-in provider layer | provider injects memory into system prompt but does not own recall/promotion |
| M03 | [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java) | lifecycle mediator | yes | indirect via `memoryManager.syncAll()` | indirect | indirect | no | publishes turn lifecycle events | memory lifecycle layer | merges memory context with routed context before LLM call |
| M04 | [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java) | per-conversation summarization writer | yes | yes | yes | no | no | no explicit write event in shown section | summarization layer | writes both daily notes and canonical files, so promotion rules must be clarified later |
| M05 | [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java) | dream / consolidation writer | yes | yes | yes | indirect through archive/fact services | no | publishes `MemoryWriteEvent` and dream outcome events | emergence layer | clearly a durable-memory authority surface |
| M06 | [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java) | recall tracking / scoring | reads recall metadata, not canonical memory text itself | writes recall metrics only | indirect through filename tracking | yes, file-level recall entities | no | no direct event in shown code | recall scoring layer | not memory content itself; it is evidence about retrieval |
| M07 | [mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java](mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java) | direct tool/file access | yes | yes | yes | no | yes | no unified memory event emission in shown methods | tool access layer | should be treated as access surface, not memory authority owner |
| M08 | [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java) | lightweight durable-memory append tool | yes | yes | no | no | yes | publishes `MemoryWriteEvent` | memory tool layer | free-form lessons need future governance boundaries |

**WP0-03 current memory surface summary**

The memory system is already layered into orchestration, provider injection, lifecycle mediation, summarization, emergence, recall scoring, and tool-based access. This is a strong sign that future work should normalize roles rather than flatten them.

**WP0-03 open questions and follow-up notes**

1. `WorkspaceMemoryTool` can write canonical memory files but should remain an access surface, not the governance source of truth.
2. `MemorySummarizationService` writes both daily notes and long-term files, so promotion boundaries are still blurry.
3. `MemoryRecallService` tracks retrieval evidence, not memory truth; this distinction must remain explicit.
4. `MemoryLifecycleMediator` is the operational join point between memory and context.

###### Worksheet `WP0-04` — Inventory Context Assembly Surfaces

**Task goal**

Map where context comes from, where it is reduced, and where it is finally injected.

**Files / classes to inspect**

- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/StateGraphReActAgent.java](mateclaw-server/src/main/java/vip/mate/agent/graph/StateGraphReActAgent.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java](mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/node/ReasoningNode.java](mateclaw-server/src/main/java/vip/mate/agent/graph/node/ReasoningNode.java)

**Execution steps**

1. Identify all current context input categories visible in the inspected files.
2. Mark which class selects context, which class trims/fits it, and which class retries on overflow.
3. Identify whether attachments, wiki, memory, project insights, and recent conversation are handled together or separately.
4. Mark prompt-too-long recovery points.
5. Record any missing provenance fields or source classification gaps.

**Required output table template**

| Context Source | Producer | Router / Selector | Window / Compaction Owner | Final Injection Owner | Retry / Fallback Point | Provenance Present? | Budget Rule Visible? | Open Question |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one context assembly inventory table
- one role split note for `ContextRouterService` vs `ConversationWindowManager`
- one list of source taxonomy gaps for future `WP-4`

**Acceptance checks**

- at least recent conversation, routed context, memory-related context, and project-aware context are mapped if present
- retry compaction path is documented
- final injection surface is explicitly named

**Explicit non-goals**

- no token budgeting redesign
- no compaction algorithm rewrite
- no prompt-building refactor

**WP0-04 first-pass filled inventory**

| Context ID | Context Source | Producer | Router / Selector | Window / Compaction Owner | Final Injection Owner | Retry / Fallback Point | Provenance Present? | Budget Rule Visible? | Open Question |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| C01 | project insight / project cache | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) | [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java) | none in router beyond bounded selection | `ContextRouterService.buildInjectionBlock()` | omission under budget pressure only | partial source labels | yes, char budget | later move to unified token-aware budgeting |
| C02 | workspace memory file hints | workspace files via `WorkspaceFileService` | `ContextRouterService.summarizeMemoryFiles()` | none in router | router injection block, then merged by memory lifecycle mediator | later history/window compaction only | partial | yes | separate durable memory recall vs file summary remains blurry |
| C03 | wiki / KB route hints | `WikiKnowledgeBaseService` | `ContextRouterService.summarizeKnowledgeBases()` / `selectWikiHints()` | none | router injection block | omission under budget | partial | yes | currently route-oriented rather than full grounding provenance |
| C04 | recent sessions / session recall hints | `SessionSearchService` | `ContextRouterService.summarizeRecentSessions()` / `selectSessionHints()` | none | router injection block | omission under budget | partial | yes | needs later convergence with session-temporary materials |
| C05 | template route declarations | `templateMetadataJson` parsed from agent | `ContextRouterService.resolveTemplateRouteConfig()` | none | router injection block | no explicit retry path | partial | yes | template context declarations need formal contract grouping |
| C06 | conversation history messages | conversation service / graph runtime | not in router | [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java) | graph runtime after `fitToWindow()` | soft trim, hard clear, structured summary, retry compaction | low / mostly implicit | yes, token budget and trigger ratio | provenance model for compressed history is incomplete |
| C07 | memory prefetch block | `MemoryManager.prefetchAll()` | none before merge | not separately compacted in manager | [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java) | none explicit in shown path | fenced as `<memory-context>` | no explicit budget | future unified budgeting needed |

**WP0-04 current context assembly summary**

Current context assembly is already split into:

- routed hint selection in `ContextRouterService`
- history compaction and retry handling in `ConversationWindowManager`
- cross-source merge timing in `MemoryLifecycleMediator`

So the project already has most of the ingredients of a context lifecycle; what is missing is a unified contract and taxonomy.

**WP0-04 role split note**

- `ContextRouterService` owns source prioritization and small bounded hint blocks
- `ConversationWindowManager` owns history token pressure handling and progressive compression
- `MemoryLifecycleMediator` owns combining routed context and memory-prefetch context before model invocation

**WP0-04 open questions and follow-up notes**

1. Provenance is partial and source labels are not yet standardized.
2. Budgeting is split between character-based and token-based logic.
3. Attachments and session-temporary evidence are not yet first-class in the same taxonomy.
4. Memory-related context is merged operationally but not formally labeled in one model.

###### Worksheet `WP0-05` — Inventory Lifecycle and Harness Events

**Task goal**

Document current graph-event publication and harness ingestion as the baseline event vocabulary.

**Files / classes to inspect**

- [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java)
- [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java)
- [mateclaw-server/src/main/java/vip/mate/harness/model/HarnessRun.java](mateclaw-server/src/main/java/vip/mate/harness/model/HarnessRun.java)
- [mateclaw-server/src/main/java/vip/mate/harness/controller/HarnessRunController.java](mateclaw-server/src/main/java/vip/mate/harness/controller/HarnessRunController.java)
- event publishing call sites in plan and reasoning graph nodes if needed

**Execution steps**

1. Enumerate current `GraphEventPublisher` event types.
2. Trace where those event types are published and where they are ingested.
3. For each event type, record whether it updates harness run state, step state, tool state, approval state, or diagnostics only.
4. Mark missing metadata fields that later lifecycle normalization may need.
5. Note any event names that already imply lifecycle categories.

**Required output table template**

| Event Name | Publisher Utility | Known Publish Sites | Harness Consumer Method | State Affected | User Visible? | Audit Visible? | Missing Metadata | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one graph-to-harness event map
- one lifecycle-category suggestion note
- one missing-envelope-fields note for `WP-5`

**Acceptance checks**

- phase, plan, step, tool, approval, direct result, and perf events are all accounted for if present
- ingestion path inside `HarnessRunService` is traceable for each inventoried event

**Explicit non-goals**

- no event renaming
- no new event schema
- no harness API redesign

**WP0-05 first-pass filled inventory**

| Event Name | Publisher Utility | Known Publish Sites | Harness Consumer Method | State Affected | User Visible? | Audit Visible? | Missing Metadata | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `phase` | [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java) `phase()` | planning, reasoning, summarizing nodes | `recordPhaseStep()` in [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java) | harness phase timeline | yes | partially | no explicit source class / policy scope / trace context | already lifecycle-like but coarse |
| `plan_created` | `planCreated()` | plan generation nodes | `recordPlanCreated()` | planning state / run metadata | yes | partially | no explicit revision/actor fields | natural prompt-plan lifecycle event |
| `plan_step_started` | `stepStarted()` | step execution nodes | `recordPlanStepStarted()` | step running state | yes | partially | no parent plan identity beyond index/title | |
| `plan_step_completed` | `stepCompleted()` | step execution nodes | `recordPlanStepCompleted()` | step completion state | yes | partially | no structured validation/evidence metadata | |
| `tool_call_started` | `toolStart()` | reasoning / step execution around tool calls | `recordToolStarted()` | tool invocation running state | yes | partially | no policy decision snapshot | tool call ID support already improves pairing |
| `tool_call_completed` | `toolComplete()` | tool execution / guard helper | `recordToolCompleted()` | tool invocation completion state | yes | partially | no normalized blocked-vs-executed cause field | |
| `tool_approval_requested` | `toolApprovalRequested()` | guard / executor approval path | `recordApprovalRequested()` | approval state in harness | yes | yes | no grant scope / approval key in event envelope | strong bridge event between runtime and approval workflow |
| `tool_direct_result` | `toolDirectResult()` | returnDirect tool flow | `recordToolDirectResult()` | assistant-message direct result | yes | limited | no richer provenance or render policy metadata | special completion event |
| `perf_summary` | `perfSummary()` | planning/reasoning nodes | `recordPerfSummary()` | diagnostics / execution summary | likely yes in diagnostics | yes | no standardized metric schema | already observability-oriented |

**WP0-05 current event flow summary**

Current lifecycle path is: graph nodes publish `GraphEventPublisher` events → agents carry them → `HarnessRunService` ingests them and mutates run/step/tool/approval state.

This is already a working lifecycle backbone, but the event envelope is still optimized for harness/UI updates rather than a unified cross-system event contract.

**WP0-05 open questions and follow-up notes**

1. Lifecycle buckets are already implicit in event names, but envelope fields are not normalized.
2. Approval events span graph runtime and DB-backed approval workflow; future normalization must bridge both.
3. Harness events and hook events should align later, not be collapsed immediately.

###### Worksheet `WP0-06` — Inventory Hook Surfaces

**Task goal**

Map the current hook system as an extensibility surface separate from graph/harness events.

**Files / classes to inspect**

- [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java)
- [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java)
- [mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java](mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java)
- hook event classes under `mateclaw-server/src/main/java/vip/mate/hook/event/`
- [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)
- representative hook actions under `mateclaw-server/src/main/java/vip/mate/hook/action/`

**Execution steps**

1. List all current `MateHookEvent` families.
2. Identify how hook events are produced directly or adapted from Spring events.
3. Identify how hook registry matching works at a high level.
4. List available hook action types.
5. Record built-in safety and governance elements already present: rate limit, concurrency, audit logging.

**Required output table template**

| Hook Event Family | Event Class/File | Produced By | Adapted From Spring? | Matched By | Executed Via | Action Types Seen | Built-in Limits / Audit | Trust Assumption Note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one hook event family inventory
- one dispatch-governance note
- one hook-vs-listener distinction note for future `WP-5`

**Acceptance checks**

- event families, registry, dispatcher, and actions are all documented as separate concerns
- rate limit/concurrency/audit behavior is acknowledged
- current hook trust assumptions are captured even if still implicit

**Explicit non-goals**

- no hook trust redesign
- no plugin marketplace plan
- no action sandbox redesign

**WP0-06 first-pass filled inventory**

| Hook Event Family | Event Class/File | Produced By | Adapted From Spring? | Matched By | Executed Via | Action Types Seen | Built-in Limits / Audit | Trust Assumption Note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| agent | `AgentEvent` under `vip.mate.hook.event` | direct publisher sites not inspected in this pass | not shown | [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java) | [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java) | built-in, HTTP, shell, channel message actions from hook action package | global enable, rate limit, concurrency cap, dispatch deadline, async audit | trust currently comes mainly from enabled DB hook entries |
| tool | `ToolEvent` | direct tool-related publishers | not shown | `HookRegistry` | `HookDispatcher` | same family | same | later align against graph tool lifecycle events |
| session | `SessionEvent` | [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java) from `ConversationCompletedEvent` | yes | `HookRegistry` | `HookDispatcher` | same family | same | session hook bus already wraps existing Spring events |
| memory | `MemoryEvent` | `SpringEventAdapter` from conversation completion and likely other memory publishers later | yes | `HookRegistry` | `HookDispatcher` | same family | same | current family exists but not yet mapped to full memory lifecycle taxonomy |
| wiki | `WikiEvent` | `SpringEventAdapter` from `WikiProcessingEvent` | yes | `HookRegistry` | `HookDispatcher` | same family | same | clearly adapted family |
| channel | `ChannelEvent` | direct publisher sites not inspected in this pass | unknown | `HookRegistry` | `HookDispatcher` | same family | same | needs later direct publisher tracing |
| cron | `CronEvent` | direct publisher sites not inspected in this pass | unknown | `HookRegistry` | `HookDispatcher` | same family | same | needs later direct publisher tracing |

**WP0-06 current hook surface summary**

The hook subsystem already has four distinct layers:

- typed hook events via [mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java](mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java)
- event adaptation from existing Spring events via [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)
- matching/index management via `HookRegistry`
- governed action execution via `HookDispatcher`

**WP0-06 dispatch-governance note**

Current governance already includes a strong baseline:

- global enable switch
- hook rate limiting
- concurrency throttling
- hard dispatch deadline
- async audit writes
- main-chain failure isolation

**WP0-06 open questions and follow-up notes**

1. Hook source trust is still implicit rather than explicitly classified.
2. Hook event vocabulary is separate from harness event vocabulary.
3. `channel` and `cron` direct publishers still need later source tracing.

###### Worksheet `WP0-07` — Inventory Template Contract Entry Points

**Task goal**

Document which template fields already function as runtime contracts rather than just descriptive metadata.

**Files / classes to inspect**

- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/controller/TemplateController.java](mateclaw-server/src/main/java/vip/mate/agent/controller/TemplateController.java)
- [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json)
- [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json)
- at least 2-3 additional representative built-in template JSON files

**Execution steps**

1. Enumerate major field groups visible in built-in template JSON.
2. Identify which fields are used only for listing/display.
3. Identify which fields drive:
   - agent defaults
   - workspace file seeding
   - runtime behavior
   - health checks / quality / acceptance
   - policy-tightening or safety defaults
4. Identify fields whose authority level is unclear.
5. Record schema drift patterns across representative templates.

**Required output table template**

| Template Field / Field Group | Seen In Template Files | Consumed By Class/File | Functional Role | Business Metadata or Runtime Contract? | Can Tighten Policy? | Seeds Workspace? | Open Authority Question |
| --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one template contract entry-point table
- one field-group normalization note
- one list of ambiguous authority fields for future `WP-6`

**Acceptance checks**

- at least coding-agent and teacher template contract-relevant fields are mapped
- descriptive metadata vs runtime contract fields are distinguished
- workspace seed behavior is explicitly identified if present

**Explicit non-goals**

- no template schema redesign
- no changes to built-in template content
- no UI catalog changes

**WP0-07 first-pass filled inventory**

| Template Field / Field Group | Seen In Template Files | Consumed By Class/File | Functional Role | Business Metadata or Runtime Contract? | Can Tighten Policy? | Seeds Workspace? | Open Authority Question |
| --- | --- | --- | --- | --- | --- | --- | --- |
| identity (`id`, `name`, `category`, `domain`, `version`) | coding-agent, teacher-exam-assistant | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java) | list/display + bootstrap identity | mostly business metadata with some bootstrap use | no | no | low ambiguity |
| `runtime` | both | `TemplateService` health checks + metadata snapshot | preferred mode / allowed modes / behavior hints | runtime contract | indirect only | no | needs later full consumer inventory |
| `permissions` | both | template config/listing surfaces | usability/editability governance hints | metadata with governance meaning | no direct runtime tightening in inspected path | no | later check if all fields are truly enforced |
| `defaultWorkspacePolicy` | both | stored in `templateMetadataJson`, later parsed by [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java) | template-level safety defaults | runtime contract | yes | no | already one of the clearest contract fields |
| `agentProfile` | both | `TemplateService.applyTemplate()` | profile and UX defaults for created agent | runtime/bootstrap contract | no | no | UX metadata vs runtime identity still needs finer separation |
| `capabilityPack` | both | `TemplateService.applyTemplate()` + metadata snapshot | capability declaration / packaging | runtime contract | indirect | no | later map declared pack to real capability enforcement |
| `contextSources` | both | [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java) via template metadata snapshot | route knowledge/session/project-derived context | runtime contract | no direct policy, but affects context routing | no | should later join unified context taxonomy |
| `tools` | both | metadata snapshot / likely UI/runtime reference | declared tool surface and risk hints | contract-adjacent | not directly in inspected runtime path | no | later cross-check with actual tool registry/runtime |
| `qualityGates`, `mockAcceptanceTasks`, `mvpScope` | both | metadata snapshot and template health/readiness surfaces | acceptance / quality declaration | business contract with runtime-adjacent value | no | no | strong candidate for explicit acceptance contract section |
| `defaultKnowledgeBases` | both | `TemplateService.seedDefaultKnowledgeBases()` | workspace KB/page seeding | runtime/bootstrap contract | no | seeds KBs, not files | should be distinct from context routing declarations |
| `workspaceFiles` | coding-agent | `TemplateService.applyTemplate()` / `syncMissingWorkspaceFiles()` | seed files and prompt-file enablement | runtime/bootstrap contract | indirect, because seeded files influence prompts/memory | yes | materialization artifact vs authority source must stay distinct |
| `homeQuickStarts`, `interactionHints`, `starterPrompts` | both | agent home/UX metadata | onboarding guidance | business metadata | no | no | should remain outside authority model |

**WP0-07 current template contract summary**

Templates already bundle identity metadata, runtime defaults, context declarations, safety defaults, workspace materialization, knowledge seeding, and acceptance declarations. They are clearly more than prompt text.

**WP0-07 open questions and follow-up notes**

1. `defaultWorkspacePolicy` is already a true runtime contract field.
2. `contextSources` is runtime-significant and should later join the core context model.
3. `workspaceFiles` affect prompt/memory-adjacent behavior but should remain materialization output, not top-level authority.
4. `qualityGates` and `mockAcceptanceTasks` already look like an acceptance-contract layer.

###### Worksheet `WP0-08` — Build Ownership Matrix

**Task goal**

Convert the raw inventories from `WP0-01 ~ WP0-07` into one ownership matrix.

**Inputs required**

- completed outputs from `WP0-01` to `WP0-07`

**Execution steps**

1. Build one row per subsystem or functional surface.
2. Fill owner columns from observed code responsibility, not from assumption.
3. If multiple candidates exist, name the observed primary owner and list ambiguity in notes.
4. Mark any surface lacking a clear audit/provenance owner.
5. Separate implementation owner from conceptual source-of-truth owner if they differ.

**Required output table template**

| Subsystem / Surface | Source of Truth Owner | Merge Owner | Enforcement Owner | Audit / Provenance Owner | Main Classes / Files | Confidence | Unresolved Note |
| --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one ownership matrix across settings, policy, memory, context, lifecycle, hooks, templates
- one short summary of ownership gaps

**Acceptance checks**

- every major subsystem row is filled
- unresolved ownership is explicit, not silently omitted
- matrix is sufficient to assign future `WP-1 ~ WP-6` implementation responsibilities

**Explicit non-goals**

- no org/team assignment proposal
- no code movement proposal

**WP0-08 first-pass filled ownership matrix**

| Subsystem / Surface | Source of Truth Owner | Merge Owner | Enforcement Owner | Audit / Provenance Owner | Main Classes / Files | Confidence | Unresolved Note |
| --- | --- | --- | --- | --- | --- | --- | --- |
| system managed settings | `SystemSettingService` + DB rows | `SystemSettingService` | consuming domain services | distributed / weak | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java) | high | no unified provenance owner |
| workspace shared settings | `WorkspaceService` / `settingsJson` | `WorkspaceService.mergeSettingsJson()` | workspace-aware consumers | distributed / weak | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) | high | settings and policy still mixed |
| workspace policy | `WorkspaceService` + `WorkspacePolicy` | split between `WorkspaceService` and executor runtime merge | tool guard / approval pipeline | tool guard audit + approval persistence | workspace service + executor + approval workflow | medium-high | merge owner split across persistence and runtime |
| template runtime defaults | built-in template JSON + `templateMetadataJson` snapshot | `TemplateService` bootstrap and downstream runtime consumers | field-specific runtime consumers | weak / distributed | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java) | medium | no single effective-template resolver |
| desktop local runtime settings | desktop config file + env overrides | `desktopConfig.cjs` | Electron main process | local config file only | [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs), [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs) | high | strong operational impact but no shared authority |
| memory orchestration | `MemoryManager` provider chain | provider chain + lifecycle mediator | provider-specific + memory services | partial through memory events | memory SPI/lifecycle/services | medium | durable-memory governance owner split |
| durable memory files | workspace file store | summarization/emergence/tools | none centralized | partial through `MemoryWriteEvent` | memory services + tools | medium | not every writer emits same provenance |
| context routing | `ContextRouterService` | `ContextRouterService` | graph runtime via mediator | weak | [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java) | high | provenance incomplete |
| conversation window / compression | `ConversationWindowManager` | `ConversationWindowManager` | graph runtime | weak | [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java) | high | separate from router but part of same eventual lifecycle |
| graph/harness lifecycle | `GraphEventPublisher` + `HarnessRunService` ingestion | publisher utility only | `HarnessRunService` | harness run record | [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java), [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java) | high | event envelope not yet generalized |
| hook bus | `MateHookEvent` + DB-configured hooks | `HookRegistry` | `HookDispatcher` | hook audit table | [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java), [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java) | high | trust source owner still implicit |

**WP0-08 ownership gap note**

The strongest ownership gaps are:

- no single effective-settings owner across all source classes
- no single policy merge owner across workspace + template + runtime constraints
- no single durable-memory governance owner across summarization, emergence, and direct tools
- no single provenance owner across context, memory, and lifecycle events

###### Worksheet `WP0-09` — Build Canonical Glossary

**Task goal**

Freeze the minimum vocabulary needed to stop term drift in follow-up work packages.

**Files to inspect**

- [docs/core-systems-consolidation-plan.md](docs/core-systems-consolidation-plan.md)
- [docs/harness.md](docs/harness.md)
- [docs/local-agent-architecture.md](docs/local-agent-architecture.md)
- [docs/agent-harness-implementation.md](docs/agent-harness-implementation.md)

**Execution steps**

1. Extract current uses of core system terms from the main planning docs.
2. Detect conflicting or overloaded meanings.
3. Define a preferred meaning for each minimum term.
4. Add a “do not use as synonym for” note where confusion is common.
5. Record terms that still need future refinement.

**Required output table template**

| Term | Canonical Meaning | Not Equivalent To | Primary Usage Scope | Example Usage | Drift Risk / Note |
| --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one glossary table
- one note listing terms still overloaded in code or docs

**Acceptance checks**

- the required minimum term set is covered
- memory/cache/context/grounding are distinct
- hook/listener/lifecycle event are distinct enough for future work

**Explicit non-goals**

- no mass doc rewrite yet
- no code renaming sweep yet

**WP0-09 first-pass glossary**

| Term | Canonical Meaning | Not Equivalent To | Primary Usage Scope | Example Usage | Drift Risk / Note |
| --- | --- | --- | --- | --- | --- |
| settings | ordinary configurable defaults or preferences | policy | product/platform/workspace config | language, provider preference | mixed with policy in workspace settings |
| defaults | low-priority initial values used when stronger source absent | effective override | all config layers | hardcoded fallback, packaged backend default | often confused with current effective value |
| overrides | higher-priority value replacing a lower-priority default | policy veto | resolution chains | forced backend env, incoming workspace payload | precedence still distributed |
| policy | authority constraint that can tighten, constrain, or veto behavior | ordinary setting | tool/workspace safety | sandbox mode, approval policy | currently partially stored through settings structures |
| memory | durable knowledge intended to help future interactions | cache, context | long-term agent/workspace knowledge | `MEMORY.md`, promoted lessons | file-touchpoints often use term loosely |
| cache | recomputable summary optimized for reuse | memory | project understanding / derived summaries | project insight cache | often confused with durable knowledge |
| context | task-scoped working evidence injected for the current model call | memory | current-turn execution | routed context block, fitted history | current system has multiple mechanisms |
| grounding | externally sourced factual basis with provenance | memory | wiki/material-backed answers | KB evidence | not always explicitly separated from context |
| hook | configurable extension execution attached to event types | listener | extensibility bus | DB-configured hook action | trust model still implicit |
| listener | internal event consumer embedded in runtime | hook | internal lifecycle wiring | Spring `@EventListener` | docs/code may blur with hook |
| lifecycle event | normalized runtime state transition event | arbitrary log line | run/session/tool/approval/context/memory transitions | graph event, hook event | current vocab split across harness and hook systems |
| template contract | template fields that influence runtime/bootstrap behavior | mere descriptive metadata | agent bootstrap and behavior packaging | `defaultWorkspacePolicy`, `contextSources` | templates currently mix metadata and contract |
| local runtime state | machine-local operational config that must not become shared truth | shared workspace settings | desktop runtime | backendUrl/proxyUrl in desktop config | high operational impact despite local scope |

**WP0-09 overloaded terms note**

Highest-risk overloaded terms today:

- settings vs policy
- memory vs memory-file access vs recall metrics
- context vs routed hints vs history window vs grounding
- hook vs listener vs lifecycle event

###### Worksheet `WP0-10` — Build Scope Lock Map

**Task goal**

Turn the inventory into an implementation boundary map for `WP-1 ~ WP-6`.

**Inputs required**

- completed outputs from `WP0-01` to `WP0-09`

**Execution steps**

1. For each future work package, list the minimum in-scope files/classes.
2. For each package, list adjacent but excluded files/classes.
3. List allowed change types and forbidden change types.
4. Mark at least one expansion risk per package.
5. Ensure package boundaries do not overlap excessively without explanation.

**Required output table template**

| Future Package | In-Scope Classes / Files | Adjacent Excluded Classes / Files | Allowed Change Types | Forbidden Change Types | Expansion Risk | Notes |
| --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one scope lock matrix for `WP-1 ~ WP-6`
- one note listing high-risk overlap areas

**Acceptance checks**

- each future package has both a positive scope and a negative boundary
- no package is left with “entire module” as its only scope description

**Explicit non-goals**

- no scheduling estimate yet
- no implementation dependency graph beyond scope locking

**WP0-10 first-pass scope lock map**

| Future Package | In-Scope Classes / Files | Adjacent Excluded Classes / Files | Allowed Change Types | Forbidden Change Types | Expansion Risk | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| WP-1 settings resolution | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs), [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs), template metadata loader surfaces | approval internals, full guard-engine rewrite, UI redesign | resolver extraction, precedence docs, source classification, diagnostics metadata | approval redesign, policy-engine rewrite, desktop UX redesign | settings/policy blur | keep compatibility-first |
| WP-2 policy authority | [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java), [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java), approval grant/workflow metadata surfaces | approval UX, unrelated tool batching/concurrency logic | policy matrix, merge helper extraction, metadata enrichment, targeted tests | replacing approval pipeline, new policy backend | riskOverride semantics | should stay adjacent to WP-1 |
| WP-3 memory governance | [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java), [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java), memory services, [mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java](mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java), [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java) | new memory backends, UI consoles, dream algorithm overhaul | taxonomy docs, write filters, provenance normalization, governance rules | storage migration, broad prompt redesign | `MEMORY.md` touchpoint sprawl | governance before productization |
| WP-4 context lifecycle | [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java), [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java), [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java), graph assembly entry points | chat UI redesign, retrieval replacement, full prompt rewrite | source taxonomy, provenance metadata, fallback contracts, compaction states | second parallel context pipeline | split budget model | keep one context assembly path |
| WP-5 lifecycle / hook contract | [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java), [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java), [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java), [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java), hook event classes | replacing Spring event infra, plugin framework redesign, full observability platform | event map, envelope normalization, naming adapters, metadata enrichment | breaking hooks, replacing harness wholesale | harness vs hook vocabulary overlap | align names before structural changes |
| WP-6 template contract | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java), [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json), [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json), other built-in templates, [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java) as consumer | marketplace, onboarding UI overhaul, business-story redesign | field grouping, schema notes, compatibility mapping, validation checklist | new product flows, marketplace expansion | schema sprawl | normalize sections before adding fields |

**WP0-10 overlap warning note**

Highest overlap-risk surfaces:

- `WorkspaceService` between `WP-1` and `WP-2`
- `MemoryLifecycleMediator` between `WP-3` and `WP-4`
- graph/harness events vs hook events in `WP-5`
- `TemplateService` touching settings, policy, context, and template concerns

###### Worksheet `WP0-11` — Record Unresolved Ambiguities

**Task goal**

Capture all unresolved boundary questions without allowing them to derail `WP-0`.

**Inputs required**

- ambiguity findings from all previous `WP-0` worksheets

**Execution steps**

1. Consolidate ambiguity notes from prior worksheets.
2. Normalize them into issue categories.
3. Assign each ambiguity to the future package most likely to resolve it.
4. Mark whether the ambiguity blocks `WP-1` start or is only a later-package concern.
5. Keep wording factual and bounded.

**Required output table template**

| Ambiguity ID | Category | Description | Seen In | Likely Owning Future Package | Blocks WP-1? | Recommended Handling | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |

**Expected deliverable**

- one unresolved ambiguities register
- one blocker/non-blocker split

**Acceptance checks**

- ambiguity list is finite and categorized
- each ambiguity has a likely destination package
- no ambiguity is “solved” inside `WP-0` by hidden design expansion

**Explicit non-goals**

- no issue resolution yet
- no new package creation unless absolutely necessary

**WP0-11 first-pass ambiguity register**

| Ambiguity ID | Category | Description | Seen In | Likely Owning Future Package | Blocks WP-1? | Recommended Handling | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A01 | mixed policy/settings semantics | `projectPermissionMode` acts like authority but is stored through settings merge path | WP0-01 / WP0-02 | WP-1 + WP-2 | yes | classify explicitly in settings contract and cross-reference policy contract | highest priority |
| A02 | duplicate merge owner | workspace policy is normalized in `WorkspaceService` but template policy is merged later in executor runtime | WP0-02 | WP-2 | no | define effective-policy resolver boundary | |
| A03 | unclear effective config chain | system, workspace, template, desktop, and runtime payloads operate as separate chains | WP0-01 | WP-1 | yes | define canonical source hierarchy without behavior rewrite | |
| A04 | risk override semantics unclear | same-key `riskOverrides` merge is overwrite-like, not visibly restrictive | WP0-02 | WP-2 | no | define restrictive merge semantics before code change | |
| A05 | memory authority overlap | multiple services/tools can write canonical memory files | WP0-03 | WP-3 | no | classify writer categories and provenance expectations | |
| A06 | memory/context boundary blur | memory prefetch is merged with routed context before LLM call | WP0-03 / WP0-04 | WP-3 + WP-4 | no | treat merged result as composed context with source tags | |
| A07 | budget model split | router uses char budget, window manager uses token estimates | WP0-04 | WP-4 | no | converge later via explicit lifecycle contract | |
| A08 | lifecycle vocabulary split | graph/harness events and hook events are separate vocabularies | WP0-05 / WP0-06 | WP-5 | no | create mapping layer first | |
| A09 | template authority blur | template JSON mixes metadata, runtime defaults, policy defaults, and acceptance declarations | WP0-07 | WP-6 | no | field-group classification first | |
| A10 | provenance unevenness | not all durable-memory writes emit the same event/provenance metadata | WP0-03 | WP-3 | no | inventory writer provenance and normalize expectations | |

**WP0-11 blocker split**

- blockers for starting `WP-1`: `A01`, `A03`
- important but non-blocking for `WP-1`: `A02`, `A04`, `A05`, `A06`, `A07`, `A08`, `A09`, `A10`

###### Worksheet `WP0-12` — Review and Freeze WP-0 Baseline

**Task goal**

Produce the final review package that determines whether `WP-1` can start without further discovery.

**Inputs required**

- outputs from `WP0-01` to `WP0-11`

**Execution steps**

1. Verify that all required artifact tables exist.
2. Verify that each table has sufficient completeness.
3. Identify any gaps that would make `WP-1` unsafe to start.
4. Produce one review summary with:
   - ready items
   - unresolved but non-blocking items
   - blocking items
5. Make an explicit decision recommendation:
   - start `WP-1`
   - patch missing `WP-0` inventory first

**Required output table template**

| Review Area | Artifact Present? | Completeness Status | Blocking? | Follow-up Needed | Reviewer Note |
| --- | --- | --- | --- | --- | --- |

**Required summary template**

```text
WP-0 Review Result:
- Ready to start WP-1: yes / no
- Blocking gaps:
- Non-blocking gaps:
- Recommended next action:
```

**Expected deliverable**

- one final readiness review section
- one explicit go / no-go recommendation for `WP-1`

**Acceptance checks**

- review result is explicit
- blockers are concrete rather than vague
- `WP-1` start decision does not require reopening discovery from scratch

**WP0-12 first-pass review table**

| Review Area | Artifact Present? | Completeness Status | Blocking? | Follow-up Needed | Reviewer Note |
| --- | --- | --- | --- | --- | --- |
| WP0-01 settings inventory | yes | first-pass sufficient | yes | later cross-check extra env/provider fallbacks | strong start artifact |
| WP0-02 policy inventory | yes | first-pass sufficient | no | later inspect guard engine details | enough for `WP-2` planning |
| WP0-03 memory surfaces | yes | first-pass sufficient | no | later enumerate all write-event emitters | enough for governance planning |
| WP0-04 context surfaces | yes | first-pass sufficient | no | later add attachment/session-temp convergence | enough for lifecycle planning |
| WP0-05 lifecycle / harness events | yes | first-pass sufficient | no | later deepen publish-site mapping | event baseline exists |
| WP0-06 hook surfaces | yes | first-pass sufficient | no | later trace direct `channel` / `cron` publishers | adequate for `WP-5` planning |
| WP0-07 template contract entry points | yes | first-pass sufficient | no | later perform field-by-field schema grouping | strong basis for `WP-6` |
| WP0-08 ownership matrix | yes | first-pass sufficient | no | later refine provenance ownership | enough to assign responsibilities |
| WP0-09 glossary | yes | first-pass sufficient | no | later propagate terminology into docs/ledger | enough to reduce drift |
| WP0-10 scope lock map | yes | first-pass sufficient | no | later refine overlap handoff rules | usable execution boundary map |
| WP0-11 ambiguity register | yes | first-pass sufficient | no | keep list finite and stable | blocker set is explicit |

**WP0-12 first-pass readiness summary**

WP-0 Review Result:
- Ready to start WP-1: yes
- Blocking gaps:
   - settings/policy boundary must be treated as an explicit WP-1 input
   - effective settings source hierarchy is still distributed and must be normalized first
- Non-blocking gaps:
   - guard engine internals not yet fully inventoried
   - memory provenance is uneven across write paths
   - some hook direct publishers still need later tracing
   - context taxonomy still lacks attachment/session-temporary convergence
- Recommended next action:
   - freeze WP-0 as first-pass baseline
   - start WP-1 with a canonical settings-source hierarchy and field classification contract
   - keep WP-2 immediately adjacent in planning because of the mixed settings/policy boundary

##### WP-0 Final Exit Criteria

WP-0 can be considered fully complete only when all of the following are true:

- every core layer has a concrete inventory table
- every inventory row has an owner or an explicit unresolved marker
- WP-1 through WP-6 each have a file/class bounded start scope
- the glossary is stable enough to prevent term drift in later tasks
- unresolved questions are captured as inputs, not silently absorbed into WP-0 implementation

#### WP-0 Completion Checklist

| Task ID | Status | Artifact | Verified |
| --- | --- | --- | --- |
| WP0-01 | [x] | Settings-source inventory table (11 sources mapped) | yes |
| WP0-02 | [x] | Policy entry-point inventory table (7 policy dimensions mapped) | yes |
| WP0-03 | [x] | Memory-surface inventory table (8 surfaces mapped) | yes |
| WP0-04 | [x] | Context-assembly inventory table (7 context sources mapped) | yes |
| WP0-05 | [x] | Lifecycle/harness event inventory table (9 event types mapped) | yes |
| WP0-06 | [x] | Hook-surface inventory table (7 event families mapped) | yes |
| WP0-07 | [x] | Template-contract entry-point inventory table (12 field groups mapped) | yes |
| WP0-08 | [x] | Ownership matrix (11 subsystems mapped) | yes |
| WP0-09 | [x] | Canonical glossary (13 terms defined) | yes |
| WP0-10 | [x] | Scope lock map for WP-1 ~ WP-6 | yes |
| WP0-11 | [x] | Unresolved ambiguity register (10 items, 2 blockers flagged) | yes |
| WP0-12 | [x] | Review and freeze decision (Ready to start WP-1: **yes**) | yes |

#### WP-0 Code Verification Summary

The following key files were verified to exist in the current codebase and match the inventory references:

**Settings / Policy**
- `mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java` — confirmed present; fields: `sandboxMode`, `approvalPolicy`, `networkPolicy`, `allowedPaths`, `deniedPaths`, `riskOverrides`
- `mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspaceEntity.java` — confirmed present; `settingsJson` stores mixed settings + policy
- `mateclaw-server/src/main/java/vip/mate/agent/model/AgentEntity.java` — confirmed present; `templateMetadataJson`, `profileId`, `capabilityPackId` observed
- `mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java` — confirmed present; bootstrap + metadata snapshot logic verified
- `mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java` — confirmed present
- `mateclaw-desktop/electron/desktopConfig.cjs` — confirmed present
- `mateclaw-desktop/electron/main.cjs` — confirmed present

**Memory**
- `mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java` — confirmed present; `beforeLlmCall` / `afterLlmCall` / `onSessionEnd` verified
- `mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java` — confirmed present

**Context**
- `mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java` — confirmed present; `summarize()`, `buildInjectionBlock()` verified
- `mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/workspace/core/model/ContextRouterSummary.java` — confirmed present

**Lifecycle / Hook**
- `mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java` — confirmed present; virtual-thread dispatch, rate limit, concurrency semaphore, audit verified
- `mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java` — confirmed present
- `mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java` — confirmed present

**Template**
- `mateclaw-server/src/main/resources/templates/coding-agent.json` — confirmed present
- `mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json` — confirmed present

**Planning**
- `mateclaw-server/src/main/java/vip/mate/planning/service/PlanningService.java` — confirmed present

#### WP-0 Current vs Target Delta Matrix

| Core Layer | Current State (Verified) | Target Contract (WP-1 ~ WP-6) | Delta Summary | Owning WP |
| --- | --- | --- | --- | --- |
| Settings resolution | Multiple independent chains (server DB, workspace JSON, desktop file, template defaults); no unified precedence model | Single explicit settings-source hierarchy with documented merge semantics | Need resolver contract + precedence docs + field classification | WP-1 |
| Policy authority | `WorkspacePolicy` embedded in `settingsJson`; template policy merged at execution time in executor; no central effective-policy resolver | Policy as top authority layer with clear load/merge/enforce separation | Extract effective-policy resolver; split policy from settings storage | WP-2 |
| Memory governance | Orchestrator (`MemoryManager`) + lifecycle mediator + provider chain + tool access surfaces coexist; provenance uneven | Unified memory taxonomy with strict read/write/merge boundaries and consistent provenance | Normalize writer provenance; classify memory vs cache vs context | WP-3 |
| Context lifecycle | Routed hints (`ContextRouterService`) + history compaction (`ConversationWindowManager`) + memory merge (`MemoryLifecycleMediator`) split across three classes | Unified context lifecycle with source taxonomy, provenance, and budget rules | Create context-source provider contract; converge budgeting model | WP-4 |
| Lifecycle / hook events | Graph events (`GraphEventPublisher` → `HarnessRunService`) and hook events (`MateHookEvent` → `HookDispatcher`) use separate vocabularies and envelopes | Normalized event envelope with lifecycle categories, aligning harness and hook vocabularies where appropriate | Event envelope normalization; mapping layer; metadata enrichment | WP-5 |
| Template contract | Template JSON mixes descriptive metadata, runtime defaults, policy defaults, workspace seeds, and acceptance declarations | Explicit template contract sections (metadata vs runtime vs policy vs acceptance vs materialization) | Field-group classification; schema normalization; consumer alignment | WP-6 |

#### WP-0 Freeze Declaration

WP-0 is hereby frozen as the first-pass execution baseline for the Core Systems Consolidation Plan.

- All twelve WP-0 tasks are completed and verified.
- The inventory, ownership matrix, glossary, scope lock map, and ambiguity register are stable enough to support WP-1 through WP-6.
- The two WP-1 blockers identified in WP0-11 (`A01` mixed policy/settings semantics, `A03` distributed effective-config chain) will be treated as explicit inputs to WP-1 rather than requiring additional WP-0 discovery.
- No further inventory expansion or schema changes are permitted within WP-0.
- Recommended next action: **start WP-1** with a canonical settings-source hierarchy and field classification contract.

---

### WP-1 — Settings Resolution Consolidation

#### Objective

Introduce a single explicit settings resolution model without changing current business flows.

#### Functional Points

1. Define a normalized settings-source enum/model covering:
   - platform defaults
   - managed system settings
   - template defaults
   - workspace shared settings
   - desktop local settings
   - runtime overrides
2. Define merge classes per field family:
   - scalar override
   - additive merge
   - restrictive merge
3. Define trust levels for each settings source.
4. Extract or centralize one resolver contract for “effective settings”.
5. Mark which fields belong to:
   - product settings
   - safety policy
   - local runtime state
   - template defaults
6. Preserve existing fallback behavior while making resolution inspectable.

#### Modification Scope

Primary server scope:

- [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- settings-related DTO/model packages under `vip.mate.system` and `vip.mate.workspace.core`

Primary desktop scope:

- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)
- [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs)

Secondary scope only if needed:

- template metadata loaders that currently inject default settings
- provider builders with fallback chains

Allowed change type:

- add resolver class / helper class / DTO / contract docs
- centralize precedence logic
- expose effective-source metadata for diagnostics

Not allowed in this package:

- changing approval semantics
- changing tool permission behavior
- changing conversation orchestration
- redesigning desktop settings UI

#### Suggested Deliverables

1. `EffectiveSettingsResolver` or equivalent server-side contract
2. `SettingsSource` / `SettingsTrustLevel` model
3. field classification table in docs
4. optional debug/introspection output for effective settings source tracing

#### Acceptance Checkpoint

WP-1 is done when:

- at least one canonical effective-settings path exists
- precedence is explicit and testable
- desktop local config is clearly separated from shared/server truth
- no existing business workflow requires behavioral retraining

#### WP-1 Landing Record

**Status:** First-pass landed  
**Landed at:** 2026-05-20  

**Deliverables produced:**

| Artifact | Path | Status |
| --- | --- | --- |
| `SettingSource` enum (canonical source taxonomy) | `mateclaw-server/src/main/java/vip/mate/setting/contract/SettingSource.java` | landed |
| `SettingFieldClass` enum (settings vs policy vs runtime classification) | `mateclaw-server/src/main/java/vip/mate/setting/contract/SettingFieldClass.java` | landed |
| `SettingMergeStrategy` enum (merge semantics) | `mateclaw-server/src/main/java/vip/mate/setting/contract/SettingMergeStrategy.java` | landed |
| `EffectiveSettingsContract` interface (read-only resolver contract) | `mateclaw-server/src/main/java/vip/mate/setting/contract/EffectiveSettingsContract.java` | landed |
| `WorkspaceSettingsResolver` (extracts `settingsJson` parsing from `WorkspaceService`) | `mateclaw-server/src/main/java/vip/mate/setting/resolver/WorkspaceSettingsResolver.java` | landed |
| `SystemSettingsResolver` (wraps `SystemSettingService` with classification) | `mateclaw-server/src/main/java/vip/mate/setting/resolver/SystemSettingsResolver.java` | landed |
| `TemplateSettingsResolver` (scans built-in templates for runtime defaults) | `mateclaw-server/src/main/java/vip/mate/setting/resolver/TemplateSettingsResolver.java` | landed |
| `SettingResolutionAggregator` (composes all resolvers into unified view) | `mateclaw-server/src/main/java/vip/mate/setting/resolver/SettingResolutionAggregator.java` | landed |
| `WorkspaceSettingsResolverTest` (unit tests for classification and parsing) | `mateclaw-server/src/test/java/vip/mate/setting/resolver/WorkspaceSettingsResolverTest.java` | landed |

**Key design decisions:**
- All new code lives in isolated `vip.mate.setting.contract.*` and `vip.mate.setting.resolver.*` packages.
- Zero existing files were modified; existing `WorkspaceService` and `SystemSettingService` behavior is preserved.
- `projectPermissionMode` and `workspacePolicy` are explicitly classified as `SAFETY_POLICY`, addressing WP-0 blocker `A01`.
- Template defaults are surfaced as a first-class `SettingSource.TEMPLATE_DEFAULTS`, addressing WP-0 blocker `A03`.
- The aggregator currently implements `EffectiveSettingsContract` for global and workspace scopes; agent-level scope is reserved for WP-2.

**Deferred to later packages:**
- Desktop config resolver (remains in desktop layer; not yet wrapped into server-side contract).
- Full cross-source merge semantics for workspace scope (needs `WorkspaceService` integration; deferred to WP-2).
- Settings-source introspection API / debug endpoint (deferred to WP-1 second pass or WP-5 diagnostics).

#### WP-1 Detailed Execution Plan

`WP-1` should stay narrowly focused on **settings resolution visibility and classification**, not on policy-engine redesign.

Execution order inside `WP-1` should be:

1. freeze canonical source hierarchy
2. freeze field-family classification
3. freeze trust + merge model
4. define the minimum effective-settings resolver contract
5. isolate desktop-local state from shared/server truth
6. classify template/default ingress points without expanding into `WP-2`
7. define diagnostics/introspection contract
8. freeze implementation cut and acceptance matrix

##### WP-1 Anti-Expansion Guardrails

Do not let `WP-1` expand into:

- approval workflow redesign
- tool-guard behavior changes
- workspace policy semantics redesign
- desktop settings UI redesign
- template schema redesign
- provider fallback rewrites beyond classification/documentation

##### WP-1 Task Matrix

| Task ID | Task Name | Focus Area | Primary Files / Classes | Expected Output | Acceptance Signal |
| --- | --- | --- | --- | --- | --- |
| WP1-01 | Freeze source hierarchy | source taxonomy | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs), [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs) | one canonical settings-source hierarchy table | every currently observed source class has one precedence slot |
| WP1-02 | Freeze field classification | field family boundary | [mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java](mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs) | one field classification matrix | settings vs policy vs local-runtime split is explicit |
| WP1-03 | Freeze trust and merge rules | source trust model | same as `WP1-01` plus template metadata ingress | one trust-level + merge-class matrix | every source family has a documented trust and merge rule |
| WP1-04 | Define resolver contract | canonical effective settings path | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) | one minimal resolver contract and cut plan | canonical path is explicit without changing behavior |
| WP1-05 | Isolate desktop local state | local desktop boundary | [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs), [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs) | one desktop local-state boundary note + table | local-only fields are clearly excluded from shared truth |
| WP1-06 | Classify template ingress | template defaults boundary | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java), template JSON metadata, [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java) | one template-ingress boundary note | ordinary settings and policy-adjacent defaults are not conflated |
| WP1-07 | Define diagnostics contract | introspection/debug surface | server resolver entry points, desktop config load path | one diagnostics payload proposal | effective source tracing becomes inspectable |
| WP1-08 | Freeze implementation cut | execution-ready bounded plan | outputs of `WP1-01` to `WP1-07` | one implementation cut matrix + acceptance grid | `WP-1` can move into code change without reopening design scope |

##### WP-1 Detailed Worksheets

###### Worksheet `WP1-01` — Freeze Canonical Source Hierarchy

#### Task goal

Freeze one bounded settings-source hierarchy that reflects the current system without rewriting runtime behavior.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java)
- [mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java](mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)
- [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs)

#### Execution steps

1. list every currently observed settings-like source class
2. separate shared/server truth from machine-local truth
3. assign one precedence tier per source family
4. mark where a source is field-specific rather than universal
5. mark policy-adjacent sources that must be handed off to `WP-2`

#### Required output table template

| Source ID | Source Class | Scope | Storage / Carrier | Current Owner | Applies To | Precedence Tier | Shared or Local | Policy-Adjacent? | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one canonical settings-source hierarchy table
- one note separating shared/server truth from local desktop truth
- one list of policy-adjacent sources to hand off to `WP-2`

#### Acceptance checks

- all currently observed source classes have a tier
- desktop-local state is explicitly separated
- field-specific env fallback is not incorrectly generalized into a global source rule

#### Explicit non-goals

- no policy merge redesign
- no provider fallback rewrite
- no desktop config schema rewrite

#### WP1-01 first-pass source hierarchy

| Source ID | Source Class | Scope | Storage / Carrier | Current Owner | Applies To | Precedence Tier | Shared or Local | Policy-Adjacent? | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S01 | code defaults | server / desktop bootstrap | hardcoded literals in service or config modules | module-local implementation owners | almost all ordinary fallback fields | 1 | mixed | no | lowest priority bootstrap layer |
| S02 | managed system settings | shared server truth | DB rows via `SystemSettingService` | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java) | product/system settings fields in `SystemSettingsDTO` | 3 | shared | low | current main shared truth for system-level settings |
| S03 | field-specific env fallback | deployment/runtime environment | process environment variables | deployment + module-specific loader | currently explicit for `SEARXNG_BASE_URL`; desktop backend url has separate env path | 4 for forced env, 2 for default env suggestions | mixed | low | must remain field-specific, not assumed global |
| S04 | workspace shared settings JSON | shared workspace truth | `WorkspaceEntity.settingsJson` | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) | workspace-scoped settings and mixed authority fields | 5 | shared | yes | contains both ordinary settings and policy-adjacent fields |
| S05 | template metadata defaults | agent bootstrap metadata | `templateMetadataJson` snapshot | [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java) | template runtime defaults and policy-adjacent defaults | 2 as bootstrap seed, not as top shared truth | shared-at-bootstrap | yes | ordinary settings use is limited; main active use is policy/context/runtime metadata |
| S06 | desktop packaged defaults | local desktop bootstrap | `createDefaultConfig()` / packaged default url logic | [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs) | backendUrl / proxyUrl / backend process fields | 1 local | local | no | operational default only |
| S07 | desktop persisted local config | local machine runtime state | `%USERPROFILE%/.metay-desktop/config.json` | `desktopConfig.cjs` + [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs) | backendUrl, proxyUrl, local backend launch fields | 3 local | local | no | must never be treated as shared workspace truth |
| S08 | desktop forced backend env | local deployment override | `METAY_BACKEND_URL` / compatible env names | desktop process environment | backendUrl only | 4 local | local | no | acts as forced local override |
| S09 | runtime request payload / save input | request-scoped incoming mutation | DTO payload / IPC payload | API/controller or desktop IPC caller | save/update targets only | 6 at write time | mixed | medium | not a persisted source class by itself; it is an ingress carrier |
| S10 | provider-specific internal fallback chains | service-local runtime logic | local builder/service code | provider/service owners | narrow service fields only | service-local | mixed | no | document only if it materially affects effective-setting observability |

#### WP1-01 shared-vs-local separation note

Shared/server truth currently comes primarily from `SystemSettingService` and `WorkspaceService`. Desktop config is operational local state and should be modeled as a separate branch, not as another shared precedence layer.

#### WP1-01 policy-adjacent handoff note

The main policy-adjacent sources that `WP-1` must classify but not redesign are:

- `projectPermissionMode` in workspace settings flow
- `workspacePolicy` inside `settingsJson`
- template `defaultWorkspacePolicy`

###### Worksheet `WP1-02` — Freeze Field Classification

#### Task goal

Classify settings-related fields into stable families so later code changes do not re-mix ordinary settings, policy, and local runtime state.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java](mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspaceEntity.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspaceEntity.java)
- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)

#### Execution steps

1. group settings fields by ownership and runtime effect
2. separate secrets from ordinary settings
3. mark boundary fields that look like settings but behave like authority
4. separate shared workspace state from local desktop state
5. keep classification minimal and implementable

#### Required output table template

| Field Family ID | Field / Field Group | Current Carrier | Canonical Class | Shared or Local | Merge Class | Trust Sensitivity | Boundary Note |
| --- | --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one field classification matrix
- one boundary note for mixed fields
- one minimum term-to-field mapping note

#### Acceptance checks

- `SystemSettingsDTO` fields are distinguished from workspace authority fields
- desktop config fields are classified as local runtime state
- mixed authority fields are not silently left in ordinary settings bucket

#### Explicit non-goals

- no field renaming sweep
- no database migration
- no new desktop UI behaviors

#### WP1-02 first-pass field classification

| Field Family ID | Field / Field Group | Current Carrier | Canonical Class | Shared or Local | Merge Class | Trust Sensitivity | Boundary Note |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | `SystemSettingsDTO.language`, `streamEnabled`, `debugMode`, `stateGraphEnabled` | system settings DB + code defaults | product setting | shared | scalar override | medium | ordinary system preferences / feature toggles |
| F02 | search / image / video / model3d / tts / stt / music provider enablement and fallback fields | `SystemSettingsDTO` + DB | product capability setting | shared | scalar override | medium-high | some fields affect runtime pathways but are still not policy |
| F03 | API keys and secret provider credentials | `SystemSettingsDTO` write-only fields + masked echoes | secret setting | shared | scalar override | high | secrecy rules differ from ordinary settings resolution |
| F04 | `serperBaseUrl`, `tavilyBaseUrl`, `searxngBaseUrl`, `zhipuBaseUrl` | system settings DB + env fallback for selected fields | endpoint setting | shared | scalar override with field-specific env override | high | env precedence must remain field-specific |
| F05 | generic workspace `settingsJson` ordinary keys | `WorkspaceEntity.settingsJson` | workspace shared setting | shared | scalar/additive depending on key | medium | future resolver should expose per-key source metadata |
| F06 | `projectPermissionMode` | workspace entity column + `settingsJson` mirror | policy-adjacent boundary field | shared | restrictive authority, not normal override | high | classify in `WP-1`, semantics finalized with `WP-2` |
| F07 | `workspacePolicy` subtree | `settingsJson` + structured field hydration | policy | shared | restrictive merge | high | out of core `WP-1` implementation, but must be explicitly excluded |
| F08 | template runtime metadata excluding policy | `templateMetadataJson` | template default / bootstrap metadata | shared-at-bootstrap | bootstrap seed only | medium | not the long-term source of truth |
| F09 | desktop `backendUrl`, `proxyUrl`, `autoStartBackend`, `backendCommand`, `backendArgs`, `backendCwd` | desktop config file + env forced backend | local runtime state | local | scalar override | medium-high | must be modeled on a separate local branch |
| F10 | runtime save/update payloads | request DTO / IPC payload | ingress carrier | mixed | n/a carrier only | depends on target field | not itself a durable settings source |

#### WP1-02 mixed-boundary note

The two highest-risk mixed fields are `projectPermissionMode` and `workspacePolicy`, because both travel through workspace settings plumbing while acting as authority-bearing inputs.

###### Worksheet `WP1-03` — Freeze Trust and Merge Rules

#### Task goal

Document one minimum trust and merge model for observed settings sources without changing current business outcomes.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)
- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)

#### Execution steps

1. assign one trust level per source class
2. assign one dominant merge class per field family
3. call out sources that are bootstrap-only rather than ongoing truth
4. mark fields that require policy handoff
5. keep the model finite and inspectable

#### Required output table template

| Source / Family | Trust Level | Dominant Merge Class | Can Override Shared Truth? | Can Seed Defaults? | Requires Policy Handoff? | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one trust + merge matrix
- one short note on bootstrap-only sources
- one explicit list of `WP-2` handoff items

#### Acceptance checks

- every major source class has one trust label
- scalar/additive/restrictive classes are used consistently
- local desktop override is not mis-labeled as shared authority

#### Explicit non-goals

- no restrictive policy merge redesign
- no approval coupling changes
- no template schema changes

#### WP1-03 first-pass trust + merge matrix

| Source / Family | Trust Level | Dominant Merge Class | Can Override Shared Truth? | Can Seed Defaults? | Requires Policy Handoff? | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| code defaults | bootstrap | scalar default | no | yes | no | last-resort baseline only |
| managed system settings DB | managed shared | scalar override | yes within system-setting scope | yes | no | strongest ordinary shared product-setting source |
| field-specific env fallback | deployment override | scalar override | yes for explicitly coded fields only | yes | low | must be documented as per-field, not global |
| workspace shared settings | managed shared workspace | scalar override / additive by key | yes within workspace scope | yes | yes for mixed fields | needs per-key classification |
| template metadata defaults | bootstrap contract | bootstrap seed | no silent override of stronger shared truth | yes | yes for policy-adjacent defaults | use as initializer or runtime metadata, not global authority |
| desktop persisted config | local trusted | scalar override on local branch | only on local desktop branch | yes | no | operational local truth |
| desktop forced backend env | deployment-local forced | scalar override | yes on local desktop branch | yes | no | explicit forced local override |
| runtime ingress payload | caller-provided ingress | carrier only | only through validated save/update path | no | depends on target | classify by destination field |
| `projectPermissionMode` | authority-boundary | restrictive authority | not as ordinary setting | no | yes | cross-package boundary item |
| `workspacePolicy` subtree | authority | restrictive merge | yes as policy, not as ordinary setting | no | yes | primary `WP-2` subject |

#### WP1-03 bootstrap-only note

Template metadata and code defaults should be modeled as bootstrap/default sources, not as perpetual top-authority settings branches.

###### Worksheet `WP1-04` — Define Resolver Contract

#### Task goal

Define the smallest possible canonical effective-settings contract that can wrap current behavior without broad refactoring.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java](mateclaw-server/src/main/java/vip/mate/system/model/SystemSettingsDTO.java)

#### Execution steps

1. list current effective-settings entry points
2. identify the smallest shared contract that can sit above them
3. keep write paths and behavior intact where possible
4. define what metadata the resolver must expose
5. keep policy fields explicitly separated or tagged

#### Required output table template

| Contract Piece | Purpose | Minimum Fields / Methods | Likely Home | Current Inputs | Non-Goals / Exclusions |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one minimal resolver contract table
- one suggested implementation cut note
- one list of existing entry points to wrap rather than rewrite

#### Acceptance checks

- at least one canonical effective-settings path is defined
- existing services can be wrapped incrementally
- policy fields are tagged rather than silently absorbed

#### Explicit non-goals

- no full settings service rewrite
- no broad controller rewrite
- no UI coupling in resolver contract

#### WP1-04 first-pass resolver contract

| Contract Piece | Purpose | Minimum Fields / Methods | Likely Home | Current Inputs | Non-Goals / Exclusions |
| --- | --- | --- | --- | --- | --- |
| `SettingsSource` enum/model | identify source class | source id, scope, trust level, local/shared flag | server shared settings package | code defaults, DB, workspace, template, env, desktop-local branch | not a behavior engine |
| `SettingsFieldClass` enum/model | classify field family | product, secret, policy-adjacent, local-runtime, template-default | same package | `SystemSettingsDTO`, workspace settings keys, desktop config keys | not a DB schema change |
| `EffectiveSettingEntry` DTO | expose one resolved field | field key, effective value preview, source, merge class, field class, policy-adjacent flag | diagnostics/shared DTO package | all resolver inputs | not necessarily persisted |
| `EffectiveSettingsSnapshot` DTO | expose grouped resolved settings | snapshot id/time, scope, entries or grouped maps | diagnostics/shared DTO package | current service outputs | not a public UI commitment yet |
| `EffectiveSettingsResolver` contract | compute/read effective settings | `resolveSystemSettings()`, `resolveWorkspaceSettings(workspaceId)`, optional `resolveDiagnostics(scope)` | server service/helper layer | `SystemSettingService`, `WorkspaceService`, optional template metadata | not a policy resolver |
| `WorkspaceSettingsClassifier` helper | classify keys inside `settingsJson` | classify key, tag policy-adjacent, extract shared settings subset | workspace/core helper | workspace `settingsJson` | not a behavioral change to stored JSON |

#### WP1-04 suggested implementation cut note

The minimum safe cut is:

1. add classification models (`SettingsSource`, `SettingsFieldClass`)
2. add a read-only resolver/snapshot helper over existing `SystemSettingService` and `WorkspaceService`
3. add optional diagnostics exposure for source tracing
4. leave existing save/update flows intact for the first code pass

###### Worksheet `WP1-05` — Isolate Desktop Local State

#### Task goal

Freeze the desktop branch as local runtime state so it does not continue drifting into shared settings discussions.

#### Files / classes to inspect

- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)
- [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs)

#### Execution steps

1. inventory all desktop config fields
2. identify forced env vs packaged default vs persisted config
3. separate target-selection logic from shared/server settings semantics
4. define what desktop config can and cannot influence in `WP-1`
5. record local-only acceptance boundaries

#### Required output table template

| Desktop Field | Current Source Chain | Canonical Class | Shared or Local | Allowed Influence | Must Not Be Treated As | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one desktop local-state boundary table
- one allowed-influence note
- one non-authority note for local desktop config

#### Acceptance checks

- every desktop config field is local-classified
- forced env override is distinguished from persisted file state
- backend target selection is documented as local operational behavior

#### Explicit non-goals

- no IPC redesign
- no desktop backend-manager redesign
- no proxy UX changes

#### WP1-05 first-pass desktop boundary table

| Desktop Field | Current Source Chain | Canonical Class | Shared or Local | Allowed Influence | Must Not Be Treated As | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `backendUrl` | packaged/code default → persisted config → forced env override | local runtime endpoint setting | local | desktop API target selection and backend bridge routing | shared workspace/system truth | highest-impact local field |
| `proxyUrl` | empty default → persisted config | local runtime endpoint override | local | desktop API target preference when non-empty | shared backend identity | if set, `getApiTargetUrl()` prefers it |
| `autoStartBackend` | code default → persisted config | local process control setting | local | local backend manager startup behavior | shared product setting | operational only |
| `backendCommand` | code default → persisted config | local process launch setting | local | local backend process launch | shared server config | machine-specific |
| `backendArgs` | code default → persisted config | local process launch setting | local | local backend process launch args | shared product setting | machine-specific |
| `backendCwd` | code default → persisted config | local process launch setting | local | local backend working directory | workspace shared base path | machine-specific |
| forced backend env names | environment → load normalization | local forced override | local | local deployment-level override for backend target | shared authority source | explicit force path |

#### WP1-05 allowed-influence note

Desktop local config may influence only desktop runtime routing and local backend process behavior. It must not become part of shared workspace truth or shared product settings diagnostics without explicit local/shared labeling.

###### Worksheet `WP1-06` — Classify Template Ingress

#### Task goal

Classify how templates participate in settings resolution without expanding `WP-1` into template-contract or policy-authority redesign.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)
- [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json)
- [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)

#### Execution steps

1. identify template fields that look settings-like
2. distinguish bootstrap/default metadata from active runtime authority
3. mark policy-adjacent fields for `WP-2`
4. record fields that should be visible in diagnostics but not merged as shared settings
5. keep the boundary narrow

#### Required output table template

| Template Surface | Current Carrier | Settings-Relevant? | Canonical Treatment In WP-1 | Handoff Package | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one template-ingress classification table
- one explicit handoff note to `WP-2` and `WP-6`
- one note on bootstrap-only behavior

#### Acceptance checks

- ordinary settings-like template inputs are classified
- `defaultWorkspacePolicy` is explicitly handed to `WP-2`
- template metadata is not mislabeled as top authority |

#### Explicit non-goals

- no template schema refactor
- no template catalog changes
- no policy merge rewrite inside executor

#### WP1-06 first-pass template-ingress classification

| Template Surface | Current Carrier | Settings-Relevant? | Canonical Treatment In WP-1 | Handoff Package | Notes |
| --- | --- | --- | --- | --- | --- |
| `runtime` block | template JSON → `templateMetadataJson` | yes | bootstrap/runtime metadata, not shared truth | `WP-6` | may appear in diagnostics as source metadata |
| `permissions` block | template metadata snapshot | partially | contract metadata, not ordinary effective settings | `WP-6` | avoid conflating with guard policy |
| `defaultWorkspacePolicy` | template metadata → runtime executor parse | yes, strongly | classify as policy-adjacent source only | `WP-2` | explicit handoff item |
| `agentProfile` | template metadata | weakly | bootstrap metadata, not effective shared setting | `WP-6` | may inform initialization only |
| `capabilityPack` | template metadata | weakly | bootstrap/runtime contract metadata | `WP-6` | not part of ordinary settings precedence |
| `contextSources` | template metadata | weakly | runtime contract metadata, not settings resolution | `WP-4` / `WP-6` | should stay out of `WP-1` resolver core |
| `workspaceFiles` | template materialization | no direct ordinary settings role | exclude from effective settings model | `WP-6` | materialization artifact only |

#### WP1-06 handoff note

`WP-1` should only classify template ingress. Actual authority semantics belong to:

- `WP-2` for `defaultWorkspacePolicy`
- `WP-4` for `contextSources`
- `WP-6` for template contract grouping and schema normalization

###### Worksheet `WP1-07` — Define Diagnostics Contract

#### Task goal

Define the minimum introspection payload needed to make settings resolution inspectable and testable.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)
- [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs)

#### Execution steps

1. define the minimum fields needed for source tracing
2. keep secret handling safe by using preview/masked values
3. include local/shared labeling
4. include policy-adjacent flagging
5. avoid committing to a large UI surface in this package

#### Required output table template

| Diagnostics Element | Purpose | Required? | Secret-Safe Handling | Source Scope | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one diagnostics payload proposal
- one masked-value rule note
- one minimal exposure recommendation

#### Acceptance checks

- source tracing can explain why a value is effective
- secret values are not exposed directly
- local/shared and policy-adjacent flags are present

#### Explicit non-goals

- no full admin console design
- no guaranteed public API commitment
- no cross-product telemetry rollout

#### WP1-07 first-pass diagnostics payload proposal

| Diagnostics Element | Purpose | Required? | Secret-Safe Handling | Source Scope | Notes |
| --- | --- | --- | --- | --- | --- |
| `fieldKey` | identify resolved field | yes | n/a | all | e.g. `searchProvider`, `backendUrl` |
| `effectiveValuePreview` | show current effective value | yes | masked/preview for secrets | all | secrets should reuse masked semantics |
| `sourceId` | show winning source | yes | n/a | all | aligns with `SettingsSource` |
| `fieldClass` | show canonical family | yes | n/a | all | product / secret / local-runtime / policy-adjacent |
| `mergeClass` | explain merge behavior | yes | n/a | all | scalar / additive / restrictive |
| `scope` | identify resolution scope | yes | n/a | all | system / workspace / local-desktop |
| `isLocalOnly` | prevent local/shared confusion | yes | n/a | all | especially for desktop config |
| `isPolicyAdjacent` | prevent settings/policy confusion | yes | n/a | mixed fields | explicit handoff marker |
| `sourceDetails` | explain field-specific env or bootstrap reason | optional | masked if needed | selected | useful for env-backed fields |
| `capturedAt` | aid debugging reproducibility | optional | n/a | all | snapshot timestamp |

#### WP1-07 masked-value rule note

Any field already treated as secret in `SystemSettingsDTO` should expose diagnostics only as masked or boolean-presence metadata, never as raw credential values.

###### Worksheet `WP1-08` — Freeze Implementation Cut and Acceptance Grid

#### Task goal

Turn the `WP-1` design outputs into a bounded code-change plan that can be implemented without reopening architecture scope.

#### Inputs required

- outputs from `WP1-01` to `WP1-07`

#### Execution steps

1. define the minimum code cut for first implementation pass
2. assign file/class touchpoints
3. identify tests or validation surfaces
4. list explicitly deferred items
5. freeze final acceptance grid

#### Required output table template

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one implementation cut matrix
- one deferred-items note
- one final acceptance grid for `WP-1`

#### Acceptance checks

- each cut has bounded files and bounded output
- deferred items are explicit
- `WP-1` can start coding without reopening source discovery

#### Explicit non-goals

- no bundling of `WP-2` into the same implementation pass
- no cross-package mega-refactor
- no hidden UI or desktop workflow redesign

#### WP1-08 first-pass implementation cut matrix

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |
| C1 | add source/classification models | server settings/shared package | `SettingsSource`, `SettingsFieldClass`, optional trust enum | compile + unit tests for enum/classifier behavior | package placement drift |
| C2 | add read-only effective settings resolver | [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), new helper/service | canonical snapshot/read path | unit tests on precedence snapshots | accidental behavior rewrite |
| C3 | add workspace settings classifier | [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) or adjacent helper | explicit tagging for `projectPermissionMode` / `workspacePolicy` / ordinary keys | classifier tests | bleed into `WP-2` |
| C4 | add optional diagnostics exposure | server diagnostics/controller/helper layer | debug/introspection DTO or internal endpoint | snapshot output validation, secret masking checks | premature public API commitment |
| C5 | add desktop local-state classification note or local helper constants | [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs), [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs) if needed | explicit local/shared metadata or comments/contracts | smoke validation on config load/save | desktop UX expansion |

#### WP1-08 deferred-items note

Explicitly defer from `WP-1`:

- `defaultWorkspacePolicy` merge redesign
- guard/approval semantics
- `workspacePolicy` restrictive-merge final contract
- template schema regrouping
- broad desktop settings UX changes

#### WP1-08 final acceptance grid

`WP-1` is review-ready when:

- one canonical source hierarchy is frozen
- one field classification contract is frozen
- one trust + merge model is frozen
- one read-only effective settings path exists or is implementation-ready
- local desktop state is explicitly isolated from shared truth
- policy-adjacent fields are tagged and handed off rather than silently absorbed

### WP-1.x — Cross-Cutting Model Routing and Dispatch Overlay

#### Objective

Add one **cross-cutting, model-selection overlay** for Agent runtime dispatch without changing the already frozen responsibilities of `WP-1` through `WP-6`.

This package is intentionally **additive**, not substitutive:

- it does **not** replace `WP-1` settings resolution
- it does **not** replace `WP-2` policy authority
- it does **not** replace `WP-4` context lifecycle
- it does **not** replace `WP-5` lifecycle event normalization
- it does **not** replace `WP-6` template contract normalization

It only introduces a bounded routing layer that decides **which model / provider / fallback path** should handle a task or execution phase.

#### Why this is a separate cross-cutting package

The codebase already has partial routing building blocks, but not one unified front-door model dispatcher:

- main Agent runtime still starts from global default model selection in [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- capability-aware provider diagnostics and selective reordering already exist in [mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java](mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java)
- step-aware model routing already exists for Wiki flows in [mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java](mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java)

This means the project already supports **local routing islands**, but not yet a unified **task-aware routing contract**.

#### Non-interference contract

`WP-1.x` must not reopen or weaken the currently frozen optimization plan.

It must follow these rules:

1. existing `WP-1 ~ WP-6` ownership stays unchanged
2. current default-model + fallback behavior remains the compatibility baseline
3. Wiki step-routing stays valid and is treated as an existence proof, not rewritten first
4. routing decisions must remain explainable by diagnostics and later lifecycle events
5. no product/UI expansion is required for the first pass

#### The four valuable routing directions

The routing overlay should record **all four directions as the target architecture**, but should not implement all four at the same depth in the first pass.

| Direction ID | Direction | Architectural Status | First-Pass Depth | Reason |
| --- | --- | --- | --- | --- |
| RT-01 | unified task router | must plan | implement first | highest leverage and lowest disruption |
| RT-02 | task-aware dispatch policy | must plan | implement first | directly improves model/provider selection quality |
| RT-03 | phase-aware dispatch | must plan | limited pilot only | valuable, but should start from already step-aware surfaces such as Wiki / validation |
| RT-04 | dynamic signal routing | must plan | defer to later pass | depends on metrics quality, diagnostics completeness, and stability data |

#### Planning decision for the four directions

The plan should therefore be:

- **record all four directions in the architecture target**
- **commit only RT-01 and RT-02 as the first implementation slice**
- **allow RT-03 only as a bounded pilot on already phase-split paths**
- **defer RT-04 until routing diagnostics and runtime metrics become trustworthy**

This keeps the routing work valuable without letting it expand into a second parallel runtime redesign.

#### Functional Points

1. define one lightweight `TaskRouter` / `ModelDispatchPolicy` contract before runtime model build
2. define task classes such as general chat, code-fix, wiki-processing, reasoning-heavy, multimodal
3. define the minimum capability labels needed for dispatch
4. let existing default-model + failover remain the compatibility fallback
5. allow bounded phase-aware routing only where execution phases already exist
6. attach routing reason metadata for diagnostics and later event publication

#### Modification Scope

Primary server scope:

- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java](mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java)
- [mateclaw-server/src/main/java/vip/mate/llm/service/ModelConfigService.java](mateclaw-server/src/main/java/vip/mate/llm/service/ModelConfigService.java)
- [mateclaw-server/src/main/java/vip/mate/llm/service/ModelProviderService.java](mateclaw-server/src/main/java/vip/mate/llm/service/ModelProviderService.java)

Secondary scope:

- [mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java](mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java)
- runtime diagnostics / audit metadata surfaces
- template metadata consumption only where it declares model needs or routing hints

Allowed change type:

- add task classification and dispatch DTOs/helpers
- add routing reason metadata
- reuse existing provider preference / capability / fallback mechanisms
- add bounded pilot dispatch for pre-existing phase-split flows

Not allowed in this package:

- replacing the current Agent graph runtime
- introducing a second independent execution pipeline
- redesigning policy authority or context lifecycle inside routing work
- broad template schema expansion unrelated to routing hints
- forcing dynamic adaptive routing before metrics are stable

#### Suggested Deliverables

1. `TaskClass` / `DispatchReason` / `ModelDispatchPolicy` contract
2. one bounded task-to-model capability matrix
3. one compatibility-preserving primary-selection flow for main Agent runtime
4. one routing diagnostics payload and later event handoff note

#### Acceptance Checkpoint

`WP-1.x` is done when:

- the main Agent runtime has one explainable routing decision point before model build
- current default-model behavior still works as compatibility fallback
- task-aware routing exists for the first bounded task classes
- phase-aware routing is limited to already split flows
- dynamic metrics-based routing is explicitly deferred unless diagnostics are ready

#### WP-1.x Detailed Execution Plan

Execution order inside `WP-1.x` should be:

1. freeze routing vocabulary and ownership boundary
2. freeze task classes and minimum capability labels
3. freeze compatibility-preserving primary-selection contract
4. freeze fallback and provider-order interaction rules
5. pilot bounded phase-aware routing on already split paths only
6. freeze diagnostics and later event handoff fields
7. freeze implementation cut and defer dynamic adaptive routing

##### WP-1.x Anti-Expansion Guardrails

Do not let `WP-1.x` expand into:

- policy redesign
- context lifecycle redesign
- multi-agent orchestration redesign
- new runtime graph topology
- benchmark platform build-out before routing contract lands
- cost-optimization experiments without diagnostics provenance

##### WP-1.x Task Matrix

| Task ID | Task Name | Focus Area | Primary Files / Classes | Expected Output | Acceptance Signal |
| --- | --- | --- | --- | --- | --- |
| WP1x-01 | Freeze routing boundary | scope and ownership | `AgentGraphBuilder`, `ProviderRouter`, `WikiModelRoutingService`, plan docs | one boundary note + ownership table | routing layer is additive and non-invasive |
| WP1x-02 | Freeze task classes | task taxonomy | runtime entry surfaces + template/skill model-needs hints | one task-class matrix | first-pass tasks are finite and explainable |
| WP1x-03 | Freeze task-aware dispatch | primary model selection | `AgentGraphBuilder`, `ProviderRouter`, model config/provider services | one dispatch contract table | task-aware selection can happen before model build |
| WP1x-04 | Freeze compatibility fallback rules | default model + provider fallback interaction | `AgentGraphBuilder`, `ProviderRouter`, provider services | one compatibility matrix | existing default/fallback semantics remain baseline-safe |
| WP1x-05 | Pilot phase-aware dispatch | bounded step-aware routing | `WikiModelRoutingService` + later validation/summarization candidates | one pilot note + phase matrix | phase-aware routing stays bounded to already split flows |
| WP1x-06 | Freeze diagnostics and implementation cut | observability and deferrals | runtime diagnostics/event handoff surfaces | one diagnostics matrix + implementation cut | dynamic adaptive routing stays explicitly deferred |

##### WP-1.x Detailed Worksheets

###### Worksheet `WP1x-01` — Freeze Routing Boundary

#### Task goal

Define the exact boundary of the new routing layer so it stays additive to the current runtime instead of becoming a second execution architecture.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java](mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java)
- [mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java](mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java)

#### Execution steps

1. identify the current main Agent model-selection entry point
2. identify existing local routing islands
3. freeze what the new routing overlay may decide and what it may not decide
4. freeze ownership boundaries with `WP-1`, `WP-2`, `WP-4`, `WP-5`, and `WP-6`
5. keep the first pass pre-build and diagnostics-oriented

#### Required output table template

| Boundary ID | Concern | Current Owner | `WP-1.x` Role | Must Not Do | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one routing-boundary ownership table
- one non-interference note
- one first-pass entry-point note

#### Acceptance checks

- routing stays additive to current runtime
- task selection and provider/model dispatch are included
- policy, context, and template redesign are kept out |

#### WP1x-01 first-pass routing-boundary table

| Boundary ID | Concern | Current Owner | `WP-1.x` Role | Must Not Do | Notes |
| --- | --- | --- | --- | --- | --- |
| RB01 | primary runtime model selection | `AgentGraphBuilder` | insert pre-build selection overlay | rewrite graph topology | main first-pass insertion point |
| RB02 | provider capability diagnostics | `ProviderRouter` | reuse and elevate into dispatch decision support | become policy engine | already useful for capability-aware routing |
| RB03 | provider configuration / fallback data | `ModelConfigService` + `ModelProviderService` | data source only | own business task classification | routing consumes, not redefines, these services |
| RB04 | step-aware wiki dispatch | `WikiModelRoutingService` | pilot reference / bounded phase-aware reuse | replace with generic framework first | existing proof point |
| RB05 | policy authority | `WP-2` surfaces | consume outputs only where needed | merge or reinterpret policy authority | explicit non-goal |
| RB06 | context lifecycle | `WP-4` surfaces | later consumer of model-context fit hints only | redesign context ranking or budgeting | explicit non-goal |

###### Worksheet `WP1x-02` — Freeze Task Classes and Minimum Capability Labels

#### Task goal

Define a small, explainable task taxonomy and the minimum capability labels needed for first-pass dispatch.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java](mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java)
- skill / template model-needs declarations already consumed by routing helpers
- representative runtime entry surfaces in Agent and Wiki flows

#### Execution steps

1. define finite first-pass task classes
2. separate user-visible task classes from internal dispatch labels
3. freeze the minimum capability labels needed now
4. keep non-mapped capabilities as later-plan items rather than fake precision
5. keep reasoning-heavy and multimodal classes planned but not mandatory in first pass

#### Required output table template

| Task Class | Dispatch Intent | Minimum Capability Labels | First-Pass Status | Notes |
| --- | --- | --- | --- | --- |

#### Expected deliverable

- one task-class table
- one capability-label note
- one deferred-label note

#### Acceptance checks

- first-pass classes are finite
- capability labels are small and implementable
- later classes are planned without forcing immediate rollout |

#### WP1x-02 first-pass task-class table

| Task Class | Dispatch Intent | Minimum Capability Labels | First-Pass Status | Notes |
| --- | --- | --- | --- | --- |
| `general_chat` | preserve compatibility baseline | `chat_stable` | required | default route |
| `code_fix` | prefer tool-stable, edit-stable model | `tool_stable`, `code_stable`, `json_stable` | required | high-value execution class |
| `wiki_processing` | prefer structured-output and long-form summary stability | `json_stable`, `summary_stable` | required | aligns with existing Wiki routing |
| `reasoning_heavy` | prefer stronger complex-reasoning path | `reasoning_strong` | planned later | classifier confidence needed |
| `multimodal` | require modality-aware route | `vision` or later modality labels | planned later | dependent on capability metadata quality |

#### WP1x-02 capability-label note

First pass should use a deliberately small label set. Do not attempt to encode every marketing claim or benchmark dimension into dispatch logic immediately.

###### Worksheet `WP1x-03` — Freeze Task-Aware Dispatch Contract

#### Task goal

Define how the main Agent runtime chooses a primary model before build while preserving current compatibility behavior.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java](mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java)
- [mateclaw-server/src/main/java/vip/mate/llm/service/ModelConfigService.java](mateclaw-server/src/main/java/vip/mate/llm/service/ModelConfigService.java)
- [mateclaw-server/src/main/java/vip/mate/llm/service/ModelProviderService.java](mateclaw-server/src/main/java/vip/mate/llm/service/ModelProviderService.java)

#### Execution steps

1. define one `DispatchRequest` input shape
2. define one `DispatchDecision` output shape
3. place dispatch before the current runtime model build
4. preserve `getDefaultModel()` as the fallback baseline
5. keep the decision explainable and serializable for diagnostics

#### Required output table template

| Contract Element | Purpose | Required? | Current Source | First-Pass Treatment | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one dispatch contract table
- one insertion-point note
- one compatibility rule note

#### Acceptance checks

- a single pre-build selection point exists conceptually
- current default model remains valid fallback
- decision output can be logged and later audited |

#### WP1x-03 first-pass dispatch contract

| Contract Element | Purpose | Required? | Current Source | First-Pass Treatment | Notes |
| --- | --- | --- | --- | --- | --- |
| `taskClass` | identify dispatch bucket | yes | routing overlay | required input | finite taxonomy |
| `agentId` | allow bound-skill/provider preference lookup | yes | runtime context | required input | enables existing `ProviderRouter` reuse |
| `requestedPhase` | optional phase-aware dispatch | no | runtime / wiki step | optional input | only pilot use |
| `capabilityNeeds` | explicit model-needs set | yes | task class + skills/templates | required input | small label set |
| `baselineModel` | compatibility fallback | yes | `ModelConfigService.getDefaultModel()` | required input | keeps safe fallback |
| `selectedModel` | chosen primary model | yes | dispatch output | required output | must always be set |
| `selectionReason` | explain why selection happened | yes | routing overlay | required output | diagnostics key field |
| `compatibilityBaselineUsed` | mark whether routing fell back to legacy baseline | yes | routing overlay | required output | avoids silent behavior drift |

#### WP1x-03 insertion-point note

The minimal code cut is to resolve `baselineModel`, build a `DispatchRequest`, ask the routing overlay for a `DispatchDecision`, and then pass `selectedModel` into the existing build path.

###### Worksheet `WP1x-04` — Freeze Compatibility Fallback Rules

#### Task goal

Make explicit how task-aware primary selection interacts with existing provider fallback and failover ordering.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java](mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java)
- provider fallback surfaces in model services

#### Execution steps

1. separate primary selection from fallback chain construction
2. keep current fallback chain semantics as baseline-safe
3. record when task-aware routing may bias primary but not fallback
4. record when capability-aware reorder may affect fallback ordering later
5. explicitly defer aggressive adaptive reorder logic

#### Required output table template

| Rule ID | Situation | Primary Selection Rule | Fallback Rule | First-Pass Status | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one compatibility matrix
- one baseline-safe note
- one deferred-reorder note

#### Acceptance checks

- primary selection and fallback are conceptually distinct
- compatibility baseline survives routing failure
- adaptive reorder remains deferred |

#### WP1x-04 first-pass compatibility matrix

| Rule ID | Situation | Primary Selection Rule | Fallback Rule | First-Pass Status | Notes |
| --- | --- | --- | --- | --- | --- |
| CF01 | routing overlay unavailable | use global default model | keep existing fallback chain | required | zero-regression rule |
| CF02 | task-aware route finds better primary | use routed primary model | keep existing fallback chain build logic | required | safest first pass |
| CF03 | routed primary provider unavailable | fall back to compatibility baseline model | keep existing fallback chain | required | fail-closed to baseline |
| CF04 | capability-aware fallback reorder candidate exists | document only | preserve current fallback order for first pass | deferred | avoid excessive behavior drift |

###### Worksheet `WP1x-05` — Pilot Bounded Phase-Aware Dispatch

#### Task goal

Allow phase-aware dispatch only on flows that already have explicit step separation.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java](mateclaw-server/src/main/java/vip/mate/wiki/job/WikiModelRoutingService.java)
- Wiki compile / narrative / processing callers
- validation or summarization-only paths if already isolated

#### Execution steps

1. inventory existing step-aware routing surfaces
2. identify one or two safe pilot phases only
3. keep phase labels implementation-light
4. avoid introducing new runtime phases just for routing
5. defer generalized phase router until after first-pass diagnostics

#### Required output table template

| Pilot ID | Existing Flow | Existing Phase Split? | Routing Opportunity | First-Pass Action | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one pilot-phase table
- one do-not-generalize-yet note
- one later-phase candidate note

#### Acceptance checks

- phase-aware routing remains bounded
- no new execution topology is introduced
- Wiki remains the main pilot reference |

#### WP1x-05 first-pass pilot-phase table

| Pilot ID | Existing Flow | Existing Phase Split? | Routing Opportunity | First-Pass Action | Notes |
| --- | --- | --- | --- | --- | --- |
| PH01 | Wiki processing | yes | keep step-specific model choice as reference architecture | retain existing routing and document shared contract | main pilot |
| PH02 | Wiki compile / summary-like flows | yes | align decision metadata with routing overlay | optional diagnostics alignment only | no framework rewrite |
| PH03 | validation / summarization in core agent | weak / partial | possible later route split | defer | do not invent phases now |

###### Worksheet `WP1x-06` — Freeze Diagnostics and Small Implementation Cut

#### Task goal

Define the minimum observability contract and the smallest code slice that delivers a safe first-pass routing overlay.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java](mateclaw-server/src/main/java/vip/mate/llm/routing/ProviderRouter.java)
- diagnostics / event-handoff surfaces adjacent to runtime build and execution

#### Execution steps

1. freeze routing diagnostics payload
2. define the minimum code-cut classes/helpers
3. define targeted tests for compatibility and task routing
4. list explicit deferments
5. freeze one small implementation draft

#### Required output table template

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one diagnostics matrix
- one implementation cut matrix
- one small implementation draft

#### Acceptance checks

- diagnostics explain decisions clearly
- code cut is bounded and compatibility-safe
- deferred adaptive routing is explicit |

#### WP1x-06 first-pass diagnostics matrix

| Diagnostic Field | Purpose | Required? | Notes |
| --- | --- | --- | --- |
| `taskClass` | classify why a route was chosen | yes | core decision field |
| `selectedProviderId` | identify runtime provider | yes | supports audits and token reports |
| `selectedModelName` | identify runtime model | yes | core decision field |
| `selectionReason` | explain route rationale | yes | human-readable summary |
| `compatibilityBaselineUsed` | show whether legacy fallback won | yes | protects rollout safety |
| `capabilityNeeds` | expose declared requirements | yes | small label set only |
| `phaseLabel` | show step-specific route when used | optional | pilot only |
| `fallbackChainPreview` | show planned fallback order | optional | may stay debug-only first |

#### WP1x-06 first-pass implementation cut matrix

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |
| C1 | add routing DTOs/enums | new `llm/routing` contract package | `TaskClass`, `DispatchRequest`, `DispatchDecision`, `DispatchReason` | compile + unit tests | package placement drift |
| C2 | add small `TaskRouter` service | new routing helper + `ProviderRouter` reuse | first-pass task classification + dispatch selection | unit tests on task-to-model choice | classifier overreach |
| C3 | wire pre-build decision into `AgentGraphBuilder` | [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java) | selected primary model before runtime build | compatibility regression tests | hidden runtime drift |
| C4 | add routing diagnostics log/debug payload | routing helper + runtime diagnostics surface | explainable routing output | snapshot/log assertion tests | premature public API expansion |

##### WP-1.x Small Implementation Draft

#### Proposed first-pass classes

| Class / DTO | Type | Purpose |
| --- | --- | --- |
| `TaskClass` | enum | bounded task taxonomy |
| `DispatchReason` | enum | `DEFAULT_BASELINE`, `TASK_MATCH`, `SKILL_CAPABILITY_MATCH`, `WIKI_STEP_ROUTE`, `ROUTER_UNAVAILABLE` |
| `DispatchRequest` | DTO | `agentId`, `taskClass`, `baselineModel`, `capabilityNeeds`, optional `phaseLabel` |
| `DispatchDecision` | DTO | `selectedModel`, `selectionReason`, `compatibilityBaselineUsed`, optional `fallbackPreview` |
| `TaskRouter` | service | classify task + choose primary model using existing provider/config services |

#### Proposed minimal flow

1. `AgentGraphBuilder` gets `baselineModel = modelConfigService.getDefaultModel()`
2. `TaskRouter` builds a bounded `DispatchRequest`
3. `TaskRouter` returns `DispatchDecision`
4. `AgentGraphBuilder` uses `decision.selectedModel()` as the runtime primary model
5. existing fallback-chain logic remains active behind that selected primary model
6. diagnostics log the route reason and whether compatibility baseline was used

#### Proposed classification rule sketch

| Signal | Resulting `TaskClass` | First-Pass Rule |
| --- | --- | --- |
| default / no strong signal | `general_chat` | baseline |
| compile / lint / test repair runtime mode or explicit coding-repair hints | `code_fix` | prefer execution-stable model |
| wiki compile / enrich / summary flow | `wiki_processing` | keep routed or structured-output-oriented model |

#### Proposed compatibility-safe pseudocode

1. `baseline = getDefaultModel()`
2. `request = taskRouter.buildRequest(agentId, runtimeMode, optionalPhase, baseline)`
3. `decision = taskRouter.selectPrimary(request)`
4. `runtimeModel = decision.selectedModel != null ? decision.selectedModel : baseline`
5. if selection fails, mark `compatibilityBaselineUsed = true`
6. continue into existing model-build and fallback-chain path

#### WP1x-06 deferred-items note

Explicitly defer from the small implementation draft:

- rate/latency/cost/error adaptive routing
- fully generalized phase router for all agent steps
- template-driven task classifier expansion beyond light hints
- public routing UI or admin control plane

##### WP-1.x First-Pass Routing Position

| Implementation Slice | Included in first pass? | Notes |
| --- | --- | --- |
| unified task router | yes | minimal contract only |
| task-aware dispatch | yes | first bounded rollout |
| phase-aware dispatch | limited pilot | only where phase split already exists |
| dynamic signal routing | no | wait for routing metrics and diagnostics completeness |

##### WP-1.x First-Pass Task Class Matrix

| Task Class | First-Pass? | Typical Example | Primary Routing Goal | Notes |
| --- | --- | --- | --- | --- |
| `general_chat` | yes | normal assistant turn | keep compatibility baseline | default path |
| `code_fix` | yes | lint/test/compile error repair | prefer execution-stable coding model | high near-term value |
| `wiki_processing` | yes | compile/enrich/summary | reuse existing step-aware routing surfaces | already partially present |
| `reasoning_heavy` | later | architecture/root-cause analysis | prefer stronger reasoning model | needs stronger classifier confidence |
| `multimodal` | later | image/file/vision-heavy requests | require modality-aware dispatch | depends on capability metadata quality |

##### WP-1.x Routing Diagnostics Contract

Minimum routing diagnostics should include:

- `taskClass`
- `selectedProviderId`
- `selectedModelName`
- `selectionReason`
- `fallbackChainPreview`
- `capabilityNeeds`
- `compatibilityBaselineUsed`
- `phaseLabel` when phase-aware routing is active

##### WP-1.x Deferred Items Note

Explicitly defer from `WP-1.x`:

- dynamic latency/cost/error-rate adaptive routing as a production decision maker
- broad route-by-template marketplace behavior
- graph-level planner/validator split redesign
- UI-facing routing console before the diagnostics contract is stable
- replacing Wiki routing with a new generalized framework in the first pass

##### WP-1.x Final Acceptance Grid

`WP-1.x` is review-ready when:

- one cross-cutting routing boundary is documented
- first-pass task classes are frozen
- main Agent runtime can make one explainable primary-selection decision before model build
- compatibility fallback remains intact
- phase-aware routing remains pilot-only and bounded
- dynamic signal routing remains explicitly deferred until metrics are ready

---

### WP-2 — Policy Authority Matrix Consolidation

#### Objective

Make policy the visible top authority across core systems, while preserving current guard behavior.

#### Functional Points

1. Define policy scopes:
   - global/system
   - workspace
   - template-safe defaults
   - runtime constraints
2. Define policy application modes:
   - constrain
   - tighten
   - veto
3. Formalize “policy is not a normal setting”.
4. Centralize or document restrictive merge semantics.
5. Publish one matrix describing which subsystem consumes which policy dimension.
6. Ensure template safety defaults can tighten but not silently weaken stronger workspace/system policy.

#### Modification Scope

Primary server scope:

- [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)

Secondary scope:

- approval-related policy adapters
- template metadata parsing used during policy merge

Allowed change type:

- extract merge helpers
- add policy authority matrix docs
- add policy source metadata to runtime decisions
- tighten template/workspace policy merge contract

Not allowed in this package:

- replacing the existing approval pipeline
- adding new policy storage backends
- broad tool execution refactor unrelated to authority resolution

#### Suggested Deliverables

1. `PolicyAuthorityMatrix` document/table
2. reusable restrictive merge helper for policy fields
3. explicit template-policy merge contract
4. targeted tests for “more restrictive wins” cases

#### Acceptance Checkpoint

WP-2 is done when:

- the effective policy source chain is explainable
- template/workspace/system interactions are deterministic
- current tool guard and approval behavior remain functionally unchanged

#### WP-2 Landing Record

**Status:** First-pass landed  
**Landed at:** 2026-05-20  

**Deliverables produced:**

| Artifact | Path | Status |
| --- | --- | --- |
| `PolicyScope` enum (authority layer taxonomy) | `mateclaw-server/src/main/java/vip/mate/policy/contract/PolicyScope.java` | landed |
| `PolicyDimension` enum (enforceable dimensions) | `mateclaw-server/src/main/java/vip/mate/policy/contract/PolicyDimension.java` | landed |
| `PolicyApplicationMode` enum (constrain/tighten/veto) | `mateclaw-server/src/main/java/vip/mate/policy/contract/PolicyApplicationMode.java` | landed |
| `EffectivePolicyContract` interface (read-only policy resolver contract) | `mateclaw-server/src/main/java/vip/mate/policy/contract/EffectivePolicyContract.java` | landed |
| `PolicyMergeHelper` (centralized restrictive-merge logic) | `mateclaw-server/src/main/java/vip/mate/policy/resolver/PolicyMergeHelper.java` | landed |
| `EffectivePolicyResolver` (layered workspace/template/runtime resolver) | `mateclaw-server/src/main/java/vip/mate/policy/resolver/EffectivePolicyResolver.java` | landed |
| `PolicyMergeHelperTest` (unit tests for tighten semantics and provenance) | `mateclaw-server/src/test/java/vip/mate/policy/resolver/PolicyMergeHelperTest.java` | landed |

**Remediation sync (2026-05-20):**
- `WP2-02`, `WP2-03`, `WP2-06`, and `WP2-07` task notes are now aligned with the actual `PolicyMergeHelper` behavior.
- `allowedPaths` is treated as additive helper merge, and `riskOverrides` now documents the current workspace-first collision rule instead of the old overwrite ambiguity wording.
- Remaining `WP-2` follow-up stays unchanged: executor wiring and deeper guard-engine precedence confirmation are still deferred.

**Key design decisions:**
- All new code lives in isolated `vip.mate.policy.contract.*` and `vip.mate.policy.resolver.*` packages.
- `PolicyMergeHelper` centralizes the merge logic previously embedded in `ToolExecutionExecutor`.
- Template defaults use `TIGHTEN` semantics: they can only make policy more restrictive, never less.
- Per-dimension `DimensionProvenance` records which scope won, what mode was applied, and what the losing values were.
- `EffectivePolicyResolver` is a Spring component that can be injected into the executor later; it does not change executor behavior today.

**Deferred to later packages:**
- Wiring `EffectivePolicyResolver` into `ToolExecutionExecutor` (deferred to WP-2 second pass or guard-engine refactor).
- Global-scope policy layer (no global policy surface exists today; reserved for future global locks).
- Runtime emergency-stop override path (contract exists but no producer yet).

#### WP-2 Detailed Execution Plan

`WP-2` should stay narrowly focused on **policy authority, restrictive merge semantics, and enforcement traceability**.

Execution order inside `WP-2` should be:

1. freeze policy authority sources and scopes
2. freeze policy dimension contract
3. freeze restrictive merge semantics per dimension
4. freeze enforcement and consumption path
5. freeze approval coupling model
6. freeze template/workspace authority contract
7. define diagnostics and targeted test matrix
8. freeze implementation cut and acceptance grid

##### WP-2 Anti-Expansion Guardrails

Do not let `WP-2` expand into:

- approval workflow replacement
- new policy storage backends
- broad tool execution refactor beyond policy merge traceability
- template schema redesign
- desktop UX or workspace UI redesign
- unrelated guard-engine feature expansion

##### WP-2 Task Matrix

| Task ID | Task Name | Focus Area | Primary Files / Classes | Expected Output | Acceptance Signal |
| --- | --- | --- | --- | --- | --- |
| WP2-01 | Freeze authority sources | policy source taxonomy | [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java), [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java) | one policy authority source hierarchy table | every observed policy source has one scope and one authority role |
| WP2-02 | Freeze dimension contract | policy field contract | [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java), [mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java](mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java) | one policy-dimension matrix | each dimension has scope, mode, and consumption note |
| WP2-03 | Freeze restrictive merge rules | effective policy merge | [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java), [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) | one merge-rules matrix | every dimension has deterministic merge semantics or explicit ambiguity |
| WP2-04 | Freeze enforcement path | guard consumption chain | [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java), [mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java](mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java), [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java) | one enforcement-path matrix | policy load, merge, context injection, evaluation, and audit are traceable |
| WP2-05 | Freeze approval coupling | policy-to-approval bridge | [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java), [mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java) | one approval-coupling matrix | policy-triggered approval behavior is explainable without redesign |
| WP2-06 | Freeze template/workspace contract | template-safe defaults vs workspace authority | [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java), template metadata sources, [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java) | one authority contract table | template tightening boundaries are explicit |
| WP2-07 | Define diagnostics and tests | observability / regression boundary | guard audit + approval metadata surfaces | one diagnostics payload note + targeted test matrix | “more restrictive wins” cases are enumerated |
| WP2-08 | Freeze implementation cut | execution-ready bounded plan | outputs of `WP2-01` to `WP2-07` | one implementation cut matrix + acceptance grid | `WP-2` can move into code work without reopening design scope |

##### WP-2 Detailed Worksheets

###### Worksheet `WP2-01` — Freeze Policy Authority Sources

#### Task goal

Freeze one bounded policy authority hierarchy so policy stops behaving like an implicit side effect of settings plumbing.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java](mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java)

#### Execution steps

1. list every currently observed policy-bearing source
2. distinguish persisted authority from runtime-composed authority
3. assign one scope and one authority role per source
4. mark which sources can tighten and which can only carry context
5. explicitly separate policy from ordinary settings carriers

#### Required output table template

| Source ID | Policy Source | Current Carrier | Scope | Authority Role | Can Tighten? | Can Weaken? | Persisted or Runtime | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one policy authority source hierarchy table
- one note separating persisted policy from runtime effective policy
- one mixed-carrier warning note

#### Acceptance checks

- all observed policy-bearing sources are listed
- runtime-composed effective policy is distinguished from stored policy
- `projectPermissionMode` is explicitly treated as boundary authority, not ordinary setting

#### Explicit non-goals

- no settings resolver redesign in this package
- no new policy storage format
- no approval redesign

#### WP2-01 first-pass authority source hierarchy

| Source ID | Policy Source | Current Carrier | Scope | Authority Role | Can Tighten? | Can Weaken? | Persisted or Runtime | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P2-S01 | workspace policy persisted record | `WorkspaceEntity.settingsJson` hydrated to `WorkspacePolicy` | workspace | primary persisted workspace authority | yes | no intended | persisted | main shared policy source today |
| P2-S02 | `projectPermissionMode` boundary authority | workspace column + `settingsJson` mirror | workspace | mode switch that can bypass template tightening via full-access path | yes | yes in effect, because full access disables template tightening | persisted + runtime interpreted | mixed settings/policy carrier |
| P2-S03 | template default workspace policy | `templateMetadataJson` → `defaultWorkspacePolicy` | template-safe default | runtime tightening source | yes | no intended | runtime-composed from persisted template snapshot | applied only when not full access |
| P2-S04 | full-access runtime flag | executor `fullAccess` boolean / `workspacePolicyMode` | runtime invocation | runtime authority mode selector | yes | yes relative to template-tightening path | runtime only | not a stored policy object, but changes effective policy composition |
| P2-S05 | tool invocation context policy payload | `ToolInvocationContext.workspacePolicy` | per tool call | carrier of effective policy into guard engine | no, carrier only | no | runtime only | effective policy handoff surface |
| P2-S06 | guard denied-tools config | `ToolGuardConfigService` global config path | system/global guard | absolute block layer adjacent to policy | yes | no | persisted/runtime config | not in `WorkspacePolicy`, but authority-bearing guard layer |
| P2-S07 | evaluation-derived approval key context | `GuardEvaluation` + approval key / grant scope | approval reuse scope | downstream authority continuity surface | no direct policy write | no | runtime + persisted approval rows | authority consequence, not source-of-truth policy |

#### WP2-01 persisted-vs-runtime note

Persisted policy today lives primarily in workspace state and template metadata snapshots. Effective policy is only finalized at runtime inside `ToolExecutionExecutor` before entering `ToolInvocationContext`.

#### WP2-01 mixed-carrier warning note

`projectPermissionMode` is the highest-risk mixed carrier because it travels through workspace settings plumbing but acts as an authority mode selector over policy composition.

###### Worksheet `WP2-02` — Freeze Policy Dimension Contract

#### Task goal

Define a stable contract for each policy dimension so merge and enforcement rules can be made explicit without adding new policy types.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java](mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)

#### Execution steps

1. enumerate every field in `WorkspacePolicy`
2. classify each field by authority mode
3. identify the effective scope of each field
4. note current runtime metadata surface for each field
5. mark ambiguities that must remain explicit

#### Required output table template

| Dimension ID | Policy Dimension | Current Type / Values | Effective Scope | Authority Mode | Runtime Carrier | Primary Consumer | Ambiguity / Note |
| --- | --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one policy-dimension matrix
- one note on dimension families
- one ambiguity note for unsafe assumptions

#### Acceptance checks

- every `WorkspacePolicy` field is classified
- constrain / tighten / veto behavior is explicit per dimension family
- consumer surface is named for each dimension

#### Explicit non-goals

- no new policy fields
- no guard-engine rule rewrite
- no approval grant redesign

#### WP2-02 first-pass policy-dimension matrix

| Dimension ID | Policy Dimension | Current Type / Values | Effective Scope | Authority Mode | Runtime Carrier | Primary Consumer | Ambiguity / Note |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D01 | `sandboxMode` | `read-only` / `workspace-write` / `full-access` | tool execution filesystem behavior | constrain / veto | `ToolInvocationContext.workspacePolicy.sandboxMode` | guard engine via tool invocation context | full relationship to `projectPermissionMode` must stay explicit |
| D02 | `approvalPolicy` | `default` / `strict` | tool approval threshold | tighten | workspace policy in invocation context | guard evaluation + approval workflow trigger | exact per-finding threshold logic lives deeper in guard engine |
| D03 | `networkPolicy` | `inherit` / `restricted` / `disabled` | network access during tool execution | constrain / veto | workspace policy in invocation context | guard engine / tool execution constraints | non-tool network paths need later validation |
| D04 | `allowedPaths` | string list | workspace path allowance | constrain | workspace policy in invocation context | guard/path-related evaluation | current helper unions workspace and template lists; final engine precedence still needs one confirmation pass |
| D05 | `deniedPaths` | string list | workspace path denial | veto | workspace policy in invocation context | guard/path-related evaluation | later confirm deny precedence inside engine |
| D06 | `riskOverrides` | map of action/rule to decision | per-risk override | tighten / veto depending on decision | workspace policy in invocation context | guard evaluation + approval key generation | workspace key wins on collision; per-decision strictness ordering is still deferred |
| D07 | `workspacePolicyMode` / `projectPermissionMode` adjunct | `limited` / `full` | invocation-wide authority mode | composition mode selector | `ToolInvocationContext.workspacePolicyMode` | executor + downstream guard context | not a `WorkspacePolicy` field, but unavoidable boundary dimension |

#### WP2-02 dimension-family note

Current policy dimensions naturally group into:

- mode selectors: `sandboxMode`, `networkPolicy`, `workspacePolicyMode`
- approval thresholding: `approvalPolicy`, `riskOverrides`
- path constraints: `allowedPaths`, `deniedPaths`

###### Worksheet `WP2-03` — Freeze Restrictive Merge Rules

#### Task goal

Make current effective-policy merge semantics explicit and bounded, especially where “more restrictive wins” is intended but not yet fully formalized.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)

#### Execution steps

1. capture current merge helper behavior per dimension
2. distinguish normalize-only behavior from cross-source merge behavior
3. identify dimensions already deterministic
4. identify dimensions that are deterministic today vs dimensions that still need deeper engine confirmation
5. record the minimum safe contract for later code extraction

#### Required output table template

| Merge ID | Dimension / Family | Current Merge Owner | Current Observed Rule | Deterministic? | Intended Restrictive Contract | Open Risk |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one restrictive-merge rules matrix
- one note on deterministic vs ambiguous dimensions
- one explicit risk list for targeted tests

#### Acceptance checks

- every policy dimension has a current observed rule
- deterministic helper rules are recorded explicitly and deeper engine questions stay flagged
- workspace normalization and template merge are separated conceptually

#### Explicit non-goals

- no immediate merge helper implementation
- no new policy precedence layer
- no path-engine rewrite

#### WP2-03 first-pass restrictive-merge matrix

| Merge ID | Dimension / Family | Current Merge Owner | Current Observed Rule | Deterministic? | Intended Restrictive Contract | Open Risk |
| --- | --- | --- | --- | --- | --- | --- |
| M01 | workspace persisted normalization | `WorkspaceService.normalizeWorkspacePolicy()` | normalize values and null-safe defaults only | yes | keep as source normalization, not cross-source merge | should not be mistaken for effective merge owner |
| M02 | `sandboxMode` | `ToolExecutionExecutor.mergeTemplatePolicy()` | rank-based `moreRestrictiveSandbox()` | yes | stronger restriction wins | depends on stable rank model |
| M03 | `approvalPolicy` | executor merge helper | `strict` wins, otherwise left if present else right | mostly yes | stricter approval wins | simple today, but future modes would need explicit ordering |
| M04 | `networkPolicy` | executor merge helper | rank-based `moreRestrictiveNetwork()` | yes | stronger network restriction wins | assumes complete finite ordering |
| M05 | `allowedPaths` | `PolicyMergeHelper.mergeAllowedPaths()` | union of workspace + template paths with deduplication | yes at helper level | additive merge stays explicit until guard-engine path precedence is separately frozen | final engine interpretation still needs explicit confirmation |
| M06 | `deniedPaths` | executor merge helper | union of both lists | yes | any denied path remains denied | deny precedence in engine still needs explicit confirmation |
| M07 | `riskOverrides` | `PolicyMergeHelper.mergeRiskOverrides()` | workspace map wins on same-key collision; template map fills gaps only | yes at helper level | workspace authority remains stable while template defaults only tighten by filling missing keys | later refine only if guard engine adopts an explicit severity lattice |
| M08 | full-access bypass | executor `fullAccess` gate | skip template merge entirely when full access | yes | explicit composition mode rule | can appear as weakening if not documented as separate authority mode |

#### WP2-03 deterministic-vs-ambiguous note

Most merge-helper rules are now deterministic. The remaining uncertainty is no longer the helper behavior itself, but the downstream guard-engine interpretation of path precedence and risk severity.

###### Worksheet `WP2-04` — Freeze Enforcement Path

#### Task goal

Trace the full policy enforcement path from persisted source to guard evaluation and audit.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java](mateclaw-server/src/main/java/vip/mate/tool/guard/model/ToolInvocationContext.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java)

#### Execution steps

1. map policy load points
2. map runtime composition points
3. map context handoff into guard evaluation
4. map audit recording point
5. record places where policy metadata is lost or implicit

#### Required output table template

| Step ID | Lifecycle Step | Class / Method | Input Policy Surface | Output / Side Effect | Metadata Preserved? | Note |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one enforcement-path matrix
- one metadata-loss note
- one consumer-boundary note

#### Acceptance checks

- persisted load, runtime merge, guard evaluation, and audit are all mapped
- effective policy handoff surface is explicit
- metadata gaps are named rather than hidden

#### Explicit non-goals

- no end-to-end tracing platform redesign
- no new event bus
- no harness merge into policy path

#### WP2-04 first-pass enforcement-path matrix

| Step ID | Lifecycle Step | Class / Method | Input Policy Surface | Output / Side Effect | Metadata Preserved? | Note |
| --- | --- | --- | --- | --- | --- | --- |
| E01 | persisted workspace policy load | `WorkspaceService.resolveWorkspacePolicy()` | workspace persisted policy | normalized `WorkspacePolicy` | partial | source provenance not attached |
| E02 | project permission mode load | `WorkspaceService.resolveProjectPermissionMode()` | persisted mode field | normalized mode string | partial | separate from policy object |
| E03 | guard context construction | `ToolExecutionExecutor.buildGuardContext()` | workspace policy + fullAccess flag + workspace/user roles | `ToolInvocationContext` | partial | central runtime composition point |
| E04 | template policy composition | `ToolExecutionExecutor.mergeTemplatePolicy()` | workspace policy + template policy + fullAccess | effective policy object | low | source-of-origin per field not preserved |
| E05 | invocation carrier handoff | `ToolInvocationContext.of(...)` | effective policy + workspacePolicyMode + tool metadata | guard-ready invocation context | medium | payload present, provenance absent |
| E06 | guard evaluation | `ToolGuardService.evaluate()` | `ToolInvocationContext` | `GuardEvaluation` | low-medium | evaluation summary/findings preserved, policy source chain not preserved |
| E07 | guard audit recording | `ToolGuardAuditService.record(...)` via `ToolGuardService` | context + evaluation + optional pendingId | audit row | partial | useful decision audit, weak policy-origin trace |

#### WP2-04 metadata-loss note

The biggest current metadata gap is that effective-policy provenance is collapsed before guard evaluation. The guard sees the effective policy payload, but not which source won each field.

###### Worksheet `WP2-05` — Freeze Approval Coupling Model

#### Task goal

Clarify how policy outcomes couple to approval creation, approval reuse, and approval persistence without redesigning the workflow.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java)
- [mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalGrantService.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java)

#### Execution steps

1. identify which policy outputs can lead to approval-required outcomes
2. map how evaluation metadata flows into pending approvals
3. map how reusable grants are keyed and scoped
4. separate policy authority from approval persistence
5. record where grant reuse depends on evaluation details

#### Required output table template

| Coupling ID | Policy / Evaluation Surface | Approval Service Consumer | Persisted Metadata | Reuse Surface | Boundary Note |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one approval-coupling matrix
- one note on policy vs approval authority separation
- one reuse-safety note

#### Acceptance checks

- pending approval creation path is traceable
- approval key / grant scope path is documented
- policy and approval are distinguished as separate layers |

#### Explicit non-goals

- no approval UX redesign
- no new grant scopes
- no approval storage changes

#### WP2-05 first-pass approval-coupling matrix

| Coupling ID | Policy / Evaluation Surface | Approval Service Consumer | Persisted Metadata | Reuse Surface | Boundary Note |
| --- | --- | --- | --- | --- | --- |
| A01 | guard evaluation requiring approval | `ApprovalWorkflowService.createPending(...)` | findings JSON, max severity, summary, workspaceId, projectPath, approvalKey, chat origin | pending approval rows + in-memory pending map | approval is consequence of policy/evaluation, not policy source |
| A02 | reusable grant identity | `ApprovalGrantService.buildApprovalKey()` | approval key persisted on approval row | `ApprovalGrantService.hasReusableGrant()` | reuse depends on evaluation signature, not raw policy object |
| A03 | grant scope selection | approval resolve path with `ApprovalGrantScope` | grantScope on approved rows when reusable | conversation/project reuse match | scope is user approval decision, not policy authority |
| A04 | pending-to-audit coupling | guard audit with optional `pendingId` | audit row linked to approval lifecycle indirectly | audit + DB + memory | policy decision and approval persistence remain separate stores |

#### WP2-05 separation note

Policy decides whether a tool call should be constrained, blocked, or require approval. Approval workflow persists and reuses the outcome, but it is not itself the authority source for policy.

###### Worksheet `WP2-06` — Freeze Template / Workspace Authority Contract

#### Task goal

Make the template-versus-workspace authority rule explicit so template-safe defaults can tighten behavior without becoming a stronger general authority source.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- template metadata snapshot path in [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)

#### Execution steps

1. identify current template-policy load path
2. identify current workspace-policy load path
3. record runtime composition rules per mode
4. define minimum authority contract wording
5. explicitly call out full-access exception behavior

#### Required output table template

| Contract Rule ID | Situation | Winning Authority Rule | Current Implementation Surface | Safe Contract Wording | Open Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one template/workspace authority contract table
- one explicit full-access exception note
- one minimum wording proposal for future code/docs

#### Acceptance checks

- template defaults are classified as tightening-only where intended
- workspace authority remains visible
- full-access exception is explicit, not implicit |

#### Explicit non-goals

- no template contract regrouping
- no workspace schema changes
- no global/system policy backend introduction

#### WP2-06 first-pass authority contract table

| Contract Rule ID | Situation | Winning Authority Rule | Current Implementation Surface | Safe Contract Wording | Open Risk |
| --- | --- | --- | --- | --- | --- |
| T01 | no template policy present | workspace policy stands alone | `mergeTemplatePolicy()` early return | workspace persisted policy is effective policy | low |
| T02 | template policy present and not full access | more restrictive template/workspace composition applies | `mergeTemplatePolicy()` / `PolicyMergeHelper` | template safe defaults may tighten effective workspace policy but must not silently weaken it | medium because downstream guard-engine interpretation still needs one confirmation pass |
| T03 | full-access mode | template policy merge is skipped | `if (fullAccess) return workspacePolicy;` | full-access mode is an explicit authority composition mode that bypasses template tightening | high perception risk if undocumented |
| T04 | template policy only with no workspace policy | template policy becomes effective runtime policy | executor merge path | template may supply the effective runtime safety default when workspace policy is absent | medium because authority origin can appear inverted |

#### WP2-06 full-access exception note

The full-access path should be documented as an explicit composition mode, not as an accidental weakening bug. Otherwise template/workspace authority behavior will remain misleading to future maintainers.

###### Worksheet `WP2-07` — Define Diagnostics and Targeted Tests

#### Task goal

Define the minimum observability and regression matrix needed to make policy authority behavior explainable and safe to refactor.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)
- [mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java](mateclaw-server/src/main/java/vip/mate/approval/ApprovalWorkflowService.java)

#### Execution steps

1. define minimum policy source-tracing metadata
2. define targeted merge-regression cases
3. define approval-coupling regression cases
4. separate deterministic checks from ambiguity-lock tests
5. keep the matrix small and implementation-ready

#### Required output table template

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one targeted test matrix
- one diagnostics payload note for effective policy tracing
- one list of ambiguity-lock tests

#### Acceptance checks

- restrictive merge cases are explicitly covered
- approval-reuse coupling is covered
- current helper behavior is locked by test and any deeper engine questions are explicitly deferred |

#### Explicit non-goals

- no full observability platform rollout
- no giant integration-test program
- no harness contract redesign

#### WP2-07 first-pass targeted test matrix

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |
| TD01 | sandbox restrictive merge | workspace sandbox + template sandbox | stricter sandbox wins | unit | rank ordering lock |
| TD02 | approval strict merge | workspace default + template strict | effective approval policy is strict | unit | deterministic |
| TD03 | network restrictive merge | workspace inherit + template disabled | effective network policy is disabled | unit | deterministic |
| TD04 | denied paths union | workspace denied + template denied | effective list contains both | unit | deterministic |
| TD05 | allowed paths contract | workspace allowed + template allowed | helper unions and deduplicates both lists | unit / contract lock | helper behavior is deterministic; engine precedence remains a separate follow-up |
| TD06 | risk override same-key conflict | workspace + template same key, different decision | workspace value wins and template-only keys still land | unit / contract lock | helper behavior is deterministic; severity ordering remains a separate follow-up |
| TD07 | full-access bypass | fullAccess=true + template policy present | template merge skipped | unit | composition-mode lock |
| TD08 | approval metadata persistence | evaluation requiring approval | pending row stores findings/summary/approvalKey | integration-light | approval coupling lock |
| TD09 | reusable grant lookup | approved reusable grant with matching signature | reuse is recognized for conversation/project scope | integration-light | approval key stability |

#### WP2-07 effective-policy diagnostics note

Minimum diagnostics for `WP-2` should include:

- effective policy payload
- source chain summary (`workspace`, `template`, `fullAccess` mode)
- per-dimension merge rule label where practical
- policy-adjacent mode flag for `projectPermissionMode`

###### Worksheet `WP2-08` — Freeze Implementation Cut and Acceptance Grid

#### Task goal

Turn `WP-2` outputs into a bounded code-change plan that can be implemented without pulling `WP-1` or `WP-6` fully into the same refactor.

#### Inputs required

- outputs from `WP2-01` to `WP2-07`

#### Execution steps

1. define the minimum code cut for first implementation pass
2. assign file/class touchpoints
3. identify regression tests
4. list explicit deferments
5. freeze final acceptance grid

#### Required output table template

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one implementation cut matrix
- one deferred-items note
- one final acceptance grid for `WP-2`

#### Acceptance checks

- each cut has bounded files and bounded output
- deferred items are explicit
- `WP-2` can start coding without reopening design discovery

#### Explicit non-goals

- no bundling of `WP-3` or `WP-6` into the same implementation pass
- no approval workflow replacement
- no hidden settings resolver rewrite beyond policy handoff boundaries

#### WP2-08 first-pass implementation cut matrix

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |
| C1 | add policy source / dimension models or docs | policy/shared package or docs-only layer | `PolicySource`, `PolicyDimension`, authority matrix docs | compile if code-backed + table review | model placement drift |
| C2 | extract effective policy merge helper | [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java), possibly adjacent helper | reusable merge contract with unchanged outcomes | unit tests for restrictive merge cases | accidental behavior change |
| C3 | add policy-origin metadata to runtime context or diagnostics | executor + guard context / diagnostics DTOs | inspectable effective policy chain | unit tests / snapshot checks | over-expansion into tracing platform |
| C4 | add targeted approval-coupling assertions | approval workflow + grant service tests | regression coverage for pending/grant behavior | integration-light tests | approval redesign creep |
| C5 | document template/workspace/full-access authority contract | docs + optional code comments at merge site | explicit contract wording near implementation | doc review + contract tests | contract drift if not colocated |

#### WP2-08 deferred-items note

Explicitly defer from `WP-2`:

- global/system policy backend introduction
- final redesign of `projectPermissionMode` storage carrier
- full guard-engine internal rule inventory
- template schema regrouping
- approval UX and cross-channel approval presentation changes

#### WP2-08 final acceptance grid

`WP-2` is review-ready when:

- one authority source hierarchy is frozen
- one policy-dimension contract is frozen
- one restrictive-merge contract is frozen with explicit ambiguities
- one end-to-end enforcement path is documented
- policy/approval coupling is documented without conflation
- template/workspace/full-access authority behavior is explicit
- targeted tests cover deterministic rules and lock current ambiguities

---

### WP-3 — Memory Taxonomy and Governance Consolidation

#### Objective

Separate long-term memory from cache, context, and grounding without changing current task flows.

#### Functional Points

1. Define a canonical information taxonomy:
   - long-term memory
   - project cache
   - working context
   - session ephemeral state
   - knowledge grounding
2. Define memory write eligibility rules.
3. Define memory exclusion rules.
4. Define provenance requirements for memory write candidates.
5. Define retention/review semantics at the contract level.
6. Clarify the role of `MEMORY.md`.

#### Modification Scope

Primary server scope:

- [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java)
- memory provider / lifecycle packages under `vip.mate.memory`
- memory candidate extraction and write-entry surfaces consumed during run completion

Related context scope:

- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)

Documentation scope:

- memory governance section in docs
- future `MEMORY.md` contract notes if applicable

Allowed change type:

- add taxonomy models / enums / docs
- add candidate classification metadata
- add memory write filters

Not allowed in this package:

- replacing memory storage provider architecture
- migrating all memory data formats
- adding a new memory product experience

#### Suggested Deliverables

1. memory taxonomy document
2. write eligibility checklist
3. exclusion list enforcement points
4. provenance fields for memory candidate metadata

#### Acceptance Checkpoint

WP-3 is done when:

- a developer can tell whether a datum is memory, cache, context, or grounding
- memory writes are rule-driven rather than ad hoc
- no current conversation flow is broken by the boundary tightening

#### WP-3 Landing Record

**Status:** First-pass landed  
**Landed at:** 2026-05-20  

**Deliverables produced:**

| Artifact | Path | Status |
| --- | --- | --- |
| `MemorySurfaceType` enum (memory surface taxonomy) | `mateclaw-server/src/main/java/vip/mate/memory/contract/MemorySurfaceType.java` | landed |
| `MemoryOperation` enum (read/write/summarize/consolidate/score/delete) | `mateclaw-server/src/main/java/vip/mate/memory/contract/MemoryOperation.java` | landed |
| `MemoryWriteProvenance` record (write metadata per surface/operation) | `mateclaw-server/src/main/java/vip/mate/memory/contract/MemoryWriteProvenance.java` | landed |
| `MemoryGovernanceContract` interface (permissions + provenance + target classification) | `mateclaw-server/src/main/java/vip/mate/memory/contract/MemoryGovernanceContract.java` | landed |
| `MemoryGovernanceFilter` (rule-based implementation of governance contract) | `mateclaw-server/src/main/java/vip/mate/memory/governance/MemoryGovernanceFilter.java` | landed |
| `MemoryWriteProvenancePublisher` (governed provenance + event normalization) | `mateclaw-server/src/main/java/vip/mate/memory/governance/MemoryWriteProvenancePublisher.java` | landed |
| `MemoryGovernanceFilterTest` | `mateclaw-server/src/test/java/vip/mate/memory/governance/MemoryGovernanceFilterTest.java` | landed |
| `MemoryWriteProvenancePublisherTest` | `mateclaw-server/src/test/java/vip/mate/memory/governance/MemoryWriteProvenancePublisherTest.java` | landed |
| `MemoryArchiveServiceTest` | `mateclaw-server/src/test/java/vip/mate/memory/archive/MemoryArchiveServiceTest.java` | landed |
| `UniversalMemoryToolTest` | `mateclaw-server/src/test/java/vip/mate/memory/tool/UniversalMemoryToolTest.java` | landed |
| `SoulSummarizerServiceTest` | `mateclaw-server/src/test/java/vip/mate/memory/service/SoulSummarizerServiceTest.java` | landed |

**Remediation sync (2026-05-20):**
- Governed writer surfaces now close the provenance loop through `MemoryWriteProvenancePublisher` instead of emitting uneven raw write events.
- `MemorySummarizationService`, `MemoryEmergenceService`, `UniversalMemoryTool`, `WorkspaceMemoryTool`, `StructuredMemoryService`, `MemoryHilService`, `MemoryArchiveService`, fact-maintenance writes, and `SoulSummarizerService` now emit standardized provenance-carrying `MemoryWriteEvent`s on bounded durable or derived-summary writes.
- `SOUL.md` is now governed as a derived-summary target with an explicit `DERIVED_SUMMARY_WRITER` surface and recursion guard on self-refresh events.
- Remaining `WP-3` follow-up is now limited to a few non-bounded legacy utility paths outside the finalized first-pass governed surface inventory.

**Key design decisions:**
- All new code lives in isolated `vip.mate.memory.contract.*` and `vip.mate.memory.governance.*` packages.
- `MemorySurfaceType` names every surface from the WP-0 inventory (orchestrator, builtin provider, lifecycle mediator, summarization writer, emergence writer, recall scoring, direct file tool, universal tool).
- `MemoryGovernanceFilter` makes implicit trust assumptions explicit and testable:
  - Only emergence and summarization writers may consolidate.
  - Recall scoring may not write canonical memory.
  - Lifecycle mediator and builtin provider are read-only surfaces.
  - All mutating operations must emit provenance.
- Target classification (daily notes / long-term canonical / metrics-only) is based on path heuristics for now.

**Deferred to later packages:**
- Full governed-surface expansion beyond the current bounded writers for any remaining legacy utility paths outside the current WP-3 cut.
- `MEMORY.md` contract formalization (deferred to template contract WP-6).

#### WP-3 Detailed Execution Plan

`WP-3` should stay narrowly focused on **memory taxonomy, write governance, provenance, and durable-vs-derived boundaries**.

Execution order inside `WP-3` should be:

1. freeze canonical information taxonomy
2. freeze memory authority and writer categories
3. freeze write eligibility rules
4. freeze exclusion rules and non-memory classes
5. freeze provenance and review metadata contract
6. freeze the role of `MEMORY.md` and adjacent files
7. define targeted tests and diagnostics
8. freeze implementation cut and acceptance grid

##### WP-3 Anti-Expansion Guardrails

Do not let `WP-3` expand into:

- new memory storage backends
- dream algorithm redesign
- full prompt/context pipeline redesign
- new end-user memory product UX
- broad knowledge-base / wiki redesign
- full context taxonomy work that belongs to `WP-4`

##### WP-3 Task Matrix

| Task ID | Task Name | Focus Area | Primary Files / Classes | Expected Output | Acceptance Signal |
| --- | --- | --- | --- | --- | --- |
| WP3-01 | Freeze information taxonomy | memory vs cache vs context vs grounding | [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java), [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java), [mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java](mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java) | one canonical information taxonomy table | memory/cache/context/grounding classes are distinct |
| WP3-02 | Freeze writer authority | durable-memory writer categories | [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java), [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java), [mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java](mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java), [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java) | one writer-authority matrix | every durable writer has a category and governance note |
| WP3-03 | Freeze write eligibility | what may become durable memory | memory summarization/emergence/tool write surfaces | one write-eligibility checklist | memory writes become rule-driven and classifiable |
| WP3-04 | Freeze exclusion rules | what must not become memory | recall, cache, routed context, ephemeral/session data | one exclusion matrix | non-memory classes are explicitly excluded |
| WP3-05 | Freeze provenance contract | provenance and review metadata | [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java), `MemoryWriteEvent`, dream/report surfaces | one provenance/review matrix | memory writes and candidates have explicit metadata requirements |
| WP3-06 | Freeze `MEMORY.md` role | canonical file contract | built-in provider + summarization/emergence/tools | one `MEMORY.md` role contract table | `MEMORY.md` stops being treated as an all-purpose bucket |
| WP3-07 | Define diagnostics and tests | regression / observability boundary | recall/emergence/summarization/tool write surfaces | one targeted test matrix + diagnostics note | durable-memory behavior is explainable and testable |
| WP3-08 | Freeze implementation cut | execution-ready bounded plan | outputs of `WP3-01` to `WP3-07` | one implementation cut matrix + acceptance grid | `WP-3` can move into code work without reopening design scope |

##### WP-3 Detailed Worksheets

###### Worksheet `WP3-01` — Freeze Canonical Information Taxonomy

#### Task goal

Freeze one bounded taxonomy that distinguishes durable memory from cache, working context, session-ephemeral state, and grounding.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java)
- [mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java](mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java)

#### Execution steps

1. identify every observed information class around memory codepaths
2. distinguish durable stored knowledge from derived signals
3. distinguish injected context from durable files
4. identify where grounding/knowledge sources remain separate from memory
5. keep the taxonomy finite and implementation-friendly

#### Required output table template

| Taxonomy ID | Information Class | Canonical Meaning | Current Surfaces | Durable or Derived | Can Be Injected At Runtime? | Must Not Be Confused With | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one canonical information taxonomy table
- one note on highest-risk confusion pairs
- one boundary note for `WP-4`

#### Acceptance checks

- memory/cache/context/grounding are all distinct
- recall metrics are not mislabeled as durable memory
- routed/injected context is explicitly separate from durable files

#### Explicit non-goals

- no context lifecycle redesign here
- no KB retrieval redesign
- no new memory classes beyond the minimum needed

#### WP3-01 first-pass canonical taxonomy

| Taxonomy ID | Information Class | Canonical Meaning | Current Surfaces | Durable or Derived | Can Be Injected At Runtime? | Must Not Be Confused With | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| I01 | durable memory | long-lived knowledge intended to help future interactions | `MEMORY.md`, `PROFILE.md`, daily memory notes, memory-oriented workspace files | durable | yes, via system prompt or later write/read paths | recall metrics, routed hints | core `WP-3` concern |
| I02 | project cache | recomputable project understanding or summary | project insight / derived summaries outside memory package | derived | yes | durable memory | should stay recomputable |
| I03 | working context | task-scoped injected evidence for the current call | `<memory-context>`, routed context block, fitted history | derived/ephemeral | yes | durable memory | main overlap with `WP-4` |
| I04 | session-ephemeral state | temporary turn/session state not intended as long-term knowledge | conversation transcript windows, pending transient context | derived/ephemeral | yes | durable memory | should not be promoted automatically |
| I05 | knowledge grounding | externally sourced factual basis with provenance | wiki/KB hits and grounding materials | derived from external source | yes | durable memory | separate authority and freshness model |
| I06 | recall evidence | retrieval frequency/diversity metadata about candidate files | `MemoryRecallService`, recall rows, score/review counters | derived | not directly as user-facing memory text | durable memory | evidence about salience, not the memory itself |
| I07 | bootstrap memory prompt content | enabled workspace files assembled into system prompt | `BuiltinMemoryProvider.systemPromptBlock()` | durable source delivered as runtime prompt | yes | per-turn prefetch context | durable origin, runtime delivery |

#### WP3-01 highest-risk confusion note

Highest-risk confusion pairs today are:

- durable memory vs recall evidence
- durable memory vs runtime injected memory-context block
- project cache vs long-term memory
- grounding materials vs promoted memory facts

###### Worksheet `WP3-02` — Freeze Writer Authority Categories

#### Task goal

Classify all current durable-memory writers so the system can distinguish authority writers, summarizers, and low-level access tools.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java)
- [mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java](mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java)
- [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java)

#### Execution steps

1. list all surfaces that can write durable memory files
2. classify whether each writer is authoritative, summarizing, consolidating, or low-level access
3. identify whether each writer emits provenance/event metadata
4. mark which writers should remain access tools rather than governance owners
5. keep categories minimal and enforceable

#### Required output table template

| Writer ID | Writer Surface | Writes Which Durable Files | Writer Category | Governance Status | Emits Event / Provenance? | Open Risk |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one writer-authority matrix
- one access-vs-authority note
- one uneven-provenance note

#### Acceptance checks

- every known writer is classified
- low-level tools are not mistaken for governance owners
- provenance gaps are explicit |

#### Explicit non-goals

- no writer removal yet
- no storage format migration
- no dream/summarize prompt redesign

#### WP3-02 first-pass writer-authority matrix

| Writer ID | Writer Surface | Writes Which Durable Files | Writer Category | Governance Status | Emits Event / Provenance? | Open Risk |
| --- | --- | --- | --- | --- | --- | --- |
| W01 | [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java) | daily note file, `MEMORY.md`, `PROFILE.md` | conversation summarizer | derived-writer, not sole authority | no explicit `MemoryWriteEvent` in shown path | writes multiple canonical files without unified provenance event |
| W02 | [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java) | `MEMORY.md` | consolidation / promotion writer | strongest durable-memory authority surface today | yes, publishes `MemoryWriteEvent` and dream events | promotion semantics still rely on heuristic adoption |
| W03 | [mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java](mateclaw-server/src/main/java/vip/mate/tool/builtin/WorkspaceMemoryTool.java) | any workspace memory markdown file | low-level access/write tool | access surface, not governance owner | no unified memory write event in shown code | easy to bypass governance if not classified explicitly |
| W04 | [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java) | `MEMORY.md` recent lessons section | lightweight lesson append writer | governed convenience writer, not sole authority | yes, publishes `MemoryWriteEvent` | free-form lessons may accumulate without stronger review rules |
| W05 | workspace bootstrap/seed paths | initial seed files such as `MEMORY.md` / `PROFILE.md` if present | bootstrap materializer | initialization only | not memory-lifecycle provenance | should not be treated as ongoing write authority |

#### WP3-02 access-vs-authority note

`WorkspaceMemoryTool` and `UniversalMemoryTool` are write surfaces, but they should not define the governance rules for what counts as durable memory. Governance should live above the tool layer.

###### Worksheet `WP3-03` — Freeze Write Eligibility Rules

#### Task goal

Define the minimum rule set for when information is eligible to become durable memory.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java)

#### Execution steps

1. infer current durable-write triggers from code paths
2. separate automatic promotion from direct user/agent append paths
3. define the minimum eligibility dimensions
4. keep the rule set compatible with current flows
5. note where current code is permissive and needs later tightening

#### Required output table template

| Rule ID | Candidate Type | Current Trigger | Minimum Eligibility Rule | Current Enforced? | Suggested Governance Direction | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one write-eligibility matrix
- one current permissiveness note
- one future tightening note

#### Acceptance checks

- automatic and manual write paths are both covered
- rules are specific enough to be testable
- no current flow is implicitly banned without note |

#### Explicit non-goals

- no immediate hard blocking of existing tool writes
- no dream algorithm overhaul
- no user-facing policy UI

#### WP3-03 first-pass write-eligibility matrix

| Rule ID | Candidate Type | Current Trigger | Minimum Eligibility Rule | Current Enforced? | Suggested Governance Direction | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| R01 | daily note append | successful summarization pass | conversation-derived factual/behavioral summary for daily record | partially | keep permissive but tag as review-stage material | daily notes are staging memory, not final truth |
| R02 | `MEMORY.md` consolidation update | dream/emergence `should_update=true` | repeated/high-salience patterns plus model justification | partially | treat as promotion path with provenance metadata | strongest current long-term promotion path |
| R03 | `PROFILE.md` update | summarization returns `profile_update` | stable identity/preference/profile facts only | weakly | explicitly restrict to user/profile class facts | profile should not become generic scratchpad |
| R04 | direct workspace memory overwrite/edit | tool call through `WorkspaceMemoryTool` | explicit actor intent plus target-file validity | weakly | require governance tagging / diagnostics rather than immediate ban | current path is highly permissive |
| R05 | free-form lesson append | `UniversalMemoryTool.remember()` | lesson-like durable note intended for later consolidation | partially | keep as append-only staging path, not final canonical fact path | recent lessons section is a staging buffer |
| R06 | recall-score-driven promotion candidate | `MemoryRecallService.computeScores()` + emergence | repeated recall, query diversity, freshness, threshold pass | yes for scoring, partial for adoption | keep as salience evidence, not direct durable write by itself | recall is gate input, not durable truth |

#### WP3-03 permissiveness note

Current direct tool write paths are permissive. `WP-3` should first classify and annotate them, then tighten through governance rules rather than abrupt removal.

###### Worksheet `WP3-04` — Freeze Exclusion Rules

#### Task goal

Make explicit what must not be treated as durable memory, even if it appears near memory-related flows.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java)
- [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java](mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java)

#### Execution steps

1. inventory nearby non-memory classes and flows
2. define why each is excluded
3. identify whether excluded items may still influence memory decisions
4. mark overlap areas with `WP-4`
5. keep exclusions concrete

#### Required output table template

| Exclusion ID | Surface / Datum | Why It Is Not Durable Memory | Can Influence Memory Decisions? | Overlap Package | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one exclusion matrix
- one overlap note with `WP-4`
- one “derived evidence is not memory” note

#### Acceptance checks

- recall metrics, injected context, and derived cache are excluded
- exclusions are concrete rather than abstract
- overlap with context lifecycle is acknowledged |

#### Explicit non-goals

- no removal of useful derived signals
- no context pipeline redesign
- no cache subsystem refactor

#### WP3-04 first-pass exclusion matrix

| Exclusion ID | Surface / Datum | Why It Is Not Durable Memory | Can Influence Memory Decisions? | Overlap Package | Notes |
| --- | --- | --- | --- | --- | --- |
| X01 | recall counts / diversity / score rows | salience evidence about retrieval, not the durable knowledge itself | yes | `WP-3` | should drive promotion, not be promoted verbatim |
| X02 | `<memory-context>` prefetch block | runtime injection wrapper, not stored truth | yes | `WP-4` | delivery mechanism, not durable class |
| X03 | routed context block | task-scoped injected evidence | yes | `WP-4` | may include memory-adjacent hints but is not durable memory |
| X04 | project insight / project cache summaries | recomputable derived summary | yes | `WP-4` / adjacent | separate from long-term memory authority |
| X05 | wiki / KB grounding | external factual grounding source | yes | `WP-4` | may inform memory, but should remain provenance-distinct |
| X06 | raw conversation transcript | session history, not automatically long-term knowledge | yes | `WP-4` | summarization can derive memory from it |

#### WP3-04 derived-evidence note

Derived evidence may influence durable-memory decisions, but it must not be conflated with durable memory itself. This is the key governance boundary for recall scores, routed context, and project cache.

###### Worksheet `WP3-05` — Freeze Provenance and Review Metadata Contract

#### Task goal

Define the minimum provenance and review metadata needed so durable memory writes and promotion candidates are inspectable.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java)
- memory event/report classes (`MemoryWriteEvent`, dream report surfaces)

#### Execution steps

1. inventory current recall/review/provenance metadata
2. identify what durable writers currently emit or omit
3. define the minimum required metadata contract
4. distinguish candidate metadata from final durable-write metadata
5. keep it small enough for first implementation pass

#### Required output table template

| Metadata ID | Metadata Field / Concept | Current Surface | Candidate Stage or Final Write Stage | Currently Present? | Why Needed | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one provenance/review matrix
- one uneven-provenance note
- one minimum contract note

#### Acceptance checks

- both candidate and final-write stages are covered
- missing metadata is explicit
- review/promotion fields are not confused with durable content |

#### Explicit non-goals

- no full audit platform rollout
- no history migration of old memory rows
- no event-bus redesign

#### WP3-05 first-pass provenance/review matrix

| Metadata ID | Metadata Field / Concept | Current Surface | Candidate Stage or Final Write Stage | Currently Present? | Why Needed | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| PV01 | recall count / daily count | `MemoryRecallEntity` via `MemoryRecallService` | candidate stage | yes | measures repeated salience | not durable content |
| PV02 | query diversity / query hashes | recall rows | candidate stage | yes | helps avoid one-query overfitting | evidence only |
| PV03 | freshness / last recalled timestamp | recall rows | candidate stage | yes | supports promotion scoring | evidence only |
| PV04 | promoted flag / review count | recall rows | candidate stage and review tracking | yes | supports promotion/review lifecycle | review metadata, not memory content |
| PV05 | write event (`MemoryWriteEvent`) + attached `MemoryWriteProvenance` | emergence + summarization + remember + governed direct edits + archive/fact maintenance + derived-summary refresh | final write stage | yes for governed writers | makes write origin observable and standardized | `SOUL.md` now classified as derived-summary rather than left outside the governed writer set |
| PV06 | dream report promoted/rejected entries | `DreamReport` / persisted report | candidate + final promotion outcome | yes | records why candidates were adopted or rejected | strongest current governance record |
| PV07 | source / reason text | dream reason, remember source param | final write stage | partial | explains why a write happened | not standardized across all writers |

#### WP3-05 uneven-provenance note

The main first-pass inconsistency has now been closed for governed writers via `MemoryWriteProvenancePublisher`. Remaining unevenness is limited to a small number of explicitly deferred utility paths outside the bounded WP-3 surface cut.

###### Worksheet `WP3-06` — Freeze `MEMORY.md` Role Contract

#### Task goal

Clarify the canonical role of `MEMORY.md` so it is no longer treated as a generic bucket for every memory-adjacent behavior.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java](mateclaw-server/src/main/java/vip/mate/memory/provider/BuiltinMemoryProvider.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java)

#### Execution steps

1. inventory all current `MEMORY.md` roles
2. separate canonical role from staging and access behavior
3. define relation to daily notes and profile
4. define what should not be stored there by default
5. keep wording compatible with current flows

#### Required output table template

| Role ID | Current `MEMORY.md` Role | Owning Surface(s) | Canonical Keep / Limit Decision | Adjacent File / Surface | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one `MEMORY.md` role contract table
- one note on relation to daily notes / `PROFILE.md`
- one anti-bucket note

#### Acceptance checks

- `MEMORY.md` role is explicit
- daily notes and `PROFILE.md` are clearly separated
- append-only lesson staging is distinguished from canonical long-term knowledge |

#### Explicit non-goals

- no file format migration now
- no mass rewrite of existing memory files
- no new memory UI |

#### WP3-06 first-pass `MEMORY.md` role contract

| Role ID | Current `MEMORY.md` Role | Owning Surface(s) | Canonical Keep / Limit Decision | Adjacent File / Surface | Notes |
| --- | --- | --- | --- | --- | --- |
| MR01 | durable long-term memory file injected into prompt | `BuiltinMemoryProvider` + workspace file prompt assembly | keep as canonical durable memory surface | system prompt assembly | primary long-term file |
| MR02 | consolidation target for dream/emergence | `MemoryEmergenceService` | keep as promoted/consolidated knowledge target | daily notes, recall candidates | strongest current governance path |
| MR03 | direct overwrite/edit target | `WorkspaceMemoryTool`, `MemorySummarizationService` | limit by governance classification, not by immediate removal | low-level tools and summarizer | high-risk bucket behavior today |
| MR04 | recent-lessons staging buffer | `UniversalMemoryTool` under `## Recent Lessons` | keep as staging subsection, not the entire semantic definition of the file | dream consolidation | recent lessons should remain visibly provisional |

#### WP3-06 relation note

- daily notes should act as temporal staging and evidence capture
- `PROFILE.md` should hold stable identity/preference/profile data
- `MEMORY.md` should hold durable, reusable long-term knowledge, including staged lessons that are awaiting consolidation but clearly marked as such

###### Worksheet `WP3-07` — Define Diagnostics and Targeted Tests

#### Task goal

Define the minimum test and diagnostics matrix needed to make memory governance refactors safe.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryRecallService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemoryEmergenceService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java](mateclaw-server/src/main/java/vip/mate/memory/service/MemorySummarizationService.java)
- [mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java](mateclaw-server/src/main/java/vip/mate/memory/tool/UniversalMemoryTool.java)

#### Execution steps

1. define taxonomy-lock tests
2. define writer/provenance regression cases
3. define promotion/review regression cases
4. define diagnostics fields for governance visibility
5. keep the matrix finite and implementation-ready

#### Required output table template

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one targeted test matrix
- one diagnostics payload note
- one ambiguity-lock note

#### Acceptance checks

- writer/provenance gaps are covered
- promotion/review path is covered
- memory-vs-non-memory boundaries are locked |

#### Explicit non-goals

- no giant end-to-end scenario suite
- no memory product analytics rollout
- no context lifecycle test expansion beyond overlap notes

#### WP3-07 first-pass targeted test matrix

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |
| MD01 | recall metadata is evidence only | recall row data | recall metrics are classified as candidate metadata, not durable memory content | unit / taxonomy lock | boundary lock |
| MD02 | remember appends under lessons section | existing/new `MEMORY.md` content + lesson text | append goes under `## Recent Lessons` and emits write event | unit | staging-path lock |
| MD03 | emergence promotion writes canonical target | daily notes + scored candidates + positive response | `MEMORY.md` is updated and a provenance-carrying `MemoryWriteEvent` is emitted | integration-light | authority-path lock |
| MD04 | summarization updates daily/profile/memory separately | LLM response with daily/profile/memory updates | each target file follows documented role split | unit / integration-light | file-role lock |
| MD05 | writer provenance consistency check | governed writer surfaces | governed writers emit standardized provenance-carrying events with writer category + target classification | contract lock | includes archive/fact maintenance and derived-summary refresh |
| MD06 | recall review progression | candidate IDs and review cycle | review counters/promoted flags behave as documented | unit | review lifecycle lock |

#### WP3-07 diagnostics note

Minimum diagnostics for `WP-3` should include:

- durable write target file
- writer category
- write trigger / reason
- whether a standardized write event was emitted
- target classification and content hash for governed writes
- candidate review/promotion status where applicable

###### Worksheet `WP3-08` — Freeze Implementation Cut and Acceptance Grid

#### Task goal

Turn `WP-3` outputs into a bounded code-change plan that can be implemented without dragging `WP-4` or new storage products into scope.

#### Inputs required

- outputs from `WP3-01` to `WP3-07`

#### Execution steps

1. define the minimum code cut for first implementation pass
2. assign file/class touchpoints
3. identify regression tests
4. list explicit deferments
5. freeze final acceptance grid

#### Required output table template

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one implementation cut matrix
- one deferred-items note
- one final acceptance grid for `WP-3`

#### Acceptance checks

- each cut has bounded files and bounded output
- deferred items are explicit
- `WP-3` can start coding without reopening design discovery

#### Explicit non-goals

- no bundling of `WP-4` into the same implementation pass
- no new memory backend
- no hidden prompt/context refactor

#### WP3-08 first-pass implementation cut matrix

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |
| C1 | add taxonomy / writer-category models or docs | memory/shared package or docs-only layer | `MemoryInformationClass`, writer category docs, governance matrix | compile if code-backed + doc review | model placement drift |
| C2 | add write-classification / eligibility helper | memory service/helper layer | write eligibility rules and target-file classification | unit tests on classification decisions | accidental tightening of current flows |
| C3 | add provenance metadata normalization | memory events / governance helpers / diagnostics DTOs | unified writer category + target classification + emitted-event metadata | unit / snapshot checks | over-expansion into audit platform |
| C4 | add targeted writer/provenance regression tests | summarization / emergence / remember / recall tests | governance lock coverage | unit + integration-light tests | residual legacy paths remain explicit deferments |
| C5 | document `MEMORY.md` / daily note / `PROFILE.md` roles near implementation | docs + optional code comments at write sites | explicit file-role contract | doc review + contract tests | role drift if not colocated |

#### WP3-08 deferred-items note

Explicitly defer from `WP-3`:

- new memory storage products or plugins
- full context-budget / compaction redesign
- final event standardization across every memory writer
- deep dream prompt/heuristic redesign
- end-user memory management UX

#### WP3-08 final acceptance grid

`WP-3` is review-ready when:

- one canonical information taxonomy is frozen
- every durable writer has a category and governance note
- write eligibility and exclusion rules are explicit
- provenance/review metadata contract is explicit
- `MEMORY.md`, daily notes, and `PROFILE.md` roles are clearly separated
- targeted tests lock core writer, promotion, and boundary behavior

---

### WP-4 — Context Lifecycle Consolidation

#### Objective

Turn existing context routing into a complete, bounded context lifecycle model.

#### Functional Points

1. Define context source taxonomy.
2. Define context provenance fields.
3. Define budget classes and ranking semantics.
4. Define compaction levels:
   - trim
   - compact
   - collapse
   - summary fallback
5. Define failure and pressure fallback rules.
6. Bring attachment-derived evidence under the same context lifecycle contract.

#### Modification Scope

Primary server scope:

- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- agent graph context assembly surfaces referenced by [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)

Related scope:

- wiki / KB context providers
- attachment extraction summary surfaces
- tool-result evidence ingestion surfaces

Allowed change type:

- add source/provenance metadata
- centralize context ranking and compaction contracts
- add explicit fallback states/metrics

Not allowed in this package:

- redesigning chat UI context display first
- replacing current retrieval stack wholesale
- introducing a second context assembly pipeline beside the current one

#### Suggested Deliverables

1. `ContextSourceType` / provenance metadata contract
2. compaction policy definition
3. pressure/fallback contract
4. attachment convergence rules document

#### Acceptance Checkpoint

WP-4 is done when:

- all major context contributors can be named under one taxonomy
- attachment evidence is no longer treated as a separate conceptual track
- compaction/fallback behavior is explicit and bounded

#### WP-4 Landing Record

**Status:** First-pass landed  
**Landed at:** 2026-05-20  

**Deliverables produced:**

| Artifact | Path | Status |
| --- | --- | --- |
| `ContextSourceType` enum (canonical context contributor taxonomy) | `mateclaw-server/src/main/java/vip/mate/context/contract/ContextSourceType.java` | landed |
| `ContextProvenance` record (source metadata per block) | `mateclaw-server/src/main/java/vip/mate/context/contract/ContextProvenance.java` | landed |
| `ContextBudgetClass` enum (budget management categories) | `mateclaw-server/src/main/java/vip/mate/context/contract/ContextBudgetClass.java` | landed |
| `ContextCompactionLevel` enum (compression state machine) | `mateclaw-server/src/main/java/vip/mate/context/contract/ContextCompactionLevel.java` | landed |
| `ContextLifecycleContract` interface (assembly + compaction contract) | `mateclaw-server/src/main/java/vip/mate/context/contract/ContextLifecycleContract.java` | landed |
| `ContextSourceProvider` interface (provider contract with priority) | `mateclaw-server/src/main/java/vip/mate/context/provider/ContextSourceProvider.java` | landed |
| `ProjectCacheContextProvider` | `mateclaw-server/src/main/java/vip/mate/context/provider/ProjectCacheContextProvider.java` | landed |
| `MemoryContextProvider` | `mateclaw-server/src/main/java/vip/mate/context/provider/MemoryContextProvider.java` | landed |
| `WikiContextProvider` | `mateclaw-server/src/main/java/vip/mate/context/provider/WikiContextProvider.java` | landed |
| `SessionContextProvider` | `mateclaw-server/src/main/java/vip/mate/context/provider/SessionContextProvider.java` | landed |
| `ContextAssemblyService` (aggregator + coarse budget enforcement) | `mateclaw-server/src/main/java/vip/mate/context/ContextAssemblyService.java` | landed |
| `ContextAssemblyServiceTest` (provider-priority regression lock) | `mateclaw-server/src/test/java/vip/mate/context/ContextAssemblyServiceTest.java` | landed |

**Remediation sync (2026-05-20):**
- `WP-4` provider ordering has been corrected to follow `ContextSourceProvider.getPriority()` directly.
- The new `ContextAssemblyServiceTest` locks the priority-order contract so later provider additions cannot silently regress to enum-order behavior.
- Remaining `WP-4` follow-up stays unchanged: graph wiring, attachment/tool-result providers, and token-level compaction are still deferred.

**Key design decisions:**
- All new code lives in isolated `vip.mate.context.contract.*`, `vip.mate.context.provider.*`, and `vip.mate.context` packages.
- `ContextSourceType` names every major contributor that `ContextRouterService` already handles implicitly.
- `ContextProvenance` makes freshness, budget, and source identity answerable for each block.
- `ContextCompactionLevel` turns the implicit behavior in `ConversationWindowManager` into an explicit state machine.
- `ContextSourceProvider` allows new context sources to be added without modifying the router directly.
- `ContextAssemblyService` is a parallel typed assembly path; it does not replace `ContextRouterService` yet.
- Provider ordering is now enforced from `ContextSourceProvider.getPriority()` rather than enum ordinal fallback, which keeps the assembly contract aligned with the provider SPI.

**Deferred to later packages:**
- Token-level window fitting and progressive compaction (still owned by `ConversationWindowManager`; migration path defined).
- Memory prefetch block integration (still owned by `MemoryLifecycleMediator`; contract alignment reserved for WP-3).
- Attachment evidence convergence (contract exists; no attachment provider yet).
- Tool-result evidence ingestion (contract exists; no tool-result provider yet).
- Graph runtime wiring (deferred to WP-5 lifecycle alignment).

#### WP-4 Detailed Execution Plan

`WP-4` should stay narrowly focused on **context source taxonomy, provenance, budgeting, compaction, and fallback lifecycle**.

Execution order inside `WP-4` should be:

1. freeze context source taxonomy
2. freeze provenance and source metadata contract
3. freeze budget classes and ranking semantics
4. freeze compaction levels and state transitions
5. freeze pressure and failure fallback rules
6. freeze attachment and external evidence convergence rules
7. define diagnostics and targeted tests
8. freeze implementation cut and acceptance grid

##### WP-4 Anti-Expansion Guardrails

Do not let `WP-4` expand into:

- retrieval stack replacement
- second parallel context pipeline
- full UI context visualization redesign
- broad prompt architecture rewrite
- memory governance redesign that belongs to `WP-3`
- hook/event vocabulary redesign that belongs to `WP-5`

##### WP-4 Task Matrix

| Task ID | Task Name | Focus Area | Primary Files / Classes | Expected Output | Acceptance Signal |
| --- | --- | --- | --- | --- | --- |
| WP4-01 | Freeze source taxonomy | context source classes | [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java), [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java), [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java) | one context taxonomy table | every major context contributor has one source class |
| WP4-02 | Freeze provenance contract | source metadata | same as `WP4-01` plus template and KB route surfaces | one provenance matrix | context blocks become explainable by source and freshness |
| WP4-03 | Freeze budget and ranking model | budget semantics | `ContextRouterService`, `ConversationWindowManager` | one budget/ranking matrix | char-budget and token-budget rules are explicitly related |
| WP4-04 | Freeze compaction lifecycle | compaction states | [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java) | one compaction state matrix | trim/compact/collapse/fallback phases are explicit |
| WP4-05 | Freeze pressure/fallback rules | overflow and failure handling | `ConversationWindowManager`, `MemoryLifecycleMediator` | one fallback matrix | pressure behavior is bounded and testable |
| WP4-06 | Freeze attachment convergence | attachment / external evidence path | attachment summary surfaces + routed context contract | one convergence table | attachments stop being a separate conceptual track |
| WP4-07 | Define diagnostics and tests | regression / observability boundary | router/window/mediator surfaces | one targeted test matrix + diagnostics note | context ranking and fallback behavior are explainable |
| WP4-08 | Freeze implementation cut | execution-ready bounded plan | outputs of `WP4-01` to `WP4-07` | one implementation cut matrix + acceptance grid | `WP-4` can move into code work without reopening design scope |

##### WP-4 Detailed Worksheets

###### Worksheet `WP4-01` — Freeze Context Source Taxonomy

#### Task goal

Freeze one bounded taxonomy for all major context contributors so routing, compaction, and fallback can operate on named source classes.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java)

#### Execution steps

1. identify every currently observed context contributor
2. distinguish routed hints from history window content
3. distinguish memory-prefetch context from durable memory itself
4. separate external evidence / grounding from local workspace context
5. keep the taxonomy finite and implementation-friendly

#### Required output table template

| Source ID | Context Source Class | Current Producer | Current Injector | Scope | Ephemeral or Reusable | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one context source taxonomy table
- one overlap note with `WP-3`
- one list of still-implicit sources for later follow-up

#### Acceptance checks

- major routed, memory, history, and external evidence sources are covered
- durable memory is not conflated with injected context
- final injection surfaces are explicit

#### Explicit non-goals

- no source ranking implementation yet
- no retrieval replacement
- no UI display design

#### WP4-01 first-pass context source taxonomy

| Source ID | Context Source Class | Current Producer | Current Injector | Scope | Ephemeral or Reusable | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| CTX01 | project-derived routed hints | `WorkspaceService.getProjectInsight()` via [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java) | `ContextRouterService.buildInjectionBlock()` | current turn | reusable summary, ephemeral injection | based on project cache/insight |
| CTX02 | workspace-memory file hints | workspace files via `WorkspaceFileService` summarized by router | router injection block | current turn | reusable source, ephemeral injection | memory-adjacent but not full durable file content |
| CTX03 | wiki / KB route hints | `WikiKnowledgeBaseService` | router injection block | current turn | reusable grounding source, ephemeral injection | grounding-oriented |
| CTX04 | recent session recall hints | `SessionSearchService` | router injection block | current turn | derived from recent sessions | session-ephemeral evidence class |
| CTX05 | template route declarations | `templateMetadataJson` route config | router injection block | current turn | reusable contract metadata | source-priority input rather than content itself |
| CTX06 | conversation history window | conversation messages managed by [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java) | graph runtime after `fitToWindow()` | current turn | ephemeral | main compaction target |
| CTX07 | memory prefetch block | `MemoryManager.prefetchAll()` via [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java) | mediator merged output | current turn | reusable source, ephemeral injection | fenced as `<memory-context>` |
| CTX08 | attachment-derived evidence | attachment extraction / summary surfaces not yet fully unified | currently partial / adjacent | current turn | ephemeral or staged | still conceptually separate and needs convergence |
| CTX09 | tool-result evidence | tool outputs retained in history and summaries | history window / summary fallback | current turn | ephemeral | currently governed mostly by history compaction |

#### WP4-01 overlap note

`WP-4` owns the runtime treatment of memory-derived context, but not the governance of durable memory truth. Durable-vs-derived rules remain owned by `WP-3`.

###### Worksheet `WP4-02` — Freeze Provenance Contract

#### Task goal

Define the minimum provenance fields needed so injected context blocks can be explained, ranked, and suppressed safely.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java)

#### Execution steps

1. inventory what source metadata is currently visible
2. identify missing provenance dimensions
3. separate source identity from ranking score
4. define minimum metadata needed across routed and compacted context
5. keep it small enough for first implementation pass

#### Required output table template

| Provenance ID | Metadata Field | Applies To | Currently Present? | Why Needed | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one provenance matrix
- one note on currently missing metadata
- one compaction-provenance note

#### Acceptance checks

- source type, scope, and freshness needs are explicit
- history summaries have provenance requirements too
- routed and memory-prefetch blocks can be described with the same contract

#### Explicit non-goals

- no full tracing platform rollout
- no user-facing provenance UI yet
- no change to current prompt text semantics beyond metadata planning

#### WP4-02 first-pass provenance matrix

| Provenance ID | Metadata Field | Applies To | Currently Present? | Why Needed | Notes |
| --- | --- | --- | --- | --- | --- |
| PV-C01 | `sourceType` | all context blocks | partial | explains what kind of evidence the block represents | router uses partial labels today |
| PV-C02 | `sourceScope` | all context blocks | weak | distinguishes workspace / session / KB / attachment / memory | missing as explicit contract |
| PV-C03 | `freshness` | session recall, cache, grounding, attachments | weak | helps suppress stale evidence | not normalized today |
| PV-C04 | `derivationClass` | cache, summaries, compacted history | no | distinguishes raw vs derived vs summarized | key for compaction trust |
| PV-C05 | `policyEligibility` | sensitive/local/attachment/tool evidence | no | future gating and safe suppression | proposed field only |
| PV-C06 | `compactionLevel` | summarized/trimmed/collapsed history or context | no | explains how much transformation occurred | critical for fallback clarity |
| PV-C07 | `sourceKey` / identifier | KB/page/session/file references | partial | allows diagnostics and refresh decisions | not standardized across sources |

#### WP4-02 missing-metadata note

The biggest current gap is that context blocks are mostly explainable by code location, not by structured provenance. This becomes risky once attachments and tool-result evidence are folded into the same lifecycle.

###### Worksheet `WP4-03` — Freeze Budget and Ranking Model

#### Task goal

Make current budget and ranking semantics explicit so future convergence from char-based and token-based logic stays bounded.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)

#### Execution steps

1. capture current router budget logic
2. capture current history-window token logic
3. separate source ranking from hard budget enforcement
4. identify where budgets currently diverge
5. define minimum future-compatible budget classes

#### Required output table template

| Budget ID | Budget Surface | Current Unit | Current Owner | Current Rule | Future Budget Class | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one budget/ranking matrix
- one char-vs-token convergence note
- one ranking-priority note

#### Acceptance checks

- both router and history-window budget systems are covered
- ranking and suppression logic are explicit
- convergence direction is documented without rewriting behavior

#### Explicit non-goals

- no immediate token-budget unification
- no estimator replacement
- no ranking ML/retrieval redesign

#### WP4-03 first-pass budget/ranking matrix

| Budget ID | Budget Surface | Current Unit | Current Owner | Current Rule | Future Budget Class | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| B01 | router injection budget | characters | `ContextRouterService` | `INJECTION_CHAR_BUDGET = 2400` with section limits | routed-hints budget | lightweight hint layer |
| B02 | router section item limits | item count | `ContextRouterService` | `INJECTION_SECTION_ITEM_LIMIT = 3` plus per-list caps | per-source quota | ranking influences which hints survive |
| B03 | history trigger threshold | tokens (estimated) | `ConversationWindowManager` | compact when total tokens exceed trigger ratio | history pressure budget | core pressure trigger |
| B04 | tail protection budget | tokens | `ConversationWindowManager` | reserve tail token budget and min protected messages | recency reserve budget | protects recent conversational continuity |
| B05 | summary budget | tokens-derived chars/text size | `ConversationWindowManager` | ratio with floor/ceiling | compaction output budget | summary-size control |
| B06 | memory prefetch budget | implicit / none explicit | `MemoryManager` + mediator | merged when non-empty | memory-context budget | currently not explicitly bounded at same layer |

#### WP4-03 convergence note

The current system already has two budget layers: routed hint budget and history-window budget. `WP-4` should normalize the contract between them before trying to force them into one immediate implementation.

###### Worksheet `WP4-04` — Freeze Compaction Lifecycle

#### Task goal

Make the compaction states inside `ConversationWindowManager` explicit and align them with the planned context lifecycle vocabulary.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)

#### Execution steps

1. identify all current compaction phases
2. map them to stable lifecycle labels
3. distinguish pre-summary reductions from final summary fallback
4. define state-transition wording
5. note where compaction results should carry provenance markers

#### Required output table template

| State ID | Current Phase / Operation | Trigger | Output Shape | Canonical Compaction Level | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one compaction state matrix
- one state-transition note
- one summary-fallback note

#### Acceptance checks

- soft trim, hard clear, pre-prune, summary fallback, and retry trim are covered
- compaction levels map to stable vocabulary
- summary fallback is explicit as a later-stage safety mechanism

#### Explicit non-goals

- no new summarizer prompts
- no model change
- no alternate compaction engine

#### WP4-04 first-pass compaction state matrix

| State ID | Current Phase / Operation | Trigger | Output Shape | Canonical Compaction Level | Notes |
| --- | --- | --- | --- | --- | --- |
| CMP01 | soft trim old tool results | initial history overflow | shortened tool outputs head+tail | trim | first pressure relief stage |
| CMP02 | hard clear old tool results | overflow persists after soft trim | placeholder-replaced tool outputs | compact | stronger lossier reduction |
| CMP03 | pre-prune for summary input | before summary generation | cleaned old messages for summarizer input | collapse preparation | internal preprocessing step |
| CMP04 | structured summary generation | overflow persists after direct reduction | summary message injected into history | collapse | main semantic compression stage |
| CMP05 | fallback keep-last-old-messages | summary unavailable | retain last few old messages | fallback | safety degradation path |
| CMP06 | trim-to-fit after compression | compressed result still exceeds budget | additional tail trimming | fallback trim | final hard bound enforcement |

#### WP4-04 state-transition note

Current lifecycle is already sequential and bounded: overflow detection → direct reduction → summary generation → fallback retention → final trim-to-fit. `WP-4` should formalize this without changing the underlying phases first.

###### Worksheet `WP4-05` — Freeze Pressure and Fallback Rules

#### Task goal

Define the minimum pressure and failure rules for routing, compaction, and summary failure so context behavior remains predictable.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)

#### Execution steps

1. identify overflow and failure triggers
2. map current fallback responses
3. separate source suppression from history compaction
4. identify where failures are swallowed vs logged
5. define bounded fallback wording

#### Required output table template

| Fallback ID | Pressure / Failure Condition | Current Owner | Current Response | Bounded Contract | Open Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one pressure/fallback matrix
- one silent-failure note
- one suppression-order note

#### Acceptance checks

- router budget pressure and history overflow are both covered
- summary failure and merge failure are explicit
- fallback paths are bounded, not open-ended

#### Explicit non-goals

- no global circuit-breaker service yet
- no tracing overhaul
- no conversation orchestration rewrite

#### WP4-05 first-pass pressure/fallback matrix

| Fallback ID | Pressure / Failure Condition | Current Owner | Current Response | Bounded Contract | Open Risk |
| --- | --- | --- | --- | --- | --- |
| FB01 | router block exceeds char budget | `ContextRouterService` | drop lower-priority lines/items under budget | routed hints degrade by priority and quota | source suppression order is implicit in code |
| FB02 | history tokens exceed threshold | `ConversationWindowManager` | enter staged compaction flow | bounded multi-stage compaction | none if phases remain explicit |
| FB03 | summary generation failure | `ConversationWindowManager` | fallback to keeping a small tail of old messages | summary failure must not block turn | fallback may preserve less semantic continuity |
| FB04 | compression still exceeds budget | `ConversationWindowManager` | final `trimToFit()` | hard safety bound wins over completeness | provenance of final loss not explicit |
| FB05 | memory/context merge failure | `MemoryLifecycleMediator` | log debug and return empty context | non-fatal failure must degrade gracefully | hidden context loss unless diagnostics added |
| FB06 | router build failure | `ContextRouterService` | log debug and return empty block | routed context may disappear without breaking turn | weak visibility today |

#### WP4-05 silent-failure note

Both router and mediator deliberately fail open by returning empty context blocks. This is useful operationally, but it makes diagnostics more important because context loss can otherwise be invisible.

###### Worksheet `WP4-06` — Freeze Attachment Convergence Rules

#### Task goal

Bring attachment-derived evidence under the same conceptual context lifecycle without forcing a full attachment implementation rewrite.

#### Files / classes to inspect

- current context contract in [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- attachment extraction / summary surfaces referenced by the product design backlog
- session-temporary material handling through template/runtime config

#### Execution steps

1. treat attachments as a source class rather than a separate feature track
2. define how they should fit source taxonomy, provenance, and budgets
3. distinguish raw attachment payloads from derived summaries
4. define bounded convergence wording for first pass
5. defer full implementation details if code surfaces are still scattered

#### Required output table template

| Convergence ID | Attachment / External Evidence Surface | Current Treatment | Target Context Class | Required Provenance | Budget / Compaction Expectation | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one attachment convergence table
- one raw-vs-derived attachment note
- one deferred implementation note

#### Acceptance checks

- attachments are classified inside the context taxonomy
- provenance and compaction expectations are explicit
- full implementation is not forced prematurely

#### Explicit non-goals

- no attachment parsing rewrite
- no OCR/indexing platform redesign
- no file-upload UX changes

#### WP4-06 first-pass attachment convergence table

| Convergence ID | Attachment / External Evidence Surface | Current Treatment | Target Context Class | Required Provenance | Budget / Compaction Expectation | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ATT01 | user-uploaded documents | partially session-temporary / adjacent | attachment-derived working context | source type, file identity, freshness, derivation class | same ranked budget as other evidence blocks | should not stay a separate conceptual feature |
| ATT02 | image or extracted OCR summary | adjacent evidence | derived attachment summary context | source type, extraction method, confidence | compact/collapse eligible | raw binary is not prompt context; summary is |
| ATT03 | large file summaries / indexing outputs | scattered / derived | project-or-attachment derived context | source scope, derivation class, freshness | quota-limited and compaction-eligible | may overlap with project cache |
| ATT04 | session-temporary materials | template/runtime session material path | session evidence context | session scope, persistence flag | highest-priority quota when explicitly attached | should converge with attachment taxonomy |

#### WP4-06 raw-vs-derived note

Attachments should enter the lifecycle as derived summaries or structured evidence blocks, not as raw payloads. Raw file bytes are not themselves context blocks.

###### Worksheet `WP4-07` — Define Diagnostics and Targeted Tests

#### Task goal

Define the minimum diagnostics and regression matrix needed to make context lifecycle refactors safe.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java)

#### Execution steps

1. define taxonomy-lock tests
2. define budget/ordering regression cases
3. define compaction/fallback regression cases
4. define diagnostics fields for source chain visibility
5. keep the matrix finite and implementation-ready

#### Required output table template

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one targeted test matrix
- one diagnostics payload note
- one ambiguity-lock note

#### Acceptance checks

- ranking, compaction, and fallback are all covered
- router/window/mediator boundaries are testable
- attachment convergence is covered at contract level

#### Explicit non-goals

- no giant scenario suite
- no full observability product rollout
- no UI instrumentation first

#### WP4-07 first-pass targeted test matrix

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |
| CX01 | router source taxonomy lock | router summary inputs | active sources map to canonical source classes | unit / contract lock | taxonomy stability |
| CX02 | router budget suppression | oversized routed hints | lower-priority lines are suppressed under budget | unit | budget contract |
| CX03 | history compaction progression | oversized history messages | soft trim → hard clear → summary path remains ordered | unit / integration-light | lifecycle lock |
| CX04 | summary failure fallback | summarizer failure | fallback keeps bounded recent old messages | unit | failure contract |
| CX05 | mediator merge behavior | routed block + memory prefetch block | merged order and empty-handling stay deterministic | unit | boundary lock |
| CX06 | attachment convergence contract | attachment-derived summary metadata | attachment evidence is classified as context source, not separate feature | contract lock | first-pass contract test |

#### WP4-07 diagnostics note

Minimum diagnostics for `WP-4` should include:

- source list and ranking order
- per-source budget class or suppression note
- compaction level applied to history/context blocks
- fallback reason when a block was dropped, summarized, or degraded

###### Worksheet `WP4-08` — Freeze Implementation Cut and Acceptance Grid

#### Task goal

Turn `WP-4` outputs into a bounded code-change plan that can be implemented without dragging `WP-3` or `WP-5` fully into the same refactor.

#### Inputs required

- outputs from `WP4-01` to `WP4-07`

#### Execution steps

1. define the minimum code cut for first implementation pass
2. assign file/class touchpoints
3. identify regression tests
4. list explicit deferments
5. freeze final acceptance grid

#### Required output table template

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one implementation cut matrix
- one deferred-items note
- one final acceptance grid for `WP-4`

#### Acceptance checks

- each cut has bounded files and bounded output
- deferred items are explicit
- `WP-4` can start coding without reopening design discovery

#### Explicit non-goals

- no bundling of `WP-5` into the same implementation pass
- no retrieval replacement
- no hidden prompt architecture rewrite

#### WP4-08 first-pass implementation cut matrix

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |
| C1 | add context source / provenance models or docs | context/shared package or docs-only layer | `ContextSourceType`, provenance docs, source taxonomy table | compile if code-backed + doc review | model placement drift |
| C2 | add source metadata and ranking contract near router | [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java) | explicit source labels, budget class notes, ranking contract | unit tests on routing/suppression | accidental prompt bloat |
| C3 | add compaction-level metadata / helper | [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java) | normalized compaction state markers | unit / snapshot checks | over-expansion into new engine |
| C4 | add mediator diagnostics for merged context | [mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java](mateclaw-server/src/main/java/vip/mate/memory/lifecycle/MemoryLifecycleMediator.java) | source-chain visibility on merged context | unit tests | drift into `WP-3` memory governance |
| C5 | document attachment convergence contract | docs + adjacent context comments/contracts | attachment/evidence classification notes | doc review + contract tests | implementation remains scattered initially |

#### WP4-08 deferred-items note

Explicitly defer from `WP-4`:

- new retrieval engines or indexes
- full attachment processing rewrite
- UI context panels redesign
- unified global token budget implementation across every source
- lifecycle event normalization into hook/harness vocabulary

#### WP4-08 final acceptance grid

`WP-4` is review-ready when:

- one context taxonomy is frozen
- provenance fields are explicit
- budget and compaction contracts are explicit
- fallback rules are bounded
- attachment evidence is classified under the same lifecycle
- targeted tests lock routing, compaction, and degradation behavior

---

### WP-5 — Lifecycle Event and Hook Contract Consolidation

#### Objective

Unify event vocabulary across hooks, listeners, approvals, memory, and context transitions.

#### Functional Points

1. Define lifecycle event categories:
   - session
   - prompt/plan
   - tool
   - approval
   - context
   - memory
   - completion/validation/export
2. Define a minimal shared event envelope.
3. Distinguish internal listeners from external hooks while aligning event names.
4. Define hook source trust classes.
5. Identify which existing events already exist and which are missing aliases only.
6. Avoid breaking the current hook dispatcher contract during first pass.

#### Modification Scope

Primary server scope:

- [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java)
- [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java)
- lifecycle listener packages related to graph execution, memory lifecycle, approval transitions, and run completion

Related scope:

- harness run audit/event models
- approval event publication points
- graph execution event publishers

Allowed change type:

- add canonical event naming layer
- add metadata fields to event envelope
- add adapter mapping from legacy event names to normalized event categories

Not allowed in this package:

- replacing Spring event infrastructure
- redesigning hook execution isolation model end-to-end
- introducing external plugin execution before contract stabilization

#### Suggested Deliverables

1. lifecycle event map
2. event envelope contract
3. legacy-to-canonical event mapping table
4. hook trust classification notes

#### Acceptance Checkpoint

WP-5 is done when:

- lifecycle events are classified under one vocabulary
- hook/listener boundaries are documented
- current hooks continue to run without behavior regression

#### WP-5 Landing Record

**Status:** First-pass landed  
**Landed at:** 2026-05-20  

**Deliverables produced:**

| Artifact | Path | Status |
| --- | --- | --- |
| `LifecycleEventCategory` enum (10 canonical categories) | `mateclaw-server/src/main/java/vip/mate/lifecycle/contract/LifecycleEventCategory.java` | landed |
| `LifecycleEventEnvelope` record (normalized event structure) | `mateclaw-server/src/main/java/vip/mate/lifecycle/contract/LifecycleEventEnvelope.java` | landed |
| `EventNormalizationContract` interface (mapping layer contract) | `mateclaw-server/src/main/java/vip/mate/lifecycle/contract/EventNormalizationContract.java` | landed |
| `LifecycleEventNormalizer` (harness ↔ hook bidirectional mapping) | `mateclaw-server/src/main/java/vip/mate/lifecycle/normalizer/LifecycleEventNormalizer.java` | landed |

**Key design decisions:**
- All new code lives in isolated `vip.mate.lifecycle.contract.*` and `vip.mate.lifecycle.normalizer.*` packages.
- `LifecycleEventCategory` unifies harness event names (`phase`, `plan_step_started`, `tool_call_started`, etc.)
  and hook event families (`agent`, `tool`, `session`, `memory`, `wiki`, `channel`, `cron`) into 10 categories.
- `LifecycleEventEnvelope` is intentionally minimal so it can be produced from any source without schema changes.
- `LifecycleEventNormalizer` provides bidirectional mapping:
  - Harness events → canonical envelope
  - Hook events → canonical envelope
  - Reverse lookup from category to typical harness/hook names
- Zero existing event publishers were modified; the normalizer is a pure mapping layer.

**Deferred to later packages:**
- Wiring `LifecycleEventNormalizer` into `HarnessRunService` or `HookDispatcher` (deferred to WP-5 second pass).
- Hook trust classification and explicit trust levels (deferred to security hardening phase).
- Plugin execution contract (deferred to plugin framework redesign).

#### WP-5 Detailed Execution Plan

`WP-5` should stay narrowly focused on **event taxonomy, canonical envelope, hook/listener boundaries, and compatibility mapping**.

Execution order inside `WP-5` should be:

1. freeze lifecycle event taxonomy
2. freeze canonical event envelope
3. freeze listener vs hook boundary contract
4. freeze legacy-to-canonical mapping
5. freeze hook trust classes
6. freeze adapter and compatibility rules
7. define diagnostics and targeted tests
8. freeze implementation cut and acceptance grid

##### WP-5 Anti-Expansion Guardrails

Do not let `WP-5` expand into:

- replacing Spring event infrastructure
- redesigning hook isolation model from scratch
- building external plugin execution first
- full observability platform rollout
- approval or memory governance redesign outside event vocabulary alignment

##### WP-5 Task Matrix

| Task ID | Task Name | Focus Area | Primary Files / Classes | Expected Output | Acceptance Signal |
| --- | --- | --- | --- | --- | --- |
| WP5-01 | Freeze lifecycle taxonomy | event categories | [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java), [mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java](mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java), [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java) | one lifecycle event taxonomy table | graph, hook, and lifecycle domains map under one vocabulary |
| WP5-02 | Freeze event envelope | shared metadata contract | same files plus adapters | one envelope matrix | minimum shared fields are explicit |
| WP5-03 | Freeze listener vs hook boundary | internal vs external execution surface | [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java), [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java), adapters | one boundary matrix | hook/listener semantics stop drifting |
| WP5-04 | Freeze legacy mapping | canonical naming compatibility | `GraphEventPublisher`, `MateHookEvent`, adapters, harness consumer | one legacy-to-canonical mapping table | old names can map without breaking hooks |
| WP5-05 | Freeze hook trust classes | hook source trust | `HookRegistry`, `HookDispatcher`, DB hook records, Spring adapter surfaces | one trust-class matrix | trust assumptions are explicit |
| WP5-06 | Freeze compatibility adapters | adapter and migration rules | `SpringEventAdapter`, event publication points | one compatibility contract table | first-pass normalization avoids breakage |
| WP5-07 | Define diagnostics and tests | regression / observability boundary | graph/hook/harness surfaces | one targeted test matrix + diagnostics note | mapping and dispatch remain explainable |
| WP5-08 | Freeze implementation cut | execution-ready bounded plan | outputs of `WP5-01` to `WP5-07` | one implementation cut matrix + acceptance grid | `WP-5` can move into code work without reopening design scope |

##### WP-5 Detailed Worksheets

###### Worksheet `WP5-01` — Freeze Lifecycle Event Taxonomy

#### Task goal

Freeze one canonical lifecycle taxonomy that can classify graph events, hook events, and adjacent runtime transitions without replacing existing event systems.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java)
- [mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java](mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java)
- [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java)
- [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)

#### Execution steps

1. inventory currently published graph and hook event families
2. map them to the planned lifecycle categories
3. separate event family from consumer implementation
4. mark missing categories vs merely missing aliases
5. keep taxonomy stable and bounded

#### Required output table template

| Taxonomy ID | Canonical Lifecycle Category | Current Event Names / Families | Primary Publisher | Primary Consumer | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one lifecycle taxonomy table
- one note on missing vs already-present categories
- one vocabulary-alignment note

#### Acceptance checks

- graph and hook events both map into the taxonomy
- approval/context/memory categories are represented
- missing categories are explicit rather than assumed absent

#### Explicit non-goals

- no mass event rename yet
- no new bus implementation
- no dispatcher redesign

#### WP5-01 first-pass lifecycle taxonomy

| Taxonomy ID | Canonical Lifecycle Category | Current Event Names / Families | Primary Publisher | Primary Consumer | Notes |
| --- | --- | --- | --- | --- | --- |
| EVT01 | session lifecycle | `session:*` hook family, `ConversationCompletedEvent` adapted to `SessionEvent` | `SpringEventAdapter` + future direct publishers | `HookDispatcher` and session-related listeners | hook family exists, graph-side vocabulary is thinner |
| EVT02 | prompt / plan lifecycle | `phase`, `plan_created`, `plan_step_started`, `plan_step_completed` | `GraphEventPublisher` | `HarnessRunService` | already strong on graph side |
| EVT03 | tool lifecycle | `tool_call_started`, `tool_call_completed`, `tool:*` hook family | graph runtime + direct hook publishers | harness + hooks | naming split exists |
| EVT04 | approval lifecycle | `tool_approval_requested` + approval persistence transitions | `GraphEventPublisher` + approval workflow | harness + approval services | graph event exists; richer approval events are still implicit |
| EVT05 | context lifecycle | no canonical event family yet; compaction/routing mostly internal | context services | internal only today | likely alias/mapping gap rather than zero implementation |
| EVT06 | memory lifecycle | `memory:*` hook family, `MemoryWriteEvent`, conversation completion adapted memory event | memory services + `SpringEventAdapter` | hooks + memory listeners | fragmented but real |
| EVT07 | completion / validation / export lifecycle | `perf_summary`, run completion, wiki processed, export-adjacent tool events | graph runtime + adapters | harness / hooks / downstream services | partially present under mixed names |

#### WP5-01 alignment note

The system already contains most lifecycle categories, but they are split across graph events, hook families, and domain-specific Spring events.

###### Worksheet `WP5-02` — Freeze Canonical Event Envelope

#### Task goal

Define the minimum shared event envelope so different event systems can align without losing compatibility.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java)
- [mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java](mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java)
- [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)

#### Execution steps

1. inventory fields already present in graph and hook events
2. identify minimum common envelope
3. distinguish canonical fields from optional payload fields
4. keep compatibility with current event data shape
5. note which fields need adapters rather than publisher rewrites

#### Required output table template

| Envelope Field | Canonical Purpose | Present In Graph Events? | Present In Hook Events? | Required or Optional | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one event-envelope matrix
- one adapter-gap note
- one minimal-required-fields note

#### Acceptance checks

- timestamp and event type alignment are explicit
- identity fields are listed with required/optional status
- envelope remains small enough for first-pass migration

#### Explicit non-goals

- no mandatory retrofitting of every publisher immediately
- no telemetry schema rollout
- no cross-service tracing system

#### WP5-02 first-pass event-envelope matrix

| Envelope Field | Canonical Purpose | Present In Graph Events? | Present In Hook Events? | Required or Optional | Notes |
| --- | --- | --- | --- | --- | --- |
| `eventType` | canonical event identity | yes (`type`) | yes (`type()`) | required | core alignment field |
| `timestamp` | event occurrence time | yes | yes | required | already common |
| `conversationId` | conversation-scoped correlation | partial | partial | optional but strongly preferred | not every event has it today |
| `agentId` | agent correlation | partial | partial | optional but strongly preferred | often buried in payload |
| `workspaceId` | workspace correlation | weak | weak | optional | needs adapters/publisher enrichment |
| `runId` | harness run correlation | weak | no | optional | harness-side enrichment candidate |
| `sourceClass` | managed/internal/local/etc trust/source label | no | no | optional first-pass | key for future hook trust contract |
| `policyScope` | authority scope for approvals/context/memory | no | no | optional | needed for cross-system explainability |
| `payload` | domain-specific details | yes | yes | required | compatibility anchor |

#### WP5-02 adapter-gap note

The biggest gap is not timestamps or event names; it is correlation and source metadata. Those can be added by adapters or enrichers before forcing all publishers to change.

###### Worksheet `WP5-03` — Freeze Listener vs Hook Boundary Contract

#### Task goal

Make the semantic difference between internal listeners and configurable hooks explicit while keeping their vocabularies alignable.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java)
- [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java)
- [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)

#### Execution steps

1. define internal listener role
2. define external/configurable hook role
3. inventory current governance differences
4. align boundary wording with trust and event taxonomy
5. keep the distinction useful and implementable

#### Required output table template

| Boundary ID | Surface | Canonical Role | Configuration Source | Execution Governance | Vocabulary Alignment Need | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one listener-vs-hook boundary matrix
- one execution-governance note
- one vocabulary-sharing note

#### Acceptance checks

- hook and listener are not treated as synonyms
- governance differences are explicit
- shared vocabulary need is documented

#### Explicit non-goals

- no collapse of listeners into hooks
- no hook sandbox redesign
- no DB schema redesign for hooks

#### WP5-03 first-pass boundary matrix

| Boundary ID | Surface | Canonical Role | Configuration Source | Execution Governance | Vocabulary Alignment Need | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| HB01 | internal Spring/domain listeners | internal runtime reaction surface | code | normal runtime semantics | medium | optimized for system correctness |
| HB02 | graph event ingestion into harness | internal observability/state projection | code | harness-specific processing | medium | graph names should map to lifecycle taxonomy |
| HB03 | hook registry + dispatcher | configurable extension surface | DB-configured hooks | enable switch, rate limit, concurrency, deadline, audit | high | externalizable behavior needs canonical names |
| HB04 | Spring-to-hook adapters | compatibility bridge | code | same hook governance after adaptation | high | lets existing events feed hook bus without invasive changes |

#### WP5-03 execution-governance note

Hooks already have stronger execution governance than many internal listeners: they carry enable toggles, rate limits, concurrency bounds, hard deadlines, and audit writes. This governance difference is one reason hooks should remain distinct from listeners.

###### Worksheet `WP5-04` — Freeze Legacy-to-Canonical Mapping

#### Task goal

Define a bounded mapping from existing event names/families into canonical lifecycle categories without breaking existing consumers.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java)
- [mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java](mateclaw-server/src/main/java/vip/mate/hook/event/MateHookEvent.java)
- [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)

#### Execution steps

1. list current names that need canonical grouping
2. map each to lifecycle category and canonical alias
3. preserve existing names for compatibility
4. distinguish real missing events from alias gaps
5. keep mapping finite

#### Required output table template

| Mapping ID | Legacy Event / Family | Canonical Category | Canonical Alias / Group | Breakage Risk | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one legacy-to-canonical mapping table
- one alias-only gap note
- one compatibility-first note

#### Acceptance checks

- graph events and hook families appear in the map
- compatibility is preserved
- alias gaps are explicit

#### Explicit non-goals

- no forced mass renaming
- no immediate publisher rewrite campaign
- no event deprecation policy rollout yet

#### WP5-04 first-pass mapping table

| Mapping ID | Legacy Event / Family | Canonical Category | Canonical Alias / Group | Breakage Risk | Notes |
| --- | --- | --- | --- | --- | --- |
| MAP01 | `phase` | prompt/plan lifecycle | `plan:phase` group | low | existing harness consumer can remain unchanged |
| MAP02 | `plan_created` | prompt/plan lifecycle | `plan:created` | low | alias-only improvement |
| MAP03 | `plan_step_started` / `plan_step_completed` | prompt/plan lifecycle | `plan:step_started` / `plan:step_completed` | low | alias-only |
| MAP04 | `tool_call_started` / `tool_call_completed` | tool lifecycle | `tool:before` / `tool:after` group | medium | naming bridge between graph and hook vocabularies |
| MAP05 | `tool_approval_requested` | approval lifecycle | `approval:requested` | low | bridge event already exists |
| MAP06 | `tool_direct_result` | tool/completion lifecycle | `tool:direct_result` | low | special tool completion flavor |
| MAP07 | `perf_summary` | completion/validation/export or diagnostics | `run:perf_summary` | low | observability-oriented alias |
| MAP08 | `session:*`, `memory:*`, `wiki:*`, `tool:*` hook families | category-aligned families | same domain-alias groups | low | mostly grouping and envelope alignment |

#### WP5-04 alias-gap note

The main first-pass problem is not event absence; it is vocabulary divergence. Many needed lifecycle signals already exist under graph names, hook family names, or adapted Spring events.

###### Worksheet `WP5-05` — Freeze Hook Trust Classes

#### Task goal

Make current hook trust assumptions explicit so future governance does not rely on hidden defaults.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java)
- [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java)
- DB hook entity configuration path
- [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)

#### Execution steps

1. infer current trust assumptions from enabled-source behavior
2. define a minimum trust-class vocabulary
3. distinguish event source trust from action execution governance
4. keep the taxonomy compatible with current DB-driven hooks
5. note what remains implicit today

#### Required output table template

| Trust ID | Trust Class | Current Example | Current Enforcement Reality | Future Contract Direction | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one hook trust-class matrix
- one note on current implicit trust
- one event-source-vs-action-governance note

#### Acceptance checks

- trust classes are explicit enough for later governance
- current DB-enabled hook behavior is represented
- source trust and action controls are not conflated

#### Explicit non-goals

- no new permission system now
- no hook signature/verification system now
- no plugin marketplace design

#### WP5-05 first-pass hook trust matrix

| Trust ID | Trust Class | Current Example | Current Enforcement Reality | Future Contract Direction | Notes |
| --- | --- | --- | --- | --- | --- |
| TR01 | internal managed event source | graph publisher, Spring domain events | implicitly trusted to publish runtime events | remain highest-trust source class | current default for most events |
| TR02 | adapted internal event source | `SpringEventAdapter` republished events | inherits trust from internal domain event | explicit adapter-managed trust class | useful distinction for diagnostics |
| TR03 | DB-configured hook subscription | enabled hook rows matched by registry | trusted if enabled and action builds successfully | explicit configurable-extension trust class | current operational trust is enable-based |
| TR04 | future local/project-influenced behavior | not first-class yet | mostly absent / implicit | explicit lower-trust or separately governed class later | reserved for future work |

#### WP5-05 source-vs-action note

Event-source trust and action-execution governance are different concerns. Source trust answers “who may emit/subscribe meaningfully”; action governance answers “how safely may the hook execute once matched.”

###### Worksheet `WP5-06` — Freeze Compatibility Adapter Contract

#### Task goal

Define how adapters and enrichers can normalize event vocabulary without forcing all existing publishers and consumers to change together.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java](mateclaw-server/src/main/java/vip/mate/hook/adapter/SpringEventAdapter.java)
- [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java)
- [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java)

#### Execution steps

1. identify current adapter/enricher surfaces
2. define where canonical aliases can be introduced safely
3. preserve legacy event names for first-pass compatibility
4. define minimal enrichment responsibilities
5. bound adapter scope to avoid total rewrite

#### Required output table template

| Adapter ID | Current Surface | Compatibility Role | Allowed Enrichment | Must Preserve | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one compatibility adapter table
- one backward-compatibility note
- one enrichment-boundary note

#### Acceptance checks

- adapters preserve legacy behavior
- canonical alignment can happen incrementally
- enrichment boundaries are explicit

#### Explicit non-goals

- no mandatory new adapter layer for everything immediately
- no harness replacement
- no hook DB schema rewrite

#### WP5-06 first-pass compatibility adapter table

| Adapter ID | Current Surface | Compatibility Role | Allowed Enrichment | Must Preserve | Notes |
| --- | --- | --- | --- | --- | --- |
| AD01 | `SpringEventAdapter` | republish existing Spring events into hook bus | canonical category metadata, source class, optional correlation hints | original event semantics | already existing compatibility bridge |
| AD02 | graph-to-canonical naming notes or helper | map graph event names to lifecycle aliases | alias labels, category tags | existing event types consumed by harness | can stay documentation/helper first |
| AD03 | harness ingestion layer | optionally understand canonical metadata while still accepting legacy graph events | correlation metadata, alias awareness | current `GraphEventPublisher` event names | consumer-side compatibility path |

#### WP5-06 backward-compatibility note

The safest first pass is alias-and-enrich, not rename-and-break. Legacy event names should remain accepted while canonical classification is added around them.

###### Worksheet `WP5-07` — Define Diagnostics and Targeted Tests

#### Task goal

Define the minimum diagnostics and regression matrix needed to normalize event vocabulary safely.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java](mateclaw-server/src/main/java/vip/mate/agent/GraphEventPublisher.java)
- [mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java](mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java)
- [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java)
- [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java)

#### Execution steps

1. define taxonomy-lock tests
2. define envelope and mapping regression cases
3. define hook dispatch governance regression cases
4. define diagnostics fields for event correlation and canonical category visibility
5. keep the matrix finite and implementation-ready

#### Required output table template

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one targeted test matrix
- one diagnostics payload note
- one compatibility-lock note

#### Acceptance checks

- taxonomy, mapping, and dispatch governance are all covered
- legacy compatibility is preserved in tests or contract notes
- hook/listener boundary remains explicit

#### Explicit non-goals

- no giant end-to-end tracing suite
- no distributed observability rollout
- no full hook security redesign

#### WP5-07 first-pass targeted test matrix

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |
| EV01 | graph event taxonomy lock | graph event types | each graph event maps to canonical lifecycle category | unit / contract lock | vocabulary stability |
| EV02 | hook family taxonomy lock | hook event families | each family maps to canonical lifecycle category | unit / contract lock | taxonomy coverage |
| EV03 | harness legacy compatibility | legacy graph event payload | `HarnessRunService` continues to ingest without regression | integration-light | compatibility lock |
| EV04 | hook registry exact/wildcard matching | DB hook rows + event types | exact and wildcard matches remain deterministic | unit | core dispatch stability |
| EV05 | hook dispatcher governance | matched hooks under load/timeout | enable switch, rate limit, concurrency, and deadline semantics hold | integration-light | governance lock |
| EV06 | Spring adapter bridge | domain events adapted to hook events | canonical family/category metadata can be added without breaking republish | unit / contract lock | bridge stability |

#### WP5-07 diagnostics note

Minimum diagnostics for `WP-5` should include:

- legacy event name and canonical category
- correlation identifiers when available (`conversationId`, `agentId`, `runId`)
- source class / adapter origin
- whether event was consumed by harness, hooks, or both

###### Worksheet `WP5-08` — Freeze Implementation Cut and Acceptance Grid

#### Task goal

Turn `WP-5` outputs into a bounded code-change plan that can be implemented without forcing every publisher and consumer into one simultaneous migration.

#### Inputs required

- outputs from `WP5-01` to `WP5-07`

#### Execution steps

1. define the minimum code cut for first implementation pass
2. assign file/class touchpoints
3. identify regression tests
4. list explicit deferments
5. freeze final acceptance grid

#### Required output table template

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one implementation cut matrix
- one deferred-items note
- one final acceptance grid for `WP-5`

#### Acceptance checks

- each cut has bounded files and bounded output
- deferred items are explicit
- `WP-5` can start coding without reopening design discovery

#### Explicit non-goals

- no bundling of `WP-6` into the same implementation pass
- no infrastructure replacement
- no hidden hook execution redesign

#### WP5-08 first-pass implementation cut matrix

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |
| C1 | add lifecycle taxonomy / canonical alias models or docs | hook/shared or docs-only layer | lifecycle event map, canonical category docs | compile if code-backed + doc review | model placement drift |
| C2 | add envelope enrichment helpers / DTOs | event/shared helper layer, adapters | canonical envelope metadata without breaking legacy names | unit tests / snapshot checks | over-expansion into tracing platform |
| C3 | add compatibility mappings near publishers/consumers | `GraphEventPublisher`, `HarnessRunService`, adapters | alias mapping and category visibility | integration-light tests | accidental consumer breakage |
| C4 | add hook trust / diagnostics metadata | `HookRegistry`, `HookDispatcher`, adapter surfaces | explicit trust/source labels and diagnostics notes | unit / contract tests | security-model creep |
| C5 | add targeted regression tests for mapping and dispatch | graph/hook/harness tests | compatibility and governance lock coverage | unit + integration-light tests | legacy edge cases remain |

#### WP5-08 deferred-items note

Explicitly defer from `WP-5`:

- Spring event infrastructure replacement
- full distributed tracing / telemetry productization
- complete approval/memory/context event publication expansion
- plugin execution model redesign
- major hook permission model changes

#### WP5-08 final acceptance grid

`WP-5` is review-ready when:

- one lifecycle taxonomy is frozen
- one canonical envelope is defined
- hook/listener boundaries are explicit
- legacy-to-canonical mappings are explicit
- hook trust assumptions are documented
- targeted tests lock compatibility and dispatch governance behavior

---

### WP-6 — Template Contract Normalization and Ledger Merge Preparation

#### Objective

Make templates explicit business contracts and prepare clean mapping back into the execution ledger.

#### Functional Points

1. Define canonical template sections:
   - identity
   - runtime profile
   - capability defaults
   - safety defaults
   - context declarations
   - knowledge declarations
   - workspace materialization
   - acceptance declarations
2. Map existing built-in template JSON fields into those sections.
3. Mark which template fields are:
   - advisory
   - defaulting
   - policy-tightening only
4. Add validation rules for template contract completeness.
5. Prepare ledger merge entries aligned by core-system theme rather than scattered feature notes.

#### Modification Scope

Primary template scope:

- [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json)
- [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json)
- other built-in template JSON under `mateclaw-server/src/main/resources/templates/`

Primary server scope:

- template loading / parsing / validation code paths
- graph build points that consume template metadata, especially [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)

Documentation scope:

- [docs/agent-harness-implementation.md](docs/agent-harness-implementation.md)
- [docs/core-systems-consolidation-plan.md](docs/core-systems-consolidation-plan.md)

Allowed change type:

- normalize template schema sections
- add validation and documentation
- add compatibility mapping for old template fields if needed

Not allowed in this package:

- redesigning built-in template business stories
- introducing a new template marketplace system
- changing agent onboarding UX first

#### Suggested Deliverables

1. template contract schema notes
2. built-in template field mapping table
3. validation checklist for template completeness
4. ledger merge draft grouped by core-system tracks

#### Acceptance Checkpoint

WP-6 is done when:

- built-in templates can be read as explicit contracts
- template defaults vs policy-tightening semantics are distinguishable
- the main ledger can be updated cleanly without duplicating or scattering tasks

#### WP-6 Landing Record

**Status:** First-pass landed  
**Landed at:** 2026-05-20  

**Deliverables produced:**

| Artifact | Path | Status |
| --- | --- | --- |
| `TemplateSection` enum (8 canonical sections) | `mateclaw-server/src/main/java/vip/mate/template/contract/TemplateSection.java` | landed |
| `TemplateFieldClassification` record (field-level metadata) | `mateclaw-server/src/main/java/vip/mate/template/contract/TemplateFieldClassification.java` | landed |
| `TemplateApplicationContract` interface (application + validation contract) | `mateclaw-server/src/main/java/vip/mate/template/contract/TemplateApplicationContract.java` | landed |
| `TemplateMetadataResolver` (scans and classifies built-in template fields) | `mateclaw-server/src/main/java/vip/mate/template/resolver/TemplateMetadataResolver.java` | landed |
| `TemplateSchemaValidator` (validates templates against section contract) | `mateclaw-server/src/main/java/vip/mate/template/resolver/TemplateSchemaValidator.java` | landed |

**Key design decisions:**
- All new code lives in isolated `vip.mate.template.contract.*` and `vip.mate.template.resolver.*` packages.
- `TemplateSection` separates descriptive metadata from runtime behavior, safety defaults, context declarations, materialization, acceptance contract, capability declaration, and UX metadata.
- `TemplateMetadataResolver` centralizes the parsing logic that was previously scattered across `TemplateService`, `ContextRouterService`, and `ToolExecutionExecutor`.
- `TemplateSchemaValidator` notes unknown fields without failing validation (forward compatibility), and flags missing recommended fields for runtime contract templates.
- Existing template JSON files (`coding-agent.json`, `teacher-exam-assistant.json`) are unchanged; the resolver reads them as-is.

**Deferred to later packages:**
- Wiring `TemplateMetadataResolver` into `TemplateService` (deferred to WP-6 second pass).
- Implementing `TemplateApplicationContract` with full agent creation + seeding logic (deferred to WP-6 second pass; `TemplateService` continues to own application today).
- Ledger merge into `docs/agent-harness-implementation.md` (deferred to post-WP-6 review).

#### WP-6 Detailed Execution Plan

`WP-6` should stay narrowly focused on **template section normalization, field-role clarity, validation completeness, and ledger merge preparation**.

Execution order inside `WP-6` should be:

1. freeze canonical template sections
2. freeze built-in field-to-section mapping
3. freeze field authority semantics
4. freeze consumer map across runtime/build/bootstrap paths
5. freeze validation checklist and completeness rules
6. freeze ledger merge grouping rules
7. define diagnostics and targeted tests
8. freeze implementation cut and acceptance grid

##### WP-6 Anti-Expansion Guardrails

Do not let `WP-6` expand into:

- new marketplace or template store design
- agent onboarding UX redesign
- business-story rewrite of built-in templates
- policy-authority redesign that belongs to `WP-2`
- context lifecycle redesign that belongs to `WP-4`

##### WP-6 Task Matrix

| Task ID | Task Name | Focus Area | Primary Files / Classes | Expected Output | Acceptance Signal |
| --- | --- | --- | --- | --- | --- |
| WP6-01 | Freeze canonical sections | template contract structure | built-in template JSON + [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java) | one canonical section taxonomy | every major field group has one section |
| WP6-02 | Freeze field mapping | built-in field-to-section map | coding/teacher templates + representative built-ins | one field mapping table | built-in templates can be read as explicit contracts |
| WP6-03 | Freeze field authority semantics | advisory/defaulting/policy-tightening | templates + consumers | one authority-semantics matrix | defaults vs tightening-only semantics are explicit |
| WP6-04 | Freeze consumer map | who consumes each field group | `TemplateService`, `AgentGraphBuilder`, `ContextRouterService`, executor merge sites | one consumer map | runtime/bootstrap consumption is traceable |
| WP6-05 | Freeze validation checklist | completeness and schema rules | template parsing/loading/repair surfaces | one validation checklist matrix | contract completeness becomes testable |
| WP6-06 | Freeze ledger merge rules | mapping back into main ledger | [docs/agent-harness-implementation.md](docs/agent-harness-implementation.md), [docs/core-systems-consolidation-plan.md](docs/core-systems-consolidation-plan.md) | one ledger-merge grouping table | ledger updates stop scattering by feature note |
| WP6-07 | Define diagnostics and tests | regression / observability boundary | template loading and representative built-in files | one targeted test matrix + diagnostics note | contract normalization is explainable and safe |
| WP6-08 | Freeze implementation cut | execution-ready bounded plan | outputs of `WP6-01` to `WP6-07` | one implementation cut matrix + acceptance grid | `WP-6` can move into code work without reopening design scope |

##### WP-6 Detailed Worksheets

###### Worksheet `WP6-01` — Freeze Canonical Template Sections

#### Task goal

Freeze one canonical section taxonomy so built-in templates stop mixing business metadata, runtime contracts, and safety defaults without structure.

#### Files / classes to inspect

- [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json)
- [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json)
- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)

#### Execution steps

1. enumerate current top-level field groups across representative built-ins
2. map them into stable contract sections
3. distinguish section purpose from field authority semantics
4. keep sections broad enough for compatibility
5. avoid schema churn beyond grouping clarity

#### Required output table template

| Section ID | Canonical Template Section | Current Field Groups | Primary Purpose | Notes |
| --- | --- | --- | --- | --- |

#### Expected deliverable

- one canonical section taxonomy table
- one grouping note for mixed fields
- one compatibility note for current template JSON

#### Acceptance checks

- major built-in template field groups are covered
- sections are stable enough for validation and ledger mapping
- grouping does not force immediate schema rewrite

#### Explicit non-goals

- no new template DSL
- no wholesale JSON rewrite now
- no business-story redesign

#### WP6-01 first-pass canonical section taxonomy

| Section ID | Canonical Template Section | Current Field Groups | Primary Purpose | Notes |
| --- | --- | --- | --- | --- |
| TS01 | identity | `id`, `name`, `nameZh`, `version`, `category`, `domain`, `visibility`, `ownerType`, `tags`, `icon` | identity and catalog placement | mostly descriptive/lookup |
| TS02 | runtime profile | `agentType`, `maxIterations`, `runtime`, `agentProfile`, `homeSubtitle` | active runtime mode and agent behavior/profile defaults | runtime-significant |
| TS03 | capability defaults | `capabilityPack`, `tools`, `inputSchema`, `outputFormats` | capability declaration and interaction shape | mixed runtime/declarative section |
| TS04 | safety defaults | `permissions`, `defaultWorkspacePolicy`, quality gate safety subset | safety/default authority constraints | contains policy-adjacent fields |
| TS05 | context declarations | `contextSources`, session temporary/knowledge binding declarations | runtime context sourcing contract | overlaps `WP-4` consumption |
| TS06 | knowledge declarations | `defaultKnowledgeBases` | knowledge seeding/bootstrap | separate from runtime grounding behavior |
| TS07 | workspace materialization | `workspaceFiles` | bootstrap files and prompt-file materialization | output artifact section |
| TS08 | acceptance declarations | `qualityGates`, `mockAcceptanceTasks`, `mvpScope`, starter guidance quality expectations | acceptance/completeness/business contract | strong ledger-merge value |
| TS09 | interaction guidance | `homeQuickStarts`, `starterPrompts`, `interactionHints` | onboarding and usage hints | business UX metadata, not top authority |

#### WP6-01 grouping note

The most mixed section today is safety defaults, because it combines permissions-like business metadata with true policy-adjacent defaults.

###### Worksheet `WP6-02` — Freeze Built-In Field Mapping

#### Task goal

Map representative built-in template fields into canonical sections so built-ins become reviewable contracts rather than ad hoc JSON blobs.

#### Files / classes to inspect

- [mateclaw-server/src/main/resources/templates/coding-agent.json](mateclaw-server/src/main/resources/templates/coding-agent.json)
- [mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json](mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json)
- additional built-in templates as spot checks if needed

#### Execution steps

1. enumerate representative top-level fields in coding and teacher templates
2. assign each to a canonical section
3. note representative differences between templates
4. keep the mapping finite and reusable
5. mark fields that still straddle sections

#### Required output table template

| Field Group | Seen In Built-Ins | Canonical Section | Current Role | Cross-Template Drift | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one built-in field mapping table
- one drift note across representative templates
- one mixed-section note

#### Acceptance checks

- at least coding and teacher templates are covered
- representative drift is explicit
- section mapping is usable for validation later

#### Explicit non-goals

- no template-file edits yet
- no per-template business review
- no marketplace metadata design

#### WP6-02 first-pass built-in field mapping

| Field Group | Seen In Built-Ins | Canonical Section | Current Role | Cross-Template Drift | Notes |
| --- | --- | --- | --- | --- | --- |
| identity/catalog fields | both | identity | listing and stable identity | low | stable across built-ins |
| `runtime` | both | runtime profile | runtime behavior contract | medium | fields differ by domain and mode |
| `permissions` | both | safety defaults | who may use/edit/bind | medium | binding/edit permissions vary by domain |
| `defaultWorkspacePolicy` | both | safety defaults | policy-tightening/runtime safety default | medium-high | coding template uses path/risk overrides; teacher template uses action allow/deny style |
| `agentProfile` | both | runtime profile | role/tone/UX defaults | low-medium | domain-specific wording varies |
| `capabilityPack` | both | capability defaults | declared capability bundle | medium | domain-specific capabilities |
| `contextSources` | both | context declarations | source declarations for knowledge/session/project | medium | coding includes project-derived; teacher emphasizes session/KB |
| `tools` | both | capability defaults | declared tool surface and risk hints | medium-high | very domain-specific |
| `qualityGates` | both | acceptance declarations | quality/acceptance contract | low-medium | rule text differs by domain |
| `mockAcceptanceTasks` | both | acceptance declarations | acceptance examples | medium | task content domain-specific |
| `defaultKnowledgeBases` | both | knowledge declarations | seed knowledge | medium | different page structures and volume |
| `workspaceFiles` | both in practice / coding strongly | workspace materialization | file seeding and prompt-file setup | medium-high | coding template is richer here |
| `starterPrompts` / `homeQuickStarts` / `interactionHints` | both | interaction guidance | onboarding guidance | medium | teacher template richer in interaction hinting |

#### WP6-02 drift note

The biggest representative drift is not in identity or runtime basics, but in how safety defaults and workspace materialization are expressed across domain templates.

###### Worksheet `WP6-03` — Freeze Field Authority Semantics

#### Task goal

Make explicit which template fields are advisory, which provide defaults, and which may only tighten behavior.

#### Files / classes to inspect

- representative built-in templates
- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)

#### Execution steps

1. group fields by authority effect
2. separate runtime-decorative metadata from active constraints
3. identify policy-tightening-only fields
4. keep authority labels finite
5. note the highest-risk mixed fields

#### Required output table template

| Authority ID | Field / Field Group | Authority Semantics | Current Consumer | May Tighten Behavior? | Must Not Be Treated As | Notes |
| --- | --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one authority-semantics matrix
- one mixed-field note
- one safety-default note

#### Acceptance checks

- advisory/defaulting/policy-tightening-only semantics are explicit
- consumers are named
- no field is silently treated as stronger authority than intended

#### Explicit non-goals

- no policy merge redesign
- no consumer rewrite campaign
- no template schema rewrite now

#### WP6-03 first-pass authority-semantics matrix

| Authority ID | Field / Field Group | Authority Semantics | Current Consumer | May Tighten Behavior? | Must Not Be Treated As | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| AU01 | identity/catalog fields | advisory / lookup | template listing, bootstrap identity | no | runtime safety authority | descriptive only |
| AU02 | `runtime` | defaulting/runtime contract | `AgentGraphBuilder` guidance and runtime behavior surfaces | indirect | policy authority | active runtime expectations |
| AU03 | `permissions` | advisory + management governance | template config/listing/editability surfaces | no direct execution tightening in inspected paths | guard policy | governance metadata, not direct execution policy |
| AU04 | `defaultWorkspacePolicy` | policy-tightening-only | executor runtime policy merge | yes | ordinary default setting | strongest authority-bearing template field |
| AU05 | `agentProfile` | defaulting/bootstrap metadata | `TemplateService.applyTemplate()` + runtime guidance | no | security authority | affects role/UX presentation |
| AU06 | `capabilityPack`, `tools` | defaulting/declared capability contract | bootstrap + runtime guidance surfaces | indirect | actual tool registry truth by itself | declarative contract |
| AU07 | `contextSources` | runtime declaration | `ContextRouterService`, runtime guidance | indirect | durable settings or policy | context contract field |
| AU08 | `workspaceFiles`, `defaultKnowledgeBases` | materialization/bootstrap defaults | `TemplateService` seeding and sync | indirect | lasting top authority after materialization | output artifact / bootstrap only |
| AU09 | `qualityGates`, `mockAcceptanceTasks`, `mvpScope` | acceptance/business contract | health/readiness/ledger planning surfaces | no | runtime policy engine | strong planning/validation value |
| AU10 | `starterPrompts`, `interactionHints`, `homeQuickStarts` | advisory interaction guidance | home/UX surfaces | no | runtime safety or capability authority | UX-only contract layer |

#### WP6-03 mixed-field note

`permissions` and `defaultWorkspacePolicy` often sit close together in template JSON, but they must not be treated as the same authority class. The former is management/governance metadata; the latter is runtime safety tightening.

###### Worksheet `WP6-04` — Freeze Consumer Map

#### Task goal

Trace which runtime and bootstrap surfaces consume template contract fields so future normalization has bounded impact.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- executor policy merge surfaces

#### Execution steps

1. identify who loads template JSON and metadata snapshots
2. identify who consumes which field groups at runtime
3. separate bootstrap materialization from runtime guidance and runtime enforcement
4. identify fields with indirect-only consumption
5. keep the map concrete

#### Required output table template

| Consumer ID | Consumer Surface | Consumed Field Groups | Consumption Type | Runtime-Critical? | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one consumer map table
- one indirect-consumption note
- one bootstrap-vs-runtime note

#### Acceptance checks

- bootstrap, runtime guidance, context routing, and policy merge consumers are covered
- indirect-only fields are marked
- consumption map is sufficient for bounded implementation planning

#### Explicit non-goals

- no refactor of every consumer now
- no full metadata schema registry
- no UI contract rewrite

#### WP6-04 first-pass consumer map

| Consumer ID | Consumer Surface | Consumed Field Groups | Consumption Type | Runtime-Critical? | Notes |
| --- | --- | --- | --- | --- | --- |
| CON01 | `TemplateService.applyTemplate()` | identity, runtime profile, agent profile, capability pack, workspace files, default knowledge bases | bootstrap materialization | yes for creation flow | primary bootstrap consumer |
| CON02 | `TemplateService.toTemplateMetadataJson()` | most template fields except `systemPrompt` and `workspaceFiles` | metadata snapshot | yes | creates runtime-readable manifest |
| CON03 | `AgentGraphBuilder.buildTemplateRuntimeGuidance()` | identity, profile, capability pack, runtime, policy, context sources | runtime guidance injection | medium-high | treats metadata as active runtime constraints/guidance |
| CON04 | `ContextRouterService.resolveTemplateRouteConfig()` | `contextSources`, knowledge bindings/session/project derived config | runtime context selection | high | core `WP-4` consumer |
| CON05 | executor template-policy parse/merge | `defaultWorkspacePolicy` | runtime safety merge | high | core `WP-2` consumer |
| CON06 | health/readiness/listing surfaces | `qualityGates`, `mockAcceptanceTasks`, permissions, starter guidance | validation/listing/business review | medium | strong planning/ledger value |

#### WP6-04 bootstrap-vs-runtime note

The biggest structural distinction is between fields consumed once at agent creation/materialization time and fields re-consumed later through `templateMetadataJson` during runtime guidance, routing, or policy merge.

###### Worksheet `WP6-05` — Freeze Validation Checklist and Completeness Rules

#### Task goal

Define the minimum validation contract that makes template completeness reviewable without forcing a new schema system first.

#### Files / classes to inspect

- representative built-in template JSON
- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)

#### Execution steps

1. define minimum required section presence
2. define field-group completeness expectations
3. distinguish hard-required vs quality-recommended sections
4. keep rules compatible with existing built-ins
5. note section-specific validation needs

#### Required output table template

| Validation ID | Section / Field Group | Hard Required or Recommended | Why | Current Built-In Coverage | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one validation checklist matrix
- one hard-vs-recommended note
- one completeness-gap note

#### Acceptance checks

- identity/runtime/safety/context/acceptance concerns are covered
- hard-required and recommended checks are distinct
- current built-ins can be evaluated without hidden criteria

#### Explicit non-goals

- no external schema registry implementation yet
- no migration of all legacy templates immediately
- no business review board process

#### WP6-05 first-pass validation checklist

| Validation ID | Section / Field Group | Hard Required or Recommended | Why | Current Built-In Coverage | Notes |
| --- | --- | --- | --- | --- | --- |
| VL01 | identity section | hard required | stable bootstrap and catalog identity | strong | every built-in needs this |
| VL02 | runtime profile section | hard required | runtime behavior and agent creation defaults | strong | includes `agentType`, runtime, profile basics |
| VL03 | safety defaults section | hard required for templates with operational constraints | policy/governance clarity | strong but heterogeneous | `permissions` + `defaultWorkspacePolicy` semantics must stay distinct |
| VL04 | capability defaults section | recommended / hard for operational templates | tool/capability clarity | strong | some simpler templates may be lighter |
| VL05 | context declarations section | recommended / hard when routing matters | runtime context sourcing contract | medium-strong | critical for context-aware templates |
| VL06 | knowledge declarations section | recommended | seed knowledge consistency | medium | not every template needs KB seeds |
| VL07 | workspace materialization section | recommended | explicit bootstrap artifact behavior | medium | varies by template richness |
| VL08 | acceptance declarations section | recommended but strongly preferred | makes ledger merge and evaluation explicit | strong in representative built-ins | high planning value |
| VL09 | interaction guidance section | recommended | onboarding clarity | medium-strong | should remain non-authoritative |

#### WP6-05 hard-vs-recommended note

Identity, runtime profile, and any operational safety defaults should be treated as hard contract layers. Knowledge seeds, workspace materialization, and interaction guidance can remain recommended sections as long as their absence is explicit.

###### Worksheet `WP6-06` — Freeze Ledger Merge Rules

#### Task goal

Prepare a clean mapping from normalized template contract work back into the main execution ledger without scattering template items across unrelated feature notes.

#### Files / classes to inspect

- [docs/agent-harness-implementation.md](docs/agent-harness-implementation.md)
- [docs/core-systems-consolidation-plan.md](docs/core-systems-consolidation-plan.md)

#### Execution steps

1. identify which template concerns map to core-system tracks
2. group ledger merge items by system theme, not by incidental feature story
3. distinguish ledger-ready items from deferred notes
4. keep the merge map finite and implementation-oriented
5. preserve compatibility with existing ledger organization where possible

#### Required output table template

| Ledger ID | Template Concern | Core-System Track | Merge Destination | Merge Style | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one ledger-merge grouping table
- one no-scatter note
- one deferred-merge note

#### Acceptance checks

- template items map back by core-system theme
- no duplicated/scattered merge plan is needed
- deferred items are explicit

#### Explicit non-goals

- no immediate ledger rewrite in this step
- no removal of historical notes yet
- no cross-doc mega-merge now

#### WP6-06 first-pass ledger merge table

| Ledger ID | Template Concern | Core-System Track | Merge Destination | Merge Style | Notes |
| --- | --- | --- | --- | --- | --- |
| LG01 | `defaultWorkspacePolicy` semantics | policy authority | main ledger policy track | merge into `WP-2` aligned section | avoid separate stray template-policy todo |
| LG02 | `contextSources` semantics | context lifecycle | main ledger context track | merge into `WP-4` aligned section | context declarations belong with context model |
| LG03 | runtime/profile/capability grouping | template contract | main ledger template/core-contract track | grouped template contract subsection | core `WP-6` output |
| LG04 | acceptance declarations (`qualityGates`, `mockAcceptanceTasks`) | acceptance / ledger hygiene | execution ledger acceptance track | grouped by validation/completeness | strong bridge to ledger |
| LG05 | workspace materialization and default knowledge seeds | bootstrap/materialization | template/bootstrap track | grouped under materialization notes | avoid mixing with runtime authority |

#### WP6-06 no-scatter note

Template concerns should merge back into the ledger by core-system theme: policy, context, bootstrap/materialization, and acceptance. They should not be re-scattered as isolated per-template notes.

###### Worksheet `WP6-07` — Define Diagnostics and Targeted Tests

#### Task goal

Define the minimum diagnostics and regression matrix needed to normalize template contracts safely.

#### Files / classes to inspect

- [mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java](mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)
- representative built-in template JSON files

#### Execution steps

1. define section-mapping lock tests
2. define authority-semantics regression cases
3. define metadata-snapshot completeness checks
4. define diagnostics fields for template contract visibility
5. keep the matrix finite and implementation-ready

#### Required output table template

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one targeted test matrix
- one diagnostics payload note
- one drift-lock note

#### Acceptance checks

- section mapping, authority semantics, and metadata completeness are covered
- built-in representative templates are included
- contract drift can be detected early

#### Explicit non-goals

- no giant template QA program
- no marketplace conformance suite
- no UI-first diagnostics rollout

#### WP6-07 first-pass targeted test matrix

| Test / Diagnostic ID | Focus | Required Inputs | Expected Assertion | Type | Notes |
| --- | --- | --- | --- | --- | --- |
| TMP01 | canonical section mapping | representative built-in templates | top-level fields map to canonical sections without orphan groups | unit / contract lock | grouping stability |
| TMP02 | metadata snapshot completeness | template + `toTemplateMetadataJson()` output | required runtime-significant fields remain present in metadata snapshot | unit | snapshot integrity |
| TMP03 | policy-tightening semantics lock | template with `defaultWorkspacePolicy` | field is classified as tightening-only, not ordinary default | contract lock | `WP-2` boundary |
| TMP04 | context declaration consumer lock | template with `contextSources` | router/runtime consumers can still resolve route config | integration-light | `WP-4` boundary |
| TMP05 | workspace materialization lock | template with `workspaceFiles` / default KBs | bootstrap seeding behavior remains distinct from runtime authority | integration-light | materialization boundary |
| TMP06 | acceptance declaration visibility | representative template acceptance fields | quality gates / mock tasks / MVP scope remain classifiable and visible | unit | ledger-merge value |

#### WP6-07 diagnostics note

Minimum diagnostics for `WP-6` should include:

- template id/version/category/domain
- section completeness summary
- field groups with authority semantics labels
- runtime-significant metadata fields present in snapshot

###### Worksheet `WP6-08` — Freeze Implementation Cut and Acceptance Grid

#### Task goal

Turn `WP-6` outputs into a bounded code-change plan that can be implemented without dragging policy, context, and ledger cleanup into one uncontrolled refactor.

#### Inputs required

- outputs from `WP6-01` to `WP6-07`

#### Execution steps

1. define the minimum code cut for first implementation pass
2. assign file/class touchpoints
3. identify regression tests
4. list explicit deferments
5. freeze final acceptance grid

#### Required output table template

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |

#### Expected deliverable

- one implementation cut matrix
- one deferred-items note
- one final acceptance grid for `WP-6`

#### Acceptance checks

- each cut has bounded files and bounded output
- deferred items are explicit
- `WP-6` can start coding without reopening design discovery

#### Explicit non-goals

- no business-story rewrite
- no marketplace rollout
- no hidden onboarding UX redesign

#### WP6-08 first-pass implementation cut matrix

| Cut ID | Change Slice | Primary Files | Output | Validation | Deferred Risk |
| --- | --- | --- | --- | --- | --- |
| C1 | add canonical section / authority models or docs | template/shared package or docs-only layer | section taxonomy docs, authority semantics table | compile if code-backed + doc review | model placement drift |
| C2 | add template contract validator / checklist helper | template loading/validation path | completeness checks and section-aware validation | unit tests on representative built-ins | accidental over-strictness |
| C3 | add consumer map and snapshot diagnostics | `TemplateService`, `AgentGraphBuilder`, diagnostics DTOs/helpers | contract visibility for runtime-significant fields | unit / snapshot checks | over-expansion into UI diagnostics |
| C4 | add targeted regression tests for representative built-ins | template tests + runtime consumer checks | section/authority/materialization lock coverage | unit + integration-light tests | drift across less common built-ins |
| C5 | prepare ledger merge draft by core-system tracks | docs only | grouped ledger merge notes | doc review | deferred actual merge may drift if not scheduled |

#### WP6-08 deferred-items note

Explicitly defer from `WP-6`:

- new template marketplace/product surfaces
- deep schema migration of all historical templates
- onboarding and template-discovery UX redesign
- policy/context redesign beyond field handoff boundaries
- large cross-doc ledger rewrite in the same implementation pass

#### WP6-08 final acceptance grid

`WP-6` is review-ready when:

- canonical template sections are frozen
- built-in field mapping is explicit
- advisory/defaulting/tightening semantics are explicit
- runtime/bootstrap consumers are mapped
- validation and ledger-merge rules are explicit
- targeted tests lock representative built-in contract behavior

---

## 21. Modification Scope Matrix by Module

To prevent uncontrolled spread, all later implementation should use the following module boundaries.

### 21.1 Server Modules Likely in Scope

#### Settings / Policy

- [mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java](mateclaw-server/src/main/java/vip/mate/system/service/SystemSettingService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java](mateclaw-server/src/main/java/vip/mate/workspace/core/service/WorkspaceService.java)
- [mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java](mateclaw-server/src/main/java/vip/mate/workspace/core/model/WorkspacePolicy.java)
- [mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java](mateclaw-server/src/main/java/vip/mate/tool/guard/service/ToolGuardService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java](mateclaw-server/src/main/java/vip/mate/agent/graph/executor/ToolExecutionExecutor.java)

#### Context / Memory

- [mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java](mateclaw-server/src/main/java/vip/mate/agent/context/ContextRouterService.java)
- [mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java](mateclaw-server/src/main/java/vip/mate/agent/context/ConversationWindowManager.java)
- [mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java](mateclaw-server/src/main/java/vip/mate/memory/spi/MemoryManager.java)
- [mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java](mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java)

#### Hooks / Lifecycle

- [mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java](mateclaw-server/src/main/java/vip/mate/hook/HookRegistry.java)
- [mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java](mateclaw-server/src/main/java/vip/mate/hook/HookDispatcher.java)
- harness run / approval / graph event publication surfaces adjacent to those modules

#### Template Contract

- template loading and validation code paths
- built-in template JSON under `mateclaw-server/src/main/resources/templates/`

### 21.2 Desktop Modules Likely in Scope

- [mateclaw-desktop/electron/desktopConfig.cjs](mateclaw-desktop/electron/desktopConfig.cjs)
- [mateclaw-desktop/electron/main.cjs](mateclaw-desktop/electron/main.cjs)
- [mateclaw-desktop/electron/localServer.cjs](mateclaw-desktop/electron/localServer.cjs)

Desktop scope is limited to:

- local runtime state separation
- source/provenance metadata passing if needed
- policy-authority boundary preservation

Desktop scope explicitly excludes in the first pass:

- large renderer UI redesign
- new desktop-only policy model
- new execution channels unrelated to current architecture

### 21.3 UI Scope Control

UI is not a first-batch primary target.

Allowed first-pass UI changes only if strictly required:

- terminology alignment
- diagnostics display for effective settings source
- policy explanation copy improvements

UI is explicitly out of scope for first-pass work if it involves:

- workflow redesign
- new multi-step management consoles
- new memory management product surfaces

---

## 22. Anti-Expansion Guardrails

To prevent infinite expansion during execution, the following guardrails should be enforced.

### 22.1 Package Exit Criteria Must Be Defined Up Front

No work package should start without a written definition of:

- exact functional target
- touched modules
- excluded modules
- done criteria

### 22.2 No Opportunistic Refactor Bundling

If a developer touches one of the scoped files and finds unrelated legacy issues, those should be recorded separately rather than absorbed into the current package by default.

### 22.3 One Runtime Contract per Package

Each package should introduce at most one new primary contract surface, for example:

- one settings resolver contract
- one policy authority matrix
- one memory taxonomy contract
- one context lifecycle contract
- one lifecycle event envelope
- one template contract schema

This prevents package goals from becoming vague.

### 22.4 Code Change First, Productization Later

For the first pass, focus on:

- backend semantics
- contract clarity
- compatibility adapters
- validation/tests/docs

Delay large product-facing UI and management surfaces until the underlying contract is stable.

### 22.5 Ledger Merge Is the Last Step, Not the First Step

The main ledger should only be expanded after the work packages and boundaries are stable.

This prevents the checklist from becoming a source of uncontrolled task multiplication.

---

## 23. Recommended Immediate Next Step

The next concrete step should **not** be full implementation across all packages.

The next step should be:

1. finish WP-0 as a documentation/inventory pass
2. choose only one implementation package to start first
3. recommend WP-1 before the others, because settings/policy boundaries determine the correctness of the later packages

Recommended first execution order:

1. WP-0
2. WP-1
3. WP-2
4. WP-4
5. WP-3
6. WP-5
7. WP-6

This order keeps the system stable because:

- settings and policy define authority first
- context needs policy/settings boundaries to be reliable
- memory boundaries are easier after context boundaries are explicit
- lifecycle normalization is safer after the above contracts exist
- template normalization should happen after the core runtime contract stabilizes

---

## 24. Practical Planning Conclusion

From this point onward, execution should no longer use only broad phrases like:

- “optimize memory system”
- “unify context”
- “improve hooks”

Instead, each task should be phrased in bounded form, for example:

- “Extract effective settings resolver from current system/workspace/template/desktop precedence logic; scope limited to `SystemSettingService`, `WorkspaceService`, and desktop config loading.”
- “Formalize restrictive policy merge helper for workspace/template policy resolution; scope limited to `WorkspacePolicy`, `WorkspaceService`, `ToolGuardService`, and `ToolExecutionExecutor`.”
- “Add context source taxonomy and provenance metadata to current context routing path; scope limited to `ContextRouterService`, `ConversationWindowManager`, and graph context assembly.”

That is the mechanism that prevents endless expansion while keeping the implementation genuinely executable.
