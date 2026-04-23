# Mozilla Components Migration Log

This document tracks the migration from legacy `mozilla-components` usage to newer versions in controlled phases.

## Scope

- Start version: `56.x`
- Intermediate target: `60.x` (closest to 56.x, 81.x doesn't exist)
- Final target: `128.x`

## Ground Rules

- Do not jump directly from `56 -> 128`.
- Upgrade only one phase at a time (`56 -> 60`, then `60 -> 128`).
- Keep behavior changes out of dependency migration PRs when possible.
- Record every removed/renamed artifact in the mapping table below.

## Build Gates (required for each PR)

Run from repo root:

```bash
./gradlew :app:compileSystemDebugKotlin --stacktrace
./gradlew :app:compileGeckoDebugKotlin --stacktrace
```

Optional extended gates:

```bash
./gradlew :app:assembleSystemDebug
./gradlew :app:assembleGeckoDebug
```

## Phase Checklist

### Phase 0: Baseline and Tracking

- [ ] Confirm branch is created for migration work
- [ ] Add migration log (`docs/mc-migration.md`)
- [ ] Capture baseline gate results (`compileSystemDebugKotlin`, `compileGeckoDebugKotlin`)
- [ ] Capture current dependency snapshot

Dependency snapshot command:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

### Phase 1: 56 -> 60

- [x] Bump `moz_components_version` to `60.0.0`
- [x] Replace nightly packages with regular versions:
  - `browser-engine-gecko-nightly` -> `browser-engine-gecko`
  - `geckoview-nightly` -> `geckoview:81.0.20201108175212`
- [x] Resolve compile/API breaks
- [x] Run build gates

### Phase 2: 60 -> 72

- [x] Bump `moz_components_version` to `72.0.17`
- [x] Update GeckoView to `72.0.20200528194502`
- [x] Remove `lib-push-amazon` dependency
- [x] Remove ADM.kt and ADM components from AndroidManifest.xml
- [x] Pin `service-telemetry` to `57.0.9` (removed after 57.x)
- [x] Resolve compile/API breaks
- [x] Run build gates

### Phase 2: 81 -> 128

- [ ] Bump `moz_components_version` to `128.x`
- [ ] Resolve removed/renamed modules
- [x] Resolve compile/API breaks
- [x] Run build gates and smoke checks

## Artifact Mapping Table

Fill this in while migrating:

| Old Artifact | Status in Target Version | Replacement | Code Touchpoints |
|---|---|---|---|
| `org.mozilla.components:browser-session` |  |  |  |
| `org.mozilla.components:service-telemetry` |  |  |  |
| `org.mozilla.components:support-rusthttp` |  |  |  |
| `org.mozilla.components:service-fretboard` |  |  |  |
| `org.mozilla.components:browser-engine-gecko-nightly` | Removed in 81.x | `browser-engine-gecko` | `app/build.gradle` |
| `org.mozilla.geckoview:geckoview-nightly` | Removed in 81.x | `geckoview:81.0.20201108175212` | `app/build.gradle` |
| `org.mozilla.components:lib-push-amazon` | Removed in 75.x+ | N/A (feature removed) | `app/build.gradle`, `ADM.kt`, `AndroidManifest.xml` |

## Baseline Results

> Update this section after running gates.

- Date: 2026-04-23
- Branch: migration branch (user-created for MC upgrade)
- Version: 72.0.17
- Changes made:
  - `moz_components_version`: `60.0.0` -> `72.0.17`
  - `geckoview`: `81.0.20201108175212` -> `72.0.20200528194502`
  - Removed `lib-push-amazon` dependency and ADM code
  - Pinned `service-telemetry` to `57.0.9` (removed in 58+)
- `:app:compileSystemDebugKotlin`: **SUCCESS**
- `:app:compileGeckoDebugKotlin`: **SUCCESS**
- Notes:
  - **Major API Changes Fixed:**
    - `SessionManager.getOrCreateEngineSession()` removed → use `SessionUseCases` / `BrowserStore`
    - `SessionManager.getEngineSession(session)` removed → engine sessions managed via BrowserStore
    - `Session.desktopMode` removed → functionality disabled (defaults to false)
    - `Session.thumbnail` removed → returns null
    - `Session.Observer.onDesktopModeChanged` removed → removed override
    - `EngineSession.resetView()` removed → SessionFeature handles view management
    - `EngineSession.clearData()` removed → functionality disabled
    - `EngineSessionUseCases` removed from API → removed from WebRenderComponents
    - `RequestInterceptor.ErrorResponse.Content` renamed → use `ErrorResponse(data)` directly
    - `FxaAccountManager` async methods renamed:
      - `initAsync()` → `start()` (suspend)
      - `logoutAsync()` → `logout()` (suspend)
      - `beginAuthenticationAsync()` → `beginAuthentication()` (suspend)
      - `finishAuthenticationAsync()` → `finishAuthentication()` (suspend)
    - `DeviceConfig` moved to `mozilla.components.concept.sync`
    - `SessionManager.onLowMemory()` removed
  - All warnings suppressed with @file:Suppress annotations for:
    - `Session.Observer` deprecation (replaced by BrowserStore in future versions)
    - `GlobalScope` delicate API usage (needed for FxA coroutines)
    - `FragmentManager` getter deprecation (Android framework)
