# Feature Specification: Dependency Intelligence Plugin

**Feature Branch**: `002-dependency-intelligence-plugin`  
**Created**: 2026-04-08  
**Status**: Draft  
**Input**: User description: "Source design document: `docs/dependency-ninja-design.md`"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Detect Outdated Dependencies Early (Priority: P1)

As a developer working in a JetBrains IDE, I want the plugin to surface outdated
dependencies from supported project manifests while I work, so I can see stale
packages without leaving the IDE.

**Why this priority**: Early visibility is the product's primary value and the
minimum outcome needed for v1.0.

**Independent Test**: Open a project with supported manifest files containing a
mix of current and outdated dependencies, then verify that the plugin shows the
outdated items with recommended versions in the editor and in a consolidated
view.

**Acceptance Scenarios**:

1. **Given** a project with supported manifest files and outdated dependencies,
   **When** the project is opened or manually rescanned, **Then** the plugin
   lists the outdated dependencies, their current versions, and their
   recommended versions.
2. **Given** a project with no supported optional language add-ons installed,
   **When** the project contains baseline supported manifest files, **Then** the
   plugin still provides dependency detection and recommendation results for
   the v1.0 scope.

---

### User Story 2 - Trust Recommended Versions (Priority: P2)

As a developer deciding whether to upgrade a dependency, I want recommendations
to reflect stability, release-age, workspace rules, and known risk signals, so
I can trust that the suggested version is safe to adopt.

**Why this priority**: Recommendations that ignore project policy or risk
signals create noise and reduce confidence in the product.

**Independent Test**: Use a project that includes prerelease versions, shared
workspace references, ignored packages, and packages with known advisories, then
verify that the plugin explains why a version is recommended, deferred, or
flagged.

**Acceptance Scenarios**:

1. **Given** a dependency with a newer prerelease that falls outside the
   project's allowed stability rules, **When** recommendations are generated,
   **Then** the prerelease is not shown as the default recommendation.
2. **Given** a dependency whose newer versions are blocked by release-age rules
   or known advisories, **When** the developer reviews its details, **Then** the
   plugin shows the reason the version was deferred or flagged.

---

### User Story 3 - Apply Updates Safely (Priority: P3)

As a developer ready to update dependencies, I want a preview-first workflow
with safe defaults, so I can apply single or bulk updates without unintended
changes to my project.

**Why this priority**: Update execution matters only after discovery and trust,
but it must still feel safe enough for everyday use.

**Independent Test**: Select one recommended update and one bulk update set,
preview the changes, confirm them, and verify that only the approved manifest
changes are applied while risky situations are surfaced before commit.

**Acceptance Scenarios**:

1. **Given** a recommended dependency update, **When** the developer previews a
   manifest-only change, **Then** the plugin shows the affected files before any
   edit is committed.
2. **Given** a project with uncommitted changes or a validation failure after an
   update attempt, **When** the developer confirms an update, **Then** the
   plugin warns about the risk and prevents or rolls back invalid changes.

---

### Edge Cases

- What happens when registry or advisory data is temporarily unavailable during
  project open or manual rescan?
- If live metadata lookup fails, cached recommendation data must remain visible
  only with an explicit stale indication, and dependencies without prior data
  must move to a verification-unavailable state.
- What happens when the same dependency is referenced through a shared workspace
  or catalog entry and individual member manifests?
- How does the plugin behave when a project contains unsupported ecosystems or
  later-roadmap sources that are intentionally out of scope for the initial
  release?
- How does the plugin behave when the host IDE does not provide JSON PSI
  support for the expected manifest files?

## Clarifications

### Session 2026-04-08

- Q: 공유 `workspace` / `catalog` 의존성은 어디를 실제 수정 대상으로 볼 것인가? → A: 공유 선언만 수정하고 개별 멤버 선언은 읽기 전용으로 본다.
- Q: 레지스트리 또는 보안 메타데이터 조회가 실패하면 기본 동작은 무엇인가? → A: 캐시가 있으면 `오래된 정보`로 표시하고, 캐시가 없으면 `확인 불가` 상태로 표시한다.
- Q: 초기 출시에서 어떤 표시 채널을 필수로 포함할 것인가? → A: `Inline hints`, 전용 검토 화면, `Inspection/Quick Action` 세 채널을 모두 필수로 포함한다.

### Session 2026-04-08 (Remediation)

- Q: 프라이빗 레지스트리 지원을 v1.0에 포함할 것인가? → A: 제외. v1.0은 공개 레지스트리만 대상으로 한다.
- Q: JSON PSI를 사용할 수 없는 IDE에서는 어떻게 동작해야 하는가? → A: 플러그인 기능을 비활성화한다(우회 파싱 경로를 두지 않음).
- Q: v1.0에 포함해야 하는 표시 채널은? → A: Inline hints, Dependency Review tool window, Inspection/Quick Action 세 채널 모두 v1.0(User Story 1 범위)에 포함한다.
- Q: IDE 기본 인스펙션과의 기본 공존 방식은? → A: Coexist(기본), Replace(고급 옵션). 프로젝트 설정에서 전환 가능.
- Q: v1.0 범위는 User Story 1/2/3을 어떻게 포함하는가? → A: US1+US2+US3 전부 v1.0에 포함하며 단계적 릴리스 중단점을 두지 않는다.
- Q: 재스캔(rescan)은 언제 트리거되는가? → A: 매니페스트 파일 저장 시 자동 재스캔(디바운스, 캐시 재사용) + 수동 트리거 지원.
- Q: 기본 release-age 규칙과 출처는? → A: 패키지 매니저가 지원하면 해당 설정을 따르고, 지원하지 않거나 사용자가 플러그인 설정을 신뢰하도록 선택한 경우 플러그인 설정을 적용한다. 플러그인 설정의 기본값은 7일. 패키지 매니저 설정과 플러그인 설정 중 어느 쪽을 신뢰할지는 사용자 선택이며 기본값은 `package manager`.
- Q: v1.0에서 플러그인은 텔레메트리/사용 분석/크래시 리포트를 수집하는가? → A: 수집하지 않는다. v1.0은 로컬 전용이며 외부 분석 엔드포인트로 데이터를 전송하지 않는다.
- Q: 번들(bulk) 업데이트 세션의 최대 대상 수 제한은? → A: 소프트 캡 25개. 초과 시 경고하되 사용자가 진행을 선택할 수 있다.

## Constitution Alignment *(mandatory)*

### Manifest Coverage & IDE Scope

- **Affected manifests**: `package.json` and related workspace or catalog
  declarations (`pnpm-workspace.yaml`, Yarn workspace fields, Bun workspace
  fields) needed for the v1.0 scope
- **Ecosystem scope**: v1.0 is limited to the npm family (`npm`,
  `pnpm`, Yarn, Bun) against **public package registries only**; private
  registry support and later roadmap items such as Deno remain out of scope
- **IDE compatibility**: Requires JSON PSI support for target manifest files.
  In host IDEs without JSON PSI support, the plugin disables itself and
  surfaces a clear reason; no alternative parsing path is provided
- **Touched presentation channels**: v1.0 scope includes inline
  hints, the Dependency Review tool window, and inspection/quick-action
  surfaces as required delivery channels — all three are required in v1.0

### Policy, Network & Update Impact

- **Policy signals**: Declared version intent, allowed stability channels,
  release-age rules, ignore rules, workspace or catalog behavior, and public
  vulnerability advisories
- **Network/caching impact**: The feature consults public package and advisory
  metadata only, reuses cached results to reduce repeated lookups, marks
  cached results as `stale` when the last successful lookup is older than
  **24 hours** OR when a live refresh has failed, and marks dependencies as
  verification-unavailable when no prior successful data exists
- **Write behavior**: Read-only analysis by default; preview-first manifest
  edits for normal updates; direct package-manager execution only when the user
  explicitly chooses it
- **IDE inspection coexistence**: Coexist mode is the default; Replace mode is
  an advanced per-project option that users can opt into from settings
- **Telemetry & privacy**: v1.0 MUST NOT collect or transmit telemetry,
  usage analytics, or crash reports to any external endpoint. Network
  traffic is strictly limited to public package registry and public
  advisory metadata lookups required for recommendation generation

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST discover declared dependencies from supported
  v1.0 manifest and workspace files when a project is opened,
  when a supported manifest file is saved (debounced auto-rescan that reuses
  cached registry/advisory results), or when the user triggers a manual
  rescan.
- **FR-002**: The system MUST show dependency status, current version, and
  recommended version for each supported dependency in all three required
  presentation channels: inline hints, the Dependency Review tool window, and
  inspection/quick-action surfaces.
- **FR-003**: The system MUST apply project policy rules when deciding which
  version to recommend, including stability preferences, release-age rules,
  ignore rules, and shared workspace or catalog behavior; the final policy
  decision MUST incorporate workspace/catalog resolution so that member
  references never produce independent recommendations. For release-age
  specifically, the system MUST source the rule per a user-selectable
  **policy source**: `package-manager` (default) reads the value from the
  native package manager configuration when supported (e.g.,
  `minimumReleaseAge` in supported tools); `plugin-settings` uses the
  plugin's own configured value. When the selected source is unavailable
  (e.g., package manager lacks the setting), the system MUST fall back to
  the plugin setting. The plugin setting's default value is **7 days**.
- **FR-004**: The system MUST flag dependencies affected by known public
  vulnerability advisories and show that risk information alongside upgrade
  guidance.
- **FR-005**: Users MUST be able to review the reason a dependency is marked
  as outdated, blocked, ignored, risky, stale, or verification-unavailable
  before taking action. `verification-unavailable` is the single canonical
  state for "no usable metadata" (no separate `unknown` state).
- **FR-006**: The system MUST support both single-dependency and bulk review
  workflows in the Dependency Review tool window for the supported
  v1.0 scope. Bulk apply MUST enforce a **soft cap of 25
  dependencies** per session: above the cap the system warns the user and
  requires explicit confirmation to proceed, but does not hard-block.
- **FR-007**: The system MUST default to preview-first `Manifest only` updates
  and require explicit user intent before any command-driven package-manager
  execution. FR-007 governs update actions; FR-006 governs review/selection.
- **FR-008**: The system MUST warn users about risky update conditions,
  specifically: (a) uncommitted VCS changes in the affected files, (b) stale
  or verification-unavailable metadata for the target dependency, and (c)
  manifest re-parse failure after apply (triggering automatic rollback).
- **FR-009**: The system MUST avoid duplicate reporting when a shared workspace
  or catalog declaration governs multiple member references, and MUST treat the
  shared declaration as the single editable target while member references
  remain read-only.
- **FR-010**: The system MUST allow users to control, at application and
  project scope: (a) on/off for each presentation channel (inline hints,
  tool window, inspection), (b) recommendation policy preferences (stability
  channel, release-age value, ignore list), (c) the **release-age policy
  source** selector (`package-manager` default / `plugin-settings`), and
  (d) IDE inspection coexistence mode (Coexist default / Replace advanced).
- **FR-011**: The system MUST require JSON PSI support for target manifest
  files. When JSON PSI is unavailable in the host IDE, the plugin MUST
  disable its features and surface a clear reason; no regex or alternative
  parsing fallback is permitted.

### Key Entities *(include if feature involves data)*

- **Dependency Declaration**: A dependency requested by the project, including
  its name, declared version intent, source manifest, and relationship to any
  shared workspace or catalog declaration.
- **Recommendation Record**: The reviewable result for a dependency, including
  current version, suggested version, freshness status, and the reasons a
  version was recommended, deferred, blocked, flagged, or left unverifiable.
- **Policy Profile**: The set of user or project preferences that shape
  recommendations, such as stability preferences, release-age rules
  (including the `package-manager` vs `plugin-settings` source selector and
  the plugin-side default of 7 days), ignore rules, coexistence behavior,
  and update mode.
- **Advisory Record**: A public risk notice associated with one or more
  dependency versions, used to inform whether an update should be highlighted or
  deprioritized.
- **Workspace Reference**: A shared package entry that can influence multiple
  member projects and must be treated as the single editable source of truth
  for updates, while member-level references remain review-only projections.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In acceptance testing on representative projects within the
  v1.0 scope, users can identify outdated or risky dependencies
  within 30 seconds of project open or manual rescan.
- **SC-002**: In product evaluation scenarios that include stability rules,
  release-age rules, and known advisories, at least 90% of default
  recommendations match the expected policy outcome without manual correction.
- **SC-003**: Users can preview and approve a single dependency update in under
  1 minute and a curated bulk update session of **up to 25 dependencies
  (the soft cap)** in under 5 minutes.
- **SC-004**: In validation across at least three supported JetBrains IDE
  variants with JSON PSI available, the baseline v1.0 scan works
  for supported manifest files; in an IDE variant without JSON PSI, the
  plugin reports itself as disabled with a clear reason instead of failing
  silently.

## Assumptions

- This specification targets the 1.0 production release described in the
  design document and does not include later roadmap items such as Deno
  support, source URL scanning, or private registry support.
- v1.0 is the sole release baseline for this specification. It delivers
  **all three user stories (US1 Detect, US2 Trust, US3 Apply) together as a
  single release**; priority labels (P1/P2/P3) indicate implementation
  sequencing only and MUST NOT be treated as a staged release cut-off or
  as an intermediate release milestone.
- Users work in supported JetBrains IDEs on projects that already contain
  readable npm-family manifest or workspace files.
- Projects use **public package registries only** in v1.0. Private registries
  and authenticated access are explicitly out of scope.
- The host IDE provides JSON PSI for target manifests. If it does not, the
  plugin is disabled.
- The product coexists with built-in IDE dependency inspections by default
  rather than replacing them unless the user deliberately changes that setting.
