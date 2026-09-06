[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$buildDir = Join-Path $projectRoot '.codex_tmp_megarax_vivo_verify'

function Assert-VivoCondition {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if (-not $Condition) {
        throw "MegaRax VIVO verification failed: $Message"
    }
}

$requiredFiles = @(
    'game\config\Custom\MegaraxVivo.ini',
    'db_installer\sql\game\megarax_vivo.sql',
    'game\data\scripts\custom\MegaraxVivo\MegaraxVivoConfig.java',
    'game\data\scripts\custom\MegaraxVivo\MegaraxVivoService.java',
    'game\data\scripts\custom\MegaraxVivo\MegaraxVivoGoddardAdapter.java',
    'game\data\scripts\handlers\communityboard\MegaraxVivoBoard.java',
    'game\data\html\CommunityBoard\Custom\vivo\main.html',
    'backups\megarax_vivo_foundation_20260830\game\data\scripts\handlers\MasterHandler.java',
    'backups\megarax_vivo_foundation_20260830\game\data\html\CommunityBoard\Custom\navigation.html'
)
foreach ($relativePath in $requiredFiles) {
    Assert-VivoCondition (Test-Path -LiteralPath (Join-Path $projectRoot $relativePath) -PathType Leaf) "Missing $relativePath"
}

$config = Get-Content -LiteralPath (Join-Path $projectRoot 'game\config\Custom\MegaraxVivo.ini') -Raw
Assert-VivoCondition ($config -match '(?m)^MegaraxVivoPilotStatus\s*=\s*PREPARATION\s*$') 'Pilot status must remain PREPARATION before private validation.'
Assert-VivoCondition ($config -match '(?m)^EnableMegaraxVivoGoddardAdapter\s*=\s*False\s*$') 'Goddard gameplay adapter must remain disabled before private validation.'

$schema = Get-Content -LiteralPath (Join-Path $projectRoot 'db_installer\sql\game\megarax_vivo.sql') -Raw
$createMatches = [regex]::Matches($schema, '(?im)^CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?(?<name>[a-z0-9_]+)`?')
Assert-VivoCondition ($createMatches.Count -eq 8) "Expected 8 additive VIVO tables, found $($createMatches.Count)."
foreach ($match in $createMatches) {
    Assert-VivoCondition ($match.Groups['name'].Value.StartsWith('megarax_vivo_')) "Schema attempts to create a non-VIVO table: $($match.Groups['name'].Value)"
}
Assert-VivoCondition (-not ($schema -match '(?im)^\s*(ALTER|DROP|TRUNCATE|DELETE|UPDATE|INSERT)\s+')) 'Installer schema contains a mutating statement other than CREATE TABLE.'

$masterPath = Join-Path $projectRoot 'game\data\scripts\handlers\MasterHandler.java'
$masterBackupPath = Join-Path $projectRoot 'backups\megarax_vivo_foundation_20260830\game\data\scripts\handlers\MasterHandler.java'
$master = Get-Content -LiteralPath $masterPath -Raw
$masterBackup = Get-Content -LiteralPath $masterBackupPath -Raw
Assert-VivoCondition (([regex]::Matches($master, '(?m)^import handlers\.communityboard\.MegaraxVivoBoard;\s*$')).Count -eq 1) 'MasterHandler must contain exactly one VIVO import.'
Assert-VivoCondition (([regex]::Matches($master, '(?m)^\s*MegaraxVivoBoard\.class,\s*$')).Count -eq 1) 'MasterHandler must register exactly one VIVO board.'
$masterWithoutVivo = [regex]::Replace($master, '(?m)^import handlers\.communityboard\.MegaraxVivoBoard;\r?\n', '')
$masterWithoutVivo = [regex]::Replace($masterWithoutVivo, '(?m)^\s*MegaraxVivoBoard\.class,\r?\n', '')
Assert-VivoCondition ($masterWithoutVivo -ceq $masterBackup) 'MasterHandler differs from its pre-VIVO backup beyond the two expected integration lines.'

$navigationPath = Join-Path $projectRoot 'game\data\html\CommunityBoard\Custom\navigation.html'
$navigationBackupPath = Join-Path $projectRoot 'backups\megarax_vivo_foundation_20260830\game\data\html\CommunityBoard\Custom\navigation.html'
$navigation = Get-Content -LiteralPath $navigationPath -Raw
$navigationBackup = Get-Content -LiteralPath $navigationBackupPath -Raw
Assert-VivoCondition (([regex]::Matches($navigation, '(?m)^<button value="MegaRax VIVO".*$')).Count -eq 1) 'Navigation must contain exactly one VIVO button.'
$navigationWithoutVivo = [regex]::Replace($navigation, '(?m)^<button value="MegaRax VIVO".*\r?\n', '')
Assert-VivoCondition ($navigationWithoutVivo -ceq $navigationBackup) 'Navigation differs from its pre-VIVO backup beyond the expected button.'

New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
$sources = @(
    (Join-Path $projectRoot 'game\data\scripts\custom\MegaraxVivo\MegaraxVivoConfig.java'),
    (Join-Path $projectRoot 'game\data\scripts\custom\MegaraxVivo\MegaraxVivoService.java'),
    (Join-Path $projectRoot 'game\data\scripts\custom\MegaraxVivo\MegaraxVivoGoddardAdapter.java'),
    (Join-Path $projectRoot 'game\data\scripts\handlers\communityboard\MegaraxVivoBoard.java'),
    $masterPath
)
$classPath = (Join-Path $projectRoot 'libs\GameServer.jar') + ';' + (Join-Path $projectRoot 'game\data\scripts')
& javac --release 8 -encoding UTF-8 -cp $classPath -d $buildDir @sources
Assert-VivoCondition ($LASTEXITCODE -eq 0) 'Java 8 compilation failed.'

$compiledClass = Join-Path $buildDir 'custom\MegaraxVivo\MegaraxVivoService.class'
$classBytes = [System.IO.File]::ReadAllBytes($compiledClass)
Assert-VivoCondition (($classBytes[0] -eq 0xCA) -and ($classBytes[1] -eq 0xFE) -and ($classBytes[2] -eq 0xBA) -and ($classBytes[3] -eq 0xBE)) 'Compiled service is not a valid Java class.'
$majorVersion = ([int]$classBytes[6] -shl 8) -bor [int]$classBytes[7]
Assert-VivoCondition ($majorVersion -eq 52) "Expected Java 8 class major version 52, found $majorVersion."

$runtimePairs = @(
    @('custom\MegaraxVivo\MegaraxVivoConfig.class', 'game\data\scripts\custom\MegaraxVivo\MegaraxVivoConfig.class'),
    @('custom\MegaraxVivo\MegaraxVivoService.class', 'game\data\scripts\custom\MegaraxVivo\MegaraxVivoService.class'),
    @('custom\MegaraxVivo\MegaraxVivoGoddardAdapter.class', 'game\data\scripts\custom\MegaraxVivo\MegaraxVivoGoddardAdapter.class'),
    @('handlers\communityboard\MegaraxVivoBoard.class', 'game\data\scripts\handlers\communityboard\MegaraxVivoBoard.class'),
    @('handlers\MasterHandler.class', 'game\data\scripts\handlers\MasterHandler.class')
)
foreach ($pair in $runtimePairs) {
    $built = Join-Path $buildDir $pair[0]
    $runtime = Join-Path $projectRoot $pair[1]
    Assert-VivoCondition (Test-Path -LiteralPath $runtime -PathType Leaf) "Runtime class is missing: $($pair[1])"
    $builtHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $built).Hash
    $runtimeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $runtime).Hash
    Assert-VivoCondition ($builtHash -ceq $runtimeHash) "Runtime class is stale: $($pair[1])"
}

Write-Host 'PASS: MegaRax VIVO foundation is additive, gated, registered once, and Java 8 compatible.' -ForegroundColor Green
