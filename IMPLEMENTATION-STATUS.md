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

### Territory and spacing

- Area-to-radius calculation: `radius = sqrt(area / PI)`
- Horizontal circle/cylinder containment with unrestricted Y
- Configurable maximum Sanctuary radius
- Configurable inter-owner spacing margin
- Future-growth spacing: `2 * maximum radius + margin`
- Different-owner spacing enforced on first placement and relocation
- Same-owner overlap allowed
- Only active Sanctuaries participate in spacing checks
- Other worlds do not conflict
- `/sanctuary admin beacons` prints the current derived radius

### Debug support

- `/sanctuary admin debugbeacon [player]` creates an already-registered `INACTIVE` debug Sanctuary
- Each debug Sanctuary uses a reserved UUID version 15 synthetic owner identity
- Admins may place a debug Beacon on behalf of its synthetic owner
- Debug Sanctuaries participate in normal different-owner spacing checks
- Breaking a debug Beacon drops nothing and deletes its database row
- Destruction of the inactive debug item also deletes its database row
- Debug Sanctuaries are marked `debug_ephemeral` by migration V003
- Recover tab completion now only lists normal `INACTIVE` Beacons

## Configuration added

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

Recovery is only available for an `INACTIVE` Sanctuary whose Beacon destruction was not recorded. A successful recovery advances `anchor_generation`.

## Deliberately not implemented yet

- Sanctuary Conduit obtain/placement lifecycle
- Anchor tier crafting/upgrades
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

Territory presence and boundary visualization are now implemented. The next major gameplay layer is trust/capability and protection behavior.

## Territory presence and awareness completed

Implemented after the territory/spacing milestone:

- Runtime active-territory membership detection
- Enter and exit transition tracking
- Direct Sanctuary-to-Sanctuary transitions
- Closest-anchor selection for overlapping same-owner territories
- Configurable entry titles
- Configurable exit chat messages
- Configurable online-owner entry alerts
- Debug-only entry chat output for ephemeral debug Sanctuaries
- `/sanctuary boundary <name|all>` particle visualization
- Boundary particle spacing and display-duration configuration
- Tests for territory membership selection and boundary point calculation

## Boundary Refactor

Implemented:
- Radius is the gameplay/persistence primitive (`territory_radius`).
- V004 preserves old territory size by converting legacy area values.
- Manual boundary particles are visible only to the invoking player.
- Automatic local proximity boundary visualization uses distance-limited vertical blob geometry.
- Human-readable boundary selectors prefer Sanctuary display names and owner names before short ID suffixes.
- `/sanctuary boundary all` respects the configured maximum render distance.

## Orphan cleanup and boundary refresh configuration

Implemented:
- Placed Sanctuary anchors whose UUID is missing from the database can be broken and cleaned up.
- Orphan cleanup suppresses item drops and warns the breaking player.
- Registered Sanctuary anchors retain normal ownership and generation validation.
- Automatic proximity boundary refresh period is configurable with `territory.boundary.automatic.update-period-ticks`.
- The update period is reloadable with `/sanctuary admin reload`.
