# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YoubiMiku (ユビキタ初音ミク) is an Android chat application where users talk to Hatsune Miku. Written in Kotlin, it supports two AI backends (Google DialogFlow and OpenAI GPT), two UI modes (Chat and Avatar), two ad networks (iMobile and IronSource), and two languages (Japanese and English).

## Build Commands

```bash
./gradlew assembleDebug       # Build debug APKs (both ads and noAds flavors)
./gradlew assembleRelease     # Build release APKs
./gradlew test                # Run unit tests
./gradlew connectedCheck      # Run instrumentation tests (requires emulator/device)
./gradlew lint                # Run Android lint
./gradlew clean               # Clean build artifacts
```

## Setup Requirements

1. Copy `secrets.defaults.properties` → `secrets.properties` and fill in values
2. Place Firebase `google-services.json` in `app/`
3. Place Dialogflow secret `dialogflow_secret.json` in `app/src/main/res/raw/`

## Build Configuration

- **Kotlin 1.9.23**, **AGP 8.7.2**, **JDK 17** (Temurin in CI), Java target 1.8
- **Compile/Target SDK 36**, Min SDK 23
- **Two product flavors**: `ads` (with ad SDKs) and `noAds` (ad-free), each with its own source set (`app/src/ads/`, `app/src/noAds/`) and manifest
- Namespace: `com.aqua_ix.youbimiku`
- ViewBinding enabled, Room schema exported via KSP

## Architecture

Single-module Android app with an activity-centric architecture. Nearly all UI logic lives in **MainActivity.kt** (~1087 lines), which manages the chat interface, AI model switching, avatar WebView, ads, and settings.

### Key Layers

- **UI**: `MainActivity` (ChatView + WebView for avatar mode), `UserNameDialogFragment`
- **AI Integration**: `DetectIntent.kt` (DialogFlow v2), OpenAI via `com.aallam.openai` library. Both clients are constructed off the main thread (the Dialogflow client lazily on the first send, the OpenAI client as soon as the credentials arrive) — see [Threading](#threading)
- **Config**: `config/` package — type-safe enums for AI model, font size, language, UI mode; `SharedPreferenceManager` wraps SharedPreferences; `RemoteConfigProvider` wraps Firebase RemoteConfig, which controls feature flags
- **Data**: Room database (`database/` package) stores chat messages; Firebase Realtime DB stores API keys and credentials at runtime
- **Ads**: `ads/` package — `AdController` interface in `main`, implementations per flavor
- **Utilities**: `TranslateUtil` (EN↔JP translation via HTTP), `ReportUtil` (message reporting)

### Configuration System

Firebase RemoteConfig drives runtime feature flags (OpenAI enablement, ad network selection, display frequency, AI model parameters). Values are read through `config/RemoteConfigProvider`, which applies `res/xml/remote_config_defaults.xml`, fetches, and reports "not fetched yet / not set / invalid" as `null` so callers can pick a fallback. Initialization that depends on RemoteConfig (ad network, OpenAI, menu visibility) runs in `MainActivity.onRemoteConfigReady()` after the fetch completes — a failed fetch still runs it with default or cached values. Local preferences use `SharedPreferenceManager` with keys defined as constants; it caches the `SharedPreferences` instance (`Context.getSharedPreferences()` touches the disk on every call) and `Application.onCreate()` warms it up on a background thread so the first read does not block the main thread.

### Threading

Blocking I/O must not run on the main thread.

- `MainActivity` uses a `CoroutineScope(Dispatchers.IO)` for history reads/writes, AI requests and report sending. `DetectIntent.send()` switches to `Dispatchers.IO` itself so it does not depend on the caller's dispatcher.
- Client construction is deferred because it is slow: `DetectIntent` loads the credentials and creates the gRPC clients lazily on the first send (about 100–400 ms), and the OpenAI client — whose constructor scans the APK for a Ktor engine (about 400 ms) — is built on `Dispatchers.IO` and published on the main thread so `canSendRequest()` sees a consistent state.
- Cleanup that has to outlive the Activity runs on `Application.applicationScope` (a process-lifetime `SupervisorJob` + `Dispatchers.IO` scope): `DetectIntent.shutdown()` resets the Dialogflow contexts (a blocking gRPC call) and closes both clients, and does nothing when they were never created.
- Firebase Realtime Database listeners registered in `setupOpenAI()` / `setupChat()` are kept in fields and removed in `onDestroy()`.
- Debug builds enable `StrictMode`'s thread policy (`detectAll().penaltyLog()`), so main thread disk/network access is visible in logcat. The app's own code is expected to produce no violations; the IronSource SDK produces several in the `ads` flavor (`IronSource.init()` reads its preferences and files synchronously on the main thread).

### Database

Room ORM with a single `MessageEntity` table. Two schema versions with auto-migration (v1→v2). DAO in `MessageDao.kt`, conversions in `DatabaseUtil.kt`.

### Ad Integration

Two ad networks (iMobile, IronSource) switchable via RemoteConfig. Interstitial ads trigger after a configurable message count (`ad_display_request_times`). `MainActivity.showInterstitialIfNeeded()` counts every sent message regardless of the AI model in `Key.MESSAGE_COUNT_FOR_AD` (`config/AdConfig.kt`), then judges and resets in one synchronous place. `AdController.showInterstitial()` reports whether the ad was actually shown, so the count is kept — and retried on the next message — while the ad network has no inventory. Readiness checks and (re)loading stay inside `AdNetworkController`: IronSource loads from the `IronSource.init()` callback (loading earlier fails with "init() had failed") and again after `onAdClosed`.

Ad SDKs are declared with the `adsImplementation` configuration, so only the `ads` flavor bundles them. `MainActivity` never references ad SDK classes directly: it goes through the `AdController` interface (`ads/AdController.kt` in the `main` source set) and obtains an instance from `AdControllerFactory`, which is defined per flavor — `AdNetworkController` (iMobile/IronSource) in `app/src/ads/`, `NoOpAdController` in `app/src/noAds/`. The `noAds` manifest also removes the `AD_ID` permission that dependencies merge in.

## Deploy to Device (WSL)

Use Windows-side `gradlew.bat` from WSL to deploy to a connected device.

```bash
# Check device connection
/mnt/c/Users/souic/AppData/Local/Android/Sdk/platform-tools/adb.exe devices

# Build and install
cmd.exe /c "cd /d D:\\Development\\Project\\YoubiMiku && gradlew.bat installAdsDebug"
```

To launch the app after installation:

```bash
/mnt/c/Users/souic/AppData/Local/Android/Sdk/platform-tools/adb.exe shell monkey -p comviewaquahp.google.sites.youbimiku -c android.intent.category.LAUNCHER 1
```

If installation fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` due to signature mismatch, uninstall the existing app first:

```bash
/mnt/c/Users/souic/AppData/Local/Android/Sdk/platform-tools/adb.exe uninstall comviewaquahp.google.sites.youbimiku
```

## CI/CD

GitHub Actions (`.github/workflows/android.yml`) runs on push/PR to main: build, lint, unit tests, and instrumentation tests (API 29 emulator). A custom setup action (`.github/actions/setup/`) configures JDK 17 and decodes secret files from GitHub Secrets.

## Maintaining This File

When making changes that affect build configuration, dependencies, architecture, or project conventions, update the relevant sections of this file to keep it accurate. Examples: updating SDK versions, adding new modules or packages, changing build flavors, modifying CI workflows.
