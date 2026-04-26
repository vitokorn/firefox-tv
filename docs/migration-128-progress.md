# firefox-tv Migration to mozilla-components 128 - Progress Report

## Status: Phase B - Core API Migration In Progress

### Completed (Phase A - Dependencies & Build Setup)

- [x] Bumped `moz_components_version` to `128.0` (timestamped format)
- [x] Updated GeckoView to matching `128.0.20240725162350`
- [x] Added Jetpack Compose dependencies and build features
- [x] Removed `browser-session` (replaced by `browser-state`)
- [x] Removed `service-fretboard` (replaced by experiments framework)
- [x] Removed `screengrab` (testing tool - can re-add later)
- [x] Added `feature-search` (replaces `browser-search`)
- [x] Added `browser-thumbnails` (new component)
- [x] Fixed `geckoview-omni` transitive dependency exclusion
- [x] Fixed `libxul.so` native library conflict with `pickFirst`
- [x] Updated JVM target from 1.8 → 17
- [x] Added `lifecycle_version = '2.5.1'` to root build.gradle
- [x] Created missing `mozac_ic_*` vector drawables (6 icons)
- [x] Fixed `VisibleForTesting.PRIVATE/NONE` imports (6 files)
- [x] Removed `SessionManager` from gecko `WebRenderComponents.kt`
- [x] Fixed `ServiceLocator.kt` `searchEngineManager` stub

### In Progress (Phase B - Core API Migration)

#### 1. Browser Session → Browser State (CRITICAL)
- [ ] `SessionRepo.kt` - Full migration from `SessionManager` to `BrowserStore`
- [ ] `SessionObserverHelper.kt` - `Session` observers → `BrowserStore` subscriptions
- [ ] `MainActivity.kt` - `Session` imports and usage
- [ ] `WebRenderFragment.kt` - `Session` API migration
- [ ] `FirefoxProgressBar.kt` - `Session` observer removal
- [ ] `VideoVoiceCommandMediaSession.kt` - `Session` references
- [ ] `ext/Session.kt` - Extension functions on removed `Session` class
- [ ] `NullSession.kt` - Reference to removed `Session` class

#### 2. Tracking Protection API (MEDIUM)
- [x] `Settings.kt` - Uses new `TrackingProtectionPolicy.recommended()` / `.select()` API
- [x] `TurboMode.kt` - Fixed: removed `null` assignment to `trackingProtectionPolicy`; always uses `settings.trackingProtectionPolicy` which returns a proper disabled policy via `TrackingProtectionPolicy.select(TrackingCategory.NONE)`. Updated `TurboModeTest.kt` to remove references to removed `Session`/`SessionManager` APIs.

#### 3. Search Engine API (MEDIUM)
- [x] `SearchEngineManagerFactory.kt` - `SearchEngineManager` removed, replaced by `feature-search`
- [x] `SearchEngineProviderWrapper.kt` - Replaced by `feature-search` `SearchEngine`/`BrowserStore`
- [x] `UrlUtils.createSearchUrl` - Uses `BrowserStore` default search engine via `feature-search`
- [x] `WebRenderComponents` - Initializes Google/Bing/DuckDuckGo search engines in `BrowserStore`
- [x] `SettingsScreen` - Added default search engine selection dialog in COMMON settings
- [x] Removed Amazon search codes and `assets/searchplugins/` XML files
- [x] Removed Amazon Device Messaging (ADM) dependency

#### 4. FxA API (MEDIUM)
- [x] `FxaRepo.kt` - `beginAuthenticationAsync()` → `beginAuthentication()` (suspend)
- [x] `FxaRepo.kt` - `logoutAsync()` → `logout()` (suspend)
- [x] `FxaRepo.kt` - `start()` replaces `initAsync()`
- [ ] `FxaLoginUseCase.kt` - `finishAuthenticationAsync()` API may need update

#### 5. Fretboard/Experiments (MEDIUM)
- [ ] `FretboardProvider.kt` - `service-fretboard` removed, needs Nimbus stub or removal
- [ ] `ExperimentsProvider.kt` - `ExperimentDescriptor` removed
- [ ] `ExperimentConfig.kt` - References removed fretboard
- [ ] `IntentValidator.kt` - References removed fretboard

#### 6. Autocomplete/T toolbar (LOW)
- [ ] `ToolbarUiController.kt` - `AutocompleteProvider` supertype missing from classpath
- [ ] May need `concept-toolbar` dependency or use alternative API

#### 7. LiveData/ReactiveStreams (LOW)
- [x] Added `lifecycle-reactivestreams-ktx` dependency
- [ ] `PinnedTileRepo.kt` - `LiveDataReactiveStreams` import
- [ ] `ToolbarViewModel.kt` - `LiveDataReactiveStreams` import

### Pending (Phase C - Compose UI Migration)

- [ ] TV Compose artifacts (currently disabled - need correct coordinates)
- [ ] Compose compiler extension verification
- [ ] TV-specific focus/DPad handling

### Build Verification Steps

Run these to check progress:
```bash
# After each batch of fixes:
./gradlew :app:compileSystemDebugKotlin
./gradlew :app:compileGeckoDebugKotlin
```

### Notes

- `browser-engine-gecko` in 128.x requires `BrowserStore` directly instead of `SessionManager`
- Many `Session` observers must be replaced with `BrowserStore` `Flow`/`LiveData` subscriptions
- `FretboardProvider` can likely be stubbed out since experiments aren't critical for basic functionality
- Search engine provider API changed significantly - may need to use `feature-search` components
