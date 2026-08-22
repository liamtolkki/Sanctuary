# Sanctuary Development

## Repository layout

During current shared-library development, keep ExtendedUI beside Sanctuary:

```text
C:\MinecraftDev\
├── ExtendedUI\
├── Sanctuary\
└── server\
    └── plugins\
```

`settings.gradle.kts` includes the sibling ExtendedUI build when present.

ExtendedItems is different now that the required item catalog has a release. Sanctuary pins and downloads the exact GitHub Release asset:

```text
ExtendedItems 0.1.0-alpha.2
extendeditems-0.1.0-alpha.2.jar
```

It is not resolved from an unspecified latest build and is not installed as a separate Paper plugin.

## First setup

Use JDK 25 for both the IntelliJ Project SDK and Gradle JVM.

If the Gradle wrapper JAR must be reconstructed, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-wrapper.ps1
```

Verify Java 25:

```powershell
.\gradlew.bat -q javaToolchains
```

Build and test:

```powershell
.\gradlew.bat clean build
```

The first build needs network access to download the pinned ExtendedItems release JAR. Subsequent builds reuse Gradle task output unless `clean` removes it.

## Development deployment

The default development Paper plugin directory is:

```text
C:\MinecraftDev\server\plugins
```

Build and copy the shaded plugin there with:

```powershell
.\gradlew.bat clean deployDev
```

Use a full Paper server restart after deploying. Do not use `/reload` as the normal development loop.

## Runtime validation for the Beacon lifecycle

After the server starts, create and place an unbound Beacon:

```text
/sanctuary admin givebeacon <player>
```

Then validate the complete lifecycle:

1. First placement creates one `ACTIVE` Sanctuary.
2. `/sanctuary admin beacons` shows generation 1 and the placed location.
3. Owner mining changes the Sanctuary to `INACTIVE`, advances the generation, and drops the sole current bound Sanctuary Beacon.
4. Re-place that Beacon elsewhere and verify the same Sanctuary ID becomes `ACTIVE` at the new location.
5. Mine it again and leave the bound item on the ground until it despawns. The registry should show `DESTROYED` with a destruction reason.
6. A `DESTROYED` Sanctuary must reject `/sanctuary recover <id>`.
7. For recovery testing without waiting, temporarily set `anchors.recovery.cooldown-seconds: 0`, mine a different Beacon, keep or hide the old item, and run `/sanctuary recover <id>`.
8. The registry should advance the generation. The recovered Beacon should place successfully; the older copy should be rejected as stale.
9. Restart Paper and verify active, inactive, destroyed, generation, and destruction audit state remain in `sanctuary.db`.

Recovery settings:

```yaml
anchors:
  recovery:
    enabled: true
    cooldown-seconds: 300
```

The SQLite database is under:

```text
plugins/Sanctuary/sanctuary.db
```

## GitHub Actions

The workflow checks out Sanctuary and ExtendedUI, sets up Java 25, and runs:

```text
./gradlew clean build --no-daemon
```

The Sanctuary build downloads the exact ExtendedItems `0.1.0-alpha.2` release JAR itself.

If ExtendedUI becomes private, add a repository secret named:

```text
SHARED_REPOS_TOKEN
```

with read access to ExtendedUI.

Before CI, verify the wrapper executable bit:

```powershell
git update-index --chmod=+x gradlew
git ls-files -s gradlew
```

The mode should be `100755`.

## Runtime validation for territory and spacing

Territory settings:

```yaml
territory:
  maximum-radius: 96.0
  spacing-margin: 16.0
```

The current Sanctuary radius is stored directly in `territory_radius`. New Sanctuaries use `anchors.initial-territory-radius`, currently `18.0`.

Spacing does not use the current 18-block radius. It reserves future growth using:

```text
minimum anchor distance = 2 * maximum-radius + spacing-margin
```

The defaults therefore require `208` horizontal blocks between anchors owned by different owners.

For a faster manual spacing test, temporarily use:

```yaml
territory:
  maximum-radius: 10.0
  spacing-margin: 5.0
```

The resulting minimum different-owner anchor distance is `25` blocks. Reload with:

```text
/sanctuary admin reload
```

Then test:

1. Run `/sanctuary admin debugbeacon` and place it in an open area.
2. Run `/sanctuary admin beacons` and verify it appears as `DEBUG-EPHEMERAL`, has a synthetic owner, and reports the expected derived territory radius.
3. Obtain a normal Beacon with `/sanctuary admin givebeacon <player>`.
4. Try to place the normal Beacon 24 blocks horizontally from the debug Beacon. Placement must be rejected.
5. Place it exactly 25 blocks horizontally away. Placement must succeed.
6. Vertical separation does not change the result. A Beacon with less than 25 blocks of horizontal separation is still rejected even at a very different Y coordinate.
7. Give yourself another normal Beacon and place it adjacent to your existing normal Sanctuary. Same-owner overlap must be accepted.
8. Break the debug Beacon. It must drop no Beacon item.
9. Run `/sanctuary admin beacons` again. The debug Sanctuary must be gone from the registry.

Restore the intended production values after testing.

## Runtime validation for recover autocomplete

1. Have one normal `INACTIVE` Sanctuary and one `DESTROYED` Sanctuary owned by your player.
2. Type `/sanctuary recover ` and request tab completion.
3. The `INACTIVE` Sanctuary ID should be listed.
4. The `DESTROYED` Sanctuary ID must not be listed.
5. Active and ephemeral debug Sanctuaries must not be listed either.

## Runtime validation for territory awareness

Build and deploy, fully restart Paper, then use `/sanctuary admin beacons` to obtain an active Sanctuary ID.

Test normal entry by walking from outside the calculated radius to inside it. With `territory.awareness.entry-title: true`, the Sanctuary name should appear as a title once per entry. Walk back out and re-enter to verify a second transition.

Test debug entry with `/sanctuary admin debugbeacon`, place it, walk outside its territory, then enter it. The entering player should receive a `[Sanctuary Debug]` chat line in addition to normal configured awareness behavior. Normal Sanctuary entries must not print this debug line.

Test boundary visualization with `/sanctuary boundary <name|all>`. The particle ring should be centered on the Beacon block and match the same horizontal radius used by entry detection.

## Radius-Based Territory and Proximity Boundaries

Territory progression now stores radius directly. V004 converts existing `territory_area` values to an equivalent `territory_radius` so existing physical boundaries do not move during upgrade.

Boundary particles are viewer-scoped with `Player.spawnParticle`, so manual and automatic boundary rendering is visible only to the player receiving it.

Automatic proximity rendering draws only cylinder-surface points within the configured trigger distance. For each horizontal boundary point at distance `d` from the viewer, the vertical half-height is `sqrt(triggerDistance^2 - d^2)`. This produces a local curved patch that grows as the viewer approaches and disappears outside the trigger distance.

`/sanctuary boundary <name>` uses human-readable name selectors. `/sanctuary boundary all` renders all eligible active boundaries whose boundary edge is within `territory.boundary.maximum-render-distance` of the viewer.

## Orphan anchor cleanup

A placed Sanctuary Beacon can outlive its SQLite record after manual database repair, rollback, or other exceptional state loss. `AnchorBreakListener` checks the anchor UUID before entering the normal break lifecycle. If the UUID is not registered, the block is treated as an orphan: the break is allowed, normal drops are suppressed, the player receives a warning, and the server log records the orphan anchor ID and location. Registered Sanctuaries continue through normal ownership, generation, and lifecycle validation.

## Automatic boundary refresh period

Automatic proximity rendering is controlled by:

```yaml
territory:
  boundary:
    automatic:
      update-period-ticks: 10
```

The scheduler itself ticks once per server tick and gates the rendering work using the configured period. This keeps the period reloadable through `/sanctuary admin reload`. Minecraft runs at 20 ticks per second under normal server conditions, so `10` ticks is approximately 0.5 seconds, `20` is approximately 1 second, and `2` is approximately 0.1 second.


## Runtime validation for trust and capabilities

Trust data is stored by player UUID. The owner always resolves to every capability. A trusted non-owner starts with no capabilities until grants are added explicitly.

Commands:

```text
/sanctuary trust <sanctuary> <player>
/sanctuary trust list <sanctuary>
/sanctuary untrust <sanctuary> <player>
/sanctuary capability <sanctuary> <player> <capability> <allow|deny>
/sanctuary admin permissions <sanctuary> <player>
```

Capabilities:

```text
BUILD
BREAK
INTERACT
CONTAINER
REDSTONE
ENTITIES
```

For runtime validation, trust a second player, verify `/sanctuary admin permissions` shows all capabilities denied, grant two capabilities, verify only those two show `ALLOWED`, restart Paper, and verify the same result persists. Then untrust the player and verify all capabilities return to `DENIED`. The owner should always show every capability as `ALLOWED` without trust rows.


## Protection testing

For solo testing, create and place an ephemeral debug Sanctuary, then use `/sanctuary admin debugtrust` to grant the operator specific capabilities. `sanctuary.admin` intentionally does not bypass normal territory protection checks. This lets one operator test denied and allowed behavior without a second account.
