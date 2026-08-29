# Sanctuary Implementation Status

This file tracks the current implementation on `main`. It is not a chronological changelog. Historical design notes should not be treated as current behavior when they disagree with source or tests.

## Foundation

Implemented:

- Java 25 / Paper 26.1.2 Gradle project
- Gradle Wrapper 9.7.1
- SQLite persistence with versioned migrations through V012
- ExtendedUI 0.1.0 pinned from GitHub Releases
- ExtendedItems 0.1.0-alpha.9 pinned from GitHub Releases
- shaded/relocated ExtendedUI, ExtendedItems, InvUI, and SQLite runtime dependencies
- read-only public `SanctuaryApi` through Paper `ServicesManager`
- GitHub Actions build/test pipeline
- deterministic `Sanctuary.jar` CI artifact
- manual GitHub Release workflow with SHA-256 asset
- Windows production deployment script with backup and rollback

## Sanctuary and anchor lifecycle

Implemented:

- Beacon and Conduit anchor model
- stable anchor UUID identity
- owner UUID, tier, generation, type, radius, and lifecycle persistence
- `ACTIVE`, `INACTIVE`, and `DESTROYED` lifecycle states
- bound anchor break/re-placement
- stale-generation rejection
- destruction audit state
- owner recovery for eligible inactive anchors
- orphan anchor cleanup
- automatic naming UI for a newly created Sanctuary
- naming UI suppressed when only adding an extender
- persisted Sanctuary names and readable selectors

## Anchor graph

Implemented:

- multiple active anchors per Sanctuary
- graph connections between anchors
- no permanent graph root
- anchor removal allowed whenever the remaining graph stays connected
- active graph anchors collectively define territory
- same-owner overlap support
- different-owner spacing based on future maximum growth

## Territory geometry

Implemented:

- flattened 3D ellipsoid per active anchor:
  `x^2 + 2.25y^2 + z^2 <= r^2`
- horizontal semi-radius `r`
- vertical semi-radius `2r/3`
- active Sanctuary territory is the union of anchor ellipsoids
- 3D player presence and entry/exit detection
- 3D hard-protection location checks
- 3D Beacon/Conduit effect coverage
- 3D Elytra suppression coverage
- 3D Watcher territory eligibility
- analytic X/Z union area for the UI territory-area value

Tier radii:

```text
I   20
II  39
III 58
IV  77
V   96
```

The displayed territory area remains square-block X/Z footprint area, not cubic volume.

## Boundary rendering

Implemented:

- ellipsoid surface rendering
- union exterior rendering across multiple anchors
- suppression of overlapping/internal shell portions
- viewer-specific relationship/threat particles
- full coarse outer shell when automatic visibility is triggered
- denser local proximity band near the viewer
- altitude-aware hybrid automatic visibility:
  - hidden deep inside territory
  - visible near the 3D border
  - visible above/below territory while horizontally over/near the footprint
- default automatic maximum distance 16 blocks

Current particle defaults:

```text
owner    SCULK_CHARGE_POP
trusted  GLOW
neutral  END_ROD
hostile  REVERSE_PORTAL
```

## Trust, security, and aggression

Implemented relationships:

```text
OWNER
TRUSTED
NEUTRAL
BLACKLISTED
```

Implemented security modes:

```text
NORMAL
LOCKDOWN
```

Implemented:

- central effective threat resolution
- neutral players remain neutral in Normal mode
- blacklisted players are hostile
- neutral players are hostile in Lockdown
- trust/blacklist mutual exclusion through management operations
- persisted temporary aggression per Sanctuary/player
- 10-minute aggression timeout
- aggression refresh on qualifying hostile acts
- aggression persistence across logout/restart
- death clears temporary aggression
- death does not clear blacklist
- hostile boundary coloring follows effective threat

Qualifying aggression events include attacks on the owner, sentries, and anchors.

The removed neutral-entry hostility behavior must not be reintroduced. Simply entering a Sanctuary as a neutral player is not a hostile act.

## Trust and optional hard protections

Implemented explicit capabilities:

```text
BUILD
BREAK
INTERACT
CONTAINER
REDSTONE
ENTITIES
```

Implemented:

- owner implicit full access
- UUID-backed trust
- explicit capability grants
- capability persistence/cascade cleanup
- optional hard claim-style protection listeners
- hard protections default disabled
- admin/debug tooling for solo permission testing

## Beacon and Conduit effects

Implemented Beacon effect pairs:

1. Regeneration / Wither
2. Resistance / Blindness
3. Strength / Weakness
4. Haste / Mining Fatigue
5. Speed / Elytra Disabled

Implemented Conduit effect pairs:

1. Regeneration / Wither
2. Conduit Power / Blindness
3. Haste / Mining Fatigue
4. Dolphin's Grace / Slowness
5. Resistance / Weakness

Implemented:

- one effect pair unlocked per anchor tier
- level 1 baseline for newly unlocked pairs
- paired per-anchor attunement persistence
- individual effect maximum caps
- safe/hostile selection through effective threat
- Conduit effects gated to water/rain
- Elytra suppression integrated with 3D ellipsoid territory

Current note: the service/persistence layer supports paired attunement levels. Player-facing Attunement Relic acquisition and upgrade semantics should remain aligned with the intended permanent-upgrade model and must not regress to free arbitrary level cycling.

## Sentries

Implemented:

- sentry post registration inside Sanctuaries
- Sanctuary-owned sentry identity/state
- global sentry defaults and per-sentry overrides
- target authorization through Sanctuary defense/security logic
- owner, sentry, anchor, hostile-mob, interaction, and other defense triggers
- Watcher's Eye proactive local detection
- recall/home behavior
- respawn cooldown
- no normal drops/XP on managed sentry death
- managed target/anger controls
- protection from unmanaged mob targeting/damage
- Warden-specific anger control
- Wither block-damage suppression
- Enderman teleport restrictions
- chunk unload protection from false death/duplicate respawn

Watcher’s Eye remains a true 12-block 3D sphere around each equipped active anchor and is not flattened with Sanctuary territory.

Known follow-up:

- some legacy `SentryService` / `SentryTask` territorial leash, path, or target-validity helpers still use horizontal compatibility containment and should be fully migrated to XYZ ellipsoid checks

## Companions

Implemented:

- Companion Egg spawning and pickup lifecycle
- Follow / Stay controls
- formation following
- delayed teleport while owner is airborne
- safe teleport candidate search
- collision/support/hazard checks
- aquatic restrictions
- owner-defense target memory
- companion-retaliation target memory
- transient combat relationships
- same-owner protection
- custom Warden behavior
- controlled Wither targeting
- Evoker/Vex handling
- Creeper handling
- Enderman teleport restrictions
- permanent companion death
- persisted companion health
- display-only Companion Egg durability bar representing health

Known follow-up/testing gaps:

- broader automated coverage for target priority
- transient combat relationships
- friendly-fire protection
- enemy companion fights
- safe teleport candidate selection
- profiling/optimization of broad loaded-mob and nearby-entity scans

## Divine Altar, crafting, loot, and progression

Implemented:

- Divine Altar placed-object behavior
- Sanctuary-owned custom crafting UI
- protected altar lifecycle against piston/explosion/orphaning cases
- persistent altar visual effects
- Consecrated Shard 2x2 fragment recipe
- altar offering persistence
- Sanctuary custom crafting/progression support
- structure-loot tagging/profiles
- debug loot/relic/shard commands

## Advancements

Implemented:

- Sanctuary advancement catalog/service
- advancement triggers integrated with Sanctuary progression paths

Known regression item:

- the Warden Companion Egg obtain advancement has had prior issues and should remain in manual/automated regression testing until explicitly verified in the current gameplay build

## UI

Implemented through ExtendedUI:

- owner anchor management
- admin/debug anchor management
- Sanctuary naming
- Players & Access management
- Security management
- trust/capability controls
- sentry management
- effect/attunement presentation
- debug controls
- territory-area display

## Configuration

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
  boundary:
    particle-spacing: 1.5
    maximum-render-distance: 128.0
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

## Release/deployment

Implemented:

- normal CI build/test on pushes and pull requests
- `Sanctuary.jar` as the deterministic deployable CI artifact
- manual `Release` workflow from `main`
- versioned Git tag and GitHub Release creation
- `Sanctuary.jar.sha256` checksum asset
- production PowerShell deployer using public GitHub Releases
- backup retention
- service stop/install/start sequence
- rollback on deployment/startup failure

See `docs/DEPLOYMENT.md`.

## Current follow-up work

Priority follow-up items:

1. finish migrating all sentry territorial leash/path/target checks to full XYZ ellipsoid containment
2. continue in-game regression testing of the new hybrid boundary visibility rules across multi-anchor Sanctuaries
3. expand companion behavior tests and profile companion task performance
4. finish/verify player-facing Attunement Relic progression semantics against the permanent-upgrade design
5. verify the Warden Companion Egg advancement in-game
6. keep README, DEVELOPMENT, and this file updated in the same changes that materially alter gameplay semantics
