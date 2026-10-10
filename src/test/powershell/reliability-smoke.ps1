[CmdletBinding()]
param([switch]$TestCrashRecovery, [switch]$TestPortConflict)

$ErrorActionPreference = 'Stop'
$projectDir = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$launcher = Join-Path $projectDir 'rapidmart.ps1'
$stateFile = Join-Path $projectDir 'runtime/child.json'
$settingsFile = Join-Path $projectDir 'runtime/settings.json'

function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}

if ($TestPortConflict) {
    & $launcher -Action Stop
    $occupiedPort = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $occupiedPort.Start()
    try {
        $testPort = $occupiedPort.LocalEndpoint.Port
        $blockedSafely = $false
        try { & $launcher -Action Start -Port $testPort }
        catch {
            if ($_.Exception.Message -like "Port $testPort belongs to another process*") { $blockedSafely = $true }
            else { throw }
        }
        Assert-True ($blockedSafely -and $occupiedPort.Server.IsBound) 'Occupied-port safety check failed.'
        Write-Host 'PASS: occupied port refused and its listener retained.'
    } finally { $occupiedPort.Stop() }
}

& $launcher -Action Start
$before = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
$port = (Get-Content -LiteralPath $settingsFile -Raw | ConvertFrom-Json).Port
$base = "http://127.0.0.1:$port"

& $launcher -Action Start
$after = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
Assert-True ($before.Pid -eq $after.Pid -and $before.StartTicks -eq $after.StartTicks) 'Duplicate Start replaced Java.'
Write-Host 'PASS: repeated Start reuses the same Java process.'

foreach ($path in @('/', '/staff-login.html', '/api/v1/products/public', '/api/v1/categories/public')) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$base$path" -TimeoutSec 15
    Assert-True ($response.StatusCode -eq 200) "GET $path failed."
}
foreach ($group in @('liveness', 'readiness')) {
    $health = Invoke-RestMethod -Uri "$base/actuator/health/$group" -TimeoutSec 10
    Assert-True ($health.status -eq 'UP') "$group is not UP."
    Assert-True ($null -eq $health.PSObject.Properties['components']) 'Health leaks component details.'
}
Write-Host 'PASS: pages, database-backed catalogue and health probes respond.'

if ($TestCrashRecovery) {
    $child = Get-Process -Id ([int]$after.Pid)
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($child.Id)"
    Assert-True ($child.StartTime.ToUniversalTime().Ticks.ToString() -eq [string]$after.StartTicks) 'Child identity changed.'
    Assert-True ($details.CommandLine.Contains((Join-Path $projectDir 'runtime/rapidmart.jar')) -and
        $details.CommandLine.Contains('-Drapidmart.instance=')) 'Refusing to stop a process not owned by this launcher.'
    # Explicit opt-in: simulate a crash of only the verified managed test instance.
    Stop-Process -Id $child.Id
    $deadline = (Get-Date).AddSeconds(180)
    $recovered = $false
    do {
        Start-Sleep -Seconds 2
        try {
            $replacement = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
            if ($replacement.StartTicks -ne $after.StartTicks) {
                $health = Invoke-RestMethod -Uri "$base/actuator/health/readiness" -TimeoutSec 5
                if ($health.status -eq 'UP') { $recovered = $true; break }
            }
        } catch { }
    } while ((Get-Date) -lt $deadline)
    Assert-True $recovered 'The application did not recover from the simulated Java crash.'
    Write-Host "PASS: Java recovered automatically (old PID $($after.Pid), new PID $($replacement.Pid))."
}

Write-Host 'Smoke checks passed. RapidMart has been left running.'
