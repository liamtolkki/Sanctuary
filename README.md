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
- Basic status/admin commands
- Automated persistence/model tests
- Development deployment task
- GitHub Actions build

Implemented anchor milestone:

- ExtendedItems `0.1.0-alpha.2` pinned from its GitHub Release
- Sanctuary Beacon identity through `ExtendedItemIds.SANCTUARY_BEACON`
- Sanctuary-owned `anchor_id`, `owner_uuid`, and `tier` PDC metadata
- Unique unbound Beacon creation
- `/sanctuary admin givebeacon <player>`
- First-placement listener
- Ownership assignment on first placement
- Existing anchor UUID used as the Sanctuary UUID
- Initial Sanctuary naming as `<PlayerName>'s Sanctuary`
- Active location and owner persisted through the existing repository
- Bound Beacon metadata written to the placed block
- Duplicate/malformed/bound placement rejection
- Unit tests for anchor binding and first-placement persistence logic

The implementation intentionally stops before anchor breaking and re-placement. Bound-anchor placement is rejected until that lifecycle is implemented.

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
```

The anchor UUID is the Sanctuary identity and must remain stable across future breaking, relocation, upgrades, and re-placement.

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
/sanctuary admin reload
/sanctuary admin givebeacon <player>
```

Administrative commands require:

```text
sanctuary.admin
```

## First-placement behavior

```text
Operator gives unbound Sanctuary Beacon
        ↓
Beacon already has a stable anchor UUID
        ↓
Player places Beacon
        ↓
ExtendedItems identity and format validated
        ↓
Sanctuary metadata validated
        ↓
Owner UUID assigned without changing anchor UUID
        ↓
Bound metadata written to placed Beacon block
        ↓
Existing SanctuaryRepository persists one ACTIVE Sanctuary
        ↓
Location, tier, owner, and default name are retained in SQLite
```

The initial territory area is configured with:

```yaml
anchors:
  initial-territory-area: 100.0
```

This value seeds the already-existing required `territory_area` field. Territory radius/containment gameplay is not implemented in this milestone.

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

The existing migration framework and `sanctuaries` table remain authoritative. The anchor work does not introduce a second persistence representation.

## Next milestone

The next anchor-lifecycle work is:

```text
Anchor breaking
    ↓
Sanctuary becomes INACTIVE
    ↓
Bound item drops with same anchor UUID
    ↓
Re-placement validates owner and UUID
    ↓
Same Sanctuary becomes ACTIVE at the new location
```

Territory calculations, spacing rules, UI, trust, protections, advancements, sentries, companions, and Conduit-specific gameplay remain later work.

See `IMPLEMENTATION-STATUS.md`, `DEVELOPMENT.md`, and `docs/Minecraft-Plugin-Architecture-and-Development-Plan.md` for additional project detail.
