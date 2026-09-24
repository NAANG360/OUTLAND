# OUTLAND Android Crash Recorder — Design Spec

**Status:** Draft for review  
**Date:** 2026-09-24  
**Scope:** Android launcher diagnostics for OUTLAND

## 1. Goal

Capture useful diagnostics from the earliest practical point in Android app startup and preserve them when OUTLAND's game/launcher process crashes. Logs must be exportable in the user-requested shared-storage location **`/sdcard/outlandlogs/`**. The feature is diagnostic-only and must not change game behavior or attempt to prevent/restart a crash loop.

## 2. Current project context

The repository is a small Gradle/libGDX project. Android currently has a single `AndroidLauncher` activity declared in `android/src/main/AndroidManifest.xml`; there is no existing Android `ContentProvider`, logging service, or manifest storage permission. The latest `main` commit at design time is `8c4a98e`.

## 3. Proposed architecture

### 3.1 Early initialization

Add a minimal Android `ContentProvider` declared in the manifest with an early init order. Its `onCreate()` performs only lightweight logger bootstrap and starts the dedicated logger service before `AndroidLauncher` is created. It must not initialize libGDX, graphics, audio, or game assets.

The provider is an early-process hook, not a guarantee of execution before Android creates the app process. It must fail open: logging initialization errors must never prevent the game from launching.

### 3.2 Isolated logger process

Run a dedicated Android `Service` under a separate process name such as `:crashlogger`. The service owns log writing, session metadata, heartbeat timestamps, rotation, and finalization. Keep it independent of libGDX and avoid references to game/rendering classes.

The separate process is intended to continue flushing already-collected records after the game process dies. It is **not** guaranteed to survive Android killing the app, force-stop, device shutdown, OEM background restrictions, or low-memory termination. Do not claim otherwise. Use a bounded restart/recovery strategy only if Android's service lifecycle permits it; do not create an unbounded restart loop or require a permanent foreground notification by default.

### 3.3 Diagnostic sources

Collect and persist:
- Session start/end markers, app version/build, Android SDK/device basics, process ID, and timestamps.
- Logger/service lifecycle and heartbeat records.
- OUTLAND-owned diagnostic messages and Java/Kotlin uncaught exceptions, including stack traces; chain the prior uncaught-exception handler so normal crash handling remains intact.
- Best-effort logcat capture, clearly marked with its source and availability.

Android restricts access to system-wide logcat on ordinary production apps. Logcat capture must be treated as best-effort and may be empty, partial, or denied depending on Android version/device. Never imply this is equivalent to ADB/root logcat or tombstone access. Do not collect unrelated personal data.

### 3.4 Storage: `/sdcard/outlandlogs/`

The desired output directory is exactly `/sdcard/outlandlogs/`, with timestamped session folders/files and a small index/manifest. Use Android's supported shared-storage access mechanism. Because direct writes to the shared-storage root are restricted on modern Android/target SDK combinations, request a one-time Storage Access Framework directory grant for the `outlandlogs` folder (or allow the user to create/select it) and persist the URI grant. Do not silently substitute app-private storage while reporting that the requested path was honored.

If direct access to `/sdcard/outlandlogs/` is not available, show a clear setup/error state and offer the SAF folder-selection flow. Document any Android-version-specific requirement. Logging must continue to a bounded app-private staging area until the shared folder is authorized; when access becomes available, copy/flush staged records to the requested directory. Never crash the game because storage is unavailable.

Suggested output layout:

```text
/sdcard/outlandlogs/
  session-YYYYMMDD-HHMMSS/
    session.txt
    crash.txt            # only when an uncaught crash is recorded
    logcat.txt           # only if capture is available
    metadata.json
```

Use append/flush behavior that prioritizes retaining recent diagnostics without excessive per-frame I/O. Rotate by size and age, cap total retained logs, and avoid deleting the newest session. Exact limits should be configurable constants with conservative defaults.

### 3.5 Crash and process lifecycle

The logger process periodically writes a heartbeat. On next startup, compare the prior session's last heartbeat and clean shutdown marker; if the previous session ended without a clean marker, annotate it as an unexpected termination (not proof of a crash). The main process reports uncaught exceptions to the logger over a small IPC channel when possible, while also writing a local emergency crash record synchronously as a fallback. IPC failure must not block crash propagation.

Do not rely on a shutdown callback being invoked after a fatal crash. Do not promise native crash/tombstone capture without privileged access. If the logger process itself fails, the next startup records that gap and proceeds normally.

## 4. User experience

- No diagnostic permission prompt should appear on every launch.
- First-run storage authorization is explained plainly and requested only when needed.
- The game remains launchable if authorization is denied; records remain in bounded private staging and the UI/log reports that export to `/sdcard/outlandlogs/` is pending.
- Provide a simple way to expose the log folder or share a zipped session, if compatible with the existing minimal app structure. Do not add a complex in-game dashboard in this scope.

## 5. Non-goals

- Preventing crashes or automatically fixing startup failures.
- Guaranteed survival after force-stop, OS process kill, reboot, or device shutdown.
- Full privileged system logcat, kernel logs, ANR traces, or native tombstones.
- Uploading logs to a remote server.
- Collecting user content, credentials, or unrelated app logs.
- Refactoring the libGDX game or changing renderer/audio startup behavior.

## 6. Failure handling and privacy

All logger paths are best-effort and fail-open. Catch storage, IPC, permission, and logcat-process errors; record a concise diagnostic when possible; never throw logger failures into the game startup path. Bound buffers, file sizes, session retention, and logcat process lifetime. Avoid logging secrets, tokens, or arbitrary user-entered content. Make it clear that logs may include device/app diagnostic details before sharing.

## 7. Validation criteria

1. Manifest/provider initialization occurs before `AndroidLauncher.onCreate()` in an instrumented startup trace.
2. Logger service starts in its own process and writes a session record before the game initializes.
3. An intentional test exception in the main process produces a durable crash record/stack trace while preserving normal exception propagation.
4. Killing only the main process during a controlled test leaves the logger able to flush already-received records when Android keeps its service process alive; test results must not be generalized to force-stop or OS kills.
5. Storage authorization writes files under the selected `/sdcard/outlandlogs/` directory; denial or unavailable storage does not crash launch and staging remains bounded.
6. Missing/denied logcat capture is reported as unavailable rather than treated as a logger failure.
7. Rotation/retention works, no unbounded log growth occurs, and logger exceptions never crash the game.
8. Android Gradle build and existing CI build pass; no claim of device-level crash-survival validation without an actual device test.

## 8. Implementation boundaries

Expected Android-side changes: manifest declaration, early-init provider, isolated logger service, IPC/record model, storage/SAF handling, crash handler, and focused tests. Keep the libGDX core module untouched unless a minimal logger facade is required. Preserve the repository's current Gradle/Android compatibility and existing startup safeguards.

## 9. Open implementation detail

During planning, verify the app's target SDK and Android Gradle Plugin constraints before choosing the exact SAF bootstrap UX and service flags. The invariant is that the requested destination remains `/sdcard/outlandlogs/`; any Android limitation must be surfaced rather than hidden.