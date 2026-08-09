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
- **AI Integration**: `DetectIntent.kt` (DialogFlow v2), OpenAI via `com.aallam.openai` library. Both clients are constructed off the main thread (the Dialogflow client lazily on the first send, the OpenAI client as soon as the credentials arrive) — see [Threading](#threading). The OpenAI request carries the recent history as context and its reply is streamed — see [Conversation Context](#conversation-context)
- **Config**: `config/` package — type-safe enums for AI model, font size, language, UI mode; `SharedPreferenceManager` wraps SharedPreferences; `RemoteConfigProvider` wraps Firebase RemoteConfig, which controls feature flags
- **Data**: Room database (`database/` package) stores chat messages; Firebase Realtime DB stores API keys and credentials at runtime. Firebase also provides RemoteConfig, Analytics and Crashlytics
- **Ads**: `ads/` package — `AdController` interface in `main`, implementations per flavor
- **Observability**: `AppLog` (the only way the app writes to logcat, and the gate in front of Crashlytics), `Analytics` (Firebase Analytics events) — see [Logging and Crash Reporting](#logging-and-crash-reporting)
- **Utilities**: `HttpClientProvider` (the shared HTTP client), `TranslateUtil` (EN↔JP translation), `ReportUtil` (message reporting) — see [Networking](#networking)

### Configuration System

Firebase RemoteConfig drives runtime feature flags (OpenAI enablement, ad network selection, display frequency, AI model and conversation parameters). Values are read through `config/RemoteConfigProvider`, which applies `res/xml/remote_config_defaults.xml`, fetches, and reports "not fetched yet / not set / invalid" as `null` so callers can pick a fallback. Values that cannot be expressed as "unset" — `max_user_text_length`, `openai_model`, `max_context_messages`, `max_context_chars` — are returned non-null with a fallback constant that repeats the bundled default; the two context limits keep `0` as a meaningful value ("send no context"), so an unset or invalid value cannot silently grow the requests. Initialization that depends on RemoteConfig (ad network, OpenAI, the input length limit, menu visibility) runs in `MainActivity.onRemoteConfigReady()` after the fetch completes — a failed fetch still runs it with default or cached values. Local preferences use `SharedPreferenceManager` with keys defined as constants; it caches the `SharedPreferences` instance, because `Context.getSharedPreferences()` touches the disk on every call. `Application.onCreate()` also starts loading it on a background thread; this is best effort, since `MainActivity.onCreate()` reads preferences synchronously (`migrateMessageCountForAd`, `getFontSizeType`) and waits for the load if it has not finished yet. In practice the warm-up wins the race — no `StrictMode` disk violation was observed from these call sites.

### Threading

Blocking I/O must not run on the main thread.

- `MainActivity` uses a `CoroutineScope(Dispatchers.IO)` for history reads/writes, AI requests and report sending. `DetectIntent.send()` switches to `Dispatchers.IO` itself so it does not depend on the caller's dispatcher.
- Client construction is slow, so it is kept off the main thread: `DetectIntent` loads the credentials and creates the gRPC clients lazily on the first send (about 100–400 ms), and the OpenAI client — whose constructor scans the APK for a Ktor engine (about 400 ms) — is built on `Dispatchers.IO` as soon as the credentials arrive and published on the main thread so `canSendRequest()` sees a consistent state. The credentials used are remembered so a re-delivered Firebase value does not rebuild the same client.
- Cleanup that has to outlive the Activity runs on `Application.applicationScope` (a process-lifetime `SupervisorJob` + `Dispatchers.IO` scope): `DetectIntent.shutdown()` resets the Dialogflow contexts (a blocking gRPC call) and closes both clients, and does nothing when they were never created.
- Firebase Realtime Database listeners registered in `setupOpenAI()` / `setupChat()` are kept in fields and removed in `onDestroy()`.
- Debug builds enable `StrictMode`'s thread policy (`detectAll().penaltyLog()`), so main thread disk/network access is visible in logcat. The app's own code is expected to produce no violations; the IronSource SDK produces several in the `ads` flavor (`IronSource.init()` reads its preferences and files synchronously on the main thread).

### Logging and Crash Reporting

No app code calls `android.util.Log` directly — everything goes through `AppLog`, which owns three concerns that were previously spread over every call site.

**Nothing writes conversation text to logcat, in any build type.** Callers log the length of a message, never the message: `sendText: $text`, `result: $result`, `request: $text` and `response result : ${res.queryResult}` (the whole Dialogflow `QueryResult`, which repeats the user's own words back) are gone. The bodies are personal, and while another app on the device cannot read them, USB debugging and bug reports can.

**Secrets are masked immediately before output.** The `secrets.properties` endpoints reach logcat without anyone logging them on purpose: Ktor's `HttpRequestTimeoutException` message is `Request timeout has expired [url=...]`, and `TranslateUtil`/`ReportUtil` used to hand that exception straight to `Log.e`. `AppLog.redact()` replaces `TRANSLATE_END_POINT`, `REPORT_END_POINT`, `AVATAR_BASE_URL`, `DIALOGFLOW_PROJECT_ID` and anything matching `sk-…` in the message *and* in the rendered stack trace, so a call site cannot leak one by forgetting.

Each endpoint is masked together with the URL that follows it, up to the first character that reads as a separator. The host alone is not enough: the translation API takes the text as a query parameter, so the timed-out URL is `<endpoint>?text=<the user's message>&target=ja` and masking only the endpoint leaves the message in the log — the thing this exists to prevent. Trimming at the separator keeps the rest of the exception (the timeout value) readable. Values shorter than 8 characters are skipped, since a pattern built from `""` would match at every position and mask the whole line. The stack trace is rendered with `printStackTrace`, not `Log.getStackTraceString`, which returns `""` for anything caused by an `UnknownHostException` and would hide every offline failure.

Conversely, the full value is not enough either, so `buildSecretPatterns()` derives a second pattern from `URL(value).host` for every endpoint that parses as a URL. Name resolution and connection failures name the host without its scheme — `Unable to resolve host "<host>": No address associated with hostname`, `failed to connect to <host>/<ip> (port 443)` — so a pattern anchored on `https://…` never matches them and the destination reaches both logcat and Crashlytics in the clear. Host patterns are applied after the full-value ones (otherwise `https://` would be left behind, stranding the path and query) and only match at a host boundary, so an unrelated `notavatar.example.com` stays readable.

**Debug output disappears in release builds.** `AppLog.d()` returns early unless `BuildConfig.DEBUG`; R8 folds the branch away once #24 turns minification on. `proguard-rules.pro` keeps `SourceFile,LineNumberTable` so Crashlytics can resolve line numbers then. The OpenAI client is also quieted: its default `LogLevel.Headers` prints `OpenAI-Organization` and `set-cookie` in the clear (the API key is masked by the SDK, those are not), so `createOpenAI()` passes `LoggingConfig(LogLevel.Info)` in debug and `LogLevel.None` in release.

`AppLog.e(tag, message, throwable)` also records the exception in Crashlytics as a non-fatal; `AppLog.w(…, throwable)` deliberately does not, and is used where a failure is routine (an ad network with no fill, an avatar sub-resource that fails while the page still renders). Before recording, `sanitize()` checks the cause chain for secrets and, only when it finds one, substitutes a `RedactedException` copy that keeps the original type name in its message and the original stack frames — so the common case still groups under its real exception class in the console.

What is deliberately **not** sent to Crashlytics: conversation text, user names, API keys, Cloudflare Access credentials, and any user identifier (`setUserId` is never called). What is sent, via `CrashlyticsKey`: `ai_model`, `ui_mode`, `ad_network`, `streamed_chars` and `last_trim_level` — all enum-ish values or counts.

Two of those keys exist because of failures that were invisible before. `streamed_chars` distinguishes the two ways a streamed reply can fail — nothing arrived, or it broke off part-way — which produce the same exception (#100); it is reset at the start of every request, so a later report cannot inherit an older count. `last_trim_level` is set from `Application.onTrimMemory()`, so a report that follows memory pressure can be told apart; a process killed outright by `lowmemorykiller` (#101) leaves no report at all, and this is the closest signal available. The levels it accepts are enumerated in `MEMORY_PRESSURE_LEVELS` rather than compared against a threshold, because `ComponentCallbacks2`' constants are not ordered by severity: `TRIM_MEMORY_UI_HIDDEN` is 20 — larger than `TRIM_MEMORY_RUNNING_LOW` (10) and `TRIM_MEMORY_RUNNING_CRITICAL` (15) — yet it only means the app went to the background, so a `>=` guard would overwrite real pressure with it every time the user pressed Home. ANRs like the one #100 produced need no code — Crashlytics reports them from `ApplicationExitInfo` on Android 11+.

`Analytics` counts the same failures for trend rather than diagnosis: `send_message`, `ai_error` (with `error_type` from `AIErrorType` and `has_partial_response`), `mode_change`, `model_change`. `MainActivity.classifyError()` produces the `AIErrorType` that both the on-screen message and the event use, so what the user was told and what was recorded cannot disagree. No event carries message text.

Both `_change` events count transitions, not taps. `mode_change` is logged inside `toggleAvatarMode()` — past the early return that defers the switch until the Cloudflare Access credentials arrive, so a switch the user had to wait for is still counted — and only when the caller passes `userAction = true`, which restoring the stored mode at startup does not. `model_change` compares the tapped row against the current selection, because `setSingleChoiceItems` calls back even when the already-selected row is tapped again.

Crashlytics is enabled in both flavors and both build types (debug reports are what makes the pipeline verifiable). `mappingFileUploadEnabled` is off for debug and on for release; with minification still disabled (#24) release has no mapping file to send, so the setting only starts doing work when that lands.

### Networking

Translation and message reporting share one Ktor client (`HttpClientProvider`), created lazily with the OkHttp engine. Ktor and that engine are already on the classpath for the OpenAI client, so no dependency is added, and naming the engine explicitly avoids the APK scan that picking one by `ServiceLoader` costs (see [Threading](#threading)). The client sets connect/socket/request timeouts, so a stalled network cannot block a coroutine indefinitely.

Redirects are left to OkHttp (`followRedirects = false` on the Ktor side, `followRedirects(true)` on the engine): both endpoints are Google Apps Script and answer every request — GET and POST alike — with a 302 whose target only accepts GET. Ktor's `HttpRedirect` does not follow redirects for POST at all, and following one with the method preserved returns 405; OkHttp downgrades a 302 to GET as the HTTP spec says.

`TranslateUtil` builds its query with `parameter()`, which encodes the value, and returns `Result<String>`:

- Concatenating the text into the URL broke on `&` (`tea & coffee` reached the endpoint as `tea `), on `#` (everything from it, including `target`, was dropped) and on `+` (decoded as a space). Non-ASCII and spaces happened to survive only because Android's `HttpURLConnection` is backed by OkHttp, which canonicalizes the URL.
- Returning `getString(R.string.message_error)` as the "translation" made a failure indistinguishable from a result: it was sent to Dialogflow as the user's message, shown as Miku's reply, and stored in the history. `DetectIntent.request()` now unwraps with `getOrThrow()` and lets `MainActivity.runAITask()` show it as an error instead.

`ReportUtil` builds its payload with `JSONObject` and declares `POST` explicitly instead of relying on `doOutput`. String concatenation produced invalid JSON for any name, message or reason containing `"`, `\` or a newline, and Apps Script answers invalid JSON with an error *page* under status 200 — so the report was dropped while the user saw the success toast.

`MainActivity.shouldInterceptRequest()` keeps its own `HttpURLConnection` for the avatar page's assets: `WebView` wants a synchronous `InputStream` that it reads at its own pace, so the connection has to outlive the call and cannot be wrapped in a suspending client.

### Database

Room ORM with a single `MessageEntity` table. Two schema versions with auto-migration (v1→v2). DAO in `MessageDao.kt`, conversions in `DatabaseUtil.kt`.

`MessageDao` never reads the whole table: `getLatest(limit)` returns the newest page and `getOlderThan(sendTime, id, limit)` walks backwards from the oldest row already loaded, so the pages do not shift when new messages arrive. Both order explicitly by `sendTime DESC, id DESC` instead of relying on the implicit rowid order — `sendTime` is `0` for every row written before the column existed, and `id` breaks those ties into insertion order. `MainActivity` loads one page (`HISTORY_PAGE_SIZE`) at startup and loads the previous page when the user pulls the chat list down (`ChatView` wraps the list in a `SwipeRefreshLayout`); the gesture is disabled again once a page comes back short. `chatmessageview` has no API to prepend to `MessageView` or to redraw it, so `prependMessages()` inserts into the public `messageList` and then adds and removes a throwaway message to make the library re-sort and redraw, keeping the reading position with `setSelectionFromTop`.

### Conversation Context

The Room history is the single source of truth for what Miku remembers. `MainActivity.buildOpenAIMessages()` sends the system prompt, the recent history and the current message, so the conversation stays connected and survives a restart. The predecessor of this — a single `openAIPreviousResponse` field — held Miku's last reply only, lived in memory, and sent an empty assistant message on the first request.

`loadContextMessages()` reads the history with `getLatest()` (never the whole table, see [Database](#database)) and is bounded twice, because every message included is billed on every request: `max_context_messages` caps the number of messages and `max_context_chars` caps their total length, dropping the oldest first. Either limit at `0` disables the context. Messages from both Miku user ids (Dialogflow and GPT) are sent as `Assistant`, since they are all Miku's side of the same visible conversation.

The message being sent is appended explicitly, and a newest row that is an identical user message is dropped: `onClick()` saves the sent message in a coroutine that races with the request, so the history may or may not already contain it, and both cases have to produce the same request. This also covers "send again" on an older message, whose text is then repeated as the newest user message on purpose.

Dialogflow's side is unchanged. Its context is server-side and tied to the session id, which `getDialogFlowSession()` derives from `System.currentTimeMillis()`, so it still resets on every launch.

The reply is streamed (`chatCompletions`) into the "thinking" bubble, which `showStreamingResponse()` mutates and redraws; `receiveMessage()` then removes it and adds the final message, which is the one stored in the history. Because the bubble is the typing indicator, the existing cleanup already removes a partial reply when a request fails. The redraw goes through `ChatView.updateMessageStatus()` with the status unchanged, the only public path to `notifyDataSetChanged()` in `chatmessageview` — guarded by a `messageList.contains()` check, since the library indexes into the list without checking and clearing the history mid-stream would otherwise crash. Updates are throttled to `STREAMING_UPDATE_INTERVAL_MS` and follow the growing bubble with `setSelection()` only while the list is at the bottom: redrawing per chunk and calling `ChatView.scrollToEnd()` (a `smoothScrollToPosition` animation) each time kept the main thread busy enough to produce an input-dispatch ANR on a long reply.

### Input Length

`max_user_text_length` used to be applied only by cutting the text with `substring()` at send time, which silently dropped what the user had typed. `setupInputLengthCounter()` now puts an `InputFilter.LengthFilter` and a counter (`current/limit`) on the input box, so the limit is visible and reached before sending; the cut at send time is kept as a safety net. `ChatView` does not expose its `EditText`, so it is looked up by `R.id.inputBox` (the library's ids are on the app's `R` because `android.nonTransitiveRClass=false`) and the counter is added next to the send button. Both are skipped with a warning if the lookup fails, so a library layout change cannot break sending. The limit is re-applied in `onRemoteConfigReady()`, since the fetched value can differ from the fallback, and the applied value is cached in a field so the counter does not read RemoteConfig on every keystroke.

### Chat Icons

`R.drawable.normal` is the icon of every message Miku sends. It lives in `drawable-nodpi`, because a bitmap in `drawable/` counts as mdpi and `decodeResource` then scales it up by the screen density. It is decoded once into `MainActivity.mikuIcon` and shared by every `User` instance; `decodeSampledBitmap` (`BitmapUtil.kt`) shrinks it to the displayed size (`R.dimen.chat_icon_size`, which matches the library's `icon_normal`). Decoding it per message produced a ~21 MB bitmap each time at 420 dpi and got the process killed by `lowmemorykiller` once the history reached a few hundred messages.

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
