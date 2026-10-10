[CmdletBinding()]
param(
    [ValidateSet('Start', 'Run', 'Stop', 'Restart', 'Status', 'Build', 'Install', 'Uninstall')]
    [string]$Action = 'Start',
    [ValidateRange(1024, 65535)][int]$Port = 8080,
    [ValidateSet('dev', 'prod')][string]$Profile = 'dev',
    [switch]$CurrentEnvironment,
    [ValidateRange(30, 600)][int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Set-Location -LiteralPath $PSScriptRoot
$runtimeDir = Join-Path $PSScriptRoot 'runtime'
$null = New-Item -ItemType Directory -Path $runtimeDir -Force
$jarPath = Join-Path $runtimeDir 'rapidmart.jar'
$lockPath = Join-Path $runtimeDir 'supervisor.lock'
$stopPath = Join-Path $runtimeDir 'stop.request'
$statePath = Join-Path $runtimeDir 'child.json'
$configPath = Join-Path $runtimeDir 'settings.json'
$supervisorLog = Join-Path $runtimeDir 'supervisor.log'
$sha = [Security.Cryptography.SHA256]::Create()
try { $instanceId = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($PSScriptRoot.ToLowerInvariant())))).Replace('-', '').Substring(0, 12) }
finally { $sha.Dispose() }
$taskName = "RapidMart-$instanceId"
$powershellPath = Join-Path $PSHOME 'powershell.exe'

if (Test-Path -LiteralPath $configPath) {
    $saved = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    if (!$PSBoundParameters.ContainsKey('Port')) { $Port = [int]$saved.Port }
    if (!$PSBoundParameters.ContainsKey('Profile')) { $Profile = [string]$saved.Profile }
}
if ($Port -lt 1024 -or $Port -gt 65535 -or $Profile -notin @('dev', 'prod')) {
    throw 'Invalid runtime/settings.json. Use a port from 1024 to 65535 and profile dev or prod.'
}

function Rotate-Log([string]$Path, [long]$MaxBytes = 1048576) {
    if ((Test-Path -LiteralPath $Path) -and (Get-Item -LiteralPath $Path).Length -ge $MaxBytes) {
        # Only this launcher's own log and its one previous copy are rotated.
        Move-Item -LiteralPath $Path -Destination "$Path.previous" -Force
    }
}

function Write-SupervisorLog([string]$Message) {
    Rotate-Log $supervisorLog
    Add-Content -LiteralPath $supervisorLog -Value "$(Get-Date -Format o) $Message"
}

function Open-SupervisorLock {
    try { return [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
    catch [IO.IOException] { return $null }
}

function Test-SupervisorRunning {
    $probe = Open-SupervisorLock
    if ($null -eq $probe) { return $true }
    $probe.Dispose()
    return $false
}

function Get-ManagedChild {
    if (!(Test-Path -LiteralPath $statePath)) { return $null }
    try {
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $process = Get-Process -Id ([int]$state.Pid) -ErrorAction Stop
        # PID alone is unsafe: Windows can reuse it. Verify launch time and command line.
        if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne [string]$state.StartTicks) { return $null }
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
        if ($details.CommandLine -notlike "*-Drapidmart.instance=$instanceId*" -or
            !$details.CommandLine.Contains($jarPath)) { return $null }
        return $process
    } catch { return $null }
}

function Test-PortBusy {
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    return @($listeners | Where-Object { $_.Port -eq $Port }).Count -gt 0
}

function Get-Health([string]$Group) {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health/$Group" -TimeoutSec 5
        return $response.status -eq 'UP'
    } catch { return $false }
}

function Save-Settings {
    @{ Port = $Port; Profile = $Profile } | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding UTF8
}

function Resolve-JavaExecutable {
    # Oracle's PATH entry can be a shim that spawns another JVM. Supervise java.home/bin/java.exe
    # directly, otherwise stopping the shim leaves an orphan listening on the application port.
    $probeInfo = New-Object Diagnostics.ProcessStartInfo
    $probeInfo.FileName = (Get-Command java.exe -ErrorAction Stop).Source
    $probeInfo.Arguments = '-XshowSettings:properties -version'
    $probeInfo.UseShellExecute = $false
    $probeInfo.CreateNoWindow = $true
    $probeInfo.RedirectStandardError = $true
    $probeInfo.RedirectStandardOutput = $true
    $probe = [Diagnostics.Process]::Start($probeInfo)
    try {
        $stderr = $probe.StandardError.ReadToEndAsync()
        $stdout = $probe.StandardOutput.ReadToEndAsync()
        if (!$probe.WaitForExit(15000)) { $probe.Kill(); throw 'Java version check timed out.' }
        $properties = $stderr.Result + $stdout.Result
        $homeMatch = [regex]::Match($properties, '(?m)^\s*java\.home\s*=\s*(.+)$')
        $versionMatch = [regex]::Match($properties, '(?m)^\s*java\.specification\.version\s*=\s*(\d+)')
        if ($probe.ExitCode -ne 0 -or !$homeMatch.Success -or !$versionMatch.Success -or [int]$versionMatch.Groups[1].Value -lt 21) {
            throw 'RapidMart needs JDK 21 or newer available as java.exe.'
        }
        $actualJava = Join-Path $homeMatch.Groups[1].Value.Trim() 'bin/java.exe'
        if (!(Test-Path -LiteralPath $actualJava)) { throw "Java executable does not exist: $actualJava" }
        return $actualJava
    } finally { $probe.Dispose() }
}

function Build-Application {
    $buildLock = Open-SupervisorLock
    if ($null -eq $buildLock) { throw 'Stop RapidMart before building: .\rapidmart.ps1 -Action Stop' }
    try {
        if ($null -ne (Get-ManagedChild)) { throw 'The managed Java process is still running. Stop RapidMart before building.' }
        & (Join-Path $PSScriptRoot 'mvnw.cmd') -B package
        if ($LASTEXITCODE -ne 0) { throw 'Build/tests failed. The previously deployed application has been preserved.' }
        [xml]$pom = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pom.xml')
        $builtJar = Join-Path $PSScriptRoot "target/$($pom.project.artifactId)-$($pom.project.version).jar"
        if (!(Test-Path -LiteralPath $builtJar)) { throw "Packaged application not found: $builtJar" }
        if (Test-Path -LiteralPath $jarPath) {
            Copy-Item -LiteralPath $jarPath -Destination "$jarPath.previous" -Force
        }
        Copy-Item -LiteralPath $builtJar -Destination $jarPath -Force
        $null = New-Item -ItemType Directory -Path (Join-Path $runtimeDir 'native') -Force
        Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'target/native') -Filter '*.dll' |
            Copy-Item -Destination (Join-Path $runtimeDir 'native') -Force
        Write-Host 'Verified build deployed to runtime/rapidmart.jar.'
    } finally { $buildLock.Dispose() }
}

function Stop-Application {
    if (Test-SupervisorRunning) {
        Set-Content -LiteralPath $stopPath -Value 'stop' -Encoding ASCII
        $deadline = (Get-Date).AddSeconds(45)
        while ((Test-SupervisorRunning) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 1 }
        if (Test-SupervisorRunning) { throw 'Stop requested; supervisor is still exiting. Inspect runtime/supervisor.log.' }
    }
    $orphan = Get-ManagedChild
    if ($null -ne $orphan) {
        Stop-Process -Id $orphan.Id
        $null = $orphan.WaitForExit(10000)
    }
    Write-Host 'RapidMart stopped. Use Action Uninstall to also remove automatic startup.'
}

function Start-Application {
    if (!(Test-Path -LiteralPath $jarPath)) { Build-Application }
    if (!(Test-SupervisorRunning)) {
        if ((Test-PortBusy) -and $null -eq (Get-ManagedChild)) {
            throw "Port $Port belongs to another process. Stop that instance or choose -Port 8081. No process was terminated."
        }
        Save-Settings
        if (Test-Path -LiteralPath $stopPath) { Remove-Item -LiteralPath $stopPath }
        $installedTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($null -ne $installedTask -and !$CurrentEnvironment) {
            Start-ScheduledTask -TaskName $taskName
        } else {
            $launchArgs = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Action Run -StartupTimeoutSeconds $StartupTimeoutSeconds"
            $null = Start-Process -FilePath $powershellPath -ArgumentList $launchArgs -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru
        }
    } else {
        # Report the running instance's actual port even if Start received other settings.
        if (Test-Path -LiteralPath $configPath) {
            $script:Port = [int](Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json).Port
        }
        Write-Host 'The supervisor is already running; reusing the existing instance.'
    }
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        if ($null -ne (Get-ManagedChild) -and (Get-Health 'readiness')) {
            Write-Host "RapidMart is ready at http://localhost:$Port (runs in the background)."
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Readiness timed out. The supervisor will retry; inspect logs/rapidmart.log and runtime/supervisor.log.'
}

function Run-Supervisor {
    $supervisorLock = Open-SupervisorLock
    if ($null -eq $supervisorLock) { Write-Host 'RapidMart supervisor is already running.'; return }
    $child = $null
    try {
        if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build RapidMart before running the supervisor.' }
        $javaPath = Resolve-JavaExecutable
        $maxHeap = if ($env:RAPIDMART_JAVA_MAX_HEAP) { $env:RAPIDMART_JAVA_MAX_HEAP } else { '512m' }
        if ($maxHeap -notmatch '^\d+[mMgG]$') { throw 'RAPIDMART_JAVA_MAX_HEAP must look like 512m or 1g.' }
        Save-Settings
        $child = Get-ManagedChild
        $retryDelay = 5
        Write-SupervisorLog "Supervisor started. Port=$Port Profile=$Profile"
        while (!(Test-Path -LiteralPath $stopPath)) {
            if ($null -eq $child -or $child.HasExited) {
                $child = $null
                if (Test-PortBusy) {
                    Write-SupervisorLog "Port $Port is occupied. Waiting 30 seconds without stopping the other process."
                    for ($i = 0; $i -lt 30 -and !(Test-Path -LiteralPath $stopPath); $i++) { Start-Sleep -Seconds 1 }
                    continue
                }
                $stdout = Join-Path $runtimeDir 'java-console.log'
                $stderr = Join-Path $runtimeDir 'java-error.log'
                Rotate-Log $stdout 1
                Rotate-Log $stderr 1
                $nativePath = Join-Path $runtimeDir 'native'
                $javaArgs = "-Xms128m -Xmx$maxHeap -XX:+ExitOnOutOfMemoryError -Drapidmart.instance=$instanceId -Djava.library.path=`"$nativePath`" -jar `"$jarPath`" --spring.profiles.active=$Profile --server.port=$Port --logging.config=classpath:logback-supervised.xml"
                $child = Start-Process -FilePath $javaPath -ArgumentList $javaArgs -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
                @{ Pid = $child.Id; StartTicks = $child.StartTime.ToUniversalTime().Ticks.ToString() } |
                    ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
                Write-SupervisorLog "Java started. PID=$($child.Id)"
            } else {
                Write-SupervisorLog "Resumed monitoring the existing managed Java process. PID=$($child.Id)"
            }
            $startedAt = Get-Date
            $failures = 0
            $wasReady = $false
            while (!$child.HasExited -and !(Test-Path -LiteralPath $stopPath)) {
                if (Get-Health 'liveness') {
                    $failures = 0
                    $retryDelay = 5
                    $ready = Get-Health 'readiness'
                    if ($ready -ne $wasReady) {
                        Write-SupervisorLog "Readiness=$ready. PID=$($child.Id)"
                        $wasReady = $ready
                    }
                } elseif (((Get-Date) - $startedAt).TotalSeconds -ge $StartupTimeoutSeconds) {
                    $failures++
                    Write-SupervisorLog "Liveness check failed ($failures/3). PID=$($child.Id)"
                    if ($failures -ge 3) {
                        Write-SupervisorLog 'Restarting unresponsive managed Java process.'
                        Stop-Process -Id $child.Id -ErrorAction SilentlyContinue
                        $null = $child.WaitForExit(10000)
                        break
                    }
                }
                for ($i = 0; $i -lt 10 -and !(Test-Path -LiteralPath $stopPath) -and !$child.HasExited; $i++) {
                    Start-Sleep -Seconds 1
                }
                $child.Refresh()
            }
            if (!(Test-Path -LiteralPath $stopPath)) {
                Write-SupervisorLog "Java exited. Retrying in $retryDelay seconds. See logs/rapidmart.log and runtime/java-error.log."
                for ($i = 0; $i -lt $retryDelay -and !(Test-Path -LiteralPath $stopPath); $i++) { Start-Sleep -Seconds 1 }
                $retryDelay = [Math]::Min(60, $retryDelay * 2)
            }
        }
    } finally {
        if ($null -ne $child -and !$child.HasExited) {
            Stop-Process -Id $child.Id -ErrorAction SilentlyContinue
            $null = $child.WaitForExit(10000)
        }
        if (Test-Path -LiteralPath $stopPath) { Remove-Item -LiteralPath $stopPath }
        Write-SupervisorLog 'Supervisor stopped.'
        $supervisorLock.Dispose()
    }
}

switch ($Action) {
    'Build' { Build-Application }
    'Start' { Start-Application }
    'Run' { Run-Supervisor }
    'Stop' { Stop-Application }
    'Restart' { Stop-Application; Start-Application }
    'Status' {
        $managed = Get-ManagedChild
        [pscustomobject]@{
            SupervisorRunning = Test-SupervisorRunning
            JavaPid = if ($null -ne $managed) { $managed.Id } else { $null }
            Port = $Port
            Live = Get-Health 'liveness'
            Ready = Get-Health 'readiness'
            AutoStartInstalled = $null -ne (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)
            Log = Join-Path $PSScriptRoot 'logs/rapidmart.log'
        } | Format-List
    }
    'Install' {
        if (!(Test-Path -LiteralPath $jarPath)) { Build-Application }
        if (Test-SupervisorRunning) { throw 'Stop RapidMart before installing or changing automatic startup.' }
        Save-Settings
        $userId = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        $taskAction = New-ScheduledTaskAction -Execute $powershellPath -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Action Run" -WorkingDirectory $PSScriptRoot
        $trigger = New-ScheduledTaskTrigger -AtLogOn -User $userId
        $principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
        $settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
        $null = Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $trigger -Principal $principal -Settings $settings -Description "RapidMart supervisor for $PSScriptRoot. Starts at sign-in and restarts after crashes." -Force
        Start-Application
        Write-Host "Automatic startup installed as $taskName for $userId."
    }
    'Uninstall' {
        if ($null -ne (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
            Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
        }
        Stop-Application
        Write-Host 'Automatic startup removed. Application files and database retained.'
    }
}
