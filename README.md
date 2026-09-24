# OUTLAND Native — Android voxel sandbox

Native Android project using Java + libGDX (no browser runtime, Godot, or engine source build).

## Build APK in GitHub Actions
Push to `main`, or run **Actions → Build OUTLAND APK → Run workflow**. Download the `OUTLAND-debug-apk` artifact from the completed workflow. CI runs core JVM tests before packaging.

## Local build
Requires JDK 17+, Android SDK, and Gradle:

```sh
gradle --no-daemon :core:test :android:assembleDebug
```

APK: `android/build/outputs/apk/debug/`

## Architecture
- `OutlandGame` is the lifecycle/composition root.
- `world/` contains pure Java voxel storage and seeded terrain generation.
- `player/` owns player simulation; `input/` defines per-frame action state.
- `render/` owns GL models and the world renderer; `ui/` owns HUD resources.
- `save/` contains a versioned world/player binary snapshot format (not yet exposed as an in-game menu).
- `diagnostics/` persists caught runtime/lifecycle failures to app-private storage.

## Crash diagnostics
OUTLAND installs its uncaught-exception handler from `OutlandApplication`, before the launcher Activity. Crash/runtime records are staged under the app's private `files/outlandlogs/` directory first. The app requests legacy external-storage write permission and, when granted, copies staged records to `/sdcard/outlandlogs/`. If permission is denied or Android blocks public-folder access, the private staged copy remains; inspect/export it through Android app storage tooling. A public write is best-effort, not guaranteed. Ordinary apps cannot read unrestricted system logcat/tombstones, and no background logger is guaranteed to survive force-stop or OS process cleanup.

## Current status
The voxel sandbox loop remains a prototype: seeded terrain, first-person movement/jump, mining/placing, inventory, and touch controls. The code has been reorganized into subsystem boundaries, but device-specific stability still requires testing on the target phone. Save serialization exists but is not yet wired into a save/load UI. CI build/test results—not a successful local/device run—are the verification source for this branch.
