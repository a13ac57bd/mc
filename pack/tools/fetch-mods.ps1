# Downloads the server-side mods listed in the packwiz pack into the self-test server (TECH_SPEC §1).
# Every file is checked against the hash in its .pw.toml.
#   powershell -ExecutionPolicy Bypass -File pack\tools\fetch-mods.ps1
param(
    [string]$Pack = (Join-Path $PSScriptRoot '..'),
    [string]$Dest = (Join-Path $PSScriptRoot '..\..\server\mods')
)
$ErrorActionPreference = 'Stop'

function Test-FileHash([string]$Path, [string]$Format, [string]$Expected) {
    if (-not $Expected) { return $true }
    $alg = switch ($Format) {
        'sha1' { 'SHA1' }
        'sha256' { 'SHA256' }
        'sha512' { 'SHA512' }
        'md5' { 'MD5' }
        default { $null }
    }
    if (-not $alg) { return $true }
    return (Get-FileHash -Path $Path -Algorithm $alg).Hash.ToLower() -eq $Expected.ToLower()
}

function Get-TomlValue([string]$Text, [string]$Key) {
    if ($Text -match "(?m)^\s*$Key\s*=\s*`"([^`"]*)`"") { return $Matches[1] }
    return $null
}

New-Item -ItemType Directory -Force -Path $Dest | Out-Null
$modsDir = Join-Path $Pack 'mods'
if (-not (Test-Path $modsDir)) {
    Write-Host "no mods yet: add some with 'packwiz modrinth add <slug>' in $Pack"
    exit 0
}

Get-ChildItem $modsDir -Filter *.pw.toml | ForEach-Object {
    $text = Get-Content $_.FullName -Raw
    $side = Get-TomlValue $text 'side'
    if ($side -eq 'client') { return }
    $file = Get-TomlValue $text 'filename'
    $url = Get-TomlValue $text 'url'
    $format = Get-TomlValue $text 'hash-format'
    $hash = Get-TomlValue $text 'hash'
    if (-not $file) { throw "no filename in $($_.Name)" }
    if (-not $url) {
        Write-Warning "${file}: no direct URL (CurseForge metadata mode), download it manually"
        return
    }
    $out = Join-Path $Dest $file
    if ((Test-Path $out) -and (Test-FileHash $out $format $hash)) {
        Write-Host "ok   $file"
        return
    }
    Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing
    if (-not (Test-FileHash $out $format $hash)) {
        Remove-Item $out
        throw "hash mismatch: $file"
    }
    Write-Host "got  $file"
}
