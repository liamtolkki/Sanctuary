# Sanctuary Development

This document covers the development environment, build/release workflow, architecture checkpoints, and runtime validation for the current Sanctuary implementation.

## Platform and dependencies

Use:

- JDK 25
- Paper 26.1.2
- Gradle Wrapper 9.7.1
- ExtendedUI 0.1.0
- ExtendedItems 0.1.0-alpha.9

Sanctuary downloads the pinned ExtendedUI and ExtendedItems GitHub Release JARs during the build and shades/relocates them into the final plugin. They are not installed separately in Paper's `plugins` directory.

Verify Java:

```powershell
.\gradlew.bat -q javaToolchains
```

Build and test:

```powershell
.\gradlew.bat clean build --no-daemon
```

If the wrapper JAR ever needs reconstruction:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-wrapper.ps1
```

## Development server deployment

The default development Paper plugin directory is:

```text
C:\MinecraftDev\server\plugins
```

Build and deploy:

```powershell
.\gradlew.bat clean deployDev
```

Use a full Paper restart after deployment. Do not use `/reload` as the normal development loop.

## GitHub Actions

`.github/workflows/build.yml` runs on pushes and pull requests. It:

1. checks out Sanctuary
2. sets up Java 25
3. sets up Gradle
4. runs `./gradlew clean build --no-daemon`
5. selects exactly one shaded Sanctuary JAR
6. publishes it as `Sanctuary.jar` inside the Actions artifact

The build intentionally excludes the `-plain.jar` output from the deployable artifact.

`.github/workflows/release.yml` is manual and should be run from `main`. It:

1. validates the supplied version
2. builds/tests with `-PreleaseVersion=<version>`
3. creates `Sanctuary.jar`
4. creates `Sanctuary.jar.sha256`
5. tags the commit as `v<version>`
6. creates a GitHub Release with both assets

See `docs/DEPLOYMENT.md` for production deployment.

## Persistence

SQLite database:

```text
plugins/Sanctuary/sanctuary.db
```

Current migrations extend through:

```text
V012__sanctuary_aggression.sql
```

The database stores Sanctuary lifecycle, anchors and graph data, trust/capabilities, security state, effect levels, sentries, altar progression, upgrades, and temporary aggression.

## Anchor lifecycle validation

Create a test Beacon with the admin tooling, then verify:

1. first placement creates an active Sanctuary
2. the naming UI opens for a newly created Sanctuary
3. breaking an owned anchor produces the bound current-generation item
4. re-placement reactivates the same Sanctuary/anchor identity
5. older generations are rejected as stale
6. recorded destruction prevents normal recovery
7. eligible inactive anchors can be recovered
8. lifecycle state survives restart

The exact admin/debug subcommands evolve during development. Use command autocomplete and `plugin.yml` as the current command source of truth rather than copying old examples from historical docs.

## Anchor graph validation

A Sanctuary can contain multiple active anchors. There is no permanent graph root.

Validate at least these cases:

- a newly placed extender joins the existing Sanctuary rather than creating a second Sanctuary
- adding an extender does not reopen the Sanctuary naming UI
- same-owner connected anchors may overlap
- removing an anchor is allowed whenever the remaining graph stays connected
- removing an anchor that would disconnect the graph is rejected
- a node with only one graph connection may be removed even if it was the first anchor ever placed
- adding an intermediate connection can make a formerly critical anchor removable

## Territory geometry

Each active anchor contributes a flattened ellipsoid:

```text
x^2 + 2.25y^2 + z^2 <= r^2
```

Equivalent semi-axes:

```text
horizontal = r
vertical   = 2r / 3
```

Territory membership is the union of the active anchor ellipsoids.

Tier radii:

```text
I   20
II  39
III 58
IV  77
V   96
```

Tier V therefore has a vertical semi-radius of 64 blocks.

Runtime checks should include:

- horizontal entry/exit
- pure vertical entry/exit
- flying above the top of the ellipsoid
- moving below the bottom of the ellipsoid
- overlap between anchors at similar and different Y levels
- holes between multiple anchors remain outside territory

The territory area shown in the UI is the analytic union of the anchors' X/Z circles in square blocks. It is intentionally not 3D volume.

## Boundary rendering validation

Boundary particles should trace only the exposed outer surface of the ellipsoid union. Surfaces hidden inside another anchor should not render.

Test:

- single anchor
- two overlapping anchors
- contained/near-duplicate anchors
- anchors at different heights
- viewer inside near the surface
- viewer deep inside
- viewer above the Sanctuary center
- viewer below the Sanctuary center
- viewer outside the horizontal render distance

Automatic visibility rules:

- deep inside the 3D Sanctuary: full shell hidden
- inside near the real 3D surface: shell visible
- vertically outside but horizontally over/near the footprint: shell visible
- horizontally farther than the automatic maximum distance: shell hidden

Current defaults:

```yaml
territory:
  boundary:
    particle-spacing: 1.5
    automatic:
      minimum-distance: 3.0
      maximum-distance: 16.0
      vertical-particle-spacing: 1.5
      update-period-ticks: 10
```

The full shell is rendered at a deliberately coarser spacing than the detailed local proximity band.

## Security and aggression validation

Relationships:

```text
OWNER
TRUSTED
NEUTRAL
BLACKLISTED
```

Security modes:

```text
NORMAL
LOCKDOWN
```

Verify:

- neutral entry in Normal mode does not itself create hostility
- blacklisted players are hostile
- neutral players become hostile under Lockdown
- owner remains safe
- qualifying attacks create temporary aggression
- temporary aggression lasts 10 minutes and refreshes on another qualifying attack
- aggressive trusted or neutral players are treated as hostile while aggression is active
- death clears temporary aggression but does not remove blacklist state
- logout/restart does not clear active aggression
- boundary color follows effective threat, not only stored relationship

## Anchor effects and attunement validation

Beacon pairs:

```text
Regeneration / Wither
Resistance / Blindness
Strength / Weakness
Haste / Mining Fatigue
Speed / Elytra Disabled
```

Conduit pairs:

```text
Regeneration / Wither
Conduit Power / Blindness
Haste / Mining Fatigue
Dolphin's Grace / Slowness
Resistance / Weakness
```

Each unlocked pair begins at level 1. Paired attunement state is persisted per anchor.

Verify:

- each pair unlocks at the correct anchor tier
- both sides of a pair use the shared attunement level, capped by the individual effect maximum
- effect coverage follows the flattened 3D territory model
- Beacon and Conduit use their own effect catalogs
- Conduit effects only apply while the player is in water or rain
- Elytra suppression stops outside the ellipsoid so players can fly over the Sanctuary

Any remaining player-facing Attunement Relic acquisition/upgrade behavior should be tested against the current implementation rather than the old free level-cycling documentation.

## Sentry validation

Test sentries against both security behavior and territory boundaries.

Important cases:

- sentry registers only inside a valid Sanctuary
- sentry belongs to the containing Sanctuary
- normal mobs cannot freely target/damage managed sentries
- sentry target is authorized by Sanctuary threat/trigger rules
- recall returns the sentry home
- death starts respawn cooldown and does not drop normal loot/XP
- home chunk unload does not count as death
- Watcher's Eye proactive awareness is local to each equipped anchor
- Watcher's Eye uses a true 12-block sphere, not the flattened territory ellipsoid

Known follow-up: some older sentry leash/path/target containment helpers still use horizontal-only territory checks. When changing sentry territorial behavior, migrate those paths to full XYZ ellipsoid containment instead of adding more compatibility calls.

## Companion validation

Companion behavior should be tested with at least:

- Follow and Stay mode switching
- longer follow distance
- delayed catch-up teleport while owner is airborne
- safe landing/collision checks for teleport destinations
- no aquatic companion spawning/teleporting onto land
- owner attacker priority
- multiple attackers and target priority
- companion retaliation memory
- same-owner friendly-fire protection
- enemy companion combat relationships
- Warden, Wither, Evoker/Vex, Creeper, Enderman special handling
- permanent companion death
- persisted health when picked up
- Companion Egg durability bar reflects health but never breaks the egg item

Performance note: `CompanionTask` still deserves profiling because broad loaded-mob scans and nearby-entity scans can become expensive with many worlds/entities.

## Divine Altar, crafting, loot, and advancements

The Divine Altar provides Sanctuary-owned recipe/progression UI where vanilla recipe-book behavior is not a good fit.

Validate:

- eligible recipes display when their requirements are met
- crafting consumes the intended custom items
- Consecrated Shard uses the current 2x2 fragment pattern
- altar persistent particles remain active while placed
- normal breaking returns the altar block
- pistons/explosions/other movement cannot orphan an altar
- structure loot profiles produce the expected tagged items
- advancement triggers fire for the current item/progression paths

The Warden Companion Egg advancement has had previous issues, so keep it in the runtime regression set until explicitly verified in-game.

## Optional hard protection validation

Hard protection defaults off.

When enabled, validate explicit capability enforcement for:

```text
BUILD
BREAK
INTERACT
CONTAINER
REDSTONE
ENTITIES
```

Owner access remains implicit. Trusted players only receive capabilities that were granted.

## Configuration defaults

Current important defaults:

```yaml
anchors:
  initial-territory-radius: 20.0
  recovery:
    enabled: true
    cooldown-seconds: 300

territory:
  maximum-radius: 96.0
  spacing-margin: 16.0
  awareness:
    entry-title: true
    exit-message: false
    owner-entry-alerts: true
  boundary:
    particle-spacing: 1.5
    display-seconds: 10
    maximum-render-distance: 128.0
    particles:
      owner: SCULK_CHARGE_POP
      trusted: GLOW
      neutral: END_ROD
      hostile: REVERSE_PORTAL
    automatic:
      enabled: true
      minimum-distance: 3.0
      maximum-distance: 16.0
      vertical-particle-spacing: 1.5
      update-period-ticks: 10

protections:
  hard:
    enabled: false
```

Reload supported configuration with:

```text
/sanctuary admin reload
```

## Source-of-truth rule

When this document and implementation disagree, current `main` source and tests are authoritative. Update the documentation in the same change whenever gameplay semantics materially change.
