# OUTLAND: WASTELAND — Game Design Specification

**Status:** Draft for owner review  
**Date:** 2026-09-24  
**Scope:** Product vision and system-level design. This is not an implementation plan and does not claim these systems exist in the current prototype.

## 1. Product vision

OUTLAND: WASTELAND is a solo-first, offline-capable open-world survival RPG set in a stylized-realistic Weird West after a civilization-ending collapse. The player wakes beside a crashed car with amnesia and finds a cracked, ordinary portable radio. The radio appears familiar because the player built or heavily modified it before losing their memory. Rebuilding it gradually reconnects the player with the world—and with their own history.

The game combines exploration, deliberate survival combat, consequential dialogue, a customizable mobile train-base, independent companions, faction simulation, settlement construction, and a mystery-driven authored campaign. It should feel atmospheric, beautiful, varied, and grounded rather than relentlessly horror-focused.

**Design pillars**
1. The world remembers what the player does.
2. Exploration—not a minimap or quest-arrow treadmill—drives discovery.
3. The train is a meaningful home, transport system, and long-term progression project.
4. Survivors and factions behave like persistent people/groups, not disposable quest dispensers.
5. Systems are ambitious but degrade gracefully to fit Android memory, CPU, storage, and network limits.
6. Offline solo is a complete experience; co-op is optional.

## 2. Opening and narrative premise

The game opens with a black screen, ringing ears, dizziness, and fragmented vision. The player wakes near a wrecked car on a remote highway. They do not know their name, destination, or what caused the crash. Nearby is a cracked portable radio with a damaged display/tuning controls and unreliable reception. It is initially a conventional radio: no built-in radiation scanner, magical knowledge, or omniscient HUD.

A nearby stranger initiates the first conversation. Dialogue supports multiple responses and silence; choices can alter trust, information revealed, future options, and later reactions. Early exposition is restrained and discovered through people, places, records, and broadcasts.

The protagonist previously built or customized the radio. This is a central personal mystery, but the exact history and campaign revelations must be authored deliberately. The radio may contain custom circuitry, saved frequencies, notes, or modifications that become meaningful as it is repaired. The game must not reveal the full truth in the opening.

### Campaign shape

The main campaign follows a chain of connected frontier regions toward an authored final destination. A broken-down train becomes the player's mobile base and means of reaching otherwise isolated regions. The central mystery links the crash, the protagonist's past, the radio, and the Collapse. The campaign supports materially different outcomes based on accumulated choices, relationships, faction states, and discoveries—not a single final binary dialogue prompt. After the authored ending, the world remains available for post-game exploration and settlement play.

The exact central conspiracy, named cast, quest sequence, and ending scenes are story-authoring deliverables for the campaign phase; they are intentionally not falsely presented as already finalized lore.

## 3. World structure and navigation

The world is composed of connected major regions. Each region combines procedural wilderness and routes with handcrafted settlements, landmarks, dungeons/facilities, and story-critical locations. Seeded generation supports replayability while authored points of interest preserve narrative quality and continuity.

- No default minimap or compass. Navigation relies on landmarks, terrain, railways, signage, NPC knowledge, and player observation.
- Rare physical maps are found in the world. They can reveal actual seed-specific geography, routes, branches, and locations; some may be incomplete, outdated, or annotated.
- Railway topology connects major regions. Procedural extensions and branches add variation while respecting authored route constraints.
- Track is generated ahead of the moving train and unloaded behind it. Major stations, bridges, tunnels, sidings, and story destinations are authored or constrained procedural pieces.
- Story-critical locations and discoveries must be stable across saves and co-op migration. Random encounters may be seeded and regenerated, but consequential outcomes are persisted.
- World streaming uses explicit budgets for active chunks, entities, physics, draw calls, and save data. Distant areas use compact state rather than full simulation.

## 4. Radio and electronics

The starting radio is ordinary, damaged hardware. Its early function is receiving nearby broadcasts, distress calls, faction traffic, and emergency loops. Signal discovery is location- and world-state-aware. Major story locations have larger effective broadcast ranges, so their transmissions can be detected well before arrival.

Radio progression is repair/modification-based, not a mandatory linear tech tree. Candidate branches include:
- **Reception/stability:** clearer reception, broader tuning, better signal separation.
- **Directional/radar tools:** estimate direction or proximity of supported signals and moving sources.
- **Environmental analysis:** a later custom module can detect radiation/exposure conditions; this is not present at game start.
- **Specialized scanning:** discover hidden transmitters, electronic traces, or curated anomaly clues where supported by the story.

The device must not become a universal quest compass. Signal strength, interference, terrain, antenna quality, power, and receiver damage can affect results. Upgrade choices should offer distinct utility while allowing future respec/reconfiguration through scarce components or specialists.

Computers, smartphones, terminals, and other electronics are lootable world objects. They can contain messages, contacts, maps, records, clues, credentials, or quest triggers. Their contents are authored or seed-selected from curated pools and tied to location/story state; they are not arbitrary generated exposition.

## 5. Consequence-driven narrative and dialogue

Dialogue is a first-class system. Conversations present explicit selectable responses, including silence or disengagement where appropriate. Choices can affect NPC trust/fear/respect, companion loyalty, faction standing, quest availability, settlement outcomes, prices, assistance, and endings.

The design is not a simple good/evil meter. Track relevant facts and relationships as world state, and let NPCs react to witnessed or credibly learned events. Consequences should be legible through changed behavior and later dialogue without constantly announcing that a choice was important. Avoid combinatorial full-branch duplication: use authored consequence flags, relationship variables, quest states, and reactivity tiers.

A small, deliberately curated set of NPCs may perform fourth-wall-adjacent humor or appear to notice repeated encounters/reloads. This is rare, character-specific, and never the default tone. Such lines must not undermine ordinary world continuity or rely on impossible access to player data.

## 6. Survival, combat, and death

Survival is challenging but fair. Hunger, thirst, temperature, injuries, radiation exposure (once introduced), and preparation matter. Supplies, ammunition, and medicine are scarce enough to encourage planning, but scarcity must not create unavoidable softlocks.

Combat emphasizes deliberate, grounded gunplay with meaningful recoil/reload/weapon handling, melee, stealth, traps, and explosives as alternatives. Conventional weapons dominate. Experimental technology is extremely rare, often niche or unreliable, and occasionally powerful; failures should create tactical tradeoffs, not arbitrarily erase long-term progression.

On death, the player's carried backpack/loot can be dropped. Scavenger NPCs may gradually take items and physically carry them to a hideout. Players can track, confront, or raid them to recover stolen gear. Secured train storage is protected from ordinary death loss. Recovery systems must clearly communicate what is at risk and avoid permanent loss of unique quest-critical items.

## 7. Train and progression

The train is a customizable transportation-first mobile base. It grows from a damaged locomotive into a meaningful home and infrastructure platform. Customization includes engine, fuel/power systems, cars, storage, workshops, living quarters, defenses, appearance, and specialized modules. Train capability gates or enables some routes and expedition options, but the player must retain meaningful on-foot play.

Power progression may include coal, diesel, and a late-game fictionalized nuclear option using rare parts/fuel and specialist support. High-end power should have substantial costs, hazards, maintenance, and strategic tradeoffs; it must not be a free universal upgrade. Train state, inventory, upgrades, and position are persistent and co-op-authoritative.

## 8. Companions, factions, and settlements

Companions have personalities, skills, routines, equipment, relationships, and contextual autonomy. Players can recruit them, issue direct orders, manage loadouts, take a limited squad on expeditions, pursue companion quests, and assign them to settlement/train jobs. Squad and active-agent limits are performance-aware and clearly communicated.

Factions persist and react to player actions and regional events. Loaded/nearby areas receive richer simulation; distant factions and settlements advance through compact, deterministic or seeded state updates. Important outcomes are persisted so unloading/reloading does not erase consequential events.

Settlement building combines modular structures with functional management. Players can establish production, agriculture, workshops, power, defenses, and infrastructure; recruit/assign NPC workers; and manage supplies and risks. Large industrial projects (including fictionalized hazardous power facilities) are late-game goals requiring space, materials, specialists, and upkeep. Settlements can attract traders, visitors, and raids based on their state. Building should be functional and systemic, not merely decorative placement.

## 9. Crafting and experimentation

Core survival items use discoverable recipes. Equipment and train/settlement systems use component-based upgrades. Experimental inventions combine curated components and a constrained property/rule library; the system must not invent arbitrary unbounded items. Experiments can consume resources and reveal information even when they fail. Discovered recipes, schematics, and useful outcomes are recorded for later use.

## 10. Co-op and persistence

The game is offline-capable and fully playable solo. Co-op is invite-only, drop-in/drop-out, and host-authoritative during a session. New arrivals spawn at a safe location—typically the train or an eligible friendly settlement—not in active combat. Remaining players continue when someone leaves.

### Host migration

The session should elect the best eligible replacement using a composite of latency to peers, connection stability, upload capacity, device performance, and replicated-state readiness—not download speed alone. State replication/checkpointing supports migration. If migration fails or state is inconsistent, preserve the latest recoverable valid checkpoint and allow later resumption rather than corrupting or silently forking the world. A small amount of uncheckpointed progress may be lost in catastrophic failures; this limitation must be communicated.

### Ownership and player progression

The host owns the persistent world save: world seed/state, settlements, factions, quests, train infrastructure, and shared world changes. Guests retain their survivor identity, eligible gear, skills, and personal progression and may return to their own worlds. Transfer rules must prevent item duplication and protect quest-critical/host-owned assets. Host migration changes the active session authority, not permanent ownership of the original world.

The host may invite as many players as the runtime can safely support; there is no arbitrary fixed design cap. This is not a promise of unlimited concurrent players. The implementation must enforce dynamic capacity based on device/network budgets, with graceful refusal or reduced simulation quality before instability. Invite-only listen-server hosting is the initial target. Dedicated servers are not required for the initial release.

## 11. Monetization and privacy

The architecture may leave room for optional rewarded ads, cosmetics, or ad removal later. The initial build includes no ad SDK, tracking, intrusive ads, or ad-gated core progression. Offline solo play cannot depend on ad availability. Monetization must not compromise save integrity, co-op fairness, or the survival economy.

## 12. Technical direction and delivery reality

The current repository is a Java + libGDX native Android voxel-sandbox prototype. Existing code includes seeded terrain, first-person movement, mining/placing, inventory, touch controls, save/load, and basic rendering. This document describes the intended product direction; it does not assert that the listed systems are implemented.

The redesign is too broad for one safe implementation pass. Work should be decomposed into independently testable milestones after this spec is approved. Preserve Android as a primary target, offline-first save behavior, and a clean separation between simulation/state, rendering, content definitions, persistence, and platform/network adapters. Prefer deterministic simulation and versioned data formats where they aid saves, replayability, and co-op recovery. Use explicit performance budgets and device profiling; degrade distant simulation and visual complexity before sacrificing save integrity or input responsiveness.

Co-op migration, persistent faction simulation, large settlements, procedural rail topology, and an evolving narrative are high-risk systems. They require technical spikes and staged validation before being represented as production-ready. No claim of unlimited player capacity, seamless migration under every failure, or arbitrary world scale is permitted.

## 13. Out of scope for the initial foundation

- Always-online single-player or mandatory accounts.
- Dedicated servers as a launch dependency.
- Intrusive advertising or ad-gated essential progression.
- A default minimap/compass/constant quest-arrow navigation model.
- A supernatural omniscient radio at game start.
- Unbounded procedural dialogue, inventions, or story generation.
- Guaranteed infinite co-op capacity or lossless migration under every network failure.
- Treating the current prototype as if it already delivers the full vision.

## 14. Acceptance criteria for the design

This design is ready to plan when the owner confirms that it accurately captures the intended game, including: the crashed-car/amnesia opening; initially ordinary broken radio personally built by the protagonist; grounded and dynamic signal discovery; consequential dialogue and selective fourth-wall humor; exploration-led connected regions; train-base progression; independent companions; settlement/faction simulation; solo/offline-first play; and invite-only co-op with host migration, safe joining, hybrid ownership, and recoverable saves.
