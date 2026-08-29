# Sanctuary Production Deployment

Sanctuary uses GitHub Releases as the production distribution source.

The public repository performs all compilation and testing on GitHub-hosted runners. Production servers do not run GitHub Actions jobs and do not need GitHub credentials.

## Release model

Normal pushes and pull requests run `.github/workflows/build.yml`.

The build workflow:

1. Checks out Sanctuary.
2. Builds with Java 25 and Gradle 9.7.1.
3. Runs the test suite.
4. Copies the shaded plugin JAR to `dist/Sanctuary.jar`.
5. Uploads `Sanctuary.jar` as the workflow artifact.

Workflow artifacts are development/CI outputs. They are not production releases.

Production releases are created manually with `.github/workflows/release.yml`.

The release workflow:

1. Can run only from `main`.
2. Accepts a version such as `0.1.0-alpha.1`.
3. Builds and tests with that version embedded in `plugin.yml`.
4. Produces `Sanctuary.jar`.
5. Produces `Sanctuary.jar.sha256`.
6. Creates a matching tag such as `v0.1.0-alpha.1`.
7. Creates a GitHub Release with generated release notes.
8. Publishes both files as release assets.

## Creating a release

Open the Sanctuary repository on GitHub and go to:

`Actions -> Release -> Run workflow`

Use `main` as the branch.

Enter the version without a leading `v`, for example:

```text
0.1.0-alpha.1
```

Leave `prerelease` enabled for alpha, beta, release candidate, or other development releases.

Disable `prerelease` for a stable production release.

A successful release contains:

```text
v0.1.0-alpha.1
  Sanctuary.jar
  Sanctuary.jar.sha256
```

Do not manually replace the JAR attached to an existing release. Create a new version instead.

## Production server setup

The deployment script is:

```text
scripts/deploy-sanctuary.ps1
```

Copy that script to the production server. A suggested location is:

```text
C:\MinecraftServer\deploy\deploy-sanctuary.ps1
```

The default deployment configuration assumes:

```text
Minecraft root: C:\MinecraftServer
Plugins:        C:\MinecraftServer\plugins
Service name:   MinecraftServer
```

The script controls the Minecraft Windows service through `Get-Service`, `Stop-Service`, and `Start-Service`. NSSM-backed services work through the normal Windows service interface, so the script does not require `nssm.exe` to be in `PATH`.

Run the script from PowerShell as Administrator.

The server only needs outbound HTTPS access to `api.github.com` and GitHub release asset downloads.

No GitHub token is required because Sanctuary and its releases are public.

## First deployment

The script recognizes either the standardized production filename:

```text
C:\MinecraftServer\plugins\Sanctuary.jar
```

or one existing versioned Sanctuary JAR such as:

```text
C:\MinecraftServer\plugins\sanctuary-0.1.0-SNAPSHOT.jar
```

If exactly one existing Sanctuary JAR is present, the script backs it up and replaces it with `Sanctuary.jar`.

If multiple Sanctuary JARs are present, deployment stops without changing the server. Remove the duplicates manually before continuing.

The Sanctuary data directory is not modified:

```text
C:\MinecraftServer\plugins\Sanctuary\
```

The production database, configuration, and runtime state therefore remain in place across plugin deployments.

## Deploying a stable release

Run:

```powershell
C:\MinecraftServer\deploy\deploy-sanctuary.ps1
```

The default channel is `Stable`.

The script selects the newest non-draft, non-prerelease GitHub Release.

## Deploying the newest prerelease

Run:

```powershell
C:\MinecraftServer\deploy\deploy-sanctuary.ps1 -Channel Prerelease
```

This selects the newest non-draft prerelease.

This is useful while Sanctuary is still using alpha releases.

## Deploying a specific release

Run:

```powershell
C:\MinecraftServer\deploy\deploy-sanctuary.ps1 -Version 0.1.0-alpha.1
```

The leading `v` is optional:

```powershell
C:\MinecraftServer\deploy\deploy-sanctuary.ps1 -Version v0.1.0-alpha.1
```

When `-Version` is supplied, the channel setting is ignored for release selection.

This also provides a straightforward way to intentionally deploy an older release.

## Deployment sequence

The production script performs these operations:

1. Resolves the selected GitHub Release.
2. Downloads `Sanctuary.jar` and `Sanctuary.jar.sha256`.
3. Verifies the downloaded JAR against the published SHA-256 checksum.
4. Finds the currently installed Sanctuary JAR.
5. Creates a timestamped backup when a previous JAR exists.
6. Stops the `MinecraftServer` service when it is running.
7. Removes the previous Sanctuary JAR.
8. Installs the new file as `C:\MinecraftServer\plugins\Sanctuary.jar`.
9. Starts the `MinecraftServer` service.
10. Waits for Windows to report the service as running.
11. Records the installed release tag.
12. Keeps the newest 10 deployment backups by default.

Downloads are verified before Minecraft is stopped.

## Deployment state

Deployment files are stored under:

```text
C:\MinecraftServer\deploy\
```

The script creates:

```text
C:\MinecraftServer\deploy\sanctuary-version.txt
C:\MinecraftServer\deploy\downloads\
C:\MinecraftServer\deploy\backups\Sanctuary\
```

`sanctuary-version.txt` contains the release tag installed by the deployment script.

For example:

```text
v0.1.0-alpha.1
```

## Backups

Before replacing an existing plugin, the script creates a timestamped backup such as:

```text
C:\MinecraftServer\deploy\backups\Sanctuary\Sanctuary-20260829-154500.jar
```

The default retention is 10 backups.

To change it:

```powershell
C:\MinecraftServer\deploy\deploy-sanctuary.ps1 -BackupCount 20
```

## Failure handling

If installation or service startup fails after the previous plugin has been backed up, the script attempts to:

1. Stop the Minecraft service.
2. Remove the failed new JAR.
3. Restore the previous JAR as `Sanctuary.jar`.
4. Restart Minecraft if it was running before deployment.
5. Return the original deployment error.

The deployment is considered successful only after Windows reports the Minecraft service as running.

This verifies service startup, not full Paper/plugin initialization. Check the Paper server log after production deployment when testing substantial Sanctuary changes.

## Custom server location or service name

The defaults can be overridden without modifying the script.

Example:

```powershell
.\deploy-sanctuary.ps1 `
    -ServerPath "D:\Minecraft" `
    -ServiceName "PaperProduction"
```

## Recommended production process

During active Sanctuary development:

1. Push changes to `main` and verify the normal Build workflow passes.
2. Test the resulting development build on the dev server.
3. When the build is ready for the real server, run the Release workflow as a prerelease.
4. On production, deploy that exact version with `-Version`.
5. Verify the Paper log and gameplay behavior.

For example:

```powershell
C:\MinecraftServer\deploy\deploy-sanctuary.ps1 -Version 0.1.0-alpha.1
```

Using an explicit version is recommended for production because publishing a newer release does not automatically change what version you deploy.

## Security model

The production Minecraft server is not a GitHub Actions runner.

The server does not accept workflow jobs from the public repository.

The production server only downloads public GitHub Release assets over HTTPS and verifies the JAR against the checksum published by the same release workflow.

There are no GitHub deployment credentials stored on the production machine.
