# OUTLAND Native — Android voxel survival

Native Android project using libGDX + Java (no browser runtime, no Godot, no engine source build).

## Build APK in GitHub Actions
1. Create a GitHub repo and upload this project.
2. Push to `main`, or run **Actions → Build OUTLAND APK → Run workflow**.
3. Download the `OUTLAND-debug-apk` artifact from the completed workflow.

## Local build
Requires JDK 17+, Android SDK, and Gradle:
`gradle :android:assembleDebug`
APK: `android/build/outputs/apk/debug/`

## Current foundation
- Native Android/libGDX app
- Seeded procedural heightmap terrain, underground layers, tree generation
- First-person camera and basic movement/jump
- Voxel block data, mining/placing interaction methods, inventory/hotbar HUD
- Android landscape orientation

## Honest status
This is a native playable foundation/scaffold, not yet a finished Minecraft-scale game. The current renderer uses per-block model instances and the interaction UI still needs dedicated on-screen buttons/raycast polish. Chunk meshing, robust save/load, crafting, mobs, caves/ores, and optimization are follow-on systems. Build has not been executed in this environment; CI is included to compile it remotely.
