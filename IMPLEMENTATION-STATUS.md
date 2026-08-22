# Sanctuary Implementation Status

## Implemented

### Foundation

- Java 25 Gradle project targeting Paper 26.1.2
- Paper plugin entry point
- Pinned ExtendedUI `0.1.0` GitHub Release dependency
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

- Radius is the persisted territory primitive; V004 converted legacy area values with `sqrt(area / PI)`
- Horizontal circle/cylinder containment with unrestricted Y
- Configurable maximum Sanctuary radius
- Configurable inter-owner spacing margin
- Future-growth spacing: `2 * maximum radius + margin`
- Different-owner spacing enforced on first placement and relocation
- Same-owner overlap allowed
- Only active Sanctuaries participate in spacing checks
- Other worlds do not conflict
- `/sanctuary admin beacons` prints the current persisted radius

### Debug support

- `/sanctuary admin debugbeacon [player]` creates an already-registered `INACTIVE` Tier V debug Sanctuary at the configured maximum radius
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
  maximum-radius: 96.0
  spacing-margin: 16.0
```

Recovery is only available for an `INACTIVE` Sanctuary whose Beacon destruction was not recorded. A successful recovery advances `anchor_generation`.

## Sanctuary management UI completed

- ExtendedUI `0.1.0` consumer integration
- Owner right-click anchor management menu
- Admin right-click / sneak-right-click debug menu
- `/sanctuary admin ui <sanctuary>`
- Boundary display action
- Trusted-player list and online-player add screen
- Per-player capability toggles
- Debug self-permission controls for solo testing

## Deliberately not implemented yet

- Sanctuary Conduit obtain/placement lifecycle
- Anchor tier crafting/upgrades
- Rename dialog
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


## Trust and capabilities

Implemented:
- V005 normalized `sanctuary_trust` and `sanctuary_capabilities` persistence.
- UUID-backed trust relationships.
- Owner implicit access to every capability.
- Explicit `BUILD`, `BREAK`, `INTERACT`, `CONTAINER`, `REDSTONE`, and `ENTITIES` grants for trusted players.
- Untrusted players receive no capabilities.
- Trust removal cascades all capability grants for that player.
- Sanctuary deletion cascades all trust/capability rows.
- `/sanctuary trust <sanctuary> <player>`.
- `/sanctuary trust list <sanctuary>`.
- `/sanctuary untrust <sanctuary> <player>`.
- `/sanctuary capability <sanctuary> <player> <capability> <allow|deny>`.
- `/sanctuary admin permissions <sanctuary> <player>` raw effective permission inspection.
- Human-readable Sanctuary selector autocomplete for trust commands.
- Unit and SQLite persistence tests for permission resolution and cascading cleanup.


## Basic player protections

Implemented:
- `BUILD`, `BREAK`, `INTERACT`, `CONTAINER`, `REDSTONE`, and `ENTITIES` are enforced by Paper event listeners.
- Owners retain implicit full access through `SanctuaryPermissionService`.
- Trusted players only receive explicitly granted capabilities.
- Debug Sanctuaries enforce protections even for operators.
- `/sanctuary admin debugtrust <debug-sanctuary> [player] <capability|all> <allow|deny>` supports solo testing.
- Anchor blocks are excluded from generic `BREAK` protection and continue through the anchor lifecycle listener.
- SQL permission lookup failures fail closed and cancel the attempted action.


### Sanctuary management UI

Implemented:
- Owner right-click on an active Sanctuary anchor opens the personal ExtendedUI management screen.
- Admin right-click and `/sanctuary admin ui <sanctuary>` open the admin/debug view.
- Trust and capability management is available through ExtendedUI menus.
- Debug Sanctuaries expose solo-test permission controls in the admin UI.
- Owners can rename their Sanctuary from the management UI using the ExtendedUI text-input dialog.
- Sanctuary names are trimmed, must be nonblank, and are limited to 32 characters.
- Renames persist through the existing `name` column and immediately affect readable Sanctuary selectors.

## Security policy foundation

Implemented:
- V006 adds persisted per-Sanctuary security mode and blacklist state.
- Security modes are `NORMAL` and `LOCKDOWN`.
- Player relationship is resolved centrally as `OWNER`, `TRUSTED`, `NEUTRAL`, or `BLACKLISTED`.
- Effective threat is resolved centrally as `SAFE`, `NEUTRAL`, or `HOSTILE`.
- In Normal mode, only explicitly blacklisted players are hostile.
- In Lockdown mode, every player except the owner and trusted players is hostile.
- Lockdown does not rewrite neutral players into blacklist rows. Returning to Normal restores neutral behavior.
- Trust and blacklist are mutually exclusive through Sanctuary management operations. Trusting a player removes their blacklist entry; blacklisting a trusted player removes trust and capability grants.
- Management UI now separates Players & Access from Security.
- Owners/admins can manage trusted and blacklisted online players through the UI.
- Until Beacon tier gating is implemented, Lockdown is visible but owner-locked; admins can toggle it from the admin UI for testing.
- Manual and automatic territory boundaries are viewer-specific colors: owner blue, trusted green, neutral white, hostile red.
- Existing hard player protections are now controlled by `protections.hard` configuration and default disabled.
- Hard-protection configuration is reloadable with `/sanctuary admin reload`.

Not implemented in this phase:
- Beacon defense tiers.
- Weakness, Wither, Blindness, Elytra suppression, or other proximity effects.
- Tier-gated owner access to Lockdown.
- Sentry trigger/response behavior.

## Configurable boundary visuals

Implemented:
- Relationship-specific boundary particles are configurable under `territory.boundary.particles`.
- Default particles are `GLOW_SQUID_INK` for owners, `GLOW` for trusted players, `END_ROD` for neutral players, and `SOUL` for hostile players.
- Configured particles must not require additional particle data. Invalid or data-bearing particle types fall back to the relationship default with a warning.
- Automatic boundary rendering now uses an exclusive visibility band: `minimum-distance < point distance < maximum-distance`.
- The previous `automatic.trigger-distance` value is accepted as a compatibility fallback for `maximum-distance` when the new key is absent.
- Boundary configuration remains reloadable with `/sanctuary admin reload`.

## Layered Beacon effects

Implemented:
- Five Beacon effect tiers share the Sanctuary's tier progression.
- Effect radius thresholds are derived from `territory.maximum-radius / 5` and are not separately configurable.
- Each effect applies from its own radius inward, so effects stack toward the Beacon core.
- Safe effect order, Tier I through V: Regeneration II max, Resistance II max, Strength II max, Haste II max, Speed III max.
- Hostile effect order from outermost Tier V to innermost Tier I: Elytra Disabled I, Mining Fatigue III max, Weakness III max, Blindness I, Wither II max.
- Newly available effects default to Level I.
- V007 persists each Sanctuary's independently selected effect level.
- The management UI exposes Beacon Effects and currently allows free level cycling for unlocked effects. No upgrade item is consumed yet.
- Owner and trusted players receive safe effects.
- Neutral players receive no Beacon effects in Normal mode.
- Blacklisted players receive hostile effects.
- Neutral players are treated as hostile in Lockdown through the existing threat resolver.
- Debug Beacons are created at Tier V and the configured maximum radius so every effect tier can be tested.
- Debug Security UI can set the current admin to Trusted, Neutral/Unconfigured, or Blacklisted for solo effect testing.
