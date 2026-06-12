$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root "tools"
$jdkHome = Join-Path $tools "jdk-21"
$archive = Join-Path $tools "jdk-21.zip"
$downloadUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"

if (Test-Path (Join-Path $jdkHome "bin\java.exe")) {
    Write-Host "JDK 21 already installed at $jdkHome"
    exit 0
}

New-Item -ItemType Directory -Force -Path $tools | Out-Null

Write-Host "Downloading Eclipse Temurin JDK 21..."
if (Test-Path $archive) {
    Remove-Item -LiteralPath $archive -Force
}

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
try {
    Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $archive
} catch {
    Write-Host "PowerShell download failed, retrying with curl.exe..."
    & curl.exe -L --fail --output $archive $downloadUrl
    if ($LASTEXITCODE -ne 0) {
        Write-Host "curl.exe download failed, retrying with Node.js..."
        node (Join-Path $root "scripts\download-file.mjs") $downloadUrl $archive
        if ($LASTEXITCODE -ne 0) {
            throw "JDK download failed."
        }
    }
}

$extractDir = Join-Path $tools "jdk-21-extract"
if (Test-Path $extractDir) {
    Remove-Item -LiteralPath $extractDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $extractDir | Out-Null

Expand-Archive -LiteralPath $archive -DestinationPath $extractDir -Force
$expandedJdk = Get-ChildItem -Path $extractDir -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
    Select-Object -First 1

if (-not $expandedJdk) {
    throw "Download enthaelt kein gueltiges JDK."
}

Move-Item -LiteralPath $expandedJdk.FullName -Destination $jdkHome
Remove-Item -LiteralPath $extractDir -Recurse -Force
Remove-Item -LiteralPath $archive -Force

Write-Host "JDK 21 installed at $jdkHome"
