# Research: Dependency Intelligence Plugin (v1.0)

## Decision: v1.0 bundles US1 + US2 + US3 as a single release

**Rationale**: Spec clarification fixed v1.0 as the sole release baseline;
US1/US2/US3 priority labels indicate implementation sequencing only, not a
staged cut-off. Shipping discovery without trust or apply would break the
product promise (trust-first recommendations).

**Alternatives considered**:
- Ship US1 as an early preview release and defer US2/US3.
- Split US3 (safe updates) into a v1.1 follow-up.

## Decision: v1.0 scope stays with the npm family only

**Rationale**: The constitution and design document prioritize the
`package.json` family, workspace/catalog semantics, release-age policy, and
OSV-backed risk data. Keeping v1.0 to npm, pnpm, Yarn, and Bun against
**public registries only** avoids rework and scopes acceptance testing.

**Alternatives considered**:
- Preserve the broader Python/Maven/Go parsers that already exist in-tree.
- Pull Deno roadmap scope forward into v1.0.
- Include private-registry auth support in v1.0.

## Decision: Evolve the current codebase incrementally

**Rationale**: The repository already has working discovery, resolution,
service, tool window, and annotator slices under
`com.github.buyoung.dependencyninja`. Planning around those roots preserves
momentum and honors the constitution's architecture-churn restraint.

**Alternatives considered**:
- Rename packages to match the design document namespace immediately.
- Rebuild the codebase into a new `modules/*` hierarchy before feature work.

## Decision: Markdown contracts capture plugin-surface expectations

**Rationale**: The feature does not expose a public HTTP or CLI API. The
most useful contract artifacts are user-surface and workflow contracts that
define what each IDE surface must show, when actions are allowed, and how
failure or staleness is represented.

**Alternatives considered**:
- Skip contracts entirely because the plugin is an in-IDE product.
- Create pseudo-API schemas that do not match the actual surface.

## Decision: OSV is the sole advisory source for v1.0

**Rationale**: The design document already establishes OSV as the single
provider. It supports public access, cross-ecosystem coverage, and a stable
schema while avoiding credential and aggregation work.

**Alternatives considered**:
- Combine OSV with additional advisory providers in v1.0.
- Delay vulnerability guidance to a later release.

## Decision: Cache freshness TTL = 24 hours; stale vs verification-unavailable distinction

**Rationale**: Clarification fixed 24 hours as the daily-freshness threshold
aligned with registry refresh cadence. Cached results remain usable only
with an explicit `stale` marker when the last success is older than 24 h
OR when a live refresh has just failed. Dependencies with no successful
prior observation move to `verification-unavailable` — the single canonical
"no usable metadata" state; there is **no separate `unknown` state** in
v1.0.

**Alternatives considered**:
- 1-hour aggressive refresh (higher network load, unnecessary for daily use).
- 7-day weekly refresh (too stale for advisory signals).
- Fail the entire scan when metadata lookup fails.
- Keep a parallel `unknown` state alongside `verification-unavailable`.

## Decision: Auto-rescan on manifest save + manual trigger

**Rationale**: Matches JetBrains-idiomatic inspection refresh behavior,
keeps inline hints consistent after the user edits `package.json`, and reuses
the cache so network pressure stays low. Rescan is debounced (500 ms) to
coalesce bursts from large saves or formatter runs.

**Alternatives considered**:
- Manual-only rescan (inline hints would drift after any edit).
- PSI-level realtime rescan on keystroke (excessive work, risks UI jitter).
- Rescan only on project open (misses mid-session manifest edits).

## Decision: Release-age policy source is user-selectable; default `package-manager`, plugin default value 7 days

**Rationale**: Clarification set two layered choices:
1. **Source selector** (`package-manager` default, `plugin-settings`
   advanced): when the native package manager supports the setting (e.g.,
   `minimumReleaseAge` in newer npm tooling), the plugin reads it so one
   place of truth governs recommendations and CLI installs. Users who
   prefer plugin-scoped policy can flip to `plugin-settings`.
2. **Plugin default value**: 7 days, matching the Renovate/Dependabot
   community norm — filters same-day package-takeover vectors without
   delaying legitimate updates.

When the selected source is unavailable (e.g., the package manager lacks
the setting), the plugin falls back to its own 7-day default.

**Alternatives considered**:
- Plugin-settings-only (ignores package-manager config → divergence risk).
- Package-manager-only (breaks when the tool lacks the setting).
- 0-day default (no release-age protection at all).
- 3-day default (faster adoption, weaker supply-chain protection).
- 14-day default (too conservative; delays legitimate updates).

## Decision: Bulk update sessions have a soft cap of 25 dependencies

**Rationale**: Most real upgrade sessions touch a handful of related
packages; 25 keeps SC-003's 5-minute bulk target defensible while still
allowing power users to confirm a larger batch after an explicit warning.
Soft cap (not hard block) avoids rejecting legitimate power-user workflows.

**Alternatives considered**:
- Hard limit 10 (blocks legitimate larger sessions).
- Soft cap 50 (erodes SC-003 timing budget).
- No limit (unreviewable previews on large projects; SC-003 not testable).

## Decision: v1.0 ships with zero telemetry

**Rationale**: A developer tool that reads source manifests raises
privacy-sensitive concerns. No-telemetry is the safest default, removes
Marketplace privacy-disclosure obligations, and matches user expectations
for a locally-run IDE plugin. Usage analytics can be added later behind
explicit opt-in if product data demands it.

**Alternatives considered**:
- Anonymous opt-in telemetry (still requires Marketplace disclosure).
- Anonymous opt-out telemetry (hostile default for a dev tool).
- Crash reports only (adds crash-reporter infrastructure for little v1.0 value).

## Decision: Shared workspace/catalog declarations are the only editable update targets

**Rationale**: Clarification fixed shared declarations as the single
editable source of truth; member references remain review-only projections.
This avoids double edits and keeps preview/rollback logic deterministic.

**Alternatives considered**:
- Allow both shared declarations and member references to be edited.
- Show member references as editable and silently rewrite the shared declaration.

## Decision: All three presentation channels are required in v1.0

**Rationale**: Inline hints, Dependency Review tool window, and
inspection/quick-action align discovery, triage, and action-taking with the
stated product flow. Shipping with only one or two channels would leave a
visible gap in the user flow.

**Alternatives considered**:
- Ship only the Dependency Review tool window and defer editor surfaces.
- Ship inline hints + tool window, leaving quick actions for later.

## Decision: JSON PSI availability gates the plugin; no alternative parser

**Rationale**: Clarification fixed "no regex or alternative parser" — if
the host IDE lacks JSON PSI for target manifest files, the plugin disables
itself cleanly with a clear reason. This keeps parsing consistent and
avoids maintaining two code paths.

**Alternatives considered**:
- Ship a regex-based fallback parser for IDEs without JSON PSI.
- Require a richer plugin dependency (e.g., the JavaScript plugin) to
  guarantee PSI availability (would violate the constitution's single
  required-dependency rule).

## Decision: Planning follows the repository's current compatibility baseline

**Rationale**: The design document mentions a broader target build window,
but the live repository configuration sets `pluginSinceBuild=252`. Planning
against the live build metadata avoids hidden compatibility work.

**Alternatives considered**:
- Plan immediately for the wider design-document compatibility window.
- Ignore repository build metadata during planning.

## Decision: IDE inspection coexistence defaults to Coexist; Replace is advanced opt-in

**Rationale**: Clarification set Coexist as default (both the plugin and
IDE built-in inspection show their results). Replace mode is an advanced
per-project option for users who want a single source of truth and are
willing to suppress built-in findings.

**Alternatives considered**:
- Replace mode as default (overrides user expectations silently).
- Remove Replace mode entirely (loses flexibility for power users).
