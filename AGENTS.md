# AGENTS.md

## 1. Overview
Dependency Ninja is an IntelliJ Platform plugin that discovers dependencies from project manifests and highlights outdated versions across NPM, PyPI, Maven, and Go ecosystems. The codebase separates shared domain/resolution logic from IDE integration points such as startup activity, editor annotations, and tool window UI.

## 2. Folder Structure
- `src/main/kotlin/com/github/buyoung/dependencyninja`: main plugin source set.
  - `core/shared/application`: cross-feature interfaces (`HttpClient`, `VersionSource`, `EcosystemAdapter`).
  - `core/shared/domain`: shared enums and data models for dependency coordinates, updates, and snapshots; includes `VersionComparator`.
  - `core/shared/infrastructure`: shared infrastructure implementations such as `SimpleHttpClient` with in-memory TTL caching.
  - `features/dependencyDiscovery`: manifest discovery and parsing pipeline.
    - `application`: project file traversal and manifest target collection.
    - `domain`: manifest target model.
    - `infrastructure`: regex-based parsers for `package.json`, `requirements.txt`, `pyproject.toml`, `pom.xml`, `build.gradle(.kts)`, and `go.mod`.
  - `features/updateResolution`: latest-version lookup and source selection.
    - `application`: `DependencyUpdateResolver` that maps ecosystem+channel to a `VersionSource`.
    - `infrastructure/http`: HTTP registry-backed `VersionSource` implementations per ecosystem.
    - `infrastructure/packageManager`: package-manager channel `VersionSource` stubs.
    - `infrastructure/registry`: ecosystem-specific `EcosystemAdapter` implementations.
  - `features/editorHighlight/presentation`: PSI annotator that marks outdated dependencies in editor files.
  - `features/dependencyToolwindow/presentation`: ToolWindow factory/panel and grouped dependency tree rendering.
  - `services`: project-level orchestration service (`DependencyNinjaProjectService`) and snapshot state.
  - `startup`: project startup activity that triggers background refresh.
- `src/main/resources`:
  - `META-INF/plugin.xml`: IntelliJ extension registrations (ToolWindow, startup activity, annotator).
  - `messages/*.properties`: i18n message bundles.
- `src/test/kotlin`: unit tests for resolver and parser/domain behavior.
- `.github/workflows`: CI workflows.
- `gradle`, `gradlew`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Gradle build/tooling configuration.

## 3. Core Behaviors & Patterns
- Layered feature structure is consistent: `application` orchestrates use cases, `infrastructure` handles IO/parsing/network, and `presentation` integrates with IntelliJ UI/PSI APIs.
- Main data flow is: `DependencyNinjaProjectActivity` -> `DependencyNinjaProjectService.refreshInBackground()` -> discovery (`DependencyDiscoveryUseCase`) -> resolution (`DependencyUpdateResolver`) -> immutable snapshot replacement -> UI refresh (`ToolWindow` listeners + daemon restart).
- Error handling is fail-soft: parsing/network operations use `runCatching` and return `emptyList()`/`null` on failure; unresolved versions are mapped to `DependencyStatus.UNKNOWN` instead of throwing.
- Concurrency model is explicit: background execution via `AppExecutorUtil`, UI-bound updates via `ApplicationManager.invokeLater`, state storage via `AtomicReference<DependencySnapshot>`, and listener management via `CopyOnWriteArrayList`.
- Module communication relies on small interfaces (`VersionSource`, `HttpClient`) with constructor-based composition in the project service rather than DI framework wiring.
- Version resolution pattern is repeated across ecosystems: URL encode coordinate -> HTTP GET -> regex extract version field -> normalize using `VersionComparator`.
- Discovery/parser pattern preserves editor highlighting accuracy by capturing `TextRange` for declared versions and carrying ranges into annotation rendering.

## 4. Conventions
- Naming follows Kotlin defaults: `PascalCase` for types, `camelCase` for functions/properties, and uppercase enum constants (`HTTP_REGISTRY`, `UP_TO_DATE`).
- Type/function naming is intent-revealing with role suffixes such as `*UseCase`, `*Service`, `*VersionSource`, `*Adapter`, `*Factory`, `*Activity`, and `*Annotator`.
- Domain models are immutable `data class` values; snapshot updates replace the whole value atomically rather than mutating nested collections.
- Parsing and extraction logic favors small private helper functions and local `Regex` variables scoped to each manifest format.
- User-visible strings are resolved through `DependencyNinjaBundle.message(...)` and stored in `messages/*.properties`.
- Comments are sparse and concise; prefer self-explanatory code and clear type names over verbose inline comments.

## 5. Working Agreements
- Respond in the user's preferred language; if unspecified, infer from repository context. Keep software/backend/infra terms in English and never translate fenced code blocks.
- Before editing, inspect related usages and neighboring feature layers (`discovery`, `updateResolution`, `presentation`, `services`) to keep behavior consistent.
- Prefer the simplest implementation that satisfies the request; avoid extra abstractions, new architecture, or broad refactors unless explicitly requested.
- If requirements are ambiguous, ask the user for clarification before implementing.
- Keep changes minimal and focused; preserve existing public APIs and behavior unless the user asks to change them.
- Do not add tests or lint/format tasks unless explicitly requested.
- After code changes, run type-check: `./gradlew compileKotlin`.
- Keep new functions/modules single-purpose and colocated near related code.
- Avoid new external dependencies unless clearly necessary; when added, explain why.
