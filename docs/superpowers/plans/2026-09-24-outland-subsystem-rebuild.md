# OUTLAND Subsystem Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace OUTLAND's monolithic game loop with independently owned gameplay/render/input/save/diagnostic components while retaining its voxel sandbox play loop and making crash records durable.

**Architecture:** Keep Java + libGDX and the Android application module. `OutlandGame` becomes the composition root; pure world/player state is separated from GL resources; Android startup installs a durable crash journal before the launcher Activity.

**Tech Stack:** Java, libGDX 1.14.2, Android Gradle Plugin 8.10.1, Android SDK 36, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-24-outland-subsystem-rebuild-design.md`

## Global Constraints

- Preserve the voxel sandbox concept and existing gameplay goals: terrain, movement, mining/placing, inventory, and mobile controls.
- Keep Java + libGDX; do not migrate engines.
- Keep `/sdcard/outlandlogs/` as the requested public destination, but stage crash records in app-private storage first.
- Diagnostics are best-effort and must never prevent launch.
- Do not claim unrestricted logcat/tombstone access or guaranteed child-process survival.
- Rendering resources are created/disposed only on the libGDX lifecycle.

## Review Focus

- Public storage permission denied/unavailable: retain crash file privately and record export failure.
- Exception during bootstrap before GL initialization: uncaught handler still persists stack trace.
- Renderer/resource creation failure: no invalid dispose calls; leave diagnostic evidence.
- Empty or mutated world: block queries and render iteration remain safe.
- App pause/resume/resize: lifecycle-safe render/input handling.

---

### Task 1: Establish early durable Android crash journal

**Files:**
- Create: `android/src/main/java/com/outland/game/OutlandApplication.java`
- Create: `android/src/main/java/com/outland/game/CrashJournal.java`
- Modify: `android/src/main/AndroidManifest.xml`
- Modify: `android/build.gradle`

**Interfaces:** `CrashJournal.install(Context)`, `CrashJournal.record(String, Throwable)`, and `CrashJournal.flushPending(Context)`; the uncaught handler writes to app-private storage synchronously and attempts public-folder output only when permitted.

- [ ] Add a JVM-testable filename/format policy and Android integration checks through build.
- [ ] Install a custom `Application` and handler before the launcher Activity.
- [ ] Request legacy external write permission at runtime from the Activity; preserve internal logs if denied.
- [ ] Verify manifest merge and Android compile; never let logging errors crash startup.
- [ ] Commit.

### Task 2: Extract deterministic world model

**Files:**
- Create: `core/src/main/java/com/outland/game/world/BlockType.java`
- Create: `core/src/main/java/com/outland/game/world/World.java`
- Create: `core/src/main/java/com/outland/game/world/TerrainGenerator.java`
- Create: `core/src/test/java/com/outland/game/world/WorldTest.java`
- Modify: `core/build.gradle`

**Interfaces:** `World.generate(long seed)`, `World.getBlock(int,int,int)`, `World.setBlock(int,int,int,BlockType)`, `World.removeBlock(...)`, `World.snapshot()`; world data contains no libGDX `Model` or `ModelInstance` references.

- [ ] Add tests for empty lookup, set/remove, bounds/key uniqueness, and same-seed terrain generation.
- [ ] Implement pure-Java voxel storage and deterministic terrain/tree generation.
- [ ] Run core tests and commit.

### Task 3: Extract player simulation and action input

**Files:**
- Create: `core/src/main/java/com/outland/game/player/PlayerState.java`
- Create: `core/src/main/java/com/outland/game/player/PlayerController.java`
- Create: `core/src/main/java/com/outland/game/input/GameAction.java`
- Create: `core/src/main/java/com/outland/game/input/InputState.java`
- Create: `core/src/test/java/com/outland/game/player/PlayerControllerTest.java`

**Interfaces:** `PlayerController.update(PlayerState, InputState, World, float)`; `InputState` is a value snapshot of movement/look/jump/mine/place/selection actions. Input parsing never mutates the world directly.

- [ ] Test gravity/grounding and bounded delta-time behavior.
- [ ] Implement isolated player state/update and action snapshot.
- [ ] Run core tests and commit.

### Task 4: Isolate renderer, HUD, and composition lifecycle

**Files:**
- Create: `core/src/main/java/com/outland/game/render/WorldRenderer.java`
- Create: `core/src/main/java/com/outland/game/ui/HudRenderer.java`
- Create: `core/src/main/java/com/outland/game/input/TouchInputAdapter.java`
- Modify: `core/src/main/java/com/outland/game/OutlandGame.java`

**Interfaces:** `WorldRenderer.create()`, `render(World, PlayerState)`, `dispose()`; `HudRenderer.render(...)`, `dispose()`; `OutlandGame` coordinates creation/update/render/disposal and owns subsystem ordering.

- [ ] Render world blocks from pure world data; cache/own GPU models only in renderer.
- [ ] Move HUD drawing out of the game class.
- [ ] Map touch/keyboard input to `InputState`, route actions through game/player/world logic.
- [ ] Ensure partial initialization cleanup is null-safe and reverse-ordered.
- [ ] Run Android/core build and commit.

### Task 5: Add versioned save/load and diagnostic export status

**Files:**
- Create: `core/src/main/java/com/outland/game/save/WorldSave.java`
- Create: `core/src/test/java/com/outland/game/save/WorldSaveTest.java`
- Modify: Android diagnostic journal/export implementation.

**Interfaces:** `WorldSave.write(OutputStream, World, PlayerState, long)` and `WorldSave.read(InputStream)` return validated, versioned snapshots; Android layer owns filesystem selection and export status.

- [ ] Test save/load round-trip and invalid/truncated version rejection.
- [ ] Implement serialization independent of rendering classes.
- [ ] Ensure failed public log export retains private pending log and records status.
- [ ] Run tests/build and commit.

### Task 6: CI verification and handoff

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`

- [ ] Run CI on the rebuild commit and inspect job result/logs.
- [ ] Document log location, permission/SAF limitations, and private fallback location.
- [ ] Confirm final commit and list device-only validation still required.
