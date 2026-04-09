# Dependency Ninja — JetBrains Plugin 설계 문서

- **Plugin ID**: `com.github.buyoung.dependencyninja`
- **배포**: JetBrains Marketplace 단독
- **타겟 빌드**: IntelliJ Platform `build 252+`
- **문서 버전**: v1.0 implementation baseline

---

## 1. 포지셔닝 및 스코프

기존 JetBrains IDE 인스펙션이 커버하지 못하는 영역을 보완하는 것이 1차 목표다. npm 계열은 WebStorm/Ultimate에서 일부 지원하지만 `packageManager` 필드 존중, pnpm catalog, `minimumReleaseAge`, 공개 레지스트리 기준의 CVE 연동 등은 방치되어 있으므로 이 영역을 메인 가치로 삼는다.

모든 JetBrains IDE에서 동작해야 하므로 플러그인은 `com.intellij.modules.platform`만 필수 의존으로 둔다. v1.0은 `package.json` 중심의 npm 계열 공개 레지스트리만 대상으로 하며, 텔레메트리 없이 로컬 상태와 공개 메타데이터만 사용한다. JSON PSI를 사용할 수 없는 IDE에서는 기능을 비활성화하고 우회 파서 경로를 두지 않는다.

**Go는 지원하지 않는다.** GoLand가 이미 module 업데이트 기능을 충분히 제공하므로 중복 가치가 없다.

---

## 2. 버전 로드맵

| 버전 | 범위 |
|---|---|
| **v1** | `package.json` 계열 전부(npm / pnpm / yarn classic / yarn berry / bun), workspace & catalog, CVE(OSV), 정책 엔진, 두 가지 업데이트 모드, presentation 3채널 |
| **v1.1** | Deno 지원 — `deno.json(c)`의 `imports` / `jsr:` / `npm:` 선언만. 소스 URL 스캔은 포함하지 않음 |
| **v1.2** | Deno 소스 URL 스캔 (PSI 기반, JS 플러그인 필수) |

---

## 3. 아키텍처 개요

전체는 단방향 파이프라인이다.

```
ManifestParser → PolicyHints 수집
      ↓
DeclaredDependency[]
      ↓
Registry (네트워크, 캐시 경유)
      ↓
PolicyEngine (필터 체인)
      ↓
ReportSnapshot (StateFlow)
      ↓
Presentation (Inlay / ToolWindow / Inspection)
```

각 단계는 IntelliJ Platform Service로 등록되며, 서비스 간 의존은 생성자 주입(`project.service<T>()`)으로 해결한다. 외부 DI 프레임워크는 사용하지 않는다.

### 3.1 스레딩 원칙

| 작업 | 실행 컨텍스트 |
|---|---|
| PSI/VFS 접근 | `ReadAction.nonBlocking().coalesceBy(key).expireWith(project).submit(AppExecutorUtil.getAppExecutorService())` |
| 네트워크 I/O | Kotlin coroutine + `Dispatchers.IO` |
| 장시간 작업 | `Task.Backgroundable` + `ProgressIndicator` |
| 에디터/UI 반영 | `invokeLater` + `ModalityState.nonModal()` |
| 파일 변경 연속 유입 | `coalesceBy` 키 + coroutine `debounce` 병행 |

IDE blocking이 발생할 여지를 완전히 차단하는 것이 설계의 핵심 제약이다.

---

## 4. 모듈 분해

패키지 구조는 다음과 같이 나눈다. 기능 모듈은 1차 depth에 펼치지 않고 `modules/<모듈이름>` 아래로 모으고, 1차 depth는 IntelliJ Platform 관례에 맞춘 횡단 계층(actions / extensions / listeners / model / services / settings / ui / utils)과 기능 버킷(`modules`)만 남긴다.

```
com.livteam.dependencyninja
├── actions        # IDE Action / Quick Fix 엔트리 포인트
├── extensions     # Extension Point 정의 및 등록 어댑터
├── listeners      # VFS / PSI / Project 리스너
├── model          # 공통 도메인 모델 (타 패키지 무의존)
├── modules        # 기능 모듈 컨테이너 (각 모듈은 model에만 의존)
│   ├── manifest   # 매니페스트 파싱/감지 (매니저별 하위 분리)
│   ├── registry   # 원격 레지스트리 클라이언트
│   ├── auth       # 프라이빗 레지스트리 인증
│   ├── policy     # 정책 필터 체인
│   ├── cache      # 버전/CVE 2단 캐시
│   ├── security   # CVE 제공자 (OSV)
│   └── apply      # 업데이트 적용 (Manifest only / CLI)
├── services       # Project / Application Service — 모듈 조합 및 StateFlow 보관
├── settings       # PersistentStateComponent
├── ui             # Inlay / ToolWindow / Inspection (presentation)
└── utils          # 공용 유틸 (Secret 래퍼, coroutine helper, 버전/문자열 유틸)
```

`modules/*` 하위 각 기능 모듈은 `model`에만 의존하고 서로 직접 참조하지 않는다. 모듈 간 연결은 `services` 계층이 오케스트레이션하며, IDE 플랫폼과의 접점(Action, Inspection, Listener, ExtensionPoint)은 각각 `actions` / `ui` / `listeners` / `extensions`로 격리한다.

### 4.1 `model`

공통 도메인 모델만 둔다. 이전 설계의 `core`에 해당한다.

- `DeclaredDependency`
- `ResolvedVersion`
- `VersionChannel { STABLE, BETA, ALPHA, NIGHTLY, DEV }`
- `UpdateCandidate`
- `PolicyDecision`
- `Ecosystem { NPM, JSR, DENO_LAND, BUN }`
- `ManifestPolicyHints`
- `VulnerabilityRecord`

이 패키지는 다른 패키지에 의존하지 않는다. 모든 하위 모듈은 이 타입에만 의존해 상호 결합을 끊는다.

### 4.2 `modules.manifest`

공통 인터페이스 `ManifestParser`, `ManifestWriter`, `PackageManagerDetector`만 상위에 두고, 구현체는 매니저별 하위 패키지로 완전히 분리한다.

| 패키지 | 담당 파일 |
|---|---|
| `modules.manifest.npm` | `package.json` (dependencies / devDependencies / peerDependencies / optionalDependencies, `overrides`) |
| `modules.manifest.pnpm` | `package.json` + `pnpm-workspace.yaml` (catalog, catalogs, `minimumReleaseAge`, `minimumReleaseAgeExclude`, `packageExtensions`) |
| `modules.manifest.yarn.classic` | `package.json` + `resolutions` |
| `modules.manifest.yarn.berry` | `package.json` + `.yarnrc.yml` (`npmScopes`, `npmRegistries`) |
| `modules.manifest.bun` | `package.json` + `bunfig.toml` (`install.registry`, scoped registry) |
| `modules.manifest.deno` *(v1.1)* | `deno.json(c)` (`imports`, `importMap`, `jsr:` / `npm:` specifier) |

각 구현체는 담당 파일만 알고, 공통 `DeclaredDependency`로만 출력한다. 매니저 고유 설정(예: `minimumReleaseAge`)은 파서가 반환하는 `ManifestPolicyHints`에 실어 `modules.policy`로 전달한다. **파서는 원격 호출을 절대 하지 않는다** (단위 테스트 용이성).

**PackageManagerDetector** 우선순위:
1. `package.json`의 `packageManager` 필드
2. Lockfile: `pnpm-lock.yaml` → `bun.lock(b)` → `yarn.lock` (`.yarnrc.yml` 존재 여부로 berry/classic 구분) → `package-lock.json`
3. `deno.json(c)` 존재
4. 기본값 npm

### 4.3 `modules.registry`

`Registry` 인터페이스는 `suspend fun fetchVersions(coordinate): RegistryResponse` 하나만 노출한다.

| 구현체 | 대상 |
|---|---|
| `modules.registry.npm` | npm registry 호환 (+ Verdaccio/Nexus/Artifactory). `.npmrc` / `.yarnrc.yml` / `bunfig.toml`의 scope별 registry & auth 해석 |
| `modules.registry.jsr` *(v1.1)* | JSR 메타데이터 |
| `modules.registry.denoland` *(v1.1)* | `deno.land/x`, `/std` 태그 |
| `modules.registry.esmsh` *(v1.2)* | esm.sh, skypack, unpkg → 원본 npm registry로 역매핑 |

공통으로 단일 `HttpClient` 서비스(`java.net.http.HttpClient`)를 주입받고, `RegistryHttpSupport`에서 ETag/`If-None-Match`, `Last-Modified`/`If-Modified-Since`, `Retry-After`, 지수 백오프, 동시성 제한(semaphore)을 처리한다.

### 4.4 `modules.auth`

`AuthProvider` 인터페이스와 체인 구조.

- `NpmrcAuthProvider`
- `YarnRcAuthProvider`
- `BunfigAuthProvider`
- `EnvVarAuthProvider`
- `IdePasswordSafeAuthProvider` (IDE `PasswordSafe`가 기본 저장소)

토큰은 로그/오류 메시지에 절대 노출되지 않도록 `utils`의 `Secret` 래퍼로 감싼다.

### 4.5 `modules.policy`

필터 체인 구조.

```
입력: DeclaredDependency
      + List<RemoteVersion>
      + ManifestPolicyHints
      + UserSettings
출력: PolicyDecision(recommended, alternatives, suppressedReasons)
```

기본 제공 필터:

| 필터 | 역할 |
|---|---|
| `SemverRangeFilter` | 선언된 range 내 최신 판정 |
| `ChannelFilter` | 사용자 채널 설정 + prerelease tag 해석 |
| `MinimumReleaseAgeFilter` | pnpm `minimumReleaseAge` + 사용자 글로벌 설정 |
| `MinimumReleaseAgeExcludeFilter` | `minimumReleaseAgeExclude` 적용 |
| `IgnoreListFilter` | 글로벌 / 프로젝트 / 패키지별 무시 목록 |
| `CveFilter` | CVE 있는 버전을 차단 또는 강등 |
| `AllowlistFilter` | 사내 환경용 |

필터는 순수 함수에 가깝게 유지한다. 외부 I/O는 `modules.registry` / `modules.security` 단계에서 완료된 상태로 주입받는다. **매니저별 고유 규칙은 새 필터를 추가하는 것만으로 확장 가능해야 한다.**

### 4.6 `modules.cache`

`VersionCache`와 `CveCache` 두 개로 분리.

| 항목 | 값 |
|---|---|
| 저장소 | 메모리 LRU + 디스크 (`PathManager.getSystemPath()/dependency-ninja/`) |
| 키 | `(ecosystem, coordinate, registryUrl)` |
| 디스크 포맷 | JSON Lines + 파일별 lock |
| 버전 메타 TTL | 기본 1시간 (설정 가능) |
| CVE TTL | **고정 24시간, 설정 불가** |
| 조건부 요청 | ETag / Last-Modified 저장 후 재사용 |

**CVE는 하루 1회 강제 캐시** 정책에 따라 별도 서비스로 둔다.
- 24시간 내 재조회 시 네트워크를 치지 않고 stale 캐시 반환
- 스케줄: 프로젝트 오픈 직후 1회 + `AppExecutorUtil.getAppScheduledExecutorService()`로 24시간 간격 실행
- 오프라인이면 다음 성공 시까지 기존 데이터 사용

### 4.7 `modules.security`

`VulnerabilityProvider` 인터페이스. v1 구현체는 **OSV.dev 단독**.

**OSV 단독 채택 근거:**
- 무인증 공개 API — 첫 실행 경험에 토큰 입력 단계 없음
- OSV는 내부적으로 GitHub Advisory Database를 수집원 중 하나로 사용 → GHSA 데이터 상당 부분 포함
- `querybatch` 엔드포인트로 다수 패키지 일괄 조회 → 하루 1회 정책과 정합
- OSV schema가 범용 표준이라 내부 모델 `VulnerabilityRecord`를 안정적으로 유지 가능
- npm / PyPI / crates.io / Maven 등 주요 생태계 단일 스키마 제공

`VulnerabilityProvider` 인터페이스 자체는 제공자 교체가 가능하도록 설계하되, v1 코드는 `OsvProvider` 하나만 출고한다. 다중 제공자는 계획에 없음.

결과는 `CveCache`를 통과해 `modules.policy.CveFilter`로 공급된다.

### 4.8 `ui` (presentation)

세 가지 채널 병행.

1. **Inlay hint** — 버전 문자열 옆에 `→ 1.2.3` 흐리게 표시
2. **Tool Window** — 전체 의존성을 한 화면에 나열, 필터, 일괄 업데이트 (npm-update-dependencies 스타일)
3. **Inspection + Quick Fix** — 단건 업데이트

세 채널 모두 `services` 계층의 `VersionReportService` (Project service)가 보유한 `StateFlow<ReportSnapshot>`를 구독해 상태 일원화. Quick Fix 엔트리는 `actions`, 파일 변경 트리거는 `listeners`에 둔다.

**WebStorm 기본 인스펙션과의 공존:**
- 설정에 `Coexist with IDE inspection` / `Replace IDE inspection` 모드
- 기본값 **Coexist** — 기본 인스펙션이 다루지 않는 정보(채널, `minimumReleaseAge`로 보류, CVE, workspace catalog 출처)만 inlay/gutter로 덧붙임
- Replace 모드는 고급 사용자 옵션

### 4.9 `modules.apply` — 업데이트 적용

사용자 옵션으로 두 가지 전략을 제공한다.

**설정: `Update strategy` (Project scope)**

| 옵션 | 동작 |
|---|---|
| **`Manifest only` (기본값)** | `package.json` / `pnpm-workspace.yaml` 텍스트만 `WriteCommandAction`으로 치환. lockfile 미변경. Undo 스택 보존 |
| `Run package manager` | 감지된 매니저로 CLI 실행: `pnpm up`, `yarn up`, `bun update`, `npm install <pkg>@<ver>` |

**기본값이 `Manifest only`인 이유:**
- Undo로 되돌릴 수 있음
- 네트워크/권한/PATH 문제 무관
- CI 환경이나 잠긴 사내 머신에서도 동작
- 플러그인 성격(알림 + 최소 개입)과 일치

**두 모드 공통 안전장치:**
- 실행 전 미커밋 VCS 변경 경고
- 단건: 변경 내용 다이얼로그 / CLI: 정확한 커맨드 다이얼로그로 확인
- 실행 후 매니페스트 재파싱으로 구조 검증, 실패 시 자동 롤백
- 일괄 업데이트는 두 모드 모두 **dry-run 프리뷰 → 체크박스 선택 → 커밋** 공통

`pnpm-workspace.yaml`의 `minimumReleaseAgeExclude`에 항목을 자동으로 추가하는 기능은 **플러그인 성격과 맞지 않아 제공하지 않는다** (사용자가 직접 편집).

### 4.10 `services`, `actions`, `listeners`, `extensions`, `utils`

기능 모듈을 조립하고 IDE 플랫폼과 연결하는 횡단 계층.

| 패키지 | 역할 |
|---|---|
| `services` | Project/Application Service. `VersionReportService`(StateFlow 보관), `UpdatePipelineService`(manifest → registry → policy 오케스트레이션), `ScheduledRefreshService`(CVE 24h 스케줄) 등 조합 로직을 전담. 여기가 유일하게 여러 `modules.*`를 동시에 참조할 수 있는 지점 |
| `actions` | `AnAction` / `IntentionAction` / Quick Fix 엔트리. UI 트리거만 담고 실제 작업은 `services`로 위임 |
| `listeners` | `BulkFileListener`, `PsiTreeChangeListener`, `ProjectManagerListener` 등. `coalesceBy` 키로 debounce 후 `services`에 재분석 요청을 던진다 |
| `extensions` | IDE Extension Point 등록 어댑터 (`LocalInspectionTool`, `InlayHintsProvider`, `ToolWindowFactory`, `Configurable`). 본체 로직은 `ui`/`services`가 보유 |
| `utils` | `Secret` 래퍼, coroutine helper, semver/버전 파싱, 경로 유틸 등 모듈 간 공유 유틸. `model` 외에는 의존하지 않음 |

### 4.11 `settings`

`PersistentStateComponent<State>` 기반. 스코프는 Application / Project 둘로 나눈다.

| 스코프 | 항목 |
|---|---|
| Application | 기본 업데이트 채널, HTTP 타임아웃, 동시 요청 수, 전역 ignore 목록 |
| Project | 채널 override, 프로젝트 ignore 목록, `minimumReleaseAge` override, 프라이빗 레지스트리 매핑 추가, 표현 채널(inlay / toolwindow / inspection) on/off, JS 기본 인스펙션 공존 모드, **Update strategy** |

---

## 5. 버전 채널 모델

`VersionChannel` enum은 생태계 독립적으로 정의한다. 기본값 `STABLE`. 생태계별 어댑터가 해석 규칙을 담당한다.

| 생태계 | 해석 규칙 |
|---|---|
| npm / jsr / bun (semver) | prerelease tag (`-alpha`, `-beta`, `-rc`, `-next`, `-canary`, `-nightly`) 매핑. dist-tag (`latest`, `next`, `beta`) 병행 해석 |
| Deno std / deno.land/x *(v1.1)* | 태그 명명 규칙 휴리스틱 + 날짜 기반 버전은 NIGHTLY 분류 |

사용자는 채널을 다중선택 가능하며(`{STABLE}`, `{STABLE, BETA}` 등), `ChannelFilter`가 집합 밖 버전을 후보에서 제거한다.

---

## 6. 워크스페이스 / 모노레포

pnpm workspace catalog, yarn workspace, bun workspace를 v1에 포함한다. 파서는 루트 매니페스트를 읽을 때 workspace 멤버를 전부 수집해 `WorkspaceContext`를 만든다.

- catalog 참조 (`catalog:`, `workspace:*`)는 원격 조회 대상에서 제외
- catalog 엔트리 자체는 `DeclaredDependency(scope=CATALOG)`로 취급
- catalog만 업데이트해도 전체 멤버에 반영되는 UX 제공

Deno workspace는 v1.1에서 동일한 방식으로 처리.

---

## 7. Deno URL 지원 상세 (v1.1 ~ v1.2)

### 7.1 지원 범위 (v1.2 소스 스캔 시)

| 구분 | 지원 | 처리 |
|---|---|---|
| `https://deno.land/std@<ver>/...` | O | `registry.denoland` |
| `https://deno.land/x/<mod>@<ver>/...` | O | `registry.denoland` |
| `https://esm.sh/<pkg>@<ver>` | O | `registry.esmsh` → npm 역매핑 |
| `https://cdn.skypack.dev/<pkg>@<ver>`, `https://unpkg.com/<pkg>@<ver>` | O | npm 역매핑 |
| `https://raw.githubusercontent.com/...` | **X** | 태깅 규칙 편차로 false positive 과다 |
| 임의 도메인 | **X** | 역매핑 불가 |

### 7.2 파싱 방식 — PSI 기반 전용

- `import` 구문의 from 문자열을 PSI로 정확히 추출
- **JavaScript 플러그인이 없으면 Deno URL 스캔 기능 자체를 비활성화** (설정 UI에서 회색 처리 + 사유 표시)
- 정규식 폴백은 두지 않음 (코드 복잡도 대비 이득 없음)

### 7.3 import map 중복 처리

- `deno.json`의 `imports`에 등록된 URL은 **소스 스캔에서 제외**
- import map에 없고 소스에만 박힌 URL만 "소스 전용 의존성"으로 리포트
- 이렇게 해야 같은 의존성이 두 번 보고되지 않음

### 7.4 업데이트 적용

소스 URL 의존성은 매니저 CLI가 없으므로 **파일 내 문자열 치환만** 가능.

- **프로젝트 전체에서 동일 `<module>@<oldver>` → `<newver>` 일괄 치환**
- dry-run 프리뷰 필수 — 영향받는 파일 목록과 라인을 먼저 표시
- 사용자가 파일 단위로 체크박스 선택 후 커밋
- WriteCommandAction 단일 트랜잭션, 실패 시 전체 롤백

---

## 8. 테스트 전략

| 레이어 | 방법 |
|---|---|
| `core`, `manifest`, `policy`, `cache` | JUnit5 단위 테스트 (순수 모듈) |
| `registry` | MockWebServer로 HTTP 레벨 테스트 |
| `security` (OSV) | 고정 JSON fixture 기반 |
| 통합 | `BasePlatformTestCase` + light project fixture, 실제 매니페스트 파일 로드 후 파이프라인 실행 |
| 서비스 교체 | `project.replaceService(...)`로 페이크 주입 |

모든 서비스는 인터페이스 → 구현 분리가 되어 있어 테스트 용이성이 보장된다.

---

## 9. 확정 사항 요약

| 항목 | 결정 |
|---|---|
| Plugin ID | `com.livteam.dependency-ninja` |
| 배포 | JetBrains Marketplace 단독 |
| 타겟 빌드 | 243 ~ 263 |
| Go 지원 | **제외** (GoLand 커버) |
| CVE 제공자 | **OSV 단독**, 다중 제공자 계획 없음 |
| CVE 캐시 | **24시간 고정**, 설정 불가 |
| Deno 범위 (v1.1) | `deno.json(c)` 선언만 — `imports`, `jsr:`, `npm:` |
| Deno 범위 (v1.2) | 소스 URL 스캔 — A/B/C/D (raw github, 임의 도메인 제외) |
| Deno 파싱 | PSI 전용, JS 플러그인 없으면 비활성 |
| Deno import map 중복 | 소스 스캔에서 제외 |
| Deno 업데이트 | 프로젝트 전체 일괄 치환 + dry-run 프리뷰 |
| 업데이트 전략 기본값 | **`Manifest only`** |
| pnpm `minimumReleaseAgeExclude` 자동 편집 | **미지원** (성격 불일치) |
| WebStorm 기본 인스펙션 | 기본값 Coexist, Replace는 고급 옵션 |
| DI | IntelliJ Platform Service 시스템 (외부 DI 없음) |
| 네트워크 | coroutine `Dispatchers.IO` + `Task.Backgroundable` |
| PSI 접근 | `ReadAction.nonBlocking().coalesceBy().expireWith()` |
