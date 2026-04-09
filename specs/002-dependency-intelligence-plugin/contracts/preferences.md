# Contract: Preferences

## Purpose

Define the user-configurable inputs that influence recommendation and display
behavior.

## Application-Level Preferences

| Preference | Contract |
|------------|----------|
| Allowed stability channels | Controls which release channels are eligible for recommendation |
| Network timeout and concurrency | Shapes how aggressively metadata is refreshed (TTL fixed at 24 h; timeouts are tunable) |
| Global ignore list | Suppresses known-noise packages across projects |
| Release-age policy source | `package-manager` (default) reads the release-age value from the native package manager when supported; `plugin-settings` uses the plugin's own configured value |
| Plugin-side release-age default | Used when source is `plugin-settings` or when `package-manager` source is unavailable. Default: **7 days** |
| Bulk update soft cap | Warn (but do not block) when a bulk session exceeds the configured threshold. Default: **25** |
| Telemetry | Fixed **off** in v1.0 — no user-adjustable setting; no data leaves the IDE |

## Project-Level Preferences

| Preference | Contract |
|------------|----------|
| Presentation channel toggles | Enables or disables supported surfaces without changing their shared status vocabulary |
| Release-age override | Refines candidate eligibility for the current project |
| Project ignore list | Suppresses project-specific packages from recommendation noise |
| Coexistence mode | Controls whether built-in IDE inspection behavior coexists (default) or is replaced where supported |
| Update strategy | Defaults to `Manifest only` and requires explicit user choice to switch to command-driven execution |

## Safety Rules

- Preferences may change recommendation outcomes, but they may not bypass the
  requirement to keep secrets out of logs and UI.
- Preferences may not make member projections editable when a shared
  declaration governs the update target.
