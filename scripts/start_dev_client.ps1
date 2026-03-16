param(
    [switch]$Background,
    [switch]$NoKill
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

function Stop-DevClientProcesses {
    $patterns = 'DropRateDevLauncher|net.runelite.client.RuneLite|gradlew.bat run'
    Get-CimInstance Win32_Process |
        Where-Object { $_.CommandLine -match $patterns } |
        ForEach-Object {
            try {
                Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop
            }
            catch {
                # Ignore races where process exits before it is stopped.
            }
        }
}

if (-not $NoKill) {
    Stop-DevClientProcesses
}

Push-Location $repoRoot
try {
    if ($Background) {
        $process = Start-Process -FilePath '.\\gradlew.bat' -ArgumentList @('run', '--stacktrace') -WorkingDirectory $repoRoot -PassThru
        Write-Host "Started dev client in background (PID: $($process.Id))."
        Write-Host "Use .\\scripts\\stop_dev_client.ps1 to stop it."
    }
    else {
        & '.\\gradlew.bat' run --stacktrace
        $exitCode = $LASTEXITCODE

        if ($exitCode -ne 0) {
            Write-Warning "gradlew run exited with code $exitCode."
            Write-Warning "If RuneLite was manually closed or stopped, this can be expected."
            exit $exitCode
        }
    }
}
finally {
    Pop-Location
}
