# Sanctuary

Sanctuary is a Paper plugin for persistent player-owned territories built around Beacon and Conduit anchors. A Sanctuary can grow into a graph of connected anchors, apply safe or hostile effects, manage trusted players, operate sentry defenses, and support companion and Divine Altar progression.

Sanctuary is part of a shared plugin ecosystem:

```text
ExtendedUI
    Shared inventory and dialog UI library

ExtendedItems
    Shared persistent custom-item identity library

Sanctuary
    Anchors, territory, security, sentries, companions, altar progression

GodPlugin
    Planned player-facing wiki and guide for supported plugins
```

Sanctuary does not depend on GodPlugin at runtime. ExtendedUI and ExtendedItems are consumed as pinned GitHub Release artifacts and shaded into the final Sanctuary plugin JAR.

## Platform

- Java 25
- Paper 26.1.2
- Gradle 9.7.1
- SQLite persistence
- ExtendedUI 0.1.0
- ExtendedItems 0.1.0-alpha.9

Build and test:

```powershell
.\gradlew.bat clean build --no-daemon
```

The shaded plugin is produced under `build/libs`.

## Sanctuary anchors

A Sanctuary begins with a special Beacon or Conduit anchor. Anchor item identity comes from ExtendedItems while Sanctuary owns the persistent instance state.

Sanctuary-owned anchor state includes a stable UUID, owner UUID, tier, generation, type, territory radius, graph connections, and upgrade/effect state.

Anchor lifecycle states are:

```text
ACTIVE
INACTIVE
DESTROYED
```

Breaking a normal anchor produces the current bound anchor item and advances its generation. Older physical copies become stale. Recovery is available only for eligible inactive anchors whose destruction was not recorded.

A newly created Sanctuary automatically opens the naming UI after its first anchor is placed. Adding an extender anchor does not reopen the naming dialog.

## Anchor graph and territory

A Sanctuary is a graph of active anchors. There is no permanent root anchor. An anchor may be removed whenever the remaining graph stays connected.

Each active anchor contributes a flattened 3D ellipsoid:

```text
x^2 + 2.25y^2 + z^2 <= r^2
```

This gives each anchor:

```text
horizontal radius = r
vertical radius   = 2r / 3
```

Territory is the union of the active anchor ellipsoids. Overlap is counted once and gaps remain outside the Sanctuary.

Anchor tier radii are:

| Tier | Horizontal radius | Vertical radius |
| --- | ---: | ---: |
| I | 20 | 13.33 |
| II | 39 | 26 |
| III | 58 | 38.67 |
| IV | 77 | 51.33 |
| V | 96 | 64 |

The management UI reports territory area as the analytic union of the anchors' X/Z footprints in square blocks. That value is intentionally a land-footprint measurement, not ellipsoid volume.

Different owners reserve horizontal room for future growth. With the default configuration:

```text
minimum anchor distance = 2 * maximum-radius + spacing-margin
                        = 2 * 96 + 16
                        = 208 blocks
```

Same-owner overlap is allowed.

## Boundary visualization

Boundary particles follow the exposed outer surface of the ellipsoid union. Internal surfaces hidden inside another anchor are suppressed.

Viewer-specific colors are based on effective threat and relationship:

```text
owner       configured owner particle
trusted     configured trusted particle
neutral     configured neutral particle
hostile     configured hostile particle
```

Temporary aggression, blacklist state, and Lockdown can therefore make the boundary hostile-colored without changing the player's stored relationship.

Automatic visualization uses two densities:

- a coarse full outer shell so the Sanctuary's height and fly-over path are readable
- a denser local band near the viewer

Visibility is intentionally different for players inside and outside the territory:

- while comfortably inside the actual 3D Sanctuary, the full shell remains hidden
- when an inside player approaches the 3D surface, the shell appears
- when a player is vertically outside the Sanctuary, the shell may remain visible based on horizontal X/Z distance even far above or below it

Default automatic maximum visibility distance is 16 blocks.

## Security

Stored relationships are:

```text
OWNER
TRUSTED
NEUTRAL
BLACKLISTED
```

Effective threat is resolved separately as safe, neutral, or hostile.

Security modes are:

```text
NORMAL
LOCKDOWN
```

In Normal mode, neutral players remain neutral and blacklisted players are hostile. In Lockdown, neutral non-owner, non-trusted players are hostile.

### Temporary aggression

Qualifying hostile acts create a temporary Sanctuary-specific aggression state for 10 minutes. This state is persisted, survives logout and restart, and refreshes on another qualifying act.

Current aggression triggers include attacks on:

- the Sanctuary owner
- a sentry
- a Sanctuary anchor

Temporary aggression ends when its timer expires or when the aggressive player dies. Death does not remove blacklist state.

Simply entering neutral territory is not itself hostile behavior.

## Effects and attunement

Beacon and Conduit anchors unlock one safe/hostile effect pair per tier.

Beacon pairs:

1. Regeneration / Wither
2. Resistance / Blindness
3. Strength / Weakness
4. Haste / Mining Fatigue
5. Speed / Elytra Disabled

Conduit pairs:

1. Regeneration / Wither
2. Conduit Power / Blindness
3. Haste / Mining Fatigue
4. Dolphin's Grace / Slowness
5. Resistance / Weakness

Each newly unlocked pair begins at attunement level 1. Paired attunement state is persisted per anchor and the active effect is capped by that effect's own maximum level.

Conduit effects only apply while the player is in water or rain.

## Sentries

Sanctuary supports registered sentry posts and managed sentry mobs. Sentries belong to the Sanctuary containing their post and use Sanctuary threat/security state when deciding what may be attacked.

Implemented sentry behavior includes:

- global Sanctuary sentry defaults and per-sentry overrides
- Sanctuary-local target authorization
- owner, sentry, anchor, hostile-mob, interaction, and other defense triggers
- recall and return-home behavior
- respawn cooldown after sentry death
- protection from unmanaged mob targeting and damage
- mob-specific handling for Wardens, Withers, Endermen, Vexes, and other special cases
- Watcher's Eye proactive local awareness

Watcher-equipped active anchors use a true 12-block 3D sphere for their local proactive awareness. This is separate from the flattened Sanctuary territory ellipsoid.

## Companions

Companion Eggs spawn persistent owner-bound companion mobs with Follow and Stay control modes.

Current companion behavior includes:

- formation following
- delayed catch-up teleport while the owner is airborne
- safe teleport candidate search and hazard rejection
- aquatic placement restrictions
- owner-defense targeting
- short-lived combat relationships between hostile companions
- custom handling for Warden, Wither, Evoker/Vex, Creeper, Enderman, and aquatic companions
- permanent companion death
- persisted companion health

Companion Egg durability bars are display-only health indicators. They must not break the egg item.

## Divine Altar, crafting, loot, and advancements

Sanctuary includes a Divine Altar and Sanctuary-owned UI for custom progression recipes that do not fit the vanilla recipe book cleanly.

The altar is a protected placed object with persistent visual effects and cleanup behavior for breaking, pistons, explosions, and other movement/destruction cases.

The project also contains Sanctuary advancement, crafting, and structure-loot systems used for progression and custom items.

## Trust and optional hard protections

Trusted players can receive explicit capabilities:

```text
BUILD
BREAK
INTERACT
CONTAINER
REDSTONE
ENTITIES
```

The owner always has every capability. Trust by itself does not grant every capability.

Optional claim-style protections are controlled through `protections.hard` and default to disabled. When enabled, ordinary building, breaking, interaction, container, redstone, and entity actions can be cancelled according to the capability system.

## Configuration

Current defaults:

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
    block-place: true
    block-break: true
    containers: true
    interactions: true
    redstone: true
    entities: true
```

Use `/sanctuary admin reload` for reloadable configuration changes.

## Commands

Primary command:

```text
/sanctuary status
/sanctuary recover <sanctuary-id>
/sanctuary boundary <name|all>
/sanctuary trust <sanctuary> <player>
/sanctuary trust list <sanctuary>
/sanctuary untrust <sanctuary> <player>
/sanctuary capability <sanctuary> <player> <capability> <allow|deny>
/sanctuary admin ...
```

Debug commands registered by the plugin also include:

```text
/sanctuarydebugcompanions [player]
/sanctuarydebugshards [player]
/sanctuarydebugrelics [player]
/sanctuarydebugloot <profile|all> [player]
```

Administrative/debug commands require `sanctuary.admin`.

## Development deployment

The default development Paper plugin directory is:

```text
C:\MinecraftDev\server\plugins
```

Build and copy the shaded plugin there with:

```powershell
.\gradlew.bat clean deployDev
```

Use a full Paper restart after deployment. Do not use `/reload` as the normal development loop.

## Production releases and deployment

Normal pushes to `main` build and test Sanctuary on GitHub-hosted runners and upload a deterministic `Sanctuary.jar` CI artifact.

Deployable builds are created manually through the `Release` GitHub Actions workflow. A release contains:

```text
Sanctuary.jar
Sanctuary.jar.sha256
```

The Windows production deployment script is:

```text
scripts/deploy-sanctuary.ps1
```

It downloads a selected public GitHub Release, verifies SHA-256, backs up the current plugin, stops the Minecraft service, installs the new JAR, starts the service, and rolls back if deployment fails.

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the full production procedure.

## Persistence and API

SQLite database:

```text
plugins/Sanctuary/sanctuary.db
```

Sanctuary currently has migrations through `V012__sanctuary_aggression.sql`.

A read-only `SanctuaryApi` is registered through Paper's `ServicesManager`. Other plugins should use the public API instead of reading Sanctuary's SQLite database directly.

## More documentation

- [DEVELOPMENT.md](DEVELOPMENT.md) for development and runtime validation
- [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) for current implementation state and known follow-up work
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for production releases and deployment
