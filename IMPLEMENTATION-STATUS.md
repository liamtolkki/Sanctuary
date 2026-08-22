# Sanctuary Implementation Status

## Implemented in this initial project cut

- Java 25 Gradle project targeting Paper 26.1.2
- Paper plugin entry point
- Composite-build development dependencies for ExtendedUI and ExtendedItems
- Shaded/relocated ExtendedUI, ExtendedItems, and InvUI in the final Sanctuary JAR
- SQLite JDBC dependency embedded in the final Sanctuary JAR
- SQLite database bootstrap
- Versioned database migration system
- Initial `sanctuaries` table and indexes
- Immutable core Sanctuary model
- Active/inactive state model
- Beacon/Conduit type model
- SQLite Sanctuary repository
- Read-only public `SanctuaryApi`
- Paper ServicesManager registration for `SanctuaryApi`
- `/sanctuary status`
- `/sanctuary admin reload`
- Unit/integration tests for model invariants, migrations, and SQLite persistence
- Development deployment task targeting `C:\MinecraftDev\server\plugins`
- GitHub Actions build pipeline

## Deliberately not implemented yet

- Sanctuary Beacon ExtendedItems ID
- Sanctuary Conduit ExtendedItems ID
- Anchor PDC metadata
- Give-beacon/give-conduit commands
- Placement/break/re-placement listeners
- Territory calculations or spacing
- Entry titles or boundary visualization
- ExtendedUI Sanctuary screens
- Rename flow
- Trust, protections, guards, advancements, or upgrades

The ExtendedItems production catalog currently contains no released gameplay IDs. The first Sanctuary anchor ID must be added to ExtendedItems itself before Sanctuary begins anchor identity work.

## Next implementation milestone

Phase 3 begins by adding the first real shared item contract to ExtendedItems for the Sanctuary Beacon, testing it there, and publishing a consumer-ready ExtendedItems release. Sanctuary can then implement its own anchor ID, owner UUID, and tier metadata on top of that stable item identity.
