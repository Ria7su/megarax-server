[CmdletBinding()]
param(
    [int]$Hours = 24,
    [string]$DbHost = $(if ($env:MEGARAX_DB_HOST) { $env:MEGARAX_DB_HOST } else { '127.0.0.1' }),
    [int]$DbPort = $(if ($env:MEGARAX_DB_PORT) { [int]$env:MEGARAX_DB_PORT } else { 3306 }),
    [string]$Database = $(if ($env:MEGARAX_DB_NAME) { $env:MEGARAX_DB_NAME } else { 'l2jmobiusinterlude' }),
    [string]$DbUser = $(if ($env:MEGARAX_DB_USER) { $env:MEGARAX_DB_USER } else { 'root' }),
    [string]$MySqlBin = $(if ($env:MEGARAX_MYSQL_BIN) { $env:MEGARAX_MYSQL_BIN } else { 'C:\xampp\mysql\bin' })
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$reportDirectory = Join-Path $projectRoot 'reports\diagnostics'
$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$reportPath = Join-Path $reportDirectory "Megarax_Diagnostics_$stamp.txt"
$startTime = (Get-Date).AddHours(-[math]::Abs($Hours))
$mysql = Join-Path $MySqlBin 'mysql.exe'
$lines = [System.Collections.Generic.List[string]]::new()

New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
function Add-Line([object]$Value = '') { $lines.Add([string]$Value) }
function Add-Section([string]$Title) { Add-Line; Add-Line "===== $Title =====" }
function Add-Objects([object[]]$Objects) {
    if (-not $Objects) { Add-Line '(none)'; return }
    Add-Line (($Objects | Format-Table -AutoSize | Out-String -Width 240).TrimEnd())
}

Add-Line 'L2 Megarax read-only diagnostic snapshot'
Add-Line "Generated: $(Get-Date -Format o)"
Add-Line "Window start: $($startTime.ToString('o'))"
Add-Line "Computer: $env:COMPUTERNAME"

Add-Section 'JAVA PROCESSES'
$javaProcesses = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Select-Object ProcessId, CreationDate, ExecutablePath, CommandLine
Add-Objects $javaProcesses

Add-Section 'SERVER PORTS'
$ports = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in 2106, 7777, 9014, 3306 } |
    Select-Object LocalAddress, LocalPort, OwningProcess
Add-Objects $ports

Add-Section 'MEMORY AND JAVA'
$os = Get-CimInstance Win32_OperatingSystem
Add-Line ('RAM total GB={0:N2}; free GB={1:N2}; boot={2}' -f ($os.TotalVisibleMemorySize / 1MB), ($os.FreePhysicalMemory / 1MB), $os.LastBootUpTime)
Add-Line "JAVA_HOME=$env:JAVA_HOME"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $javaVersion = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1
    $ErrorActionPreference = $previousErrorPreference
    foreach ($line in $javaVersion) { Add-Line $line }
}
Add-Line "Game java.cfg=$(Get-Content (Join-Path $projectRoot 'game\java.cfg') -Raw)"
Add-Line "Login java.cfg=$(Get-Content (Join-Path $projectRoot 'login\java.cfg') -Raw)"

Add-Section 'POWER EVENTS'
$powerEvents = Get-WinEvent -FilterHashtable @{ LogName = 'System'; StartTime = $startTime } -ErrorAction SilentlyContinue |
    Where-Object { $_.Id -in 41, 42, 107, 172, 506, 507, 566, 6006, 6008 -or $_.ProviderName -match 'Power-Troubleshooter' } |
    Select-Object TimeCreated, Id, ProviderName, LevelDisplayName, @{ Name = 'Message'; Expression = { ($_.Message -replace '\s+', ' ').Trim() } }
Add-Objects $powerEvents

Add-Section 'JAVA APPLICATION FAILURES'
$appFailures = Get-WinEvent -FilterHashtable @{ LogName = 'Application'; StartTime = $startTime } -ErrorAction SilentlyContinue |
    Where-Object { ($_.Id -in 1000, 1001) -and $_.Message -match 'java|GameServer|LoginServer|l2jmobius' } |
    Select-Object TimeCreated, Id, ProviderName, LevelDisplayName, @{ Name = 'Message'; Expression = { ($_.Message -replace '\s+', ' ').Trim() } }
Add-Objects $appFailures

Add-Section 'LATEST GAME LOG ENDINGS'
$gameLogs = Get-ChildItem (Join-Path $projectRoot 'game\log') -Filter 'java*.log*' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 15 |
    ForEach-Object { [pscustomobject]@{ Name = $_.Name; Modified = $_.LastWriteTime; Bytes = $_.Length; LastLine = (Get-Content $_.FullName -Tail 1 -ErrorAction SilentlyContinue) } }
Add-Objects $gameLogs

Add-Section 'CRITICAL LOG SIGNATURES'
$pattern = 'OutOfMemoryError|StackOverflowError|NullPointerException|SQLException|Communications link failure|Too many connections|ConcurrentModificationException|deadlock|Connection reset'
$matches = Get-ChildItem (Join-Path $projectRoot 'game\log'), (Join-Path $projectRoot 'login\log') -File -ErrorAction SilentlyContinue |
    ForEach-Object { Select-String -Path $_.FullName -Pattern $pattern -ErrorAction SilentlyContinue } |
    Where-Object { $_.Line -notmatch 'DeadlockWatcher: Thread started' } |
    Select-Object Path, LineNumber, Line
Add-Objects ($matches | Select-Object -Last 100)

Add-Section 'DATABASE STATUS'
if (Test-Path -LiteralPath $mysql) {
    if ($env:MEGARAX_DB_PASSWORD) { $env:MYSQL_PWD = $env:MEGARAX_DB_PASSWORD }
    try {
        $query = "SELECT VERSION() engine_version, @@version_comment engine; SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Connections','Aborted_connects','Aborted_clients','Max_used_connections','Slow_queries','Uptime'); SHOW GLOBAL VARIABLES WHERE Variable_name IN ('max_connections','wait_timeout','interactive_timeout','slow_query_log','long_query_time'); SELECT COUNT(*) tables_count, ROUND(SUM(data_length+index_length)/1024/1024,2) size_mb FROM information_schema.tables WHERE table_schema='$Database';"
        $dbResult = & $mysql --protocol=tcp --host=$DbHost --port=$DbPort --user=$DbUser --batch --raw $Database -e $query 2>&1
        foreach ($line in $dbResult) { Add-Line $line }
        if ($LASTEXITCODE -ne 0) { Add-Line "DATABASE QUERY EXIT CODE=$LASTEXITCODE" }
    }
    finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
else { Add-Line "mysql client not found: $mysql" }

Add-Section 'DATABASE SERVICES'
$dbServices = Get-CimInstance Win32_Service -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match 'mysql|maria' -or $_.DisplayName -match 'mysql|maria' } |
    Select-Object Name, State, StartMode, ProcessId, PathName
Add-Objects $dbServices

Add-Section 'GEODATA'
$geoDirectory = Join-Path $projectRoot 'game\data\geodata'
$geoFiles = Get-ChildItem $geoDirectory -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne 'Readme.txt' }
Add-Line "Geo files=$(@($geoFiles).Count)"
Add-Line ((Select-String -Path (Join-Path $projectRoot 'game\config\GeoEngine.ini') -Pattern '^(PathFinding|GeoDataPath|PathnodePath)' | ForEach-Object Line) -join '; ')

Add-Section 'RELEVANT SCHEDULED TASKS'
$tasks = Get-ScheduledTask -ErrorAction SilentlyContinue |
    Where-Object { $_.TaskName -match 'Megarax|L2|Java|GameServer|LoginServer|Restart|Shutdown|MySQL|MariaDB' } |
    Select-Object TaskPath, TaskName, State
Add-Objects $tasks

$lines | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host "Diagnostic report created: $reportPath"
