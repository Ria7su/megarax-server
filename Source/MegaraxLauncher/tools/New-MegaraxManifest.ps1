param(
    [Parameter(Mandatory = $true)]
    [string] $PatchRoot,

    [Parameter(Mandatory = $true)]
    [string] $BaseUrl,

    [Parameter(Mandatory = $true)]
    [string] $Version,

    [string] $OutputPath = "manifest.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $PatchRoot -PathType Container)) {
    throw "PatchRoot does not exist: $PatchRoot"
}

$resolvedRoot = (Resolve-Path -LiteralPath $PatchRoot).Path.TrimEnd('\', '/')
$base = $BaseUrl.TrimEnd('/')
$rootUri = New-Object System.Uri(($resolvedRoot + [System.IO.Path]::DirectorySeparatorChar))

$criticalFiles = @(
    "system/l2.exe",
    "system/interface.xdat",
    "system/npcgrp.dat",
    "system/itemname-e.dat",
    "system/etcitemgrp.dat",
    "system/armorgrp.dat",
    "system/weapongrp.dat",
    "system/skillgrp.dat"
)

function Convert-ToRelativeUrlPath {
    param([string] $RelativePath)

    $segments = $RelativePath -split "/"
    return ($segments | ForEach-Object { [System.Uri]::EscapeDataString($_) }) -join "/"
}

$files = Get-ChildItem -LiteralPath $resolvedRoot -File -Recurse |
    Where-Object { $_.Name -ne "manifest.json" } |
    Sort-Object FullName |
    ForEach-Object {
        $fileUri = New-Object System.Uri($_.FullName)
        $relativePath = [System.Uri]::UnescapeDataString($rootUri.MakeRelativeUri($fileUri).ToString())
        $relativePath = $relativePath.Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $encodedPath = Convert-ToRelativeUrlPath -RelativePath $relativePath

        [ordered]@{
            localPath = $relativePath
            url = "$base/$encodedPath"
            sha256 = $hash
            size = $_.Length
            critical = $criticalFiles -contains $relativePath.ToLowerInvariant()
        }
    }

$manifest = [ordered]@{
    version = $Version
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    files = @($files)
}

$json = $manifest | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8
Write-Host "Manifest created: $OutputPath"
