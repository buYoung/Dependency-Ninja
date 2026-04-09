# Contract: Presentation Surfaces

## Purpose

Define the minimum information and actions that each required v1.0 surface
must expose.

## Surface 1: Inline Hints

| Contract Item | Requirement |
|---------------|-------------|
| Trigger | Show beside supported dependency declarations after scan results are available |
| Minimum content | Current version, recommended version when available, and a compact status marker |
| Status handling | Must distinguish fresh, stale, and verification-unavailable states |
| Editability | Member projections of shared workspace/catalog declarations are review-only |
| Action handoff | Must provide a path to richer review or quick action for deeper details |

## Surface 2: Dedicated Dependency Review View

| Contract Item | Requirement |
|---------------|-------------|
| Trigger | Available after startup scan, debounced auto-rescan on manifest save, and manual refresh |
| Minimum content | One row or grouped item per recommendation record with package name, source manifest, current version, recommended version, status, freshness, and reason summary |
| Grouping | Must group or otherwise make shared workspace/catalog declarations recognizable as the single editable target |
| Bulk review | Must support curated bulk selection before any update is applied, and must warn when the selection exceeds the configured bulk soft cap (default 25) |
| Failure display | Must show stale results explicitly and show verification-unavailable items without pretending they are current |

## Surface 3: Inspection / Quick Action

| Contract Item | Requirement |
|---------------|-------------|
| Trigger | Available from outdated or risky dependency findings in supported manifests |
| Minimum content | Short explanation of the finding and the proposed update action |
| Safety gates | Must respect preview-first behavior and warning conditions before applying edits |
| Shared declarations | Must route editable actions to the shared declaration when one governs the member reference |
| Fallback | If no valid update action exists, the surface must remain informative rather than destructive |

## Cross-Surface Consistency Rules

- All surfaces must use the same canonical statuses and reason vocabulary.
- A stale recommendation on one surface must remain stale on all others until a
  successful refresh replaces it.
- A dependency marked verification-unavailable must never display a fabricated
  recommended version.
