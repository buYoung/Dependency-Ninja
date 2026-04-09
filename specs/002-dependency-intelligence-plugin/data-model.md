# Data Model: Dependency Intelligence Plugin (v1.0)

## Entity: Manifest Scope

Represents one manifest-bearing file set that contributes dependency
declarations to the scan.

| Field | Description |
|-------|-------------|
| `manifestPath` | Absolute or project-relative path to the manifest file |
| `manifestKind` | One of `package.json`, `pnpm-workspace.yaml`, Yarn config, or Bun config relevant to v1.0 |
| `ecosystem` | npm-family ecosystem classification |
| `moduleName` | Human-readable project or workspace member name |
| `isWorkspaceRoot` | Whether this manifest governs other member declarations |
| `registryContext` | Registry and auth lookup context derived from nearby config |

**Validation rules**
- `manifestPath` must uniquely identify one manifest scope.
- Workspace roots may own multiple member declarations but remain a single scope.

## Entity: Dependency Declaration

Represents one discovered dependency statement from a manifest or shared
workspace declaration.

| Field | Description |
|-------|-------------|
| `declarationId` | Stable identifier for the declaration |
| `packageName` | Canonical dependency name |
| `sourceManifestPath` | Path where the declaration was found |
| `moduleName` | Owning member or project name |
| `dependencyKind` | Production, development, peer, optional, override, or shared-catalog style declaration |
| `declaredVersionText` | Raw version or range text shown to the user |
| `normalizedCurrentVersion` | Best-effort normalized current version for comparison |
| `workspaceReferenceId` | Optional link to the shared declaration that governs this dependency |
| `isEditableTarget` | Whether this declaration is directly writable |

**Validation rules**
- Non-shared declarations are unique by `packageName + sourceManifestPath + dependencyKind`.
- If `workspaceReferenceId` is present, `isEditableTarget` must be `false`.
- A declaration without a parseable version remains valid but enters resolution
  with an unknown comparison baseline.

## Entity: Workspace Reference

Represents a shared workspace or catalog declaration that governs many member
references.

| Field | Description |
|-------|-------------|
| `workspaceReferenceId` | Stable shared-declaration identifier |
| `referenceType` | Workspace or catalog |
| `ownerManifestPath` | Path to the shared declaration file |
| `packageName` | Canonical dependency name |
| `declaredVersionText` | Shared version text |
| `affectedMemberCount` | Number of member declarations governed by this reference |
| `isEditableTarget` | Always `true` for the shared declaration itself |

**Validation rules**
- Unique by `referenceType + ownerManifestPath + packageName`.
- Member declarations may project this reference, but only the shared reference
  may be edited.

## Entity: Policy Profile

Represents the configuration that shapes recommendation behavior.

| Field | Description |
|-------|-------------|
| `profileScope` | Application or project |
| `allowedChannels` | Stable/prerelease allowance set |
| `releaseAgePolicySource` | `package-manager` (default) or `plugin-settings` — chooses which config the release-age value is read from |
| `minimumReleaseAgePluginDefaultDays` | Plugin-side release-age value used when source is `plugin-settings` or when `package-manager` source is unavailable. Defaults to **7 days**. |
| `minimumReleaseAgeExclusions` | Packages exempt from the age rule |
| `ignoredPackages` | Names or patterns excluded from recommendation noise |
| `coexistenceMode` | `coexist` (default) or `replace` relative to the built-in IDE dependency inspection |
| `updateStrategy` | `Manifest only` (default) or explicit package-manager execution |
| `presentationToggles` | On/off per presentation channel: inline hints, Dependency Review tool window, inspection/quick-action |
| `bulkApplySoftCap` | Warning threshold for bulk update sessions. Defaults to **25**; exceeding it warns the user but does not block. |

**Validation rules**
- Project-level overrides may refine application defaults but may not remove
  the v1.0 safety rule that update execution requires explicit intent.
- `updateStrategy` defaults to `Manifest only`.
- `releaseAgePolicySource` defaults to `package-manager`. When the selected
  source is unavailable, evaluation falls back to the plugin default
  (`minimumReleaseAgePluginDefaultDays`).
- `coexistenceMode` defaults to `coexist`.

## Entity: Registry Observation

Represents the latest known metadata snapshot from package registries.

| Field | Description |
|-------|-------------|
| `packageName` | Canonical dependency name |
| `registryUrl` | Registry source used for lookup |
| `availableVersions` | Ordered candidate versions returned by lookup |
| `fetchedAt` | Time of the last successful retrieval |
| `freshnessState` | `fresh`, `stale`, or `unavailable` |
| `authSource` | Public only in v1.0 (private registry access out of scope) |

**Validation rules**
- `freshnessState=fresh` requires `fetchedAt` within the last **24 hours**
  AND no failed refresh attempt since then.
- `freshnessState=stale` is valid when `fetchedAt` exists but is older than
  24 hours, OR when a refresh attempt failed since the last success.
- `freshnessState=unavailable` is used when no usable prior observation
  exists.

## Entity: Advisory Record

Represents one public vulnerability advisory relevant to a dependency.

| Field | Description |
|-------|-------------|
| `advisoryId` | Stable advisory identifier |
| `packageName` | Affected dependency |
| `affectedRange` | Version range impacted by the advisory |
| `summary` | Short user-facing risk summary |
| `severityLabel` | Normalized severity or risk bucket |
| `fixedVersions` | Known safe target versions when available |
| `fetchedAt` | Time of the last successful advisory retrieval |

**Validation rules**
- Advisory records are unique by `advisoryId`.
- Advisory records may be stale but must still show a freshness signal when used.

## Entity: Recommendation Record

Represents the reviewable decision presented to the user.

| Field | Description |
|-------|-------------|
| `recommendationId` | Stable record identifier |
| `declarationId` | Referenced dependency declaration |
| `workspaceReferenceId` | Optional shared declaration source |
| `currentVersion` | Current normalized version or unresolved text |
| `recommendedVersion` | Best candidate version, if one exists |
| `status` | `up-to-date`, `outdated`, `blocked`, `risky`, `ignored`, `stale`, or `verification-unavailable` (no separate `unknown` state in v1.0) |
| `reasonCodes` | Structured explanation of why the decision was made |
| `freshnessState` | Fresh, stale, or unavailable metadata context |
| `surfaceAvailability` | Which UI surfaces may show this record |

**Validation rules**
- `status=stale` requires cached metadata.
- `status=verification-unavailable` requires no usable prior successful
  observation.
- If `workspaceReferenceId` exists, update actions must target the shared
  declaration, not the member projection.

## Entity: Update Preview Item

Represents one pending update action the user can review before applying.

| Field | Description |
|-------|-------------|
| `previewId` | Stable preview identifier |
| `recommendationId` | Source recommendation |
| `targetManifestPath` | File that will be edited |
| `packageName` | Dependency to update |
| `fromVersionText` | Current manifest text |
| `toVersionText` | Proposed new manifest text |
| `executionMode` | `Manifest only` or explicit package-manager execution |
| `warningFlags` | Risk markers such as uncommitted changes or validation issues |
| `validationState` | Draft, previewed, approved, applied, validated, rolled-back, or failed |

**Validation rules**
- A member projection may not create its own preview item when a shared
  declaration governs the update.
- `executionMode` must be explicit for every preview item.
- Bulk sessions larger than the Policy Profile's `bulkApplySoftCap`
  (default 25) MUST raise a warning that the user must explicitly
  acknowledge before any preview item transitions to `approved`.

## Relationships

- One **Manifest Scope** contains many **Dependency Declarations**.
- One **Workspace Reference** may govern many **Dependency Declarations**.
- One **Policy Profile** shapes many **Recommendation Records**.
- One **Registry Observation** and zero or more **Advisory Records** influence
  one or more **Recommendation Records**.
- One **Recommendation Record** may produce zero or one **Update Preview Item**
  for the current user action.

## State Transitions

### Recommendation Record

`discovered` → `up-to-date`  
`discovered` → `outdated`  
`discovered` → `blocked`  
`discovered` → `risky`  
`discovered` → `ignored`  
`outdated|blocked|risky|up-to-date` → `stale` when cached data exceeds 24h
or a live refresh fails  
`discovered` → `verification-unavailable` when no usable prior metadata
exists  
`stale|verification-unavailable` → any fresh status after successful refresh

### Update Preview Item

`draft` → `previewed` → `approved` → `applied` → `validated`  
`approved` → `failed`  
`applied` → `rolled-back`
