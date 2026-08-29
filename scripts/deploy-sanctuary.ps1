[CmdletBinding()]
param(
    [ValidateSet("Stable", "Prerelease")]
    [string]$Channel = "Stable",

    [string]$Version,

    [string]$ServerPath = "C:\MinecraftServer",

    [string]$ServiceName = "MinecraftServer",

    [ValidateRange(1, 100)]
    [int]$BackupCount = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Repository = "liamtolkki/Sanctuary"
$PluginFileName = "Sanctuary.jar"
$ChecksumFileName = "Sanctuary.jar.sha256"

$PluginsPath = Join-Path $ServerPath "plugins"
$PluginPath = Join-Path $PluginsPath $PluginFileName
$DeployPath = Join-Path $ServerPath "deploy"
$BackupPath = Join-Path $DeployPath "backups\Sanctuary"
$VersionFile = Join-Path $DeployPath "sanctuary-version.txt"
$DownloadPath = Join-Path $DeployPath "downloads"
$TemporaryJar = Join-Path $DownloadPath $PluginFileName
$TemporaryChecksum = Join-Path $DownloadPath $ChecksumFileName

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Get-Release {
    param(
        [string]$RequestedVersion,
        [string]$RequestedChannel
    )

    $Headers = @{
        Accept = "application/vnd.github+json"
        "User-Agent" = "Sanctuary-Production-Deploy"
        "X-GitHub-Api-Version" = "2022-11-28"
    }

    if ($RequestedVersion) {
        $Tag = if ($RequestedVersion.StartsWith("v")) {
            $RequestedVersion
        }
        else {
            "v$RequestedVersion"
        }

        $EncodedTag = [System.Uri]::EscapeDataString($Tag)
        $Url = "https://api.github.com/repos/$Repository/releases/tags/$EncodedTag"
        return Invoke-RestMethod -Uri $Url -Headers $Headers -Method Get
    }

    $Url = "https://api.github.com/repos/$Repository/releases?per_page=100"
    $Releases = @(Invoke-RestMethod -Uri $Url -Headers $Headers -Method Get)

    if ($RequestedChannel -eq "Prerelease") {
        $Release = $Releases |
            Where-Object { -not $_.draft -and $_.prerelease } |
            Select-Object -First 1
    }
    else {
        $Release = $Releases |
            Where-Object { -not $_.draft -and -not $_.prerelease } |
            Select-Object -First 1
    }

    if (-not $Release) {
        throw "No $RequestedChannel Sanctuary release is available."
    }

    return $Release
}

function Get-ReleaseAsset {
    param(
        [object]$Release,
        [string]$Name
    )

    $Asset = @($Release.assets) |
        Where-Object { $_.name -eq $Name } |
        Select-Object -First 1

    if (-not $Asset) {
        throw "Release $($Release.tag_name) does not contain $Name."
    }

    return $Asset
}

function Wait-ServiceState {
    param(
        [System.ServiceProcess.ServiceController]$Service,
        [System.ServiceProcess.ServiceControllerStatus]$Status,
        [int]$TimeoutSeconds = 60
    )

    $Service.WaitForStatus(
        $Status,
        [TimeSpan]::FromSeconds($TimeoutSeconds)
    )
    $Service.Refresh()

    if ($Service.Status -ne $Status) {
        throw "Service $($Service.ServiceName) did not reach state $Status."
    }
}

function Get-ExistingSanctuaryJars {
    if (-not (Test-Path $PluginsPath)) {
        return
    }

    Get-ChildItem -Path $PluginsPath -File -Filter "*.jar" |
        Where-Object {
            $_.Name -ieq $PluginFileName -or
            $_.Name -match "^sanctuary[-.].*\.jar$"
        }
}

function Restore-Backup {
    param(
        [string]$BackupFile,
        [string[]]$PathsToRemove
    )

    Write-Warning "Deployment failed. Restoring previous Sanctuary plugin."

    foreach ($Path in $PathsToRemove) {
        if (Test-Path $Path) {
            Remove-Item -Path $Path -Force
        }
    }

    if ($BackupFile -and (Test-Path $BackupFile)) {
        Copy-Item -Path $BackupFile -Destination $PluginPath -Force
    }
}

$IsAdministrator = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)
if (-not $IsAdministrator) {
    throw "Run this script from PowerShell as Administrator."
}

if (-not (Test-Path $PluginsPath)) {
    throw "Minecraft plugins directory does not exist: $PluginsPath"
}

New-Item -ItemType Directory -Path $DeployPath -Force | Out-Null
New-Item -ItemType Directory -Path $BackupPath -Force | Out-Null
New-Item -ItemType Directory -Path $DownloadPath -Force | Out-Null

Write-Step "Resolving Sanctuary release"
$Release = Get-Release -RequestedVersion $Version -RequestedChannel $Channel
$ReleaseTag = [string]$Release.tag_name

$InstalledVersion = if (Test-Path $VersionFile) {
    (Get-Content -Path $VersionFile -Raw).Trim()
}
else {
    "unknown"
}

Write-Host "Installed: $InstalledVersion"
Write-Host "Selected:  $ReleaseTag"

if ($InstalledVersion -eq $ReleaseTag) {
    Write-Host "Sanctuary $ReleaseTag is already installed."
    exit 0
}

$JarAsset = Get-ReleaseAsset -Release $Release -Name $PluginFileName
$ChecksumAsset = Get-ReleaseAsset -Release $Release -Name $ChecksumFileName

Write-Step "Downloading $PluginFileName"
Remove-Item -Path $TemporaryJar -Force -ErrorAction SilentlyContinue
Remove-Item -Path $TemporaryChecksum -Force -ErrorAction SilentlyContinue
Invoke-WebRequest -Uri $JarAsset.browser_download_url -OutFile $TemporaryJar -UseBasicParsing
Invoke-WebRequest -Uri $ChecksumAsset.browser_download_url -OutFile $TemporaryChecksum -UseBasicParsing

Write-Step "Verifying SHA-256 checksum"
$ExpectedHashLine = (Get-Content -Path $TemporaryChecksum -Raw).Trim()
$ExpectedHash = ($ExpectedHashLine -split "\s+")[0].ToLowerInvariant()
$ActualHash = (Get-FileHash -Path $TemporaryJar -Algorithm SHA256).Hash.ToLowerInvariant()

if ($ExpectedHash -notmatch "^[0-9a-f]{64}$") {
    throw "Release checksum file contains an invalid SHA-256 hash."
}

if ($ActualHash -ne $ExpectedHash) {
    throw "Sanctuary.jar checksum verification failed. Expected $ExpectedHash, got $ActualHash."
}

$ExistingJars = @(Get-ExistingSanctuaryJars)
if ($ExistingJars.Count -gt 1) {
    $Names = ($ExistingJars | ForEach-Object { $_.FullName }) -join [Environment]::NewLine
    throw "Multiple Sanctuary plugin JARs were found. Remove the duplicates before deploying:`n$Names"
}

$BackupFile = $null
if ($ExistingJars.Count -eq 1) {
    $Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $BackupFile = Join-Path $BackupPath "Sanctuary-$Timestamp.jar"
    Write-Step "Backing up current Sanctuary plugin"
    Copy-Item -Path $ExistingJars[0].FullName -Destination $BackupFile -Force
}

$Service = Get-Service -Name $ServiceName -ErrorAction Stop
$ServiceWasRunning = $Service.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Stopped

try {
    if ($ServiceWasRunning) {
        Write-Step "Stopping $ServiceName"
        Stop-Service -Name $ServiceName -Force
        $Service.Refresh()
        Wait-ServiceState -Service $Service -Status ([System.ServiceProcess.ServiceControllerStatus]::Stopped)
    }

    Write-Step "Installing $ReleaseTag"
    foreach ($ExistingJar in $ExistingJars) {
        if (Test-Path $ExistingJar.FullName) {
            Remove-Item -Path $ExistingJar.FullName -Force
        }
    }
    Copy-Item -Path $TemporaryJar -Destination $PluginPath -Force

    Write-Step "Starting $ServiceName"
    Start-Service -Name $ServiceName
    $Service.Refresh()
    Wait-ServiceState -Service $Service -Status ([System.ServiceProcess.ServiceControllerStatus]::Running)

    Set-Content -Path $VersionFile -Value $ReleaseTag -Encoding ascii

    Write-Step "Removing old deployment backups"
    Get-ChildItem -Path $BackupPath -File -Filter "Sanctuary-*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $BackupCount |
        Remove-Item -Force

    Write-Host ""
    Write-Host "Sanctuary $ReleaseTag deployed successfully."
}
catch {
    $DeploymentError = $_

    try {
        $Service.Refresh()
        if ($Service.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Stopped) {
            Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
            $Service.Refresh()
            Wait-ServiceState -Service $Service -Status ([System.ServiceProcess.ServiceControllerStatus]::Stopped)
        }

        Restore-Backup -BackupFile $BackupFile -PathsToRemove @($PluginPath)

        if ($ServiceWasRunning) {
            Start-Service -Name $ServiceName
            $Service.Refresh()
            Wait-ServiceState -Service $Service -Status ([System.ServiceProcess.ServiceControllerStatus]::Running)
        }
    }
    catch {
        Write-Error "Rollback also failed: $($_.Exception.Message)"
    }

    throw $DeploymentError
}
finally {
    Remove-Item -Path $TemporaryJar -Force -ErrorAction SilentlyContinue
    Remove-Item -Path $TemporaryChecksum -Force -ErrorAction SilentlyContinue
}
