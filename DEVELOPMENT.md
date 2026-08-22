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

## Runtime validation for the anchor milestone

After the server starts:

```text
/sanctuary status
```

Then, as an operator/admin:

```text
/sanctuary admin givebeacon <player>
```

Validate:

1. The target receives a `Sanctuary Beacon`, not an ordinary Beacon.
2. The item places normally for its first placement.
3. Placement reports the newly created Sanctuary name and UUID.
4. `/sanctuary status` remains healthy.
5. Restart Paper and verify the Sanctuary still exists in `sanctuary.db`.
6. Attempting to place an already-bound Beacon is rejected because re-placement is intentionally the next milestone.

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
