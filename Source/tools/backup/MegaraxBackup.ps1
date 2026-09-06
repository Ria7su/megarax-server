[CmdletBinding()]
param(
    [ValidateSet('full', 'database', 'files')]
    [string]$Mode = 'full',
    [string]$HostName = $(if ($env:MEGARAX_DB_HOST) { $env:MEGARAX_DB_HOST } else { '127.0.0.1' }),
    [int]$Port = $(if ($env:MEGARAX_DB_PORT) { [int]$env:MEGARAX_DB_PORT } else { 3306 }),
    [string]$Database = $(if ($env:MEGARAX_DB_NAME) { $env:MEGARAX_DB_NAME } else { 'l2jmobiusinterlude' }),
    [string]$User = $(if ($env:MEGARAX_DB_USER) { $env:MEGARAX_DB_USER } else { 'root' }),
    [int]$RetentionCount = $(if ($env:MEGARAX_BACKUP_RETENTION) { [int]$env:MEGARAX_BACKUP_RETENTION } else { 14 }),
    [string]$MySqlBin = $(if ($env:MEGARAX_MYSQL_BIN) { $env:MEGARAX_MYSQL_BIN } else { 'C:\xampp\mysql\bin' })
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backupRoot = Join-Path $projectRoot 'backups'
$logDirectory = Join-Path $backupRoot 'logs'
$logFile = Join-Path $logDirectory 'megarax-backup.log'
$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'

New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

function Write-BackupLog([string]$Level, [string]$Message) {
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Add-Content -LiteralPath $logFile -Encoding UTF8 -Value $line
    Write-Host $line
}

function Remove-ExpiredBackups([string]$Directory, [string]$Pattern) {
    if ($RetentionCount -le 0) { return }
    $expired = Get-ChildItem -LiteralPath $Directory -Filter $Pattern -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $RetentionCount
    foreach ($file in $expired) {
        $resolvedDirectory = (Resolve-Path $Directory).Path
        if (-not $file.FullName.StartsWith($resolvedDirectory, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Retention target escaped backup directory: $($file.FullName)"
        }
        Remove-Item -LiteralPath $file.FullName
        $sidecar = "$($file.FullName).sha256"
        if (Test-Path -LiteralPath $sidecar) { Remove-Item -LiteralPath $sidecar }
        Write-BackupLog 'INFO' "Retention removed $($file.Name)."
    }
}

function Write-HashSidecar([string]$FilePath) {
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $FilePath).Hash
    $sidecar = "$FilePath.sha256"
    Set-Content -LiteralPath $sidecar -Encoding ASCII -Value "$hash  $([IO.Path]::GetFileName($FilePath))"
    return $hash
}

function Backup-Database {
    $directory = Join-Path $backupRoot 'database'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $dumpTool = Join-Path $MySqlBin 'mysqldump.exe'
    if (-not (Test-Path -LiteralPath $dumpTool)) { throw "mysqldump not found: $dumpTool" }

    $sqlFile = Join-Path $directory "Megarax_FullBackup_$stamp.sql"
    $zipFile = "$sqlFile.zip"
    if ($env:MEGARAX_DB_PASSWORD) { $env:MYSQL_PWD = $env:MEGARAX_DB_PASSWORD }
    try {
        & $dumpTool --protocol=tcp --host=$HostName --port=$Port --user=$User --default-character-set=utf8mb4 `
            --single-transaction --quick --routines --triggers --events --hex-blob --databases $Database `
            --result-file=$sqlFile
        if ($LASTEXITCODE -ne 0) { throw "mysqldump exited with code $LASTEXITCODE" }
    }
    finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }

    if (-not (Test-Path -LiteralPath $sqlFile) -or (Get-Item $sqlFile).Length -lt 1024) {
        throw 'Database dump is missing or too small.'
    }
    $content = Get-Content -Raw -LiteralPath $sqlFile
    foreach ($table in @('accounts', 'characters', 'items', 'clan_data', 'clan_war_chest', 'kingdom_treasury')) {
        if ($content -notmatch [regex]::Escape("CREATE TABLE ``$table``")) {
            throw "Database dump does not contain critical table $table."
        }
    }

    Compress-Archive -LiteralPath $sqlFile -DestinationPath $zipFile -CompressionLevel Optimal
    $hash = Write-HashSidecar $zipFile
    Remove-Item -LiteralPath $sqlFile
    Write-BackupLog 'SUCCESS' "Database backup verified: $([IO.Path]::GetFileName($zipFile)); SHA256=$hash"
    Remove-ExpiredBackups $directory 'Megarax_FullBackup_*.sql.zip'
}

function Backup-ProjectFiles {
    $groups = @(
        @{ Name = 'config'; Prefix = 'Megarax_ConfigBackup'; Paths = @('game\config', 'login\config', 'game\java.cfg', 'login\java.cfg', 'game\log.cfg', 'login\log.cfg') },
        @{ Name = 'datapack'; Prefix = 'Megarax_DatapackBackup'; Paths = @('game\data\scripts\custom', 'game\data\stats\npcs\custom', 'game\data\html\CommunityBoard', 'game\data\html\custom', 'game\data\custom', 'db_installer\sql\game') }
    )
    foreach ($group in $groups) {
        $directory = Join-Path $backupRoot $group.Name
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $paths = @($group.Paths | ForEach-Object { Join-Path $projectRoot $_ } | Where-Object { Test-Path -LiteralPath $_ })
        if ($paths.Count -eq 0) { throw "No source paths found for $($group.Name) backup." }
        $zipFile = Join-Path $directory "$($group.Prefix)_$stamp.zip"
        Compress-Archive -Path $paths -DestinationPath $zipFile -CompressionLevel Optimal
        if ((Get-Item $zipFile).Length -lt 1024) { throw "$($group.Name) backup is too small." }
        $hash = Write-HashSidecar $zipFile
        Write-BackupLog 'SUCCESS' "$($group.Name) backup verified: $([IO.Path]::GetFileName($zipFile)); SHA256=$hash"
        Remove-ExpiredBackups $directory "$($group.Prefix)_*.zip"
    }
}

try {
    Write-BackupLog 'INFO' "Backup started. Mode=$Mode Database=$Database Host=$HostName Port=$Port Retention=$RetentionCount"
    if ($Mode -in @('full', 'database')) { Backup-Database }
    if ($Mode -in @('full', 'files')) { Backup-ProjectFiles }
    Write-BackupLog 'SUCCESS' "Backup operation completed. Mode=$Mode"
}
catch {
    Write-BackupLog 'ERROR' $_.Exception.Message
    exit 1
}
