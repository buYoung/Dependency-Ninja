# Dependency-Ninja

![Build](https://github.com/buYoung/Dependency-Ninja/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

## Scope

Dependency Ninja v1.0 focuses on the npm family only: `npm`, `pnpm`, Yarn,
and Bun manifests driven by `package.json` and shared workspace declarations.
The plugin surfaces findings through inline hints, the Dependency Review tool
window, and inspections/quick actions.

Recommendations are policy-aware. They respect ignored packages, prerelease
preferences, release-age rules, shared workspace targets, and OSV advisory
signals. Update flows default to preview-first `Manifest only` edits.

v1.0 intentionally excludes private registries, telemetry, analytics, and
crash reporting. Out-of-scope adapters remain in the repository for later
roadmap work but are not active in the shipped resolver path.

<!-- Plugin description -->
Dependency Ninja is an IntelliJ Platform plugin that scans project manifests and
surfaces outdated or risky dependencies directly in the editor and tool window.

The current product direction prioritizes the `package.json` family (`npm`,
`pnpm`, Yarn, Bun), workspace and catalog-aware recommendations, release-age
policy signals, and OSV-backed vulnerability data. Update flows default to safe
`Manifest only` edits so the IDE stays responsive and changes remain undoable.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml)
file which will be extracted by the [Gradle](/build.gradle.kts) during the
build process.

To keep everything working, do not remove `<!-- ... -->` sections.
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Dependency-Ninja"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/buYoung/Dependency-Ninja/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the IntelliJ Platform Plugin template foundation.
