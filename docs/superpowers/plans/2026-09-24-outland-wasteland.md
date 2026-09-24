# OUTLAND: WASTELAND Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the existing Java/libGDX Android voxel prototype into OUTLAND: WASTELAND through a sequence of playable, independently verifiable milestones, beginning with a polished offline narrative-survival vertical slice.

**Architecture:** Preserve the native Java + libGDX project and separate deterministic simulation/domain state from rendering, content definitions, persistence, and platform/network adapters. Build a cohesive single-player vertical slice first; add world streaming, systemic NPCs, settlement depth, and host-authoritative co-op only after their dependencies and performance limits are validated. Keep authored narrative and curated content data-driven so consequences are testable and content can expand without coupling it to rendering.

**Tech Stack:** Java 17, libGDX 1.14.2, Android Gradle project, JUnit/core JVM tests, GitHub Actions Android build.

**Spec:** `docs/superpowers/specs/2026-09-24-outland-wasteland-design.md`

## Global Constraints

- Android is a primary target; preserve native Java + libGDX (no browser runtime, Godot, or engine source build).
- Maintain offline-capable solo play; no mandatory account or always-online dependency.
- Initial build has no ad SDK, tracking, intrusive ads, or ad-gated core progression.
- Keep the starting radio ordinary and broken; no radiation scanner at game start.
- No default minimap or compass; navigation is exploration-led.
- Use versioned saves and deterministic/seeded state where appropriate; never silently overwrite a valid save with a corrupt or conflicting one.
- Use explicit entity, chunk, memory, draw-call, and simulation budgets; graceful capacity limits are mandatory.
- Co-op is invite-only and host-authoritative; host migration is best-effort with recoverable checkpoints, not a guarantee of lossless migration under every failure.
- Guests retain eligible character progression; host owns the world save. Prevent duplication and protect quest-critical assets.
- Do not claim unimplemented vision features are shipped; verify builds/tests and clearly distinguish CI from device testing.

## Review Focus

- Corrupt, older-version, or interrupted saves must recover safely rather than silently reset/overwrite progress (persistence milestone).
- Seed changes and chunk unload/reload must not duplicate or erase story-critical content (world milestone).
- Dialogue choices must remain valid when prerequisites, NPC knowledge, or faction state changes (narrative milestone).
- NPC/settlement load spikes must respect Android budgets and degrade distant simulation predictably (simulation milestone).
- Co-op disconnects, duplicate item transfers, and stale host checkpoints must not silently fork or corrupt the world (network milestone).

---

## Delivery strategy

Do not attempt the entire vision in one giant code change. Each milestone must leave the app buildable and produce a playable/testable increment. The first release target is a **vertical slice**, not the full open world: crash-site opening, a small authored region, one settlement, a repairable radio with real broadcasts, consequence-bearing dialogue, basic survival/combat, and a short train acquisition/upgrade loop. Large-scale simulation and co-op follow only after this slice is stable.

## File and module map

Retain existing `core/src/main/java` package conventions and adapt exact paths after inspecting the current tree. Expected responsibility boundaries:

- `OutlandGame`: lifecycle/composition root only.
- `world/`: seeded region/chunk data, authored POI placement, streaming boundaries.
- `player/`: player state, movement, survival, combat-facing state.
- `input/`: platform-neutral action state and control mapping.
- `render/`: libGDX rendering, effects, camera modes; no authoritative game rules.
- `ui/`: HUD, dialogue, inventory, radio, menus; UI emits intents rather than mutating world state directly.
- `save/`: versioned serialization, migrations, atomic writes, recovery/checkpoint policy.
- `narrative/`: dialogue graph, choice requirements/effects, quest flags, relationship/reputation state.
- `radio/`: frequency/signal definitions, reception model, repair/module progression, discovery log.
- `items/` and `crafting/`: item definitions, inventory rules, recipes, component upgrades.
- `actors/` / `ai/`: NPC definitions, behavior, perception, schedules, companion orders.
- `train/`: locomotive/cars, fuel, modules, storage, route capability.
- `settlement/` and `factions/`: worker assignments, production, faction state, distant simulation.
- `network/`: session protocol, authoritative commands, replication, checkpointing, migration election; isolated from core offline simulation.
- `content/`: authored region, dialogue, item, signal, quest, and encounter data.
- `core/src/test/`: deterministic simulation, narrative, save, and rule tests.

Exact package/file names are to be reconciled with the repository before each milestone; avoid speculative mass-renames.

## Milestone 0 — Baseline and guardrails

### Task 0.1: Inventory the current implementation

**Files:** Inspect `README.md`, Gradle files, `core/src/main/java/**`, `core/src/test/**`, Android manifest, and CI workflow. No behavior changes.

- [ ] Record current launch flow, renderer, input, world/player models, save schema, and existing tests.
- [ ] Run `./gradlew :core:test :android:assembleDebug` (or the repository's documented Gradle command) and record exact results.
- [ ] Identify current frame-time/memory observations on available target hardware; mark device tests unavailable if no device is connected.
- [ ] Commit a short baseline note under `docs/superpowers/` with known-good command/results.

### Task 0.2: Establish regression gates

**Files:** Modify CI workflow only if needed; add tests under `core/src/test/**`.

- [ ] Add/confirm deterministic tests for seeded world generation and save/load round-trip using current APIs.
- [ ] Confirm CI runs core tests before APK packaging and fails on test failure.
- [ ] Build debug APK and inspect its archive for required libGDX native libraries.
- [ ] Commit the baseline gates.

## Milestone 1 — Playable crash-site vertical slice

### Task 1.1: Separate game state from presentation

**Files:** Existing player/world models; `OutlandGame`; new domain-state classes only where required; matching core tests.

- [ ] Write failing tests for state transitions independent of rendering/input devices.
- [ ] Move authoritative player/world state transitions behind small core APIs; keep `OutlandGame` as composition root.
- [ ] Ensure render and UI read state and submit actions rather than owning game rules.
- [ ] Run core tests and Android debug assembly; commit.

### Task 1.2: Build the crash-site opening

**Files:** `content/` opening data; `narrative/` dialogue model; `ui/` dialogue screen; world POI placement; tests.

- [ ] Test opening-state initialization and dialogue-choice effects before implementation.
- [ ] Implement black-screen/ringing/dizziness opening sequence with a skippable accessibility-safe transition.
- [ ] Place crashed car, wreckage, and the protagonist's cracked ordinary radio in the first authored area.
- [ ] Implement stranger's opening conversation with selectable responses and silence; store resulting facts/relationship changes.
- [ ] Verify new game and save-resume preserve opening progress; commit.

### Task 1.3: Make a polished small playable region

**Files:** `world/`, `render/`, `input/`, `ui/`, authored content, tests.

- [ ] Define a small region boundary and explicit active-object/draw-call budgets.
- [ ] Improve terrain silhouettes, lighting, fog/atmosphere, materials, and landmark readability without sacrificing Android frame stability.
- [ ] Add a minimal interaction loop for inspecting wreckage and collecting a few authored supplies.
- [ ] Test region generation determinism and interaction range/invalid-target behavior.
- [ ] Verify touch controls, pause/resume, and low-memory-safe disposal; commit.

## Milestone 2 — Consequence-driven narrative foundation

### Task 2.1: Dialogue and world-fact engine

**Files:** `narrative/` dialogue graph, conditions/effects, relationship/reputation state; tests.

- [ ] Write tests for choice prerequisites, effect application, unavailable choices, silence/disengage, and save/load.
- [ ] Implement data-driven dialogue nodes with typed conditions and effects; reject malformed content at load time.
- [ ] Track witnessed/learned facts separately so NPCs react only to information they could know.
- [ ] Add consequence tiers for dialogue, prices, quest access, and relationship reactions without a global good/evil score.
- [ ] Test deterministic replay of the same choice sequence; commit.

### Task 2.2: Quest and authored ending framework

**Files:** `narrative/` quest state and ending evaluation; authored content; tests.

- [ ] Test multi-quest prerequisites, mutually exclusive outcomes, and ending evaluation from accumulated state.
- [ ] Implement quest stages and consequence flags as content data, not UI-specific branches.
- [ ] Author a short vertical-slice quest with at least two materially different outcomes and later NPC acknowledgement.
- [ ] Add a campaign-ending contract/interface, but do not fabricate the full campaign's final story before its authored content is reviewed.
- [ ] Commit with content validation tests.

## Milestone 3 — Radio and scavenged electronics

### Task 3.1: Grounded radio and signal model

**Files:** `radio/`, `content/signals`, `ui/` radio screen, tests.

- [ ] Test signal eligibility by distance, region, terrain/interference, radio condition, and story state.
- [ ] Implement conventional reception: broadcasts, distress calls, faction traffic, emergency loops.
- [ ] Give major story locations larger effective broadcast ranges through authored signal parameters.
- [ ] Add signal discovery/logging that persists across save/load and does not become a quest-arrow/minimap substitute.
- [ ] Commit.

### Task 3.2: Repair, upgrades, and electronics loot

**Files:** `radio/` repair/module progression; `items/`; `content/electronics`; tests.

- [ ] Test module compatibility, resource costs, and reconfiguration/respec rules.
- [ ] Implement repair tiers and modular reception/directional scanning upgrades; radiation analysis remains a later constructed module, never a starting feature.
- [ ] Add authored smartphone/computer/terminal records with validated quest hooks and location-bound content.
- [ ] Test duplicate-reading prevention and content/save migration; commit.

## Milestone 4 — Survival, combat, inventory, and death recovery

### Task 4.1: Survival simulation

**Files:** `player/` survival state; `items/`; `ui/` status display; tests.

- [ ] Test hunger, thirst, temperature, injuries, healing, and boundary/clamp behavior with a controllable clock/tick.
- [ ] Implement tunable survival rates and readable warnings; avoid unavoidable softlocks.
- [ ] Add consumables and safe save/resume behavior for survival state.
- [ ] Commit.

### Task 4.2: Combat and death backpack

**Files:** combat/weapon domain classes, `actors/` damage interfaces, inventory, death/recovery logic; tests.

- [ ] Test ammo consumption, reload interruption, damage, stealth/melee resolution, and death transition.
- [ ] Implement a small conventional weapon set with deliberate handling, plus melee and one trap/explosive interaction.
- [ ] On death, create a recoverable backpack record; keep secured train storage and quest-critical items protected.
- [ ] Add scavenger theft/recovery as a staged feature only after NPC navigation and persistence are reliable; initially provide deterministic recovery rules.
- [ ] Commit.

## Milestone 5 — Train and region progression

### Task 5.1: Train as mobile base

**Files:** `train/`, inventory/storage, world route gating, UI, tests.

- [ ] Test train fuel, movement state, module compatibility, storage ownership, and persistence.
- [ ] Implement a modest functional locomotive loop: fuel, travel between defined stops, secured storage, and one upgradeable car/module.
- [ ] Ensure train state is versioned and recovered atomically; commit.

### Task 5.2: Connected regions and rail generation

**Files:** `world/` region graph/streaming, railway topology, authored POIs; tests.

- [ ] Test seeded route connectivity, valid curves/bridges/tunnels, train clearance, and stable authored POI placement.
- [ ] Implement a finite region graph with authored major destinations and constrained seeded branches.
- [ ] Stream track/terrain ahead of the train and unload behind it; persist consequential changes and regenerate only disposable seeded content.
- [ ] Verify unload/reload and save-resume do not duplicate unique loot/story locations; commit.

## Milestone 6 — NPCs, companions, factions, settlements

### Task 6.1: NPC behavior and companions

**Files:** `actors/`, `ai/`, `narrative/` relationship hooks, companion UI/equipment, tests.

- [ ] Test deterministic decision inputs, command validation, equipment constraints, recruitment, and companion persistence.
- [ ] Implement a bounded active-agent system with simple perception, routines, contextual autonomy, and direct commands.
- [ ] Add recruitment, loadouts, limited expedition squad, relationship state, and one companion quest.
- [ ] Profile active-agent caps on target Android hardware; commit.

### Task 6.2: Faction and settlement simulation

**Files:** `factions/`, `settlement/`, distant simulation scheduler, tests.

- [ ] Test compact-state updates, deterministic seeded outcomes, worker assignments, production costs, and event persistence.
- [ ] Implement one functional settlement with modular construction, worker jobs, production, supplies, and defense.
- [ ] Simulate unloaded regions through bounded state updates; preserve important events/loot and avoid per-frame distant AI.
- [ ] Add trader/raid triggers driven by settlement/faction state and commit.

## Milestone 7 — Crafting, experimental inventions, and advanced progression

### Task 7.1: Recipe and component crafting

**Files:** `crafting/`, item definitions, train/settlement upgrade definitions, tests.

- [ ] Test recipe validation, ingredient consumption, output caps, and save/load of discovered recipes.
- [ ] Implement curated recipes and component-based equipment/train/settlement upgrades.
- [ ] Commit.

### Task 7.2: Constrained experimentation

**Files:** `crafting/` experiment rules/property library, discovery log, tests.

- [ ] Test invalid combinations, bounded outcomes, resource loss, and repeat-discovery behavior.
- [ ] Implement curated component/property combinations; no unconstrained arbitrary item generation.
- [ ] Add discoverable schematics and useful failure feedback; commit.

## Milestone 8 — Offline save integrity and co-op readiness

### Task 8.1: Save schema, atomicity, and checkpoints

**Files:** `save/`, migration/recovery tests.

- [ ] Test interrupted writes, corrupt newest checkpoint, older schema migration, and conflicting snapshots.
- [ ] Implement atomic versioned snapshots, rotating checkpoints, checksums, and explicit recovery selection.
- [ ] Verify player/world ownership fields and stable IDs for transferable entities; commit.

### Task 8.2: Co-op protocol spike (not yet production multiplayer)

**Files:** isolated `network/` protocol/session abstractions and tests; no UI dependency.

- [ ] Document and test a host-authoritative command/state boundary while offline mode uses the same simulation APIs.
- [ ] Prototype invite/session lifecycle and safe-location spawn validation with a small fixed test harness.
- [ ] Define replication priorities and dynamic capacity rejection based on measured CPU, memory, and bandwidth budgets.
- [ ] Test disconnect, stale checkpoint, duplicate transfer, and host-candidate readiness scenarios.
- [ ] Do not label host migration production-ready until multi-device soak tests demonstrate recovery; commit the spike separately.

### Task 8.3: Host migration and hybrid ownership

**Files:** `network/` election, replication, checkpoint restore, transfer validation; tests.

- [ ] Test composite host selection using peer latency, stability, upload capacity, device headroom, and state readiness.
- [ ] Implement migration checkpoints and coordinated graceful handoff; handle abrupt loss by selecting the newest valid shared checkpoint.
- [ ] Enforce host-owned world state and guest-owned eligible character progression with anti-duplication transfer validation.
- [ ] Test failed migration fallback and later resume without silent world forks.
- [ ] Validate on multiple physical devices/networks before claiming robust migration; commit.

## Milestone 9 — Release hardening and optional monetization seam

### Task 9.1: Performance, accessibility, and device QA

**Files:** renderer, streaming/simulation budgets, input/UI, diagnostics, CI, README.

- [ ] Profile representative low/mid-range Android hardware for frame time, memory, thermal behavior, save latency, and long-session stability.
- [ ] Add quality tiers and distant-simulation degradation before compromising input responsiveness or save integrity.
- [ ] Test touch controls, first-/third-person camera switching, pause/resume, low storage, and recovery flows.
- [ ] Run clean CI build/tests and document exactly which device checks were performed; commit.

### Task 9.2: Monetization-ready boundary (no ads enabled)

**Files:** platform service interfaces/configuration only; tests/docs.

- [ ] Define optional reward/cosmetic/ad-removal interfaces with no-op offline implementation.
- [ ] Test that gameplay, saves, rewards, and co-op remain fully functional when no monetization provider is configured or network is unavailable.
- [ ] Keep SDKs, tracking, and ad placements out of the initial build; commit.

## Completion criteria for the first vertical slice

- A fresh install can start offline, play the crash-site opening, talk to the stranger through selectable choices, explore a polished small region, discover and repair the ordinary radio, receive at least one authored broadcast, complete a consequence-bearing quest, survive a basic encounter, acquire/use a train in a limited route loop, save, quit, and resume without losing or duplicating key state.
- Core rules have deterministic JVM tests; Android debug APK assembles in CI.
- Performance and device-runtime status are reported honestly; no claim of full open-world/co-op completion based solely on compilation.
