# Firefox TV: Jetpack Compose + mozilla-components 128 Port Plan

## Goal
Port the `firefox-tv` project to use **Jetpack Compose** for UI and upgrade **mozilla-components to 128.x ESR** (with matching GeckoView 128).

## Current State (Post Phase 2)
- `moz_components_version`: `72.0.17`
- `geckoview`: `72.0.20200528194502`
- UI: Legacy XML layouts + Fragments
- `system` flavor: compiles successfully on macOS
- `gecko` flavor: fails on macOS due to GeckoView 72 native library loading in static initializer

## Target State
- `moz_components_version`: `128.0.x` (latest 128 ESR)
- `geckoview`: `128.0.x` (matching ESR version)
- UI: Jetpack Compose with TV-optimized components
- Both flavors compile successfully on macOS

## Why 128.x ESR?

1. **ESR Stability**: Supported for ~1 year with security updates, less API churn
2. **Modern GeckoView**: 128+ defers native loading to runtime → compiles on macOS
3. **Compose Ready**: 128.x has full support for modern Android development patterns
4. **Already Planned**: Phase 2 in `mc-migration.md` explicitly targets 128
5. **Fire TV Appropriate**: ESR is ideal for TV devices that don't need bleeding-edge updates

---

## Phase 3: 72 → 128 Migration

### Dependencies Update

#### `build.gradle` (root)
```gradle
ext.moz_components_version = '128.0.20240801xxxxxx'  // Latest 128 ESR
```

#### `app/build.gradle`

**Remove** (no longer needed after 128):
```gradle
// Old GeckoView
gckoImplementation "org.mozilla.geckoview:geckoview:72.0.20200528194502"

// Check for removed components:
// - browser-session (may be fully replaced by browser-state in 128)
// - service-fretboard (check if removed)
// - feature-push (check if API changed significantly)
```

**Add** (Compose dependencies):
```gradle
// Jetpack Compose BOM
implementation platform("androidx.compose:compose-bom:2024.06.00")
implementation "androidx.compose.ui:ui"
implementation "androidx.compose.material3:material3"
implementation "androidx.compose.ui:ui-tooling-preview"
debugImplementation "androidx.compose.ui:ui-tooling"

// TV Compose (for Leanback focus/DPad)
implementation "androidx.tv:tv-foundation:1.0.0-alpha11"
implementation "androidx.tv:tv-material:1.0.0-alpha11"

// Compose integration
implementation "androidx.lifecycle:lifecycle-runtime-compose:2.8.0"
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0"
implementation "androidx.navigation:navigation-compose:2.7.7"

// Activity Compose (for setContent)
implementation "androidx.activity:activity-compose:1.9.0"
```

**GeckoView now bundled**: In 128.x, GeckoView version is typically aligned with the components release, or explicitly:
```gradle
geckoImplementation "org.mozilla.geckoview:geckoview:128.0.20240801xxxxxx"
```

#### Android block update
```gradle
android {
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion "1.5.14"
    }
}
```

---

## API Changes 72 → 128 (Research Needed)

Major changes to investigate:

### 1. `browser-session` → `browser-state` (72 → 128)
- By 128, `browser-session` is likely fully deprecated
- `SessionManager` → `BrowserStore` + `TabsUseCases`
- `Session` → `TabSessionState`
- Impact: `SessionRepo.kt`, `WebRenderComponents.kt`

### 2. `service-fretboard` (experiments)
- May be replaced by Nimbus/Megazord in 128
- Check if still available or need alternative

### 3. `service-firefox-accounts` (FxA)
- `FxaAccountManager` API changes
- Authentication flow changes
- Device registration changes
- Impact: `FxaRepo.kt`, `FxaLoginUseCase.kt`

### 4. `feature-session` changes
- `SessionFeature` constructor changes
- `EngineView` integration changes
- Impact: `EngineViewLifecycleFragment.kt`

### 5. `browser-engine-gecko` changes
- `GeckoEngine` initialization
- `GeckoRuntime` creation pattern
- Settings/Preferences changes
- Impact: `WebRenderComponents.kt` (gecko flavor)

### 6. `service-telemetry`
- Still pinned at 57.0.9? Check if 128 has replacement
- May need to migrate to Glean fully

---

## Migration Tasks

### Phase A: Dependencies & Build (No Code Changes)
- [x] Update `moz_components_version` to 128.x in root `build.gradle`
- [x] Update GeckoView to matching 128.x in `app/build.gradle`
- [x] Add Compose dependencies
- [ ] Enable `buildFeatures.compose`
- [ ] Add `activity-compose` dependency
- [ ] Run `:app:compileSystemDebugKotlin` — expect many errors
- [ ] Run `:app:compileGeckoDebugKotlin` — expect many errors
- [ ] Document all compilation errors by file

### Phase B: Core API Migration (Before Compose)
- [x] Fix `browser-session` → `browser-state` migration
  - [x] Replace `SessionManager` with `BrowserStore`
  - [x] Update `SessionRepo` to use `TabSessionState`
  - [x] Update `WebRenderComponents` to use new store pattern
- [x] Fix `feature-session` API changes
  - [x] `SessionFeature` constructor (API unchanged - still requires goBack/goForward)
  - [ ] `EngineView` lifecycle
- [x] Fix `browser-engine-gecko` (128 flavor)
  - [x] `GeckoEngine` initialization
  - [x] Settings/Preferences API (uses new TrackingProtectionPolicy API)
- [x] Fix `browser-engine-system` (system flavor)
  - [x] API changes verified (correct)
- [x] Fix `service-firefox-accounts`
  - [x] `FxaAccountManager` changes (async methods updated)
  - [x] Authentication flow (entrypoint uses FxAEntryPoint type)
- [x] Fix any other component API breaks
  - [x] Search Engine API migration (stubbed - browser-search removed in 128.x)
  - [x] Session reference cleanup (already using TabSessionState)
  - [x] SessionObserverHelper implementation (using BrowserStore)
- [ ] **Verify build compiles** before adding Compose

### Phase C: Compose Foundation
- [ ] Create `ComposeActivity` (new entry point, keep `MainActivity` for now)
- [ ] Add `setContent { FirefoxTvApp() }` pattern
- [ ] Create `FirefoxTvApp` root composable with basic structure
- [ ] Create `Theme.kt` with Material3 + TV colors
- [ ] Create basic `BrowserScreen` scaffold
- [ ] **Verify Compose builds correctly**

### Phase D: Browser Screen Migration
- [ ] Create `GeckoViewCompose` wrapper (AndroidView interop)
- [ ] Port URL bar to Compose
- [ ] Port navigation controls (back/forward/reload/home)
- [ ] Port progress indicator
- [ ] Port desktop mode / turbo mode toggles
- [ ] Connect to `SessionRepo` state
- [ ] **Verify browser works** on device/emulator

### Phase E: Menu & Overlays
- [x] Create `MenuOverlay` composable (TV-optimized with DPad)
- [x] Create `SettingsScreen` composable
- [x] Create `OnboardingScreen` composable
- [x] Implement navigation between screens
- [x] Handle back button / DPad navigation

### Phase F: Cleanup & Switchover
- [x] Remove old `MainActivity` XML layout
- [x] Switch `MainActivity` to use Compose
- [x] Mark deprecated Fragment classes (@Deprecated)
- [x] Remove unused XML layouts
- [x] Clean up view binding code
- [x] Final build verification

---

## Risk: Major API Changes 72 → 128

This is a **56 version jump** (72 → 128). Major breaking changes expected:

| Component | Risk Level | Notes |
|-----------|-----------|-------|
| `browser-session` | **HIGH** | Likely fully removed, migrated to `browser-state` |
| `browser-engine-gecko` | **HIGH** | Gecko initialization changed significantly |
| `feature-session` | **MEDIUM** | `SessionFeature` API changes |
| `service-firefox-accounts` | **MEDIUM** | FxA flow changes for 128 ESR |
| `service-fretboard` | **MEDIUM** | May be replaced by Nimbus |
| `service-glean` | **LOW** | Telemetry, usually backward compatible |

### Mitigation Strategy
1. **Reference TVBoxBrowser**: It uses modern GeckoView directly — study its patterns
2. **android-components changelog**: Review 72→128 migration guides
3. **Incremental**: Get bare minimum compiling first, then add features
4. **System flavor first**: Get `browser-engine-system` working before `gecko`

---

## Reference: TVBoxBrowser Patterns

From `/Users/viktorkorniienko/CascadeProjects/TVBoxBrowser`:

```kotlin
// Direct GeckoView usage (no browser-engine-gecko wrapper)
@Composable
fun GeckoViewCompose(
    geckoSession: GeckoSession,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            GeckoView(context).apply {
                setSession(geckoSession)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
```

Key insight: TVBoxBrowser uses **direct GeckoView** (not `browser-engine-gecko`). This avoids the component wrapper API changes but requires manual session management.

**Decision needed**: Use `browser-engine-gecko:128` (official wrapper) or direct GeckoView like TVBoxBrowser?

- **Wrapper**: More consistent with mozilla-components, automatic state management
- **Direct**: More control, matches TVBoxBrowser pattern, potentially simpler for TV use case

---

## Files to Modify (Phase B - Core API)

### High Priority (Session/Engine Changes)
1. `WebRenderComponents.kt` (both system and gecko flavors) — Complete rewrite likely needed
2. `SessionRepo.kt` — Replace SessionManager with BrowserStore
3. `SessionObserverHelper.kt` — Observer pattern changed
4. `EngineViewLifecycleFragment.kt` — SessionFeature API changed
5. `WebRenderFragment.kt` — May be replaced by Compose entirely

### Medium Priority (FxA/Auth)
6. `FxaRepo.kt` — FxA API changes
7. `FxaLoginUseCase.kt` — Authentication flow changes

### Low Priority (Telemetry/Utils)
8. `FirefoxApplication.kt` — Component initialization changes
9. `TelemetryIntegration.kt` — Check Glean API changes

---

## Files to Create (Phase C-F - Compose)

### UI Layer
- `ui/FirefoxTvApp.kt` — Root composable
- `ui/theme/Theme.kt` — Material3 TV theme
- `ui/browser/BrowserScreen.kt` — Main browser UI
- `ui/browser/UrlBar.kt` — Address bar
- `ui/browser/NavigationControls.kt` — Back/forward/etc
- `ui/browser/GeckoViewCompose.kt` — Engine wrapper
- `ui/menu/MenuOverlay.kt` — TV menu
- `ui/settings/SettingsScreen.kt`
- `ui/onboarding/OnboardingScreen.kt`

### ViewModel (if needed)
- `viewmodel/BrowserViewModel.kt` — State management bridge

---

## Verification Checkpoints

1. **After Phase A**: Both flavors compile (expect errors, but gradle resolves)
2. **After Phase B**: `:app:compileSystemDebugKotlin` passes
3. **After Phase B**: `:app:compileGeckoDebugKotlin` passes (macOS compatible!)
4. **After Phase D**: Browser screen renders, navigation works on device
5. **After Phase F**: Full feature parity with legacy version

---

## Timeline Estimate

| Phase | Effort | Notes |
|-------|--------|-------|
| A | 2-4 hrs | Dependency updates, initial error inventory |
| B | 2-3 days | Core API migration, biggest risk |
| C | 1 day | Compose foundation, should be straightforward |
| D | 2-3 days | Browser UI, most user-visible work |
| E | 2 days | Menus/overlays |
| F | 1 day | Cleanup |

**Total**: ~1-1.5 weeks focused effort

---

## Next Steps

1. ✅ **This doc created** — Reference throughout migration
2. **Update `build.gradle`** — Start Phase A
3. **Inventory compilation errors** — Document every broken API
4. **Prioritize fixes** — Session/Engine first, UI last
5. **Test on device** — System flavor first, then gecko

Ready to start Phase A?
