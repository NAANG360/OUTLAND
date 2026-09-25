# OUTLAND — WASTELAND Art Direction

## Core visual target
Stylized realism: a believable post-apocalyptic world rendered with low-poly geometry that feels authored, weathered, and atmospheric rather than voxel-game generic.

The visual language should evoke:
- Weird West landscapes
- dry grass, dusty soil, weathered wood and faded industrial materials
- warm sunlight against a cool open sky
- beautiful vistas with occasional unsettling traces of the old world
- readable silhouettes on mobile hardware

## Geometry rules
- Terrain storage/collision may remain voxel-compatible internally, but visible terrain should progressively move away from obvious Minecraft-like cubes.
- Organic objects must have connected silhouettes: trunks connect to branches, branches enter foliage, cactus arms grow from the main stalk.
- Avoid repeated perfect spheres, identical cylinders, and symmetric arrangements.
- Prefer 6–12 sided low-poly forms with deliberate asymmetry and varied scale.
- Small props should use silhouette and lighting rather than texture-heavy assets.

## Palette
- Grass: muted olive/sage greens, with subtle per-cell variation.
- Soil: warm dark brown with occasional cooler variants.
- Stone: neutral gray with slightly warm/cool variation.
- Wood: dark weathered brown.
- Foliage: deep desaturated green with darker interior masses.
- Sky: pale dusty blue rather than saturated cyan.
- Sunlight: warm directional light; ambient fill remains restrained.
- Rare materials such as uranium can be brighter and more luminous than ordinary world materials.

## Lighting
One dominant warm directional sun plus restrained ambient fill. Preserve strong form shadows without crushing mobile displays into black. Atmospheric distance should gradually soften silhouettes.

## Vegetation
Trees are the first hero prop:
1. broad lower trunk
2. narrower upper trunk
3. 2–4 irregular branches
4. overlapping foliage masses with different scales
5. no floating leaf balls
6. no perfect radial symmetry

Cacti should have a continuous main stalk, rounded tops, and attached arms with varied height.

## Terrain progression
The current prototype uses voxel-compatible blocks for gameplay. The rendering roadmap is:
1. material/color variation — current pass
2. better low-poly prop silhouettes — current pass
3. sloped/low-poly terrain surface over voxel collision
4. rock/grass/soil dressing
5. atmospheric depth and weather
6. authored landmark materials and lighting

## Mobile constraint
Art must remain practical for the Galaxy A12-class target. Prefer reusable meshes, ModelCache chunking, frustum culling, and deterministic variation over large texture packs or high-poly assets.

## Anti-goals
- Minecraft-like default block aesthetics
- hyper-realistic assets that overwhelm the mobile GPU
- uniformly dark horror visuals
- procedural noise without authored silhouettes
- excessive bloom, fog, or post-processing
