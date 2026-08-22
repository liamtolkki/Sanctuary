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
- Active/inactive/destroyed state model
- Beacon/Conduit type model
- SQLite Sanctuary repository
- Read-only public `SanctuaryApi`
- Paper `ServicesManager` registration for `SanctuaryApi`
- `/sanctuary status`
- `/sanctuary admin reload`
- Unit/integration tests for model invariants, migrations, and SQLite persistence
- Development deployment task targeting `C:\MinecraftDev\server\plugins`
- GitHub Actions build pipeline

### Beacon anchor lifecycle

- `ExtendedItemIds.SANCTUARY_BEACON` consumed from ExtendedItems `0.1.0-alpha.2`
- Sanctuary-owned PDC keys:
  - `sanctuary:anchor_id`
  - `sanctuary:owner_uuid`
  - `sanctuary:tier`
  - `sanctuary:generation`
- Legacy generation-less phase-1 Beacon metadata is treated as generation 1
- Unbound Beacon creation with a unique stable anchor UUID
- First-placement ownership assignment and persistence
- Owner/admin anchor breaking
- Explicit bound Beacon drop with stable anchor UUID and next generation
- `ACTIVE` to `INACTIVE` transition on break
- Owner-only bound Beacon re-placement/reactivation
- Existing Sanctuary record reused at the new location
- Generation mismatch rejects superseded Beacon copies
- Recorded bound-item destruction marks the Sanctuary `DESTROYED`
- Destruction time and reason retained for audit
- Owner recovery for `INACTIVE` Sanctuaries when no destruction was recorded
- Recovery increments generation and invalidates earlier copies
- Recovery disabled for `ACTIVE` and `DESTROYED` Sanctuaries
- `/sanctuary admin beacons` prints the complete registered Beacon metadata set
- Lifecycle, recovery, destruction, migration, and persistence tests

## Configuration added

```yaml
anchors:
  initial-territory-area: 100.0
  recovery:
    enabled: true
    cooldown-seconds: 300
```

Recovery is only available for an `INACTIVE` Sanctuary whose Beacon destruction was not recorded. A successful recovery advances `anchor_generation`.

## Deliberately not implemented yet

- Sanctuary Conduit obtain/placement lifecycle
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
sanctuary:generation
```

ExtendedItems `0.1.0-alpha.2` is downloaded from its exact GitHub Release asset during the build. No fallback or alternate ExtendedItems version is configured.

## Next implementation milestone

Implement territory calculations and placement-spacing validation on top of the completed Beacon lifecycle.
