# Sanctuary Implementation Status

## Implemented

### Foundation

- Java 25 Gradle project targeting Paper 26.1.2
- Paper plugin entry point
- ExtendedUI sibling composite-build development dependency
- ExtendedItems `0.1.0-alpha.2` pinned as an exact GitHub Release JAR
- Shaded/relocated ExtendedUI, ExtendedItems, and InvUI in the final Sanctuary JAR
- SQLite JDBC embedded in the final Sanctuary JAR
- SQLite database bootstrap
- Versioned database migration system
- Initial `sanctuaries` table and indexes
- Immutable core Sanctuary model
- Active/inactive state model
- Beacon/Conduit type model
- SQLite Sanctuary repository
- Read-only public `SanctuaryApi`
- Paper `ServicesManager` registration for `SanctuaryApi`
- `/sanctuary status`
- `/sanctuary admin reload`
- Unit/integration tests for model invariants, migrations, and SQLite persistence
- Development deployment task targeting `C:\MinecraftDev\server\plugins`
- GitHub Actions build pipeline

### Anchor identity and first placement

- `ExtendedItemIds.SANCTUARY_BEACON` consumed from ExtendedItems `0.1.0-alpha.2`
- `ExtendedItemIds.SANCTUARY_CONDUIT` resolves from the same pinned release for future Conduit lifecycle work
- Sanctuary-owned PDC keys:
  - `sanctuary:anchor_id`
  - `sanctuary:owner_uuid`
  - `sanctuary:tier`
- Unbound Beacon creation with a unique stable anchor UUID
- ExtendedItems validation retained after Sanctuary metadata is added
- `/sanctuary admin givebeacon <player>`
- First-placement event handling
- Ownership assignment on first placement
- Existing anchor UUID used as Sanctuary UUID
- Default name `<PlayerName>'s Sanctuary`
- Existing `SanctuaryRepository` used for persistence
- `BEACON` type, tier, location, owner, territory seed area, and `ACTIVE` state persisted
- Bound metadata written to the placed Beacon block
- Malformed Beacon rejection
- Already-bound Beacon placement rejection until re-placement exists
- Duplicate anchor UUID protection
- Tests for metadata binding invariants and first-placement persistence behavior

## Configuration added

```yaml
anchors:
  initial-territory-area: 100.0
```

The existing persistence model requires a positive `territory_area`. This setting provides the initial persisted value without implementing territory-radius gameplay early.

## Deliberately not implemented yet

- Sanctuary Conduit obtain/placement lifecycle
- Anchor breaking
- Bound anchor item drops
- Anchor re-placement/reactivation
- Anchor tier crafting/upgrades
- Territory calculations
- Inter-owner spacing validation
- Entry/exit detection
- Entry titles
- Boundary visualization
- Entry alerts
- Sanctuary management UI
- Rename dialog
- Trust/capabilities
- Protection gameplay
- Advancements
- Sentry posts
- Sentry mobs
- Companion guards
- Conduit-specific gameplay

## Dependency boundary

ExtendedItems owns the released item identity and format:

```text
extendeditems:id
extendeditems:version
```

Sanctuary owns the stateful instance metadata and gameplay:

```text
sanctuary:anchor_id
sanctuary:owner_uuid
sanctuary:tier
```

ExtendedItems `0.1.0-alpha.2` is downloaded from its exact GitHub Release asset during the build. No fallback or alternate ExtendedItems version is configured.

## Next implementation milestone

Continue the Beacon anchor lifecycle with breaking and re-placement:

```text
ACTIVE Sanctuary
        ↓
owner breaks anchor
        ↓
Sanctuary becomes INACTIVE
        ↓
bound Beacon drops with the same anchor UUID
        ↓
valid owner re-places bound Beacon
        ↓
existing Sanctuary location is updated
        ↓
same Sanctuary becomes ACTIVE
```
