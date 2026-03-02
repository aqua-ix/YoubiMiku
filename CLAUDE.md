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
- **Compile/Target SDK 35**, Min SDK 23
- **Two product flavors**: `ads` (with ad SDKs) and `noAds` (ad-free)
- Namespace: `com.aqua_ix.youbimiku`
- ViewBinding enabled, Room schema exported via KSP

## Architecture

Single-module Android app with an activity-centric architecture. Nearly all UI logic lives in **MainActivity.kt** (~1087 lines), which manages the chat interface, AI model switching, avatar WebView, ads, and settings.

### Key Layers

- **UI**: `MainActivity` (ChatView + WebView for avatar mode), `UserNameDialogFragment`
- **AI Integration**: `DetectIntent.kt` (DialogFlow v2), OpenAI via `com.aallam.openai` library
- **Config**: `config/` package — type-safe enums for AI model, font size, language, UI mode; `SharedPreferenceManager` wraps SharedPreferences; Firebase RemoteConfig controls feature flags
- **Data**: Room database (`database/` package) stores chat messages; Firebase Realtime DB stores API keys and credentials at runtime
- **Utilities**: `TranslateUtil` (EN↔JP translation via HTTP), `ReportUtil` (message reporting)

### Configuration System

Firebase RemoteConfig drives runtime feature flags (OpenAI enablement, ad network selection, display frequency, AI model parameters). Local preferences use `SharedPreferenceManager` with keys defined as constants.

### Database

Room ORM with a single `MessageEntity` table. Two schema versions with auto-migration (v1→v2). DAO in `MessageDao.kt`, conversions in `DatabaseUtil.kt`.

### Ad Integration

Two ad networks (iMobile, IronSource) switchable via RemoteConfig. The `ads` flavor includes ad SDKs; `noAds` flavor excludes them. Interstitial ads trigger after a configurable message count.

## Deploy to Device (WSL)

Use Windows-side `gradlew.bat` from WSL to deploy to a connected device.

```bash
# Check device connection
/mnt/c/Users/souic/AppData/Local/Android/Sdk/platform-tools/adb.exe devices

# Build and install
cmd.exe /c "cd /d D:\\Development\\Project\\YoubiMiku && gradlew.bat installAdsDebug"
```

If installation fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` due to signature mismatch, uninstall the existing app first:

```bash
/mnt/c/Users/souic/AppData/Local/Android/Sdk/platform-tools/adb.exe uninstall comviewaquahp.google.sites.youbimiku
```

## CI/CD

GitHub Actions (`.github/workflows/android.yml`) runs on push/PR to main: build, lint, unit tests, and instrumentation tests (API 29 emulator). A custom setup action (`.github/actions/setup/`) configures JDK 17 and decodes secret files from GitHub Secrets.

## Maintaining This File

When making changes that affect build configuration, dependencies, architecture, or project conventions, update the relevant sections of this file to keep it accurate. Examples: updating SDK versions, adding new modules or packages, changing build flavors, modifying CI workflows.
