# Sanctuary

Sanctuary is a Paper plugin that creates persistent player-owned protected territories anchored by special Beacon and Conduit items.

The project is part of the shared Minecraft plugin ecosystem:

```text
ExtendedUI
    Shared UI library

ExtendedItems
    Shared persistent item identity library

God
    AI, Favor, quests, divine rewards

Sanctuary
    Anchors, territory, protections, trust, guards, advancements
```

Sanctuary does not depend on God at runtime. Shared physical item identity comes from ExtendedItems; Sanctuary owns Sanctuary-specific persistent state and gameplay behavior.

## Current implementation

Implemented foundation:

- Java 25 / Paper 26.1.2 Gradle project
- Plugin bootstrap and configuration
- SQLite initialization and versioned migrations
- Immutable Sanctuary model
- SQLite repository
- Read-only public `SanctuaryApi`
- Development deployment task and GitHub Actions build

Implemented Beacon lifecycle:

- ExtendedItems `0.1.0-alpha.2` pinned from its GitHub Release
- Sanctuary Beacon identity through `ExtendedItemIds.SANCTUARY_BEACON`
- Sanctuary-owned `anchor_id`, `owner_uuid`, `tier`, and `generation` PDC metadata
- Unique unbound Beacon creation and first-placement ownership assignment
- Existing anchor UUID used as the Sanctuary UUID
- Explicit owner/admin breaking with an intentionally generated bound Beacon drop
- `ACTIVE` to `INACTIVE` transition when the anchor is mined
- Bound Beacon re-placement/reactivation without recreating the Sanctuary
- Generation validation that rejects superseded recovered Beacon copies
- Recorded item destruction transitions the Sanctuary to `DESTROYED`
- Safe owner recovery for unrecorded disappearance, with generation advancement
- Configurable recovery enablement and cooldown
- `/sanctuary admin beacons` metadata registry output
- Area-based territory radius calculation with horizontal cylinder containment
- Different-owner future-growth spacing validation on first placement and re-placement
- Same-owner overlap allowed
- Ephemeral registered debug Beacons with synthetic non-player owners
- Debug Beacon deletion on break with no item drop
- Recover autocomplete limited to recoverable `INACTIVE` Sanctuaries
- Automated model, migration, repository, territory, spacing, debug, first-placement, and lifecycle tests

## Anchor identity contract

ExtendedItems owns shared item identity:

```text
extendeditems:id = sanctuary_beacon
extendeditems:version = 1
```

Sanctuary owns instance state:

```text
sanctuary:anchor_id = <UUID>
sanctuary:owner_uuid = <UUID when bound>
sanctuary:tier = 1
sanctuary:generation = <positive integer>
```

The anchor UUID is the Sanctuary identity and remains stable across breaking, relocation, recovery, upgrades, and re-placement. Each successful break emits the next Beacon generation, and recovery advances it again when needed. Older physical copies are permanently stale.

Lifecycle states:

```text
ACTIVE
INACTIVE
DESTROYED
```

`DESTROYED` is retained as an audit record rather than deleting the row immediately. Normal gameplay recovery is not allowed once destruction was recorded.

## Build requirements

- JDK 25
- Gradle Wrapper 9.7.1
- Paper 26.1.2 target
- ExtendedUI available as a sibling repository during the current development phase
- Network access to download the pinned ExtendedItems GitHub Release asset on first build

Build and test:

```powershell
.\gradlew.bat clean build
```

The build downloads exactly:

```text
ExtendedItems 0.1.0-alpha.2
```

from the release asset:

```text
v0.1.0-alpha.2/extendeditems-0.1.0-alpha.2.jar
```

It is shaded and relocated into the final Sanctuary plugin JAR. ExtendedItems is not installed separately in Paper's `plugins` directory.

## Development deployment

Default development server plugin path:

```text
C:\MinecraftDev\server\plugins
```

Build and deploy:

```powershell
.\gradlew.bat clean deployDev
```

Fully restart Paper after deployment. Do not use `/reload` as the normal development loop.

## Commands

```text
/sanctuary status
/sanctuary recover <sanctuary-id>
/sanctuary admin reload
/sanctuary admin beacons
/sanctuary admin givebeacon <player>
/sanctuary admin debugbeacon [player]
```

Administrative commands require:

```text
sanctuary.admin
```

## Beacon lifecycle behavior

```text
Unbound Sanctuary Beacon
        ↓ first placement
ACTIVE Sanctuary
        ↓ owner/admin breaks anchor
INACTIVE Sanctuary + bound Beacon item
        ↓ owner re-places matching generation
ACTIVE Sanctuary at new location
```

If the inactive bound item is explicitly removed by a recorded destructive Paper removal cause, the Sanctuary becomes `DESTROYED` and normal recovery is permanently blocked. Pickup, chunk unload, and item merge are not destruction.

If an inactive Beacon disappears without a recorded destruction, the owner may use:

```text
/sanctuary recover <sanctuary-id>
```

Recovery creates a new bound Beacon generation. Any older copy that later reappears is stale and cannot activate the Sanctuary.

Recovery and territory configuration:

```yaml
anchors:
  initial-territory-radius: 18.0
  recovery:
    enabled: true
    cooldown-seconds: 300

territory:
  maximum-radius: 64.0
  spacing-margin: 16.0
```

Current territory radius is derived from stored area:

```text
radius = sqrt(area / PI)
```

Different owners reserve enough room for future growth:

```text
minimum anchor distance = 2 * maximum-radius + spacing-margin
```

With the default values, different-owner anchors must be at least 144 blocks apart horizontally. Y distance is intentionally ignored. Same-owner overlap is allowed.

## Public API

Sanctuary registers a read-only `SanctuaryApi` through Paper's `ServicesManager`.

Current queries are:

```java
Optional<SanctuaryView> getSanctuary(UUID sanctuaryId);
List<SanctuaryView> getPlayerSanctuaries(UUID playerId);
```

Other plugins should use the public API rather than Sanctuary's database or internal repositories.

## Persistence

SQLite database:

```text
plugins/Sanctuary/sanctuary.db
```

The existing migration framework and `sanctuaries` table remain authoritative. Migration V002 adds anchor generation and destruction audit fields. Migration V003 adds the internal ephemeral-debug marker while preserving existing rows.

## Debug territory testing

Create a pre-registered Beacon owned by a reserved synthetic non-player identity:

```text
/sanctuary admin debugbeacon
/sanctuary admin debugbeacon <player>
```

Admins may place the debug Beacon even though its synthetic owner is not the player. It participates in normal different-owner spacing checks. When broken, it drops nothing and its database row is deleted. If the inactive debug item is destroyed before placement, its row is also cleaned up.

## Next milestone

The Beacon lifecycle, territory math, spacing, presence tracking, and boundary visualization foundations are now established. Trust/capability and protection work can build on this runtime territory awareness.

UI, trust, protections, advancements, sentries, companions, and Conduit-specific gameplay remain later work.

See `IMPLEMENTATION-STATUS.md`, `DEVELOPMENT.md`, and `docs/Minecraft-Plugin-Architecture-and-Development-Plan.md` for additional project detail.

## Territory presence and awareness

Active Sanctuary territory is now evaluated while players move horizontally through the world.

- Entering an active Sanctuary may show an entry title.
- Leaving may print a configurable exit message.
- An online owner may receive a configurable alert when another player enters.
- Direct movement from one Sanctuary into another is handled as an exit followed by an entry.
- If same-owner territories overlap, the closest anchor is selected deterministically.
- `INACTIVE` and `DESTROYED` Sanctuaries never count as current territory.
- Entering a `DEBUG-EPHEMERAL` Sanctuary additionally prints a debug chat message to the entering player.

Boundary visualization:

```text
/sanctuary boundary <name|all>
```

Owners may display their own active Sanctuary boundary. Players with `sanctuary.admin` may display any active Sanctuary boundary. The boundary is drawn with `END_ROD` particles around the horizontal territory circle.

Awareness and boundary configuration:

```yaml
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
    automatic:
      enabled: true
      trigger-distance: 12.0
      vertical-particle-spacing: 1.5
```
