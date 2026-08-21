# Sanctuary Implementation Plan

## Purpose

Sanctuary will be a Paper plugin that adds player-owned protected territories anchored by special Beacon and Conduit items.

A Sanctuary is a persistent gameplay entity with:

- A unique anchor ID
- An owner
- A name
- A physical anchor location when active
- Territory
- Protection upgrades
- Configurable active protection levels
- Trust and permissions
- Sentry guards
- Companion guards
- Progression and advancements

Sanctuary will remain independent of God.

God may provide special ExtendedItems artifacts used by Sanctuary progression, but Sanctuary must not depend on God being online or available for Sanctuary mechanics to function.

## High-Level Architecture

```text
ExtendedUI
    Shared UI library

ExtendedItems
    Shared custom item identity library

God
    AI, Favor, quests, divine rewards

Sanctuary
    Anchors
    Territory
    Protections
    Trust
    Guards
    Advancements
```

Sanctuary will depend on:

- Paper API
- ExtendedUI
- ExtendedItems
- SQLite

Sanctuary will not depend directly on God.

## Core Design Principle

A Sanctuary is identified by its anchor ID.

The anchor ID remains stable across:

- Placement
- Breaking
- Relocation
- Beacon or Conduit upgrades
- Server restarts

The anchor location, tier, and active state may change.

The anchor ID does not.

Conceptually:

```text
anchor_id == sanctuary identity
```

## Sanctuary Types

Initial Sanctuary types:

```text
Sanctuary
├── Beacon Sanctuary
│   └── Land-focused
│
└── Conduit Sanctuary
    └── Water-focused
```

Beacon and Conduit Sanctuaries should share the same core systems where possible.

Shared systems include:

- Ownership
- Naming
- Territory
- Trust
- Protection configuration
- Guard ownership
- Persistence
- Progression state
- UI
- Advancement tracking

The main differences will be:

```text
Beacon
- Land-oriented effects
- Land sentries
- Land protections

Conduit
- Water-oriented effects
- Aquatic sentries
- Water protections
```

## Ownership

Ownership is assigned when an unbound Sanctuary anchor is placed for the first time.

Before first placement:

```text
Sanctuary Beacon
anchor_id = UUID
owner = unbound
```

First placement:

```text
Player places anchor
        ↓
Sanctuary validates placement
        ↓
Owner UUID assigned
        ↓
Sanctuary record created
        ↓
Anchor becomes bound
```

After ownership is assigned, breaking the anchor does not remove or transfer ownership.

If another player obtains the bound anchor, Sanctuary should not allow them to claim ownership simply by placing it.

Ownership transfer is not part of the initial implementation.

It may be added later as an explicit controlled action.

## Multiple Sanctuaries

A player may own multiple Sanctuaries.

There is no one-Beacon or one-Conduit-per-player limit in the core design.

Examples:

```text
Owner: Liam

Sanctuaries:
- Home
- Mountain Keep
- Village
- Ocean Temple
```

Sanctuary names are display metadata only.

Internal references always use the anchor ID.

## Sanctuary Naming

Every Sanctuary will have a configurable display name.

The default may be generated from the owner's name until changed.

Example:

```text
Liam's Sanctuary
```

A player may rename the Sanctuary from the Sanctuary UI.

The name is stored in SQLite and updates immediately.

Names do not need to be globally unique.

### Name Entry

Renaming should use a native Paper text-input dialog through ExtendedUI.

Conceptually:

```text
Sanctuary Settings
        ↓
Rename Sanctuary
        ↓
Native text input dialog
        ↓
Validate
        ↓
Persist new name
```

Suggested initial rules:

- 1 to 32 characters
- Not blank
- No control characters
- No formatting injection
- Normal Unicode may be allowed
- Names do not need to be unique

Exact validation rules may be refined during implementation.

## Sanctuary Entry Title

When a player crosses from outside a Sanctuary into its territory, Sanctuary should display a title card.

Example:

```text
        Mountain Keep
      Sanctuary of Liam
```

The title should only display on a territory transition.

It should not repeatedly display while the player remains inside.

Conceptually:

```text
Outside
   ↓
Mountain Keep
   → show title

Mountain Keep
   ↓
Mountain Keep
   → do nothing
```

If multiple same-owner Sanctuaries overlap, the current Sanctuary should be resolved deterministically.

Closest anchor is the preferred initial rule unless implementation testing shows a better choice.

## Territory Model

Sanctuary progression should be based on protected area rather than direct radius progression.

Internally:

```text
territory_area = N
```

The effective radius is derived from area:

```text
radius = sqrt(area / PI)
```

This prevents progression from scaling as radius squared.

The exact area values are balancing data and should be configurable.

### Territory Shape

Initial territory shape:

```text
Horizontal circle / cylinder
```

A player is inside the Sanctuary when:

```text
horizontal_distance_squared <= radius_squared
```

Vertical limits should be unrestricted initially.

This allows:

- Towers
- Underground bases
- Multi-level structures

without requiring separate vertical claim configuration.

## Inter-Owner Spacing

A Sanctuary anchor may not be placed too close to another player's Sanctuary.

The minimum anchor-to-anchor distance is:

```text
2 * MAX_SANCTUARY_RADIUS + MARGIN
```

The margin is expected to be approximately 10 blocks, but must be configurable.

Example:

```text
minimum_distance =
    (2 * max_radius)
    + margin
```

This reserves the full future growth envelope of both Sanctuaries.

The rule prevents two different owners from placing anchors in positions where their territories could overlap after future upgrades.

Validation applies when:

- Placing a new Sanctuary for the first time
- Relocating an existing Sanctuary

Same-owner overlap may be allowed.

If later gameplay reveals a problem with same-owner overlap, that rule may be refined.

## Anchor Lifecycle

### Initial Crafting

Sanctuary anchors are physical custom items identified through ExtendedItems.

Examples:

```text
Sanctuary Beacon
Sanctuary Conduit
```

The initial anchor should be crafted from:

- The vanilla Beacon or Conduit
- Materials that represent the normal structure cost
- Possibly an ExtendedItems progression artifact

For Beacons, the recipe should consume the materials that would normally have been used to construct the beacon pyramid.

The player should not need to build a normal pyramid after placing the Sanctuary Beacon.

The material cost has already been paid through crafting.

Conduits should use the same general philosophy.

Exact recipes are not part of the initial architecture and should be balanced later.

### First Placement

```text
Unbound anchor item
        ↓
BlockPlaceEvent
        ↓
Validate spacing
        ↓
Assign owner
        ↓
Create Sanctuary record
        ↓
Persist location
        ↓
Set state ACTIVE
```

### Breaking

Breaking an active Sanctuary anchor does not delete the Sanctuary.

Instead:

```text
Active Sanctuary
        ↓
Anchor broken
        ↓
Sanctuary becomes INACTIVE
        ↓
Territory removed from runtime
        ↓
Protections stop
        ↓
Sentry mobs despawn
        ↓
Anchor item drops with same anchor ID
```

The following remain persisted:

- Owner
- Name
- Anchor ID
- Trust configuration
- Protection unlocks
- Protection active levels
- Guard posts
- Guard ownership
- Progression state
- Advancement-related state
- Anchor tier

### Re-Placement

When the same bound anchor is placed again:

```text
Bound anchor item
        ↓
Validate anchor ID
        ↓
Validate owner
        ↓
Validate new placement spacing
        ↓
Update Sanctuary location
        ↓
Set ACTIVE
        ↓
Restore runtime systems
```

The Sanctuary remains the same logical Sanctuary.

## Anchor Upgrades

Anchor upgrades should happen through crafting rather than through a database-only menu purchase.

Example:

```text
Sanctuary Beacon I
+ rare Minecraft materials
+ ExtendedItems artifact
        ↓
Sanctuary Beacon II
```

The upgraded output must preserve the same anchor ID.

Example:

```text
Tier I
anchor_id = ABC
        ↓
craft
        ↓
Tier II
anchor_id = ABC
```

This means:

- Same Sanctuary
- Same owner
- Same name
- Same trust
- Same protections
- Same guards
- Same progression state

Only the anchor tier changes.

The Sanctuary UI may show:

- Current tier
- Next tier
- Required recipe
- Required artifacts

but should not directly perform the physical tier upgrade.

## ExtendedItems Integration

Sanctuary will use ExtendedItems for:

- Sanctuary Beacon identity
- Sanctuary Conduit identity
- Anchor upgrade artifacts
- Protection unlock artifacts
- Guard deployment items where appropriate

Sanctuary must not identify special items by:

- Material alone
- Display name
- Lore
- Glint

ExtendedItems PDC identity is authoritative.

Example:

```text
extendeditems:id = sanctuary_beacon
extendeditems:version = 1

sanctuary:anchor_id = UUID
sanctuary:owner_uuid = UUID
sanctuary:tier = 2
```

ExtendedItems identifies what the item is.

Sanctuary owns the stateful metadata.

## Progression Model

Sanctuary will not use its own currency initially.

Permanent Sanctuary progression should use:

```text
Minecraft resources
        +
ExtendedItems artifact
        +
progression prerequisites
```

God may grant the required ExtendedItems artifact through:

- Quest completion
- Favor purchase
- Relationship progression
- Divine events
- Operator actions
- Future systems

Sanctuary does not care how the artifact was obtained.

Sanctuary only validates that the correct ExtendedItems artifact is present.

This keeps Sanctuary independent from God's economy.

## Progression Tree

Sanctuary should have its own Minecraft advancement tree.

Initial progression concepts may include:

```text
Sanctuary
│
├── First Sanctuary
│   Craft/place first Sanctuary Beacon
│
├── Expanded Territory
│   Upgrade Sanctuary area
│
├── First Protection
│   Unlock first protection
│
├── Trusted Ground
│   Add a trusted player
│
├── First Sentry
│   Deploy first sentry
│
├── Advanced Sanctuary
│   Reach a higher anchor tier
│
└── Underwater Sanctuary
    Craft/place first Sanctuary Conduit
```

Exact advancement names, descriptions, icons, and requirements should be finalized during implementation.

Sanctuary owns these advancements.

God does not.

## Protection Model

Each protection has two separate values:

```text
max_unlocked_level
active_level
```

Example:

```text
PROPERTY
Unlocked: 3
Active: 0
```

or:

```text
HOSTILE_MOBS
Unlocked: 4
Active: 2
```

The active level must always satisfy:

```text
0 <= active_level <= max_unlocked_level
```

Level 0 means disabled.

Once a protection level has been permanently unlocked, the player may freely switch between:

```text
Off
Level I
Level II
...
Highest unlocked level
```

No additional payment should be required merely to toggle an already unlocked protection.

## Initial Protection Categories

Initial planned protection categories:

```text
BLOCK_EDIT
EXPLOSION
HOSTILE_MOBS
PROPERTY
PROTECTED_ENTITIES
INTRUSION
```

Exact effects and level counts are balancing work.

### Block Edit Protection

Potential progression:

```text
I
Protect block breaking

II
Protect block placing

III
Protect more indirect block modification
```

### Explosion Protection

Potential progression:

```text
I
Creeper block protection

II
TNT block protection

III
General protected explosion behavior
```

### Hostile Mob Protection

Potential progression:

```text
I
Reduced hostile spawning

II
No natural hostile spawning

III
Damage hostile mobs inside territory

IV
Stronger defensive effect near the Sanctuary center
```

### Property Protection

Potential progression may include protection for:

- Chests
- Barrels
- Furnaces
- Shulker boxes
- Hoppers
- Doors
- Buttons
- Levers
- Workstations
- Item frames
- Armor stands
- Other interactable blocks

### Protected Entity Protection

Potential protected entities include:

- Pets
- Livestock
- Villagers
- Wandering traders
- Iron golems
- Other configured friendly entities

### Intrusion Protection

Potential progression:

```text
I
Warning

II
Debuffs

III
Damage field

IV
Stronger damage closer to Sanctuary center
```

The final design should preserve player agency and allow the owner to disable or lower the active level at any time.

## Trust and Permissions

Sanctuary trust should use capabilities internally.

Possible capabilities:

```text
ENTER
USE_DOORS
USE_CONTAINERS
USE_WORKSTATIONS
PLACE_BLOCKS
BREAK_BLOCKS
MANAGE_ANIMALS
MANAGE_GUARDS
MANAGE_TRUST
RENAME_SANCTUARY
MANAGE_SANCTUARY
```

Named roles may map to groups of capabilities.

Possible role concepts:

```text
VISITOR
GUEST
RESIDENT
BUILDER
STEWARD
CO_OWNER
```

The role names and exact capability mappings should remain configurable or easy to refine.

The underlying capability model should be the authoritative permission mechanism.

## ExtendedUI Integration

Sanctuary will use ExtendedUI for all custom management interfaces.

Sanctuary should not depend directly on InvUI.

Planned primary navigation:

```text
Sanctuary Main
├── Territory
├── Protections
├── Trust
├── Guards
├── Upgrades
├── Status
└── Settings
```

### Settings

Settings should include:

```text
Sanctuary Name
Other future Sanctuary preferences
```

Renaming should use an ExtendedUI wrapper around Paper's text-input dialog API.

## Sentry Guards

Sentry guards are Sanctuary-bound defenders.

A sentry is associated with a persistent sentry post.

The post should be visually represented by a half slab or other configured base block.

The post is authoritative even when the sentry mob is absent.

Each post has persistent data such as:

```text
post_id
sanctuary_id
owner_uuid
guard_type
guard_tier
location
state
respawn_at
```

The slab itself is not relied upon for all persistent data because normal slabs do not provide block-entity PDC storage.

SQLite is authoritative.

## Sentry Post Visual State

The post should remain visibly special.

Possible visual elements:

- Periodic particles
- Particle burst on state changes
- Floating TextDisplay status
- Subtle ambient effect

Possible status labels:

```text
Divine Sentry
Active
```

```text
Divine Sentry
Inactive
```

```text
Divine Sentry
Reforming - 7:42
```

The display should only be visible within a reasonable distance to avoid visual clutter.

Exact particle types, display distances, and text formatting are presentation details to finalize during implementation.

## Sanctuary Deactivation and Sentries

When the Sanctuary anchor is broken:

```text
Sanctuary becomes INACTIVE
        ↓
All active sentry guards immediately despawn
        ↓
Large particle effect
        ↓
Sentry posts remain
        ↓
Posts change to INACTIVE
```

The sentry mob should not remain in the world while its Sanctuary is inactive.

The half slab/post remains registered and can be broken by the authorized player.

Breaking the post should return the appropriate guard deployment item and unregister the post.

## Sentry Respawn

Base sentries are persistent defenses.

If a sentry dies while the Sanctuary is active:

```text
Sentry dies
        ↓
Post remains
        ↓
Respawn cooldown begins
        ↓
Status becomes REFORMING
        ↓
Particles may increase as respawn approaches
        ↓
Sentry respawns when eligible
```

A default cooldown around 10 minutes is currently envisioned.

The exact value must be configurable.

Respawn state should persist using an absolute timestamp rather than an in-memory tick count.

Example:

```text
respawn_at = UTC timestamp
```

Restarting the server must not reset the cooldown.

If a Sanctuary becomes inactive during the cooldown:

- The cooldown may continue
- No sentry may spawn while the Sanctuary is inactive
- Reactivation should evaluate whether the cooldown has completed

## Companion Guards

Companion guards are mobile bodyguards associated with the player rather than the Sanctuary anchor.

They should be weaker than equivalent sentry versions.

Companion rules:

```text
- Follow owner
- Protect owner
- Reduced stats or abilities compared to sentry form
- Smaller threat/detection radius
- No Sanctuary respawn
- Permanent death
```

Companion guards may use the same guard type and behavior infrastructure as sentries but with different combat profiles.

## Planned Guard Types

Current candidate guard mobs include:

```text
Pillager
Skeleton
Iron Golem
Enderman
Evoker
Piglin Brute
Baby Zombie
Warden
Creaking
Blaze
Wither
```

Potential underwater guards include:

```text
Drowned
Guardian
Elder Guardian
Axolotl
Dolphin support roles
```

Not every mob needs to exist as both a sentry and a companion.

Examples likely to remain sentry-only:

```text
Warden
Creaking
Wither
Elder Guardian
```

Exact unlock tiers and balancing are not finalized.

## Guard Behavior

Guards will require custom Paper mob goals and custom threat logic.

Shared threat concepts should include:

```text
Owner
Trusted players
Unknown players
Explicit enemies
Protected villagers
Protected livestock
Hostile mobs
Players attacking owner
Players damaging protected entities
Players violating protected actions
```

Threat state should distinguish:

```text
Trusted
Unknown
Trespassing
Hostile
Explicit enemy
```

Unknown players should not automatically be attacked merely for existing unless the Sanctuary is configured for that behavior.

## Guard Architecture

Conceptually:

```text
GuardController
├── Ownership
├── Threat tracking
├── Target selection
├── Friendly-fire rules
├── Sanctuary trust lookup
├── Combat profiles
└── Mob-specific behavior
```

Then:

```text
Guard
├── SentryGuard
└── CompanionGuard
```

Mob-specific combat behavior should be separated from common guard ownership and targeting logic.

## Wither Guard Special Handling

If a Wither sentry is implemented, it should behave as a heavily customized entity.

Required constraints include:

- No destructive block damage
- No normal uncontrolled targeting
- No attacks against trusted entities
- No standard unrestricted roaming
- Preferably no boss health bar
- Custom non-destructive attacks
- Sanctuary-bounded behavior

This feature should not be part of the first guard milestone.

It should be implemented only after the general guard framework is stable.

## Creaking Guard Special Handling

Creaking behavior may require special handling due to its heart relationship and non-standard vanilla damage behavior.

Possible implementation:

```text
Special Creaking Post / Heart
        ↓
Divine Creaking
        ↓
Sanctuary defense
```

This should be prototyped before being included in the stable progression tree.

## Conduit Sanctuaries

Conduits should behave as the underwater equivalent of Beacons.

The core lifecycle should be the same:

```text
Craft
Place
Assign owner
Create Sanctuary
Configure
Break
Persist inactive state
Upgrade
Replace
Reactivate
```

Conduit Sanctuaries should reuse:

- Anchor ID model
- Ownership
- Naming
- Territory
- Trust
- Protection configuration
- Persistence
- UI
- Guard framework
- Advancement framework

Water-specific protections and guard types may differ.

## Persistence

Sanctuary will use SQLite.

JSON should not be used as the primary gameplay persistence format.

SQLite will be authoritative for all persistent Sanctuary state.

Recommended database:

```text
sanctuary.db
```

## Initial Database Model

Conceptual tables:

### sanctuaries

```text
id
owner_uuid
type
name
world
x
y
z
tier
territory_area
state
created_at
updated_at
```

Inactive Sanctuaries may have a null or inactive location representation depending on final schema design.

### sanctuary_protections

```text
sanctuary_id
protection_type
max_unlocked_level
active_level
```

### sanctuary_permissions

```text
sanctuary_id
player_uuid
role
```

If custom per-capability overrides are later supported, a separate capability table may be added.

### guard_posts

```text
id
sanctuary_id
owner_uuid
guard_type
guard_tier
world
x
y
z
state
respawn_at
```

### companion_guards

```text
id
owner_uuid
guard_type
guard_tier
entity_uuid
state
```

Additional tables may be added for:

- Upgrade history
- Audit history
- Advancement-related persistent state
- Custom trust overrides

## Database Migrations

Database migrations should exist from the first persistent release.

The schema must not rely on destructive recreation.

Future plugin updates should be able to migrate existing Sanctuary state safely.

## Runtime State

SQLite is authoritative.

Runtime caches may be used for:

- Active Sanctuary lookup
- Territory checks
- Current player territory
- Active sentries
- Trust resolution
- Protection resolution

Caches must be rebuildable from persistent state.

A server restart must not lose gameplay configuration.

## Territory Lookup Performance

Territory checks may occur frequently.

The implementation should avoid scanning every Sanctuary for every player movement or block event.

Potential approaches include:

- World-indexed active Sanctuary collections
- Spatial chunk indexing
- Cached nearby Sanctuary lookup
- Squared-distance calculations

The exact optimization should be based on expected server scale and profiling.

Correctness should come before premature complexity.

## Commands

Sanctuary should provide player and operator commands even though normal interaction is GUI-driven.

Initial concepts:

```text
/sanctuary
/sanctuary open
/sanctuary status
```

Operator/admin concepts:

```text
/sanctuary admin givebeacon <player>
/sanctuary admin giveconduit <player>
/sanctuary admin info <anchor-id>
/sanctuary admin list <player>
/sanctuary admin remove <anchor-id>
/sanctuary admin set-tier <anchor-id> <tier>
/sanctuary admin reload
```

Exact syntax should be finalized during implementation.

Commands should support contextual tab completion.

## Public API

Sanctuary should expose a public API for other plugins.

God and future systems should not access Sanctuary internals directly.

Possible API concepts:

```java
public interface SanctuaryApi
{
    Optional<SanctuaryView> getSanctuary(UUID sanctuaryId);

    List<SanctuaryView> getPlayerSanctuaries(UUID playerId);

    Optional<SanctuaryView> getSanctuaryAt(Location location);

    boolean hasPermission(
        UUID sanctuaryId,
        UUID playerId,
        SanctuaryPermission permission);

    Optional<SanctuaryProtectionView> getProtection(
        UUID sanctuaryId,
        SanctuaryProtectionType type);
}
```

Mutation APIs should be added only where cross-plugin use cases justify them.

Public APIs should expose immutable views or DTOs rather than mutable persistence entities.

## Suggested Project Structure

```text
Sanctuary/
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    ├── main/
    │   ├── java/
    │   │   └── dev/liamtolkkinen/sanctuary/
    │   │       ├── SanctuaryPlugin.java
    │   │       │
    │   │       ├── api/
    │   │       ├── anchor/
    │   │       ├── sanctuary/
    │   │       ├── territory/
    │   │       ├── protection/
    │   │       ├── trust/
    │   │       ├── guard/
    │   │       ├── advancement/
    │   │       ├── persistence/
    │   │       ├── command/
    │   │       ├── listener/
    │   │       └── ui/
    │   │
    │   └── resources/
    │       ├── plugin.yml
    │       └── config.yml
    │
    └── test/
        └── java/
```

The exact Java package name may be adjusted before implementation.

## Configuration

The plugin should use configuration for balancing values rather than hardcoding them.

Likely configurable values include:

```text
Maximum Sanctuary radius
Inter-owner placement margin
Territory area tiers
Anchor recipes
Protection requirements
Protection maximum levels
Guard respawn cooldown
Guard limits
Guard stats
Guard detection range
Particle settings
Title display settings
Name limits
```

Configuration should not replace persistent player state.

SQLite remains authoritative for player-owned Sanctuary data.

## Testing

Automated tests should be added from the beginning.

Important test areas include:

### Anchor Identity

- New anchors receive a valid anchor ID.
- Anchor ID survives placement.
- Anchor ID survives breaking.
- Anchor ID survives upgrading.
- Replaced anchor restores the same Sanctuary.
- Another player cannot claim a bound anchor.

### Placement

- First placement assigns ownership.
- Placement respects inter-owner minimum spacing.
- Relocation respects inter-owner minimum spacing.
- Same-owner behavior matches configured rules.
- Failed placement does not corrupt Sanctuary state.

### Persistence

- Sanctuary survives restart.
- Inactive Sanctuary survives restart.
- Trust survives restart.
- Protection state survives restart.
- Guard posts survive restart.
- Respawn timestamps survive restart.

### Territory

- Territory boundary calculations are correct.
- Area-to-radius conversion is correct.
- Entry detection only fires on transitions.
- Closest-anchor overlap handling is deterministic.

### Protections

- Active level cannot exceed unlocked level.
- Level 0 disables protection.
- Switching active levels does not remove unlocks.
- Each protection event respects trust capabilities.

### Sentries

- Deactivation despawns active sentries.
- Sentry post remains registered.
- Inactive post does not spawn guard.
- Respawn cooldown persists.
- Breaking post returns correct guard item.
- Sanctuary reactivation restores eligible sentries.

### ExtendedItems Integration

- Sanctuary recognizes valid custom anchors.
- Sanctuary rejects malformed anchors.
- Sanctuary recognizes valid progression artifacts.
- Sanctuary does not accept ordinary vanilla lookalike items.

### ExtendedUI Integration

- Owner can open Sanctuary UI.
- Unauthorized player cannot manage Sanctuary.
- Rename validation works.
- Navigation and refresh use current state.

## Implementation Order

### Phase 1: Project Foundation

Create:

- Gradle project
- Paper plugin entry point
- SQLite dependency
- ExtendedItems dependency
- ExtendedUI dependency
- Automated test project
- Initial configuration
- Initial README

### Phase 2: Persistence

Implement:

- Database bootstrap
- Migration system
- Sanctuary repository
- Core Sanctuary entity
- Active/inactive state

### Phase 3: Anchor Identity

Implement:

- Sanctuary Beacon ExtendedItem
- Anchor ID
- Unbound state
- Bound owner state
- Tier metadata

### Phase 4: Initial Placement

Implement:

- Placement listener
- Ownership assignment
- Spacing validation
- Database creation
- Active location registration

### Phase 5: Breaking and Re-Placement

Implement:

- Break handling
- Special item drop
- INACTIVE state
- Re-placement
- Location migration
- State restoration

This phase must be stable before tier upgrades are added.

### Phase 6: Anchor Upgrading

Implement:

- Tiered crafting
- Same anchor ID preservation
- Stateful metadata preservation
- Upgrade requirement validation
- ExtendedItems progression artifact validation

### Phase 7: Territory

Implement:

- Area-based progression
- Radius derivation
- Territory lookup
- Entry and exit tracking
- Entry title display
- Maximum-radius spacing rules

### Phase 8: ExtendedUI Main Menu

Implement:

```text
Territory
Protections
Trust
Guards
Upgrades
Status
Settings
```

Add Sanctuary rename through ExtendedUI text input.

### Phase 9: Trust and Capabilities

Implement:

- Trust entries
- Roles
- Capability resolution
- Permission checks
- UI management

### Phase 10: First Protections

Implement the simplest protection categories first.

Recommended initial order:

```text
BLOCK_EDIT
PROPERTY
EXPLOSION
```

Then expand to:

```text
PROTECTED_ENTITIES
HOSTILE_MOBS
INTRUSION
```

### Phase 11: Advancements

Implement Sanctuary's advancement tree using actual completed feature milestones.

Do not create advancement requirements for systems that are not yet implemented.

### Phase 12: Sentry Framework

Implement:

- Guard ID
- Guard post
- Half slab registration
- Visual state
- TextDisplay state
- Active/inactive transitions
- Respawn cooldown
- Basic threat model

Start with one simple guard type.

Recommended first guard:

```text
Iron Golem
```

or another low-complexity mob.

### Phase 13: Additional Sentry Types

Add:

- Pillager
- Skeleton
- Piglin Brute
- Enderman
- Evoker
- Blaze
- Other approved types

Add advanced mobs only after the framework is stable.

### Phase 14: Companion Guards

Implement the companion mode using the shared guard framework.

Companions should remain weaker and permanently die.

### Phase 15: Advanced Guards

Prototype:

- Warden
- Creaking
- Wither
- Elder Guardian

These should not block the initial Sanctuary release.

### Phase 16: Conduit Sanctuaries

Reuse the mature Beacon Sanctuary foundation for:

- Conduit anchors
- Underwater territory
- Aquatic protections
- Aquatic sentries
- Conduit-specific advancement branches

## First Release Milestone

The first meaningful Sanctuary release should prove:

```text
1. Sanctuary plugin loads.
2. SQLite database initializes.
3. Operator can obtain a Sanctuary Beacon.
4. Beacon has ExtendedItems identity and anchor ID.
5. First placement assigns ownership.
6. Placement spacing rules work.
7. Sanctuary persists.
8. Sanctuary has a name.
9. Entering territory shows the Sanctuary title.
10. Owner can open the ExtendedUI menu.
11. Owner can rename the Sanctuary.
12. Owner can break the anchor.
13. Sanctuary becomes inactive.
14. Anchor item preserves identity and ownership.
15. Replacing the same anchor restores the same Sanctuary.
16. Server restart preserves active and inactive Sanctuaries.
```

Only after this lifecycle is solid should permanent protections and guards become the focus.

## Open Balancing Decisions

The following are intentionally not finalized in this implementation plan:

- Exact Beacon recipes
- Exact Conduit recipes
- Exact anchor tier count
- Exact maximum radius
- Exact area progression values
- Exact placement margin
- Exact protection tier effects
- Exact protection unlock ingredients
- Exact God-issued artifact names
- Exact advancement names
- Exact guard progression
- Exact guard statistics
- Exact sentry capacity
- Exact companion pricing or acquisition
- Exact particle effects
- Exact entry title duration

These should be decided during implementation and gameplay testing without changing the core architecture.

## Design Rules

1. Sanctuary identity is the anchor ID.
2. Ownership is assigned on first placement.
3. Players may own multiple Sanctuaries.
4. Different owners must obey the future-growth spacing rule.
5. Breaking an anchor deactivates rather than deletes the Sanctuary.
6. Upgrading an anchor preserves the same anchor ID.
7. Sanctuary state is stored in SQLite.
8. Sanctuary does not use its own currency initially.
9. Permanent upgrades use Minecraft resources and ExtendedItems artifacts.
10. God is not a runtime dependency of Sanctuary.
11. Protections have separate unlocked and active levels.
12. Unlocked protections may be disabled or reduced freely.
13. ExtendedUI owns reusable UI infrastructure.
14. ExtendedItems owns shared item identity.
15. Sanctuary owns all Sanctuary gameplay logic.
16. Sentry posts persist when Sanctuary is inactive.
17. Sentry mobs immediately despawn when Sanctuary deactivates.
18. Companion guards are weaker than sentry equivalents and permanently die.
19. Conduits reuse the same core Sanctuary model as Beacons.
20. Exact balance values should remain configurable and should not dictate architecture.

## Long-Term Direction

Sanctuary should eventually provide a complete server-side base progression system without requiring client mods.

The intended experience is:

```text
Craft Sanctuary Anchor
        ↓
Claim and name territory
        ↓
Expand territory
        ↓
Unlock protections
        ↓
Configure trust
        ↓
Deploy sentries
        ↓
Earn higher-tier divine artifacts
        ↓
Upgrade physical anchor
        ↓
Unlock stronger Sanctuary systems
        ↓
Build advanced land and underwater Sanctuaries
```

The system should feel like an extension of vanilla Minecraft progression rather than a replacement for it.

Physical crafting, exploration, rare materials, advancements, custom items, base building, and configurable protections should all remain meaningful parts of the progression loop.
