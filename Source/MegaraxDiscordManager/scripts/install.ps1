[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $project

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw 'Node.js 22.12 o superior no está disponible en PATH.'
}
if ([version]((node --version).TrimStart('v')) -lt [version]'22.12.0') {
    throw 'Se requiere Node.js 22.12 o superior.'
}
$pnpmCommand = Get-Command pnpm -ErrorAction SilentlyContinue
if ($pnpmCommand) {
    $pnpmExecutable = 'pnpm'
    $pnpmPrefix = @()
}
elseif (Get-Command corepack -ErrorAction SilentlyContinue) {
    corepack prepare pnpm@11.9.0 --activate
    $pnpmExecutable = 'corepack'
    $pnpmPrefix = @('pnpm')
}
else {
    throw 'No se encontró pnpm ni Corepack. Reinstala Node.js LTS con Corepack.'
}

& $pnpmExecutable @pnpmPrefix install --frozen-lockfile
if ($LASTEXITCODE -ne 0) {
    throw "La instalación de dependencias falló con código $LASTEXITCODE."
}
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
    Write-Host 'Se creó .env. Completa sus valores localmente; nunca compartas el token.'
}
& $pnpmExecutable @pnpmPrefix check
if ($LASTEXITCODE -ne 0) {
    throw "La validación del proyecto falló con código $LASTEXITCODE."
}
Write-Host "Instalación validada. Usa '$pnpmExecutable $($pnpmPrefix -join ' ') <comando>' para los próximos pasos."
