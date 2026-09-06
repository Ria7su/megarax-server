[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$javaHome = $env:JAVA_HOME
if (-not $javaHome) { throw 'JAVA_HOME is not configured.' }

$javac = Join-Path $javaHome 'bin\javac.exe'
$source = Join-Path $PSScriptRoot 'MegaraxAccountSync.java'
$output = Join-Path $projectRoot 'runtime\account-sync\classes'
$connector = Join-Path $projectRoot 'libs\mysql-connector-j-9.2.0.jar'

foreach ($required in @($javac, $source, $connector)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required file is missing: $required" }
}

New-Item -ItemType Directory -Force -Path $output | Out-Null
& $javac -encoding UTF-8 -cp $connector -d $output $source
if ($LASTEXITCODE -ne 0) { throw "Account synchronizer compilation failed with exit code $LASTEXITCODE." }
Write-Host 'Account synchronizer compiled successfully.'
