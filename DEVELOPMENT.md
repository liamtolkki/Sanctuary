# Sanctuary Development

## Repository layout

During shared-library development, keep the repositories as siblings:

```text
C:\MinecraftDev\
├── ExtendedItems\
├── ExtendedUI\
├── Sanctuary\
└── server\
    └── plugins\
```

`settings.gradle.kts` automatically includes the sibling ExtendedUI and ExtendedItems builds when they are present. This is a development convenience only. The final Sanctuary plugin JAR shades and relocates those libraries so they are not installed separately on Paper.

## First setup

Use JDK 25 for both the IntelliJ Project SDK and Gradle JVM.

If the Gradle wrapper JAR has not been generated yet, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-wrapper.ps1
```

Then verify Java 25:

```powershell
.\gradlew.bat -q javaToolchains
```

Build and test:

```powershell
.\gradlew.bat clean build
```

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

## First runtime check

After the server starts, run:

```text
/sanctuary status
```

Expected result:

- Sanctuary reports its version.
- Database reports ready.
- The server log reports the path to `sanctuary.db`.
- `plugins/Sanctuary.jar` is the only Sanctuary-related runtime JAR needed. ExtendedUI and ExtendedItems are embedded in it.

The SQLite database should be created under the plugin data directory:

```text
plugins/Sanctuary/sanctuary.db
```

## GitHub Actions and private shared repositories

The workflow checks out ExtendedUI and ExtendedItems beside Sanctuary so Gradle can use the same composite-build arrangement as local development.

If all repositories are public, the default GitHub token is sufficient. If either shared repository is private, add a repository secret named:

```text
SHARED_REPOS_TOKEN
```

Use a token that has read access to ExtendedUI and ExtendedItems.

Before the first CI push, verify the wrapper executable bit:

```powershell
git update-index --chmod=+x gradlew
git ls-files -s gradlew
```

The mode should be `100755`.
