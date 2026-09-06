[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$HostName = $(if ($env:MEGARAX_DB_HOST) { $env:MEGARAX_DB_HOST } else { '127.0.0.1' }),
    [int]$Port = $(if ($env:MEGARAX_DB_PORT) { [int]$env:MEGARAX_DB_PORT } else { 3306 }),
    [string]$ProductionDatabase = $(if ($env:MEGARAX_DB_NAME) { $env:MEGARAX_DB_NAME } else { 'l2jmobiusinterlude' }),
    [string]$User = $(if ($env:MEGARAX_DB_USER) { $env:MEGARAX_DB_USER } else { 'root' }),
    [string]$MySqlBin = $(if ($env:MEGARAX_MYSQL_BIN) { $env:MEGARAX_MYSQL_BIN } else { 'C:\xampp\mysql\bin' }),
    [switch]$KeepTemporaryDatabase
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$logDirectory = Join-Path $projectRoot 'backups\logs'
$logFile = Join-Path $logDirectory 'megarax-restore-test.log'
$temporaryDatabase = 'megarax_restore_test_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
$mysql = Join-Path $MySqlBin 'mysql.exe'
$tempDirectory = Join-Path ([IO.Path]::GetTempPath()) ('megarax_restore_' + [guid]::NewGuid().ToString('N'))

New-Item -ItemType Directory -Force -Path $logDirectory,$tempDirectory | Out-Null

function Write-RestoreLog([string]$Level, [string]$Message) {
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Add-Content -LiteralPath $logFile -Encoding UTF8 -Value $line
    Write-Host $line
}

function Invoke-Scalar([string]$Database, [string]$Query) {
    $result = & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$User --batch --skip-column-names $Database -e $Query
    if ($LASTEXITCODE -ne 0) { throw "SQL query failed against $Database." }
    return [long]$result
}

if (-not (Test-Path -LiteralPath $mysql)) { throw "mysql client not found: $mysql" }
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
if (-not $resolvedBackup.EndsWith('.sql.zip', [StringComparison]::OrdinalIgnoreCase)) { throw 'Backup must be a .sql.zip file.' }
if ($temporaryDatabase -eq $ProductionDatabase) { throw 'Temporary database name matches production.' }

if ($env:MEGARAX_DB_PASSWORD) { $env:MYSQL_PWD = $env:MEGARAX_DB_PASSWORD }
try {
    $sidecar = "$resolvedBackup.sha256"
    if (Test-Path -LiteralPath $sidecar) {
        $expected = ((Get-Content -LiteralPath $sidecar -Raw).Trim() -split '\s+')[0]
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedBackup).Hash
        if ($actual -ne $expected) { throw 'Backup SHA256 verification failed.' }
    }

    Expand-Archive -LiteralPath $resolvedBackup -DestinationPath $tempDirectory
    $sqlFile = Get-ChildItem -LiteralPath $tempDirectory -Filter '*.sql' -File | Select-Object -First 1
    if (-not $sqlFile -or $sqlFile.Length -lt 1024) { throw 'Extracted SQL is missing or invalid.' }

    & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$User -e "CREATE DATABASE ``$temporaryDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    if ($LASTEXITCODE -ne 0) { throw 'Could not create temporary restore database.' }

    # Rewrite only the dump's database selection so production is never targeted.
    $sqlText = Get-Content -Raw -LiteralPath $sqlFile.FullName
    $sqlText = $sqlText.Replace("CREATE DATABASE /*!32312 IF NOT EXISTS*/ ``$ProductionDatabase``", "CREATE DATABASE IF NOT EXISTS ``$temporaryDatabase``")
    $sqlText = $sqlText.Replace("USE ``$ProductionDatabase``;", "USE ``$temporaryDatabase``;")
    Set-Content -LiteralPath $sqlFile.FullName -Encoding UTF8 -Value $sqlText

    Get-Content -Raw -LiteralPath $sqlFile.FullName | & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$User $temporaryDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Restore import failed.' }

    $checks = @('accounts', 'characters', 'items', 'clan_data', 'character_variables', 'item_variables', 'global_variables', 'clan_war_chest', 'kingdom_treasury')
    foreach ($table in $checks) {
        $exists = Invoke-Scalar $temporaryDatabase "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$temporaryDatabase' AND table_name='$table';"
        if ($exists -ne 1) { throw "Restored database is missing table $table." }
        $productionCount = Invoke-Scalar $ProductionDatabase "SELECT COUNT(*) FROM ``$table``;"
        $restoredCount = Invoke-Scalar $temporaryDatabase "SELECT COUNT(*) FROM ``$table``;"
        if ($productionCount -ne $restoredCount) { throw "Row mismatch for ${table}: production=$productionCount restored=$restoredCount" }
        Write-RestoreLog 'PASS' "$table rows=$restoredCount"
    }
    Write-RestoreLog 'SUCCESS' "Restore verified in isolated database $temporaryDatabase."
}
catch {
    Write-RestoreLog 'ERROR' $_.Exception.Message
    throw
}
finally {
    if (-not $KeepTemporaryDatabase -and $temporaryDatabase -ne $ProductionDatabase) {
        & $mysql --protocol=tcp --host=$HostName --port=$Port --user=$User -e "DROP DATABASE IF EXISTS ``$temporaryDatabase``;" 2>$null
        Write-RestoreLog 'INFO' "Temporary database removed: $temporaryDatabase"
    }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $tempDirectory) { Remove-Item -LiteralPath $tempDirectory -Recurse }
}
