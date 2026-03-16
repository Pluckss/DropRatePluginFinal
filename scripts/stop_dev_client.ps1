$ErrorActionPreference = 'Stop'

$patterns = 'DropRateDevLauncher|net.runelite.client.RuneLite|gradlew.bat run'
$stopped = 0

Get-CimInstance Win32_Process |
    Where-Object { $_.CommandLine -match $patterns } |
    ForEach-Object {
        try {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop
            $stopped++
        }
        catch {
            # Ignore races where process exits before it is stopped.
        }
    }

Write-Host "Stopped processes: $stopped"
