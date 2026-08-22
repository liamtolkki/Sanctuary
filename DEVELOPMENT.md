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
