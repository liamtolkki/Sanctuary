# Minecraft Plugin Architecture and Development Plan

## Purpose

This document defines how the Minecraft server's custom plugin ecosystem is organized, how the projects interact, how development should proceed, and how the complete system is tested and deployed.

It is intentionally higher level than the implementation plans for the individual projects.

Each repository should maintain its own `README.md` that starts as an implementation plan and gradually becomes the authoritative documentation for that project.

This document focuses on:

- Project responsibilities
- Dependency direction
- Shared library usage
- Cross-plugin contracts
- Runtime boundaries
- Persistence ownership
- Development order
- Testing strategy
- Local development server usage
- Deployment flow
- Example end-to-end interactions

## Project Overview

The current planned project set is:

```text
ExtendedUI
ExtendedItems
God
Sanctuary
```

Additional plugins or libraries may be introduced later as real requirements appear.

The goal is to keep each project focused and avoid both extremes:

- One giant plugin that owns everything
- Too many tiny projects with unnecessary runtime dependencies

## High-Level Architecture

```text
                     Shared Libraries

             +------------------------+
             |                        |
             v                        v
        ExtendedUI               ExtendedItems
             |                        |
             |                        |
      +------+-------+          +-----+------+
      |              |          |            |
      v              v          v            v
    God          Sanctuary     God       Sanctuary
      |              |
      |              |
      +------ future plugins ---+
```

The shared libraries provide reusable infrastructure.

The gameplay plugins own actual gameplay systems.

## Responsibility Summary

### ExtendedUI

ExtendedUI is a normal Java library.

It is not a Paper plugin.

It provides reusable user interface infrastructure for the custom plugin ecosystem.

ExtendedUI owns concepts such as:

- Menus
- Buttons
- Navigation
- Refresh
- Pagination
- Confirmation screens
- Item builders
- Theme conventions
- Safe inventory click handling
- Text input dialog wrappers
- Future reusable Paper UI abstractions

ExtendedUI does not own gameplay logic.

It should not know what a Sanctuary, Favor balance, guard, quest, or protection rule is.

### ExtendedItems

ExtendedItems is a normal Java library.

It is not a Paper plugin.

It defines stable identity and construction rules for special items shared between projects.

ExtendedItems owns:

- Stable item IDs
- Shared PDC keys
- Item format versioning
- Item creation
- Item identification
- Item validation
- Shared display metadata

ExtendedItems does not own:

- Quests
- Favor
- Sanctuary progression
- Player ownership
- Crafting rules
- Inventory transactions
- Gameplay permissions

### God

God is a Paper plugin.

God owns the AI-driven and divine interaction systems.

Current and planned God responsibilities include:

- Natural-language interaction
- Doctrine
- Relationship
- Favor
- Divine judgments
- Quests
- Divine rewards
- Favor-based rewards or purchases
- Awarding ExtendedItems artifacts
- Existing event processing
- Existing validated server actions

God does not own Sanctuary territory, protections, guards, or Sanctuary persistence.

God may interact with Sanctuary through Sanctuary's public API where a real cross-plugin use case exists.

### Sanctuary

Sanctuary is a Paper plugin.

Sanctuary owns:

- Sanctuary Beacon anchors
- Sanctuary Conduit anchors
- Ownership
- Naming
- Territory
- Boundary visualization
- Protection unlocks
- Active protection levels
- Trust
- Capabilities
- Sentry posts
- Sentry guards
- Companion guards
- Sanctuary advancements
- SQLite persistence
- Sanctuary UI
- Public Sanctuary API

Sanctuary does not own Favor.

Sanctuary progression uses Minecraft resources and ExtendedItems artifacts.

God may be one source of those artifacts, but Sanctuary must not depend on God being available at runtime.

## Dependency Direction

Dependencies should always point toward reusable infrastructure or explicitly exposed APIs.

Preferred direction:

```text
God -----------> ExtendedUI
God -----------> ExtendedItems

Sanctuary -----> ExtendedUI
Sanctuary -----> ExtendedItems

FuturePlugin --> ExtendedUI
FuturePlugin --> ExtendedItems
```

Where cross-plugin interaction is required:

```text
God -----------> Sanctuary API
```

or:

```text
FuturePlugin --> Sanctuary API
```

Sanctuary should not reach into God internals.

ExtendedUI and ExtendedItems must not depend on God or Sanctuary.

## Shared Libraries Are Build Dependencies

ExtendedUI and ExtendedItems should initially be normal Java libraries that are included during the build of consuming plugins.

They should not be installed independently in the Paper `plugins` directory.

Conceptually:

```text
God.jar
├── God code
├── shaded ExtendedUI
└── shaded ExtendedItems
```

```text
Sanctuary.jar
├── Sanctuary code
├── shaded ExtendedUI
└── shaded ExtendedItems
```

The exact Gradle shading and relocation configuration should be documented in each consuming repository.

## Cross-Plugin Communication Rules

Cross-plugin communication should use one of two mechanisms.

### Persistent Data Contract

Use stable persisted metadata when the thing being exchanged is a physical Minecraft item.

Example:

```text
God
    creates Consecrated Keystone
        ↓
ExtendedItems writes stable PDC identity
        ↓
Player stores item
        ↓
Server restarts
        ↓
Sanctuary reads item
        ↓
ExtendedItems validates identity
        ↓
Sanctuary consumes it for progression
```

This is the preferred contract for special items.

### Public Plugin API

Use a public API when one plugin needs current runtime information or needs to request a supported action from another plugin.

Example:

```text
God
    ↓
SanctuaryApi
    ↓
Query Sanctuary status
```

Public APIs should expose stable DTOs or immutable views.

Plugins should not directly access another plugin's:

- Database
- Internal repositories
- Private service classes
- Runtime caches
- Internal entity models

## Cross-Plugin Item Flow

The primary ExtendedItems use case is:

```text
God
  |
  | Quest completion or Favor purchase
  v
ExtendedItems.create(...)
  |
  v
Special artifact
  |
  | survives inventory, chests, restart
  v
Sanctuary
  |
  | ExtendedItems.validate(...)
  v
Sanctuary progression requirement satisfied
```

Sanctuary does not care how God awarded the item.

God does not care what Sanctuary does with the item after it is awarded.

That separation must remain intact.

## Stateful Item Ownership

ExtendedItems identifies the type of a special item.

The gameplay plugin that owns the item owns instance-specific state.

Example Sanctuary Beacon:

```text
extendeditems:id = sanctuary_beacon
extendeditems:version = 1

sanctuary:anchor_id = UUID
sanctuary:owner_uuid = UUID
sanctuary:tier = 2
```

ExtendedItems knows:

```text
This is a Sanctuary Beacon.
```

Sanctuary knows:

```text
This specific Beacon represents Sanctuary ABC,
belongs to player X,
and is Tier II.
```

## UI Flow

Gameplay plugins should use ExtendedUI rather than directly building every inventory or dialog from raw Paper or InvUI calls.

Preferred flow:

```text
Player interacts with gameplay system
        ↓
Gameplay plugin loads current state
        ↓
Gameplay plugin builds ExtendedUI screen
        ↓
ExtendedUI handles presentation and navigation
        ↓
Player clicks action
        ↓
Gameplay plugin validates action
        ↓
Gameplay plugin changes state
        ↓
ExtendedUI refreshes
```

ExtendedUI never decides whether the gameplay action is valid.

## Example Sanctuary UI Flow

```text
Player right-clicks Sanctuary Beacon
        ↓
Sanctuary resolves anchor ID
        ↓
Sanctuary loads current state
        ↓
ExtendedUI opens Sanctuary Main Menu
        ↓
Player selects Settings
        ↓
Player selects Rename
        ↓
ExtendedUI opens text input dialog
        ↓
Sanctuary validates name
        ↓
Sanctuary updates SQLite
        ↓
UI refreshes with new name
```

## Persistence Ownership

Each gameplay plugin owns its own persistent data.

### Sanctuary

Sanctuary uses SQLite.

Expected persistent state includes:

- Sanctuaries
- Anchor ownership
- Anchor tier
- Name
- Active/inactive state
- Anchor location
- Territory area
- Trust
- Protection unlocks
- Active protection levels
- Guard posts
- Guard state
- Respawn timestamps
- Other Sanctuary-specific progression state

Sanctuary must use migrations from the first persistent release.

### God

God continues to own its own existing runtime data and persistence.

Sanctuary must not write directly into God data.

If a future shared economy system is created, that should be handled as a separate design decision rather than mixing databases.

## Sanctuary Anchor Lifecycle

The core Sanctuary lifecycle is:

```text
Craft unbound Sanctuary anchor
        ↓
ExtendedItems identity exists
        ↓
Player places anchor for first time
        ↓
Sanctuary validates placement
        ↓
Owner assigned
        ↓
Sanctuary record created
        ↓
Anchor becomes active
        ↓
Territory and protections operate
```

When broken:

```text
Active anchor
        ↓
Anchor broken
        ↓
Sanctuary becomes INACTIVE
        ↓
Territory deactivates
        ↓
Protections stop
        ↓
Sentry mobs despawn
        ↓
Persistent Sanctuary state remains
        ↓
Bound anchor item drops
```

When upgraded:

```text
Bound Tier I anchor
        ↓
Craft with required resources/artifact
        ↓
Bound Tier II anchor
        ↓
Same anchor ID
        ↓
Same Sanctuary
```

When placed again:

```text
Bound anchor item
        ↓
Validate anchor ID and owner
        ↓
Validate location
        ↓
Update active location
        ↓
Reactivate same Sanctuary
```

## Sanctuary Territory and Boundary Flow

Sanctuary territory is area-based.

The effective radius is derived from configured area.

Different owners must obey the future-growth spacing rule:

```text
minimum anchor distance =
    2 * max Sanctuary radius
    + configured margin
```

Passive boundary warnings are always active.

Example runtime flow:

```text
Player moves
        ↓
Sanctuary checks nearby boundaries
        ↓
Player is near boundary
        ↓
Resolve viewer access state
        ↓
Render local particle arc
```

Beacon and Conduit Sanctuaries use different palettes.

Manual full-boundary preview is separate:

```text
/sanctuary show
/sanctuary hide
```

These commands do not disable passive warnings.

## Sanctuary Entry Alert Flow

Entry alerts are an upgradeable Sanctuary feature.

```text
Player outside Sanctuary
        ↓
Player crosses boundary
        ↓
Sanctuary detects transition
        ↓
Check owner alert upgrade/settings
        ↓
Check anti-spam state
        ↓
Send owner action-bar notification
```

The notification is intentionally non-intrusive.

A full-screen title is reserved for the player entering the Sanctuary.

Ordinary live alerts are not queued for offline owners.

## Guard and Sentry Flow

### Sentry Post

A sentry post is persistent.

The slab or post remains even if the guard mob is absent.

```text
Sentry item placed
        ↓
Post registered in SQLite
        ↓
Guard mob spawned
        ↓
Post and mob linked by IDs
```

When Sanctuary deactivates:

```text
Anchor broken
        ↓
Sanctuary INACTIVE
        ↓
Sentry mob immediately despawns
        ↓
Particle burst
        ↓
Post remains
        ↓
Post status becomes INACTIVE
```

When the Sanctuary reactivates:

```text
Sanctuary ACTIVE
        ↓
Registered posts evaluated
        ↓
Eligible guards respawn
```

If a sentry dies:

```text
Guard death
        ↓
Post remains
        ↓
Respawn timestamp persisted
        ↓
Status REFORMING
        ↓
Cooldown completes
        ↓
Respawn if Sanctuary active
```

### Companion Guards

Companions use the shared guard framework but are tied to the player rather than a Sanctuary post.

They are intentionally weaker than equivalent sentries and permanently die.

## Advancement Ownership

Sanctuary owns Sanctuary progression advancements.

God owns God-specific progression.

ExtendedUI and ExtendedItems own no advancements.

Example Sanctuary advancement path:

```text
Craft first Sanctuary anchor
        ↓
Place first Sanctuary
        ↓
Unlock first protection
        ↓
Expand territory
        ↓
Deploy first sentry
        ↓
Upgrade anchor
        ↓
Create Conduit Sanctuary
```

Exact advancement names and balance belong in the Sanctuary repository.

## Repository Strategy

Each major project has its own GitHub repository.

Current repositories:

```text
ExtendedUI
ExtendedItems
God
Sanctuary
```

Each repository should have:

```text
README.md
```

The README evolves over time.

### Early Development

The README acts as:

- Implementation plan
- Scope definition
- Architecture notes
- Development phases
- Open decisions

### Mature Project

The README becomes:

- Installation guide
- Usage documentation
- API documentation
- Commands
- Configuration
- Troubleshooting
- Development instructions
- Upgrade/migration notes

Implementation planning sections should gradually be replaced or moved as features become real.

## Source Control Expectations

Each repository should be independently buildable.

Cross-project dependencies should be versioned.

Avoid depending on local source folders from unrelated repositories for normal builds.

Preferred dependency options include:

- Published Maven package
- GitHub Packages
- Local development composite build only for development convenience

The final dependency distribution approach should be documented in ExtendedUI and ExtendedItems once their build pipelines are implemented.

## Development Environment

Development must not occur on the active Minecraft server.

The development machine will host a separate local Paper server.

Recommended structure:

```text
C:\MinecraftServer
    Active server

C:\MinecraftServerDev
    Local development server
```

The development server must use separate:

- Port
- World
- Plugin directory
- SQLite files
- Logs
- Configuration
- Runtime data

The active server should not be referenced by development plugins.

## Development Server Networking

Recommended development server configuration:

```properties
server-ip=127.0.0.1
server-port=25566
```

The developer connects using:

```text
localhost:25566
```

The active server remains on its normal port.

## Development World

The development world should be disposable.

A superflat world is recommended initially for:

- Anchor placement
- Territory measurement
- Boundary rendering
- Explosion tests
- Sentry tests
- Trust tests

A dedicated test area may contain:

```text
Anchor Test Area
Boundary Test Area
Protection Test Area
Explosion Test Area
Guard Arena
Conduit Pool
```

Development data should be safe to delete and recreate.

## Build and Local Deployment Flow

Each plugin should support a development deployment task.

Conceptually:

```text
Source change
        ↓
Unit tests
        ↓
Build plugin
        ↓
Copy JAR to C:\MinecraftServerDev\plugins
        ↓
Restart development Paper server
        ↓
Manual integration test
```

Do not rely on `/reload` for normal plugin development.

Full server restarts are preferred because plugins may own:

- Threads
- Database connections
- Event listeners
- Runtime caches
- Mob goals
- Scheduled tasks

## Automated Testing Layers

Testing should happen at several levels.

### Unit Tests

Use unit tests for deterministic logic.

Examples:

```text
ExtendedItems
- ID creation
- PDC validation
- Version handling

Sanctuary
- Area to radius
- Spacing
- Territory containment
- Protection state rules
- Trust capability resolution
- Guard state transitions
- Alert state transitions
```

### Persistence Integration Tests

Use temporary SQLite databases for:

- Schema creation
- Migrations
- Save/load
- Inactive Sanctuary persistence
- Guard posts
- Protection state
- Trust
- Respawn timestamps

Tests should never use the real development or active database.

### Paper Runtime Testing

Use the local development Paper server for behavior that requires actual server runtime.

Examples:

```text
Block placement
Block breaking
Particles
Titles
Action bars
Inventory menus
Text dialogs
Recipes
Explosions
Mob AI
Custom goals
Guard combat
Conduit behavior
Player movement
```

### Active Server Validation

The active server is the final deployment target.

Only tested builds should reach it.

The active server should not be the environment used to discover basic implementation bugs.

## Development Order

The current recommended development sequence is:

```text
1. ExtendedItems
2. ExtendedUI
3. Sanctuary foundation
4. Sanctuary anchor lifecycle
5. Sanctuary territory and UI
6. Sanctuary trust
7. Sanctuary protections
8. Sanctuary advancements
9. Sanctuary sentry framework
10. Additional guards
11. Companion guards
12. Conduit Sanctuaries
13. God integration with new ExtendedItems artifacts
14. Further cross-plugin features
```

This order may change slightly based on implementation needs.

The important rule is that shared contracts should exist before gameplay plugins rely on them.

## ExtendedItems Development Goal

ExtendedItems should be completed enough to provide:

```text
Stable IDs
Stable PDC keys
Creation
Identification
Validation
Versioning
Tests
```

before Sanctuary anchor items depend on it.

## ExtendedUI Development Goal

ExtendedUI should be completed enough to provide:

```text
Menus
Buttons
Navigation
Refresh
Pagination
Confirmation
Safe click handling
Text input dialog wrapper
```

before Sanctuary relies heavily on management screens.

ExtendedUI should grow from real Sanctuary requirements rather than speculative framework design.

## Sanctuary Initial Development Goal

The first meaningful Sanctuary milestone is not guards or advanced protection.

It is the full anchor lifecycle:

```text
Create anchor
Place
Assign owner
Persist
Name
Show territory
Open UI
Break
Deactivate
Restart
Replace
Reactivate same Sanctuary
```

This foundation must be stable before complex protections or AI are introduced.

## God Integration Timing

God integration with new Sanctuary progression should happen after:

- ExtendedItems is stable
- Sanctuary recognizes the required item IDs
- Sanctuary progression requirements are implemented

Then God may add:

- Quest rewards
- Favor purchases
- Divine artifacts

God should not be required to manually test the basic Sanctuary lifecycle.

## Example End-to-End Flow: God Quest to Sanctuary Upgrade

```text
Player asks God for stronger Sanctuary protection
        ↓
God determines quest/reward path
        ↓
Player completes requirement
        ↓
God awards ExtendedItems artifact
        ↓
Player stores artifact
        ↓
Player later opens Sanctuary progression
        ↓
Sanctuary checks Minecraft resources
        ↓
Sanctuary validates ExtendedItems artifact
        ↓
Sanctuary performs unlock/crafting action
        ↓
Artifact consumed by Sanctuary
```

No Sanctuary currency is required.

## Example End-to-End Flow: Beacon Tier Upgrade

```text
Player owns Tier I Sanctuary Beacon
        ↓
Player breaks Beacon
        ↓
Sanctuary becomes INACTIVE
        ↓
Sentries despawn
        ↓
Bound Beacon item drops
        ↓
Player crafts Tier II recipe
        ↓
Same anchor ID preserved
        ↓
Player places upgraded Beacon
        ↓
Sanctuary validates spacing
        ↓
Existing Sanctuary becomes ACTIVE
        ↓
Previous name/trust/protections/posts restored
```

## Example End-to-End Flow: Rename Sanctuary

```text
Owner opens Sanctuary UI
        ↓
Settings
        ↓
Rename
        ↓
ExtendedUI text dialog
        ↓
Owner enters new name
        ↓
Sanctuary validates
        ↓
SQLite updated
        ↓
Runtime cache updated
        ↓
Future entry titles use new name
```

## Example End-to-End Flow: Passive Boundary Warning

```text
Player approaches Sanctuary
        ↓
Territory system detects nearby boundary
        ↓
Access resolver checks relationship/capabilities
        ↓
Beacon or Conduit palette selected
        ↓
Local boundary arc sent to that player
        ↓
Player sees warning before crossing
```

This warning cannot be disabled.

## Example End-to-End Flow: Sanctuary Entry Alert

```text
Visitor crosses into Sanctuary
        ↓
Transition detected
        ↓
Check Perimeter Awareness upgrade
        ↓
Check notification settings
        ↓
Check anti-spam cooldown
        ↓
Owner online?
    yes ↓
Action-bar message sent
```

## Example End-to-End Flow: Sentry Deactivation

```text
Sanctuary active
        ↓
Guard standing at registered post
        ↓
Owner breaks Sanctuary anchor
        ↓
Sanctuary state becomes INACTIVE
        ↓
Guard removed with particle effect
        ↓
Post slab remains
        ↓
Status display changes to INACTIVE
        ↓
Anchor later replaced
        ↓
Post becomes eligible to spawn guard again
```

## Runtime Safety Rules

The following architectural rules should remain consistent across projects:

1. AI output is not trusted gameplay authority.
2. Gameplay plugins validate their own actions.
3. Shared libraries do not own gameplay state.
4. One plugin does not access another plugin's database directly.
5. Cross-plugin physical item exchange uses ExtendedItems identity.
6. Cross-plugin runtime interaction uses public APIs.
7. Persistent gameplay state belongs to the owning plugin.
8. UI presentation does not perform business validation.
9. Development data is separate from active server data.
10. Active server deployment happens only after automated and local runtime testing.

## Configuration Strategy

Balance values should be configurable in the plugin that owns them.

Examples:

```text
Sanctuary
- Max radius
- Margin
- Territory tiers
- Protection requirements
- Alert cooldown
- Guard cooldown
- Guard stats
- Particle settings

God
- Favor values
- Model settings
- Event behavior
- Quest/reward configuration
```

Shared libraries should avoid owning gameplay balance configuration.

## Logging and Diagnostics

Each gameplay plugin should provide useful diagnostics.

Recommended categories:

```text
Startup
Database migration
Configuration loading
API registration
Anchor lifecycle
Protection decisions
Guard lifecycle
Cross-plugin calls
Errors
```

Development builds should make failures easy to diagnose without requiring inspection of raw internal state.

Admin commands should support targeted inspection where useful.

## Version Compatibility

Each project should use semantic versioning or another consistent versioning strategy.

Cross-project compatibility must be deliberate.

For example:

```text
Sanctuary requires ExtendedItems API >= X
Sanctuary requires ExtendedUI API >= Y
```

Because libraries may be shaded into plugins, persisted formats such as ExtendedItems PDC IDs are more important than Java object identity.

Persistent formats should change carefully.

## CI/CD Direction

Each repository should eventually have its own CI pipeline.

At minimum:

```text
Commit
    ↓
Build
    ↓
Automated tests
    ↓
Artifact
```

Later:

```text
Tag
    ↓
Release artifact
```

Development deployment to the local Paper server can remain a local Gradle or PowerShell task.

Production deployment should only use successful tested builds.

## README Strategy Across Repositories

Each repository README should answer:

```text
What is this project?
What does it own?
What does it depend on?
How do I build it?
How do I test it?
How do I use it?
How do I configure it?
What public API does it expose?
What persistent data does it own?
What features are implemented?
What is still planned?
```

During early development, planned behavior is acceptable.

As implementation progresses, planned sections should be replaced with actual behavior.

This top-level architecture document should not become a duplicate of the project READMEs.

## Future Projects

New plugins or libraries should only be created when a responsibility becomes independently meaningful.

Potential future examples may include:

```text
Equipment progression
Vaults
Shared economy
Additional world systems
Other gameplay progression
```

A new project should have a clear responsibility boundary before being created.

## Final Architecture Principle

The system should be modular without becoming fragmented.

The preferred pattern is:

```text
Shared library
    provides reusable infrastructure

Gameplay plugin
    owns gameplay state and rules

Public API or persistent item contract
    connects independently owned systems
```

The goal is for God, Sanctuary, and future systems to feel like one coherent server experience while remaining independently testable, maintainable, and replaceable.
