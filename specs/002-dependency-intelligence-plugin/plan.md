# Implementation Plan: Dependency Intelligence Plugin

**Branch**: `002-dependency-intelligence-plugin` | **Date**: 2026-04-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-dependency-intelligence-plugin/spec.md`

## Summary

Deliver the v1.0 Dependency Intelligence slice as a single bundled release
covering all three user stories (US1 Detect, US2 Trust, US3 Apply). The
pipeline discovers npm-family manifest and workspace declarations, resolves
recommendations against public registries and OSV advisories, and surfaces
results in three required presentation channels (inline hints, Dependency
Review tool window, inspection/quick-action). Updates default to preview-
first `Manifest only` edits with a soft cap of 25 for bulk sessions. Cached
metadata older than 24 hours or refreshed into failure is marked `stale`;
dependencies without any successful prior observation surface as
`verification-unavailable` (the single canonical "no usable metadata"
state — `unknown` is removed). Release-age policy follows a user-selectable
source: `package-manager` (default) reads from the native package manager
config when supported, otherwise the plugin's own setting (default 7 days).
v1.0 ships with **no telemetry**, no private-registry support, and disables
itself cleanly when JSON PSI is unavailable.

## Technical Context

**Language/Version**: Kotlin on JVM 21
**Primary Dependencies**: IntelliJ Platform SDK (`com.intellij.modules.platform`
only required), Kotlin stdlib, Gradle version catalog, IDE-bundled JSON PSI
for manifest parsing, `java.net.http.HttpClient` via `SimpleHttpClient` for
registry/OSV lookups, no runtime DI framework
**Storage**: In-memory per-project snapshot (`DependencyNinjaProjectService`);
IDE-managed cache directory for registry/advisory response cache keyed by
`(registryUrl, packageName)` with a **24-hour freshness TTL** (stale marker
applies when last success is older than TTL or refresh has failed)
**Testing**: JUnit 5 + IntelliJ Platform test framework
(`BasePlatformTestCase`) for PSI/service integration; plain Kotlin unit tests
for domain (version comparator, policy evaluation, recommendation state
machine)
**Target Platform**: JetBrains IDEs matching the build range in
`gradle.properties` / `plugin.xml` (current repo baseline `pluginSinceBuild=252`)
**Project Type**: IntelliJ Platform plugin (single Gradle module)
**Performance Goals**: First findings visible ≤30 s after project open
(SC-001); single preview ≤1 min, curated bulk (≤25 deps) ≤5 min (SC-003);
zero UI-thread blocking — PSI reads via non-blocking read actions, network
and heavy resolution on application pool, results marshalled back to EDT
explicitly
**Constraints**: No telemetry (local-only, zero outbound analytics); no
private-registry support; plugin disables itself when JSON PSI unavailable;
v1.0 scope strictly npm-family public registries; manifest writes default to
`Manifest only`; all user-visible text through `DependencyNinjaBundle`
**Scale/Scope**: Up to ~500 dependencies per project in acceptance tests;
bulk apply soft-capped at 25 per session; recommendation cache per-project

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Layer boundaries**: All new code stays in
  `core/shared/{domain,application,infrastructure}`,
  `features/{dependencyDiscovery,updateResolution,dependencyToolwindow,editorHighlight,updateWorkflow,settings}/{domain,application,infrastructure,presentation}`,
  `services/`, and `startup/`. No presentation code leaks into domain;
  cross-stage orchestration stays in application services.
- [x] **Required platform dependency**: Only
  `com.intellij.modules.platform` is required. JSON handling uses the
  IDE-bundled JSON PSI that ships with the platform module; when absent,
  the plugin disables itself (FR-011) — no fallback parser, no new required
  plugin dependency.
- [x] **Pipeline stages identified**: manifest discovery
  (`features/dependencyDiscovery`) → policy hints
  (`core/shared/domain` models + `features/settings`) → version resolution
  (`features/updateResolution`) → policy/security evaluation
  (`features/updateResolution/application` combining registry + OSV) →
  immutable snapshot publication (`services/DependencyNinjaProjectService`) →
  presentation (`features/dependencyToolwindow`, `features/editorHighlight`,
  `features/inspection`).
- [x] **Threading model**: PSI reads wrapped in
  `ReadAction.nonBlocking { ... }`; registry and OSV HTTP calls run on
  `AppExecutorUtil.getAppExecutorService()`; tool window and annotator
  updates dispatch back via `invokeLater`/`ModalityState.defaultModalityState()`;
  auto-rescan on manifest save is debounced (500 ms) to coalesce bursts.
- [x] **Write behavior & secrets**: Default is read-only analysis.
  Updates default to preview-first `Manifest only`; package-manager
  execution requires explicit per-action user intent. v1.0 targets public
  registries only, so no credentials are collected, stored, or logged.
  No telemetry endpoint is configured. `DependencyNinjaBundle` owns all
  user-visible strings.
- [x] **Roadmap scope**: v1.0 matches
  `docs/dependency-ninja-design.md` — npm family, public registries, OSV
  advisories, release-age policy, workspace/catalog semantics. Deno,
  private registries, and source-URL scanning remain deferred.

**Result**: PASS — no principle violations, Complexity Tracking section
left empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-dependency-intelligence-plugin/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── presentation-surfaces.md
│   ├── preferences.md
│   └── update-workflow.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Generated by /speckit.tasks
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── kotlin/com/github/buyoung/dependencyninja/
│   │   ├── DependencyNinjaBundle.kt              # existing i18n entry
│   │   ├── core/shared/
│   │   │   ├── domain/                            # value objects: versions, ranges, statuses
│   │   │   ├── application/                       # abstractions: HttpClient, VersionSource, EcosystemAdapter
│   │   │   └── infrastructure/                    # SimpleHttpClient, cache store
│   │   ├── features/
│   │   │   ├── dependencyDiscovery/
│   │   │   │   ├── domain/ManifestTarget.kt
│   │   │   │   ├── application/DependencyDiscoveryUseCase.kt
│   │   │   │   └── infrastructure/ManifestDependencyParser.kt     # JSON PSI + yaml via platform
│   │   │   ├── updateResolution/
│   │   │   │   ├── domain/                        # policy profile, recommendation record, reason codes
│   │   │   │   ├── application/DependencyUpdateResolver.kt
│   │   │   │   └── infrastructure/
│   │   │   │       ├── http/                      # NpmHttpVersionSource, (Go/Maven/PyPi retained but out-of-scope for v1.0)
│   │   │   │       ├── packageManager/            # NpmPackageManagerVersionSource etc.
│   │   │   │       ├── registry/RegistryAdapters.kt
│   │   │   │       ├── advisory/OsvAdvisoryClient.kt              # NEW
│   │   │   │       └── cache/RegistryResponseCache.kt             # NEW (24h TTL)
│   │   │   ├── dependencyToolwindow/presentation/                 # Dependency Review tool window (existing scaffold)
│   │   │   ├── editorHighlight/presentation/                      # inline hints (existing annotator)
│   │   │   ├── inspection/                                        # NEW: LocalInspectionTool + QuickFix
│   │   │   ├── updateWorkflow/                                    # NEW: preview/apply/rollback
│   │   │   │   ├── domain/UpdatePreviewItem.kt
│   │   │   │   ├── application/UpdatePreviewService.kt
│   │   │   │   └── infrastructure/ManifestEditor.kt
│   │   │   └── settings/                                          # NEW: Policy Profile settings UI + persisted state
│   │   │       ├── application/PolicyProfileService.kt
│   │   │       └── presentation/DependencyNinjaConfigurable.kt
│   │   ├── services/DependencyNinjaProjectService.kt              # snapshot publication
│   │   └── startup/DependencyNinjaProjectActivity.kt              # startup scan + manifest save listener
│   └── resources/
│       ├── META-INF/
│       │   ├── plugin.xml                                          # extension points: toolWindow, annotator, inspection, projectService, projectConfigurable, postStartupActivity
│       │   └── pluginIcon.svg
│       └── messages/
│           └── DependencyNinjaBundle.properties
└── test/
    └── kotlin/com/github/buyoung/dependencyninja/
        ├── features/updateResolution/...
        ├── features/updateWorkflow/...
        ├── features/dependencyDiscovery/...
        └── integration/                                           # BasePlatformTestCase-based
```

**Structure Decision**: Evolve the existing
`core/shared` + `features/*` + `services` + `startup` tree. Three new
feature packages are introduced for v1.0: `features/inspection` (required
third presentation channel), `features/updateWorkflow` (preview/apply/
rollback per US3), and `features/settings` (policy profile persistence +
configurable UI). Existing non-npm `http/` and `packageManager/` adapters
under `features/updateResolution/infrastructure/` remain in the tree but
are **not wired into v1.0 plugin.xml extension points** — they are
out-of-scope stubs kept for future roadmap reuse. All user-visible strings
flow through `DependencyNinjaBundle`.

## Complexity Tracking

> No constitution violations. Section intentionally left empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
