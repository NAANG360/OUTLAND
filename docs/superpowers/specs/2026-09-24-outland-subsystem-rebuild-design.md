# OUTLAND Subsystem Rebuild Design

## Goal
Rebuild the internals of OUTLAND while preserving its Java + libGDX Android voxel-sandbox identity and core play loop: terrain, first-person movement, mining/placing, inventory, and touch controls. This is a structural rebuild, not a cosmetic patch or a wholesale game-concept replacement.

## Current-state findings
The Android launcher directly initializes libGDX. `OutlandGame` currently owns bootstrap, world generation/storage, player movement, input, rendering, HUD, and error presentation in one class. Startup/render exceptions are converted to a screen message, but there is no dependable durable crash-report pipeline. The current Android manifest is minimal and the app targets SDK 28.

## Architecture
Keep libGDX and Java. `OutlandGame` becomes a lifecycle/composition root only. Separate packages/classes own:
- `world`: block data, terrain generation, block queries and mutation.
- `player`: position, velocity, collision/grounding, camera orientation.
- `input`: keyboard/touch interpretation into game actions; no world mutation directly.
- `render`: world renderer and its GPU resources, created/disposed only on the GL lifecycle.
- `ui`: HUD drawing and touch affordances, separate from simulation.
- `save`: versioned world/player snapshot persistence, independent of rendering.
- `diagnostics`: session lifecycle, uncaught-exception capture, bounded log files and export/storage status.

A small shared model/config layer defines block IDs, game actions, and tunables. No subsystem initializes/disposes another subsystem's resources. The lifecycle root initializes in dependency order and disposes in reverse order. A failure in optional diagnostics must never prevent the game from launching.

## Android diagnostics and storage
Initialize diagnostics as early as practical in `Application.onCreate` (with a manifest-declared custom Application), before the launcher Activity. Install a default uncaught-exception handler that writes a compact crash record synchronously to app-private storage before delegating to the prior handler. A separately declared Android service process may maintain a heartbeat/session journal and flush staged records, but it is best-effort—not guaranteed to survive force-stop, process-group cleanup, OS reclaim, or device shutdown. Do not promise unrestricted system logcat/tombstone access to an ordinary app.

The requested public destination is `/sdcard/outlandlogs/`. Since Android storage permissions and scoped-storage rules vary by OS/target SDK, write first to app-private durable staging, then attempt a public-folder flush when legacy external-storage permission is granted. Provide an explicit SAF folder authorization/export fallback for Android versions where direct access is blocked. Never silently report success when the public write failed; retain staged files and expose the exact failure reason. Keep per-session timestamped files, bounded size/rotation, and a storage status marker. Logger failures are swallowed after recording what can be recorded.

## Gameplay and runtime behavior
Preserve the existing voxel prototype's feature intent and mobile-first orientation. Rebuild the simulation/render boundaries without adding unrelated systems. Simulation state must not depend on GPU object lifetime. Rendering consumes world state and owns only render resources. Input produces actions; player/world systems apply them. Use deterministic, testable world-generation inputs where practical.

## Failure handling
- Bootstrap stages are individually logged.
- A subsystem initialization failure unwinds already-created resources in reverse order.
- Rendering/simulation errors are recorded and shown in a safe diagnostic overlay when a valid GL context remains; fatal JVM errors still use the uncaught handler.
- Diagnostics are fail-open: inability to create or flush logs cannot crash the game.
- Crash records include timestamp, app/build version, thread, throwable stack trace, last lifecycle stage, and storage-write outcome where available.

## Verification
CI must compile the Android app and run JVM unit tests for pure world/player logic, block-coordinate/key behavior, terrain determinism, and save/load round trips. Static checks should verify manifest declarations and logger path/permission behavior. Device verification remains necessary for actual GPU/device-specific launch stability and shared-storage permission/SAF behavior; CI success is not represented as proof of device stability.

## Non-goals
No engine migration, no unrelated gameplay expansion, no claims of guaranteed background-process survival, unrestricted logcat access, or guaranteed root-folder writes without user-granted access. Existing code may be replaced where it conflicts with the architecture.