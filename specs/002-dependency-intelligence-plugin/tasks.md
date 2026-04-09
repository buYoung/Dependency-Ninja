---
description: "Task list for Dependency Intelligence Plugin v1.0"
---

# Tasks: Dependency Intelligence Plugin (v1.0)

**Input**: Design documents from `/specs/002-dependency-intelligence-plugin/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Targeted unit/integration tests are included for the correctness-critical
paths (version comparison, policy evaluation, workspace resolution, manifest
write+rollback). Presentation scenarios are covered by the `quickstart.md`
acceptance sweep rather than by per-surface automated tests. This matches the
spec's success criteria without over-investing in fragile UI tests.

**Organization**: Tasks are grouped by user story. Per the v1.0 clarification,
**US1 + US2 + US3 ship together as a single release** — there is no staged MVP
cut-off. The phase split below preserves independent-testability for internal
checkpoints and parallel work streams, but release-gating happens only after
Phase 5 completes.

## Constitution Alignment

- Tasks stay inside approved layers: `core/shared/{domain,application,infrastructure}`,
  `features/*/{domain,application,infrastructure,presentation}`, `services/`,
  `startup/`, `src/main/resources/`.
- Each cross-cutting concern (threading, cache, secrets/telemetry, plugin
  registration, i18n bundle) has its own explicit task rather than being
  bundled into feature work.
- Write behavior stays **Read-only analysis + preview-first `Manifest only`**
  by default; explicit package-manager execution is opt-in per action
  (T054).
- v1.0 targets public npm-family registries only; no credentials, no
  telemetry.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on
  incomplete tasks)
- **[Story]**: User story label (US1/US2/US3). Setup, Foundational, and
  Polish phases have no story label.
- All descriptions include repository-relative paths.

## Path Conventions

- **Plugin code**: `src/main/kotlin/com/github/buyoung/dependencyninja/`
- **Plugin resources**: `src/main/resources/`
- **Tests**: `src/test/kotlin/com/github/buyoung/dependencyninja/`
- **Docs and specs**: `docs/`, `specs/002-dependency-intelligence-plugin/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and baseline scaffolding reviewed before
any feature work starts.

- [X] T001 Verify Gradle/IntelliJ Platform build compiles on current `pluginSinceBuild` baseline by running a clean build against `build.gradle.kts` and confirming no drift from `gradle.properties`
- [X] T002 Create the v1.0 feature package skeleton with empty placeholder Kotlin files under `src/main/kotlin/com/github/buyoung/dependencyninja/features/inspection/`, `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/{domain,application,infrastructure,presentation}/`, and `src/main/kotlin/com/github/buyoung/dependencyninja/features/settings/{application,presentation,domain}/`
- [X] T003 [P] Add message bundle keys for v1.0 user-visible strings (status labels, stale/verification-unavailable reasons, bulk soft-cap warning, telemetry-off notice, JSON-PSI-unavailable disabled reason) in `src/main/resources/messages/DependencyNinjaBundle.properties`
- [X] T004 [P] Ensure `docs/dependency-ninja-design.md` and `README.md` are consistent with v1.0 scope (npm family, public registries only, all three surfaces, no telemetry); update mismatches only
- [X] T005 [P] Freeze the v1.0 canonical status vocabulary (`up-to-date`, `outdated`, `blocked`, `risky`, `ignored`, `stale`, `verification-unavailable`) in `src/main/kotlin/com/github/buyoung/dependencyninja/core/shared/domain/DependencyModels.kt` header comment so parser/policy/presentation code shares one source of truth

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure shared by every user story. **No user story
work may start until this phase completes.**

- [X] T006 Remove the legacy `unknown` status and any references in `src/main/kotlin/com/github/buyoung/dependencyninja/core/shared/domain/DependencyModels.kt` and its usages; v1.0 uses `verification-unavailable` as the single "no usable metadata" state (FR-005)
- [X] T007 Add `FreshnessState { FRESH, STALE, UNAVAILABLE }` plus a `CacheFreshnessPolicy` value object (TTL = 24 h) in `src/main/kotlin/com/github/buyoung/dependencyninja/core/shared/domain/Freshness.kt`
- [X] T008 [P] Add a `ReleaseAgePolicySource { PACKAGE_MANAGER, PLUGIN_SETTINGS }` enum and a `ReleaseAgeRule(sourceSelector, pluginDefaultDays = 7, exclusions)` data class in `src/main/kotlin/com/github/buyoung/dependencyninja/core/shared/domain/ReleaseAgeRule.kt`
- [X] T009 [P] Add a `RegistryResponseCache` with pluggable clock and 24 h TTL at `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/cache/RegistryResponseCache.kt`
- [X] T010 [P] Add an `OsvAdvisoryClient` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/advisory/OsvAdvisoryClient.kt` that wraps `SimpleHttpClient` and returns `AdvisoryRecord` instances with their own `FreshnessState`
- [X] T011 [P] Non-blocking PSI read helper: add `PsiReadOps.kt` in `src/main/kotlin/com/github/buyoung/dependencyninja/core/shared/infrastructure/PsiReadOps.kt` exposing a `<T> nonBlockingRead(project, computable): T` wrapper over `ReadAction.nonBlocking`
- [X] T012 [P] Background execution helper: add `BackgroundExecution.kt` in `src/main/kotlin/com/github/buyoung/dependencyninja/core/shared/infrastructure/BackgroundExecution.kt` exposing the app-pool scheduler and an EDT re-dispatch utility
- [X] T013 Extend `DependencyNinjaProjectService` in `src/main/kotlin/com/github/buyoung/dependencyninja/services/DependencyNinjaProjectService.kt` to publish an immutable `ProjectSnapshot` (declarations + recommendations + freshness) via a read-only accessor; consumers must not mutate it
- [X] T014 Register v1.0 extension points in `src/main/resources/META-INF/plugin.xml`: `projectService`, `postStartupActivity`, `toolWindow`, `annotator`, `localInspection`, and `projectConfigurable` (values filled as later tasks add their classes)
- [X] T015 Gate the plugin on JSON PSI availability in `src/main/kotlin/com/github/buyoung/dependencyninja/startup/DependencyNinjaProjectActivity.kt`: if JSON PSI is unavailable for target manifests, publish a disabled snapshot with a clear reason (FR-011, SC-004) and skip wiring any scan trigger
- [ ] T016 [P] Domain unit test for the `CacheFreshnessPolicy` 24 h TTL logic at `src/test/kotlin/com/github/buyoung/dependencyninja/core/shared/domain/CacheFreshnessPolicyTest.kt`

**Checkpoint**: Foundation ready — US1/US2/US3 implementation may proceed.

---

## Phase 3: User Story 1 — Detect Outdated Dependencies Early (Priority: P1)

**Story Goal**: Open a project with supported manifest files and see outdated
dependencies, with recommended versions, surfaced through **all three**
required presentation channels: inline hints, the Dependency Review tool
window, and an inspection/quick-action.

**Independent Test**: Open a fixture project containing npm-family manifests
with a mix of current and outdated dependencies; confirm (a) the inline
annotator renders hints beside outdated declarations, (b) the Dependency
Review tool window lists the same items with current/recommended versions,
and (c) invoking the inspection on any outdated declaration surfaces the
same recommendation.

### Implementation — Discovery & Scanning

- [ ] T017 [P] [US1] Narrow `ManifestDependencyParser` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/dependencyDiscovery/infrastructure/ManifestDependencyParser.kt` to the v1.0 npm-family set (`package.json`, `pnpm-workspace.yaml`, Yarn workspace fields, Bun workspace fields) using JSON PSI only — no regex fallback
- [X] T018 [P] [US1] Introduce a `ManifestScope` aggregate and extend `ManifestTarget` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/dependencyDiscovery/domain/ManifestTarget.kt` with the fields listed in `data-model.md` (manifestKind, ecosystem, isWorkspaceRoot, registryContext)
- [X] T019 [US1] Update `DependencyDiscoveryUseCase` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/dependencyDiscovery/application/DependencyDiscoveryUseCase.kt` to run discovery via `PsiReadOps.nonBlockingRead` and emit `DependencyDeclaration` + `WorkspaceReference` records per the data model (depends on T017, T018)
- [X] T020 [US1] Add a debounced manifest-save listener in `src/main/kotlin/com/github/buyoung/dependencyninja/startup/DependencyNinjaProjectActivity.kt` that triggers auto-rescan (FR-001, 500 ms debounce) and reuses the cache; manual trigger is exposed via the tool window (depends on T013, T019)

### Implementation — Minimal Resolution Wiring for v1.0

- [X] T021 [P] [US1] Wire `NpmHttpVersionSource` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/http/NpmHttpVersionSource.kt` through the v1.0 cache (`RegistryResponseCache`) and mark cached responses with the correct `FreshnessState` (fresh/stale/unavailable)
- [X] T022 [P] [US1] In `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/registry/RegistryAdapters.kt`, ensure only npm-family adapters are registered for v1.0; other ecosystems (Go, Maven, PyPi) remain in-tree but must not be selectable from the resolver registry
- [X] T023 [US1] Extend `DependencyUpdateResolver` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/application/DependencyUpdateResolver.kt` to produce `RecommendationRecord` values with status + freshness + reason codes, running on the app pool via `BackgroundExecution` (depends on T021, T022)
- [X] T024 [US1] Publish resolved recommendations into `DependencyNinjaProjectService`'s immutable snapshot (depends on T013, T023)

### Implementation — Three Required Presentation Channels

- [X] T025 [P] [US1] Update `DependencyOutdatedAnnotator` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/editorHighlight/presentation/DependencyOutdatedAnnotator.kt` to render inline hints for `outdated`, `stale`, `verification-unavailable`, and `risky` statuses using bundle strings; member projections render as review-only
- [X] T026 [P] [US1] Flesh out the Dependency Review tool window in `src/main/kotlin/com/github/buyoung/dependencyninja/features/dependencyToolwindow/presentation/DependencyToolWindowFactory.kt` to render the snapshot (name, source manifest, current version, recommended version, status, freshness, reason), group shared workspace/catalog references, and expose a Rescan button
- [X] T027 [P] [US1] Create `DependencyRecommendationInspection` (a `LocalInspectionTool` subclass) and a matching `UpdateDependencyQuickFix` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/inspection/presentation/DependencyRecommendationInspection.kt` that reads the snapshot via the project service and surfaces the same recommendation (without performing writes yet — writes land in US3)
- [X] T028 [US1] Register the inspection and quick-action entries in `src/main/resources/META-INF/plugin.xml`, and add their user-visible strings to `src/main/resources/messages/DependencyNinjaBundle.properties`
- [X] T029 [US1] Add presentation-channel toggles (inline hints / tool window / inspection on-off) to a transient state holder in `src/main/kotlin/com/github/buyoung/dependencyninja/features/settings/application/PolicyProfileService.kt` and honor them at each surface entry point (settings UI lands in US2)
- [ ] T030 [US1] Integration test at `src/test/kotlin/com/github/buyoung/dependencyninja/features/dependencyDiscovery/DiscoveryIntegrationTest.kt` that opens a fixture project with outdated dependencies and asserts the project service snapshot contains the expected `RecommendationRecord` values

**Checkpoint**: US1 complete — users can see outdated dependencies in all
three required surfaces without any update action yet.

---

## Phase 4: User Story 2 — Trust Recommended Versions (Priority: P2)

**Story Goal**: Recommendations reflect stability rules, release-age rules
(with user-selectable source: package-manager default or plugin-settings),
ignore rules, workspace/catalog behavior, and OSV risk signals. Users can
see *why* a version was recommended, deferred, or flagged.

**Independent Test**: On a fixture project with prerelease versions, shared
workspace/catalog references, ignored packages, and packages matching a
seeded OSV advisory, verify that each recommendation's `reasonCodes`
explain the outcome and that flipping the release-age source between
`package-manager` and `plugin-settings` changes the eligible candidate set
as expected.

### Implementation — Policy Profile & Reason Codes

- [X] T031 [P] [US2] Define `PolicyProfile` data class per `data-model.md` (including `releaseAgePolicySource`, `minimumReleaseAgePluginDefaultDays = 7`, `bulkApplySoftCap = 25`, `presentationToggles`, `coexistenceMode`) in `src/main/kotlin/com/github/buyoung/dependencyninja/features/settings/domain/PolicyProfile.kt`
- [X] T032 [P] [US2] Add `ReasonCode` sealed enum (e.g., `STABILITY_BLOCKED`, `RELEASE_AGE_BLOCKED`, `IGNORED`, `SHARED_REFERENCE_TAKES_PRECEDENCE`, `ADVISORY_FLAGGED`, `STALE_METADATA`, `VERIFICATION_UNAVAILABLE`) in `src/main/kotlin/com/github/buyoung/dependencyninja/core/shared/domain/ReasonCode.kt` and plumb it into `RecommendationRecord`
- [X] T033 [US2] Implement `PolicyProfileService` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/settings/application/PolicyProfileService.kt` as a `PersistentStateComponent` so application-level and project-level profiles are persisted and refreshable (depends on T031)
- [X] T034 [P] [US2] Implement a `PackageManagerReleaseAgeReader` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/packageManager/PackageManagerReleaseAgeReader.kt` that reads `minimumReleaseAge` (or equivalent) from npm-family tool configuration when available and returns `null` otherwise
- [X] T035 [US2] Extend `DependencyUpdateResolver` to apply the `ReleaseAgeRule` with the configured source selector: `PACKAGE_MANAGER` consults `PackageManagerReleaseAgeReader`, falling back to `pluginDefaultDays` (7) when the reader returns `null`; `PLUGIN_SETTINGS` always uses `pluginDefaultDays` (depends on T023, T031, T034)
- [X] T036 [P] [US2] Apply stability-channel filtering (stable/prerelease allowance) and ignored-packages filtering inside `DependencyUpdateResolver`, populating `ReasonCode` on each `RecommendationRecord`
- [X] T037 [US2] Implement workspace/catalog resolution inside `DependencyUpdateResolver` so a shared declaration produces a single `WorkspaceReference`-backed recommendation and member projections render as read-only (FR-009) (depends on T018, T035)

### Implementation — Risk Signals & Presentation Reason Plumbing

- [X] T038 [P] [US2] Populate `AdvisoryRecord` lookups via `OsvAdvisoryClient` inside the resolver so matching advisories flag the recommendation as `risky` and attach an `ADVISORY_FLAGGED` reason code (depends on T010, T023)
- [X] T039 [US2] Surface `reasonCodes` in the Dependency Review tool window row detail view (T026) so users can read *why* a recommendation is blocked/deferred/flagged without leaving the IDE
- [X] T040 [US2] Surface a short reason string in the inline annotator tooltip (T025) and in the inspection description (T027) using `DependencyNinjaBundle`
- [X] T041 [P] [US2] Stale/verification-unavailable rendering: confirm all three surfaces honor the `FreshnessState` and never fabricate a recommended version when state is `unavailable` (contract: `contracts/presentation-surfaces.md` cross-surface consistency)

### Implementation — Settings UI

- [X] T042 [US2] Create `DependencyNinjaConfigurable` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/settings/presentation/DependencyNinjaConfigurable.kt` (project-scoped), exposing: presentation channel toggles, allowed stability channels, ignored packages, release-age policy source selector (`package-manager`/`plugin-settings`), plugin-side release-age default (7 days), coexistence mode (coexist/replace), and bulk soft cap (default 25)
- [X] T043 [US2] Register `DependencyNinjaConfigurable` in `src/main/resources/META-INF/plugin.xml` and add its user-visible strings to `src/main/resources/messages/DependencyNinjaBundle.properties`
- [ ] T044 [US2] Unit test for policy evaluation at `src/test/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/DependencyUpdateResolverPolicyTest.kt`: verifies stability filtering, release-age source selector behavior (PM default, PM-missing fallback to plugin 7 d, PLUGIN_SETTINGS path), ignored list, and advisory risk tagging
- [ ] T045 [US2] Unit test for workspace/catalog resolution at `src/test/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/WorkspaceReferenceResolutionTest.kt`: asserts exactly one editable `WorkspaceReference`-backed recommendation per shared declaration and read-only member projections

**Checkpoint**: US2 complete — recommendations are trustworthy, reasoned,
and configurable.

---

## Phase 5: User Story 3 — Apply Updates Safely (Priority: P3)

**Story Goal**: Preview-first single and bulk updates that default to
`Manifest only`, enforce the 25-item bulk soft cap, warn on risky
conditions (uncommitted VCS changes, stale/verification-unavailable
metadata, post-apply re-parse failure), roll back on validation failure,
and route edits to the shared declaration when one governs the target.

**Independent Test**: From the Dependency Review tool window, (a) preview
and apply a single update, (b) preview and apply a 3-item bulk, (c)
attempt a 30-item bulk and confirm the soft-cap warning fires, (d) corrupt
a preview to force re-parse failure and confirm rollback, and (e) attempt
an update against a member-only projection of a shared workspace reference
and confirm the edit is routed to the shared declaration.

### Implementation — Preview, Apply, Rollback

- [X] T046 [P] [US3] Define `UpdatePreviewItem` data class per `data-model.md` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/domain/UpdatePreviewItem.kt` (including `validationState` machine: `draft → previewed → approved → applied → validated` plus `failed`/`rolled-back`)
- [X] T047 [P] [US3] Define `WarningFlag` enum (`UNCOMMITTED_VCS_CHANGES`, `STALE_METADATA`, `VERIFICATION_UNAVAILABLE_METADATA`, `REPARSE_FAILED_AFTER_APPLY`) in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/domain/WarningFlag.kt` (FR-008)
- [X] T048 [US3] Implement `UpdatePreviewService` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/application/UpdatePreviewService.kt` that converts one or more `RecommendationRecord`s into `UpdatePreviewItem`s, routing shared-workspace-governed updates to the shared declaration (depends on T046, T047)
- [X] T049 [US3] Implement `ManifestEditor` in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/infrastructure/ManifestEditor.kt` that performs the `Manifest only` write under a `WriteCommandAction`, snapshots prior file content, re-parses via JSON PSI, and raises `REPARSE_FAILED_AFTER_APPLY` on failure (depends on T011, T048)
- [X] T050 [US3] Implement rollback in `ManifestEditor`: on any post-apply failure (or user-requested undo) restore the snapshotted content atomically under a single `WriteCommandAction` and set `validationState = rolled-back`
- [X] T051 [US3] VCS dirty-state check: in `UpdatePreviewService`, detect uncommitted changes on the target file via `ChangeListManager` and raise `UNCOMMITTED_VCS_CHANGES` before transitioning to `approved` (FR-008 a)
- [X] T052 [US3] Freshness guard: in `UpdatePreviewService`, raise `STALE_METADATA` / `VERIFICATION_UNAVAILABLE_METADATA` warnings when the source recommendation's freshness is not `fresh` (FR-008 b)

### Implementation — Bulk Session & Package-Manager Execution

- [X] T053 [US3] Extend `UpdatePreviewService` with bulk-session support: accept a selection, enforce the `bulkApplySoftCap` from `PolicyProfile` (default 25) as a **warning** requiring explicit acknowledgement before any item transitions to `approved` — **not** a hard block (FR-006, data-model.md, contracts/update-workflow.md)
- [X] T054 [P] [US3] Add an explicit `PACKAGE_MANAGER_EXECUTION` path in `UpdatePreviewItem.executionMode` that shows the exact intended command before running and is only reachable when the user explicitly opts in per action (FR-007, contracts/update-workflow.md); wire a stub executor in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/infrastructure/PackageManagerExecutor.kt` that returns "not executed" unless explicitly confirmed

### Implementation — Presentation Wiring for Apply

- [X] T055 [US3] Wire a Preview / Apply button flow into the Dependency Review tool window (T026) via `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/presentation/UpdateWorkflowPanel.kt` that invokes `UpdatePreviewService`, renders the preview (target file, from text, to text, warning flags), and only enables Apply when all required acknowledgements are collected
- [X] T056 [US3] Update `UpdateDependencyQuickFix` (T027) to delegate to `UpdatePreviewService` for single-item apply instead of emitting a no-op
- [X] T057 [P] [US3] Bulk-session reporting view in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/presentation/BulkResultPanel.kt`: after a bulk apply completes, render a results summary in the tool window identifying applied/failed/rolled-back/skipped items (contracts/update-workflow.md bulk reporting)

### Tests — Correctness-Critical Paths

- [ ] T058 [P] [US3] Integration test for single-update apply + rollback at `src/test/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/ManifestEditorTest.kt`: covers successful apply, re-parse-failure rollback, and uncommitted-VCS warning surfacing
- [ ] T059 [P] [US3] Unit test for bulk soft cap at `src/test/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/BulkSoftCapTest.kt`: asserts a 30-item selection raises a warning and requires acknowledgement, a 25-item selection does not, and neither size is hard-blocked
- [ ] T060 [US3] Integration test for shared-workspace routing at `src/test/kotlin/com/github/buyoung/dependencyninja/features/updateWorkflow/WorkspaceApplyRoutingTest.kt`: asserts that applying an update to a member projection rewrites the shared declaration file and leaves member manifests untouched

**Checkpoint**: US3 complete — v1.0 feature set is implementation-ready for
release validation.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: v1.0 release hardening. These tasks apply across all three
user stories and must complete before the single v1.0 release ships.

- [X] T061 [P] Zero-telemetry audit across `src/main/kotlin/com/github/buyoung/dependencyninja/`: confirm no analytics/crash-reporting endpoints, no HTTP calls outside registry/advisory adapters, and no third-party telemetry SDKs. Record the audit result in `specs/002-dependency-intelligence-plugin/checklists/requirements.md`
- [X] T062 [P] i18n audit: confirm all user-visible strings added during US1–US3 route through `DependencyNinjaBundle`; migrate any literals into `src/main/resources/messages/DependencyNinjaBundle.properties`
- [X] T063 [P] Threading audit across `src/main/kotlin/com/github/buyoung/dependencyninja/`: confirm every PSI read uses `PsiReadOps.nonBlockingRead`, every HTTP call runs on the app pool via `BackgroundExecution`, every UI update dispatches back to EDT, and the manifest-save auto-rescan debounce coalesces bursts. Add missing wraps where detected
- [X] T064 [P] Cache behavior verification in `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/cache/RegistryResponseCache.kt`: confirm the 24 h TTL is enforced and `stale` is raised both on TTL expiry AND on refresh failure (contracts/presentation-surfaces.md cross-surface consistency, research.md)
- [ ] T065 Run the full `specs/002-dependency-intelligence-plugin/quickstart.md` scenario sweep (Scenarios 1–12) against at least two JetBrains IDE variants with JSON PSI and one variant without JSON PSI (SC-004). Record pass/fail per scenario in `specs/002-dependency-intelligence-plugin/checklists/requirements.md`
- [X] T066 [P] Update `README.md` and `docs/dependency-ninja-design.md` to reference v1.0 scope and remove any stale "MVP" or "initial release" wording that may have crept in during implementation
- [X] T067 [P] Mark non-npm-family adapters as out-of-scope: add `@Deprecated("out of v1.0 scope")` to Go/Maven/PyPi version source and package-manager adapter classes under `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/http/` and `src/main/kotlin/com/github/buyoung/dependencyninja/features/updateResolution/infrastructure/packageManager/` (FR-011 scope)
- [X] T068 Final constitution compliance review against `.specify/memory/constitution.md`: verify principles I–V (platform dependency, pipeline layers, non-blocking execution, safe updates/secret handling, roadmap scope) are satisfied by the shipped code. Record the review result in `specs/002-dependency-intelligence-plugin/checklists/requirements.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phases 3–5)**: Each depends on Phase 2. Per v1.0 clarification
  they all ship together, but they can be built in parallel after Phase 2
- **Polish (Phase 6)**: Depends on Phases 3, 4, and 5

### Key Intra-Phase Dependencies

- **Phase 2**: T006 before T007 (status cleanup before freshness model);
  T013 is used by every later phase; T014/T015 precede any surface wiring
- **Phase 3 (US1)**: T019 depends on T017+T018; T020 depends on T013+T019;
  T023 depends on T021+T022; T024 depends on T013+T023; T030 depends on T019+T024
- **Phase 4 (US2)**: T033 depends on T031; T035 depends on T023+T031+T034;
  T037 depends on T018+T035; T039/T040 depend on T032
- **Phase 5 (US3)**: T048 depends on T046+T047; T049 depends on T011+T048;
  T050 depends on T049; T053 depends on T031+T048; T055 depends on T026+T048;
  T056 depends on T027+T048

### Parallel Opportunities

- Phase 1: T003, T004, T005 in parallel
- Phase 2: T008, T009, T010, T011, T012, T016 in parallel; then T013 → T014/T015
- Phase 3 (US1): T017/T018 in parallel → T019; T021/T022 in parallel → T023;
  T025/T026/T027 in parallel once T024 publishes the snapshot
- Phase 4 (US2): T031/T032 in parallel → T033; T034 parallel with T031;
  T036/T038/T041 parallel after T035; T044/T045 parallel with each other
- Phase 5 (US3): T046/T047 in parallel → T048; T054 parallel with T051/T052;
  T058/T059 parallel with each other
- Phase 6: T061, T062, T063, T064, T066, T067 all in parallel

---

## Parallel Example: Phase 3 (US1) after T019 and T023 land

```text
Task: T025 [P] [US1] Update DependencyOutdatedAnnotator in
      src/main/kotlin/com/github/buyoung/dependencyninja/features/editorHighlight/presentation/DependencyOutdatedAnnotator.kt
Task: T026 [P] [US1] Flesh out Dependency Review tool window in
      src/main/kotlin/com/github/buyoung/dependencyninja/features/dependencyToolwindow/presentation/DependencyToolWindowFactory.kt
Task: T027 [P] [US1] Create DependencyRecommendationInspection in
      src/main/kotlin/com/github/buyoung/dependencyninja/features/inspection/presentation/DependencyRecommendationInspection.kt
```

---

## Implementation Strategy

### v1.0 single-release (authoritative)

Per the spec's v1.0 clarification, US1 + US2 + US3 ship as **one** release:

1. Complete Phase 1 (Setup)
2. Complete Phase 2 (Foundational) — blocks everything
3. Complete Phases 3, 4, 5 in priority order (or in parallel if staffed)
4. Complete Phase 6 (Polish)
5. **Ship v1.0**

### Internal checkpoint use (non-release)

The story-by-story structure still supports internal checkpoints for
testing and demos:

- After Phase 3: US1 can be exercised internally (detection only; no
  safe writes). Do **not** release — US2 reasons and US3 safe-apply are
  part of the release promise.
- After Phase 4: US1 + US2 can be exercised internally (reasoned,
  trusted recommendations with settings UI). Do **not** release.
- After Phase 5: the full v1.0 feature set is implementation-ready;
  proceed to Phase 6 and release validation.

### Parallel Team Strategy

With multiple developers, after Phase 2 completes:

- Developer A: Phase 3 (US1 discovery + 3 surfaces)
- Developer B: Phase 4 (US2 policy + settings)
- Developer C: Phase 5 (US3 update workflow)

Integrate continuously against `DependencyNinjaProjectService`'s snapshot
API (T013) to keep parallel streams coherent.

---

## Notes

- [P] = different files, no dependencies on incomplete tasks
- [Story] label maps tasks to US1/US2/US3 for traceability; Setup,
  Foundational, and Polish phases have no story label
- Non-npm-family resolver/package-manager adapters remain in-tree for
  future roadmap reuse but are out of v1.0 scope (T022, T067)
- v1.0 has **zero telemetry** — T061 is a release-gating audit
- When a task touches `plugin.xml` or `DependencyNinjaBundle.properties`,
  update both together to keep the plugin loadable
