[CmdletBinding()]
param(
    [bool]$AutoRestart = $true,
    [int]$RestartDelaySeconds = 10,
    [int]$MaxRestartsPerHour = 5,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runtimeDirectory = Join-Path $projectRoot 'runtime\supervisor'
$logDirectory = Join-Path $projectRoot 'reports\stability'
$supervisorLog = Join-Path $logDirectory 'megarax-supervisor.log'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { $null }
$accountSyncConfig = Join-Path $projectRoot 'tools\account-sync\account-sync.local.ini'
$accountSyncEnabled = Test-Path -LiteralPath $accountSyncConfig

New-Item -ItemType Directory -Force -Path $runtimeDirectory,$logDirectory | Out-Null
if (-not $java -or -not (Test-Path -LiteralPath $java)) { throw 'JAVA_HOME does not point to a valid Java runtime.' }

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class MegaraxPowerGuard {
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint SetThreadExecutionState(uint flags);
    public const uint ES_CONTINUOUS = 0x80000000;
    public const uint ES_SYSTEM_REQUIRED = 0x00000001;
    public const uint ES_AWAYMODE_REQUIRED = 0x00000040;
}
'@

function Write-SupervisorLog([string]$Level, [string]$Message) {
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Add-Content -LiteralPath $supervisorLog -Encoding UTF8 -Value $line
    Write-Host $line
}

function Read-JavaArguments([string]$ConfigPath) {
    $line = (Get-Content -LiteralPath $ConfigPath -TotalCount 1).Trim()
    if (-not $line) { throw "Empty Java configuration: $ConfigPath" }
    return $line
}

function Start-MegaraxProcess([string]$Name, [string]$WorkingDirectory, [string]$JarName, [string]$JavaConfig) {
    $stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
    $stdout = Join-Path $logDirectory "$Name-$stamp.stdout.log"
    $stderr = Join-Path $logDirectory "$Name-$stamp.stderr.log"
    $arguments = "$(Read-JavaArguments $JavaConfig) -jar `"$JarName`""
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
    Set-Content -LiteralPath (Join-Path $runtimeDirectory "$Name.pid") -Encoding ASCII -Value $process.Id
    Write-SupervisorLog 'START' "$Name PID=$($process.Id) Java=$java stdout=$stdout stderr=$stderr"
    return $process
}

function Start-AccountSyncProcess {
    $buildScript = Join-Path $projectRoot 'tools\account-sync\Build-AccountSync.ps1'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $buildScript
    if ($LASTEXITCODE -ne 0) { throw "Account synchronizer build failed with exit code $LASTEXITCODE." }

    $stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
    $stdout = Join-Path $logDirectory "AccountSync-$stamp.stdout.log"
    $stderr = Join-Path $logDirectory "AccountSync-$stamp.stderr.log"
    $classes = Join-Path $projectRoot 'runtime\account-sync\classes'
    $connector = Join-Path $projectRoot 'libs\mysql-connector-j-9.2.0.jar'
    $classpath = "$classes;$connector"
    $arguments = "-cp `"$classpath`" megarax.accountsync.MegaraxAccountSync `"$projectRoot`" `"$accountSyncConfig`""
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
    Set-Content -LiteralPath (Join-Path $runtimeDirectory 'AccountSync.pid') -Encoding ASCII -Value $process.Id
    Write-SupervisorLog 'START' "AccountSync PID=$($process.Id) stdout=$stdout stderr=$stderr"
    return $process
}

function Test-ServerPortsInUse {
    return [bool](Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 2106,7777,9014 })
}

function Register-Restart([System.Collections.Generic.List[datetime]]$History, [string]$Name) {
    $threshold = (Get-Date).AddHours(-1)
    for ($i = $History.Count - 1; $i -ge 0; $i--) { if ($History[$i] -lt $threshold) { $History.RemoveAt($i) } }
    if ($History.Count -ge $MaxRestartsPerHour) {
        Write-SupervisorLog 'FATAL' "$Name exceeded $MaxRestartsPerHour restarts in one hour. Recovery stopped."
        return $false
    }
    $History.Add((Get-Date))
    return $true
}

if (Test-ServerPortsInUse) { throw 'A LoginServer or GameServer port is already listening. Refusing to start duplicates.' }
foreach ($requiredFile in @('libs\LoginServer.jar', 'libs\GameServer.jar', 'login\java.cfg', 'game\java.cfg')) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $requiredFile))) { throw "Required server file is missing: $requiredFile" }
}
if ($accountSyncEnabled) {
    foreach ($requiredFile in @('tools\account-sync\MegaraxAccountSync.java', 'tools\account-sync\Build-AccountSync.ps1', 'libs\mysql-connector-j-9.2.0.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $requiredFile))) { throw "Required account sync file is missing: $requiredFile" }
    }
}
if ($ValidateOnly) {
    Write-SupervisorLog 'VALIDATION' "Supervisor prerequisites passed. Java=$java; no server ports are occupied; AccountSync=$accountSyncEnabled."
    exit 0
}

$loginHistory = [System.Collections.Generic.List[datetime]]::new()
$gameHistory = [System.Collections.Generic.List[datetime]]::new()
$accountSyncHistory = [System.Collections.Generic.List[datetime]]::new()
$login = $null
$game = $null
$accountSync = $null

try {
    [void][MegaraxPowerGuard]::SetThreadExecutionState([MegaraxPowerGuard]::ES_CONTINUOUS -bor [MegaraxPowerGuard]::ES_SYSTEM_REQUIRED -bor [MegaraxPowerGuard]::ES_AWAYMODE_REQUIRED)
    Write-SupervisorLog 'INFO' "Supervisor started. AutoRestart=$AutoRestart; sleep prevention active only for this supervisor session."
    $login = Start-MegaraxProcess 'LoginServer' (Join-Path $projectRoot 'login') '..\libs\LoginServer.jar' (Join-Path $projectRoot 'login\java.cfg')
    Start-Sleep -Seconds 3
    if ($login.HasExited) { throw "LoginServer exited during startup with code $($login.ExitCode)." }
    $game = Start-MegaraxProcess 'GameServer' (Join-Path $projectRoot 'game') '..\libs\GameServer.jar' (Join-Path $projectRoot 'game\java.cfg')
    if ($accountSyncEnabled) {
        $accountSync = Start-AccountSyncProcess
    }
    else {
        Write-SupervisorLog 'INFO' 'AccountSync disabled because account-sync.local.ini is absent.'
    }

    while ($true) {
        Start-Sleep -Seconds 2
        if ($login.HasExited) {
            $code = $login.ExitCode
            Write-SupervisorLog $(if ($code -eq 0) {'STOP'} else {'ERROR'}) "LoginServer exited with code $code."
            Remove-Item -LiteralPath (Join-Path $runtimeDirectory 'LoginServer.pid') -ErrorAction SilentlyContinue
            if (-not $AutoRestart -or $code -eq 0 -or -not (Register-Restart $loginHistory 'LoginServer')) { $login = $null }
            else { Start-Sleep -Seconds $RestartDelaySeconds; $login = Start-MegaraxProcess 'LoginServer' (Join-Path $projectRoot 'login') '..\libs\LoginServer.jar' (Join-Path $projectRoot 'login\java.cfg') }
        }
        if ($game.HasExited) {
            $code = $game.ExitCode
            Write-SupervisorLog $(if ($code -eq 0) {'STOP'} else {'ERROR'}) "GameServer exited with code $code."
            Remove-Item -LiteralPath (Join-Path $runtimeDirectory 'GameServer.pid') -ErrorAction SilentlyContinue
            if (-not $AutoRestart -or $code -eq 0 -or -not (Register-Restart $gameHistory 'GameServer')) { $game = $null }
            else { Start-Sleep -Seconds $RestartDelaySeconds; $game = Start-MegaraxProcess 'GameServer' (Join-Path $projectRoot 'game') '..\libs\GameServer.jar' (Join-Path $projectRoot 'game\java.cfg') }
        }
        if (($null -ne $accountSync) -and $accountSync.HasExited) {
            $code = $accountSync.ExitCode
            Write-SupervisorLog $(if ($code -eq 0) {'STOP'} else {'ERROR'}) "AccountSync exited with code $code."
            Remove-Item -LiteralPath (Join-Path $runtimeDirectory 'AccountSync.pid') -ErrorAction SilentlyContinue
            if (-not $AutoRestart -or $code -eq 0 -or -not (Register-Restart $accountSyncHistory 'AccountSync')) { $accountSync = $null }
            else { Start-Sleep -Seconds $RestartDelaySeconds; $accountSync = Start-AccountSyncProcess }
        }
        if ($null -eq $login -and $null -eq $game) { break }
    }
}
catch {
    Write-SupervisorLog 'FATAL' $_.Exception.Message
    throw
}
finally {
    [void][MegaraxPowerGuard]::SetThreadExecutionState([MegaraxPowerGuard]::ES_CONTINUOUS)
    Write-SupervisorLog 'INFO' 'Supervisor ended; sleep prevention released. Child processes are not force-killed by the supervisor.'
}
