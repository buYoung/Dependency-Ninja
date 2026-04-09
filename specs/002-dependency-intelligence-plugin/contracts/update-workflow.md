# Contract: Update Workflow

## Purpose

Define the user-visible workflow for previewing and applying updates.

## Preconditions

- The dependency belongs to the supported v1.0 scope (npm family, public registries).
- A recommendation record exists.
- If a shared workspace or catalog declaration governs the dependency, that
  shared declaration is the only editable target.
- The user has explicitly initiated an update action.

## Manifest-Only Update Contract

| Step | Requirement |
|------|-------------|
| Preview generation | The plugin must show the target file, package name, current text, and proposed new text before applying changes |
| Warning handling | The plugin must surface uncommitted changes, invalid follow-up validation, missing credentials, or metadata freshness issues before commit |
| Apply | Only the approved target manifest is edited |
| Validation | The plugin must re-parse the edited manifest set after apply |
| Failure | If validation fails, the plugin must roll back the attempted edit and show the failure state |

## Explicit Package-Manager Execution Contract

| Step | Requirement |
|------|-------------|
| Availability | Only available after explicit user choice of that update mode |
| Preview | The plugin must show the exact intended command or effect before execution |
| Scope | The chosen package manager action must map to the same dependency target selected in the preview |
| Failure | Command failure must not be silently converted into a successful update state |

## Bulk Update Contract

| Step | Requirement |
|------|-------------|
| Selection | Users choose which recommended items to include |
| Soft cap | A bulk selection exceeding the configured soft cap (default 25) must surface an explicit warning and require user acknowledgement before any item transitions to `approved`. The cap is not a hard block. |
| Shared declarations | Shared workspace/catalog declarations appear once as the editable item |
| Commit | The plugin applies only the approved subset |
| Reporting | The result must identify which items were applied, failed, rolled back, or skipped |
