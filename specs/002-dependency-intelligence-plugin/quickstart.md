# Quickstart: Dependency Intelligence Plugin (v1.0)

## Goal

Validate the v1.0 feature slice end to end inside a supported JetBrains IDE.

## Prerequisites

- A JetBrains IDE compatible with the repository's current plugin configuration
- A sample project using one of the supported v1.0 manifest setups:
  npm, pnpm, Yarn, or Bun
- At least one outdated dependency
- A second sample with shared workspace or catalog declarations

## Scenario 1: Baseline Discovery (All Three Channels, ≤30s)

1. Open the sample project and **start a stopwatch**.
2. Wait for the startup scan or trigger a manual refresh.
3. Confirm that outdated dependencies appear in inline hints.
4. Open the **Dependency Review tool window** and confirm the same findings
   are present there.
5. Invoke an inspection or quick action for one outdated dependency and confirm
   it references the same recommendation outcome.
6. **Record the elapsed time from project open to first visible finding.**
   The target is ≤30 seconds on representative projects (SC-001); record any
   miss in the quickstart results.

## Scenario 2: Policy-Aware Recommendation

1. Use a sample that includes prerelease versions, ignored packages, and
   release-age-sensitive packages.
2. Refresh results.
3. Confirm that blocked or deferred recommendations explain why the newer
   version is not the default recommendation.
4. Confirm that risky dependencies include advisory context in all applicable
   surfaces.

## Scenario 3: Stale and Verification-Unavailable Fallback

1. Run one successful scan with network access.
2. Disable registry or advisory access.
3. Refresh again.
4. Confirm previously known dependencies stay visible with an explicit stale
   indication.
5. Confirm dependencies without prior metadata move to a verification-unavailable
   state instead of showing fabricated fresh results.

## Scenario 4: Shared Workspace or Catalog Updates

1. Open the sample monorepo with a shared workspace or catalog declaration.
2. Review an outdated dependency governed by that shared declaration.
3. Confirm the shared declaration appears once as the editable target.
4. Confirm member references remain visible but are not individually editable.

## Scenario 5: Safe Update Workflow

1. Select one recommended update.
2. Open the preview.
3. Confirm the preview shows the target file, current text, and proposed text.
4. Apply the update in `Manifest only` mode.
5. Confirm the manifest is re-validated after the edit.
6. Repeat with a simulated failure condition and confirm the attempted edit is
   rolled back or blocked.
7. With uncommitted VCS changes present on the affected file, attempt an
   update and confirm the risky-condition warning appears (FR-008 a).
8. With stale or verification-unavailable metadata, attempt an update and
   confirm the corresponding warning appears (FR-008 b).

## Scenario 6: JSON PSI Unavailable (Disabled State)

1. Use an IDE variant or configuration where JSON PSI is not available for
   the target manifest files.
2. Open the sample project.
3. Confirm the plugin reports itself as disabled with a clear reason and
   does **not** attempt regex/alternative parsing (FR-011, SC-004).

## Scenario 7: Inspection Coexist / Replace Mode

1. With the default **Coexist** mode, confirm the plugin's inspection results
   appear alongside the built-in IDE dependency inspection (no suppression).
2. Switch to **Replace** mode in settings.
3. Confirm the plugin takes over and the built-in inspection is suppressed
   for the affected dependencies.

## Scenario 8: Presentation Channel Toggles

1. In settings, disable the inline hints channel and confirm annotations
   disappear while the Dependency Review tool window and inspection remain.
2. Re-enable inline hints, then disable the Dependency Review tool window
   channel and confirm the opposite.
3. Repeat for the inspection channel.

## Scenario 9: Auto-Rescan on Manifest Save

1. With the plugin enabled and a scan already complete, edit a dependency
   version in `package.json` and save the file.
2. Confirm that inline hints and the Dependency Review tool window update
   to reflect the new declared version within a debounced window
   (~500 ms) without a manual refresh.
3. Confirm that repeated rapid saves coalesce (no more than one rescan
   per debounce window).

## Scenario 10: Bulk Update Soft Cap

1. In the Dependency Review tool window, select more than 25 recommended
   updates in a single bulk session.
2. Confirm that the workflow surfaces a warning referencing the configured
   soft cap and requires explicit acknowledgement before any item
   transitions to `approved`.
3. Acknowledge the warning and confirm the bulk apply proceeds (the cap
   is a warning, not a hard block).
4. Select ≤25 updates and confirm no warning appears.

## Scenario 11: Release-Age Policy Source

1. With the policy source set to `package-manager` (default) and a
   package manager config that defines a release-age value (e.g., newer
   npm tooling with `minimumReleaseAge`), confirm recommendations respect
   the package manager's value.
2. Remove or disable the package manager config and confirm recommendations
   fall back to the plugin default of **7 days**.
3. Switch the policy source to `plugin-settings`, change the plugin-side
   release-age value, and confirm recommendations now use the plugin value
   regardless of the package manager config.

## Scenario 12: No Telemetry

1. With the plugin running a full scan, bulk apply, and rollback, capture
   outbound network traffic from the IDE process.
2. Confirm the only outbound destinations are public package registries
   and public advisory endpoints used for recommendation generation.
3. Confirm there is no traffic to any analytics, telemetry, or crash
   reporting endpoint owned by this plugin.
