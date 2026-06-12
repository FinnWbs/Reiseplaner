$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$toolJdkRoot = Join-Path $root "tools\jdk-21"

$candidates = @(
    $toolJdkRoot,
    "C:\Program Files\Eclipse Adoptium\jdk-21*",
    "C:\Program Files\Java\jdk-21*",
    "C:\Program Files\Microsoft\jdk-21*",
    "C:\Program Files\Amazon Corretto\jdk21*"
)

$jdkHome = $null
foreach ($candidate in $candidates) {
    $match = Get-ChildItem -Path $candidate -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
        Sort-Object FullName -Descending |
        Select-Object -First 1

    if ($match) {
        $jdkHome = $match.FullName
        break
    }

    if ((Test-Path $candidate) -and (Test-Path (Join-Path $candidate "bin\java.exe"))) {
        $jdkHome = $candidate
        break
    }
}

if (-not $jdkHome) {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        throw "JDK 21 wurde nicht gefunden und Docker ist nicht verfuegbar. Nutze scripts\install-jdk21.ps1 oder installiere JDK 21 parallel."
    }

    Write-Host "JDK 21 wurde lokal nicht gefunden. Fuehre Tests in Docker mit Eclipse Temurin 21 aus..."
    Push-Location $root
    try {
        docker run --rm -v "${root}\backend:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
        if ($LASTEXITCODE -ne 0) {
            throw "Backend tests failed in Docker."
        }
        exit 0
    } finally {
        Pop-Location
    }
}

$env:JAVA_HOME = $jdkHome
$env:PATH = "$jdkHome\bin;$env:PATH"

Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
java -version
mvn -version

$realPathCheck = @"
import java.nio.file.*;

public class RealPathCheck {
    public static void main(String[] args) throws Exception {
        Paths.get(args[0]).toRealPath();
    }
}
"@
$realPathCheckFile = Join-Path ([System.IO.Path]::GetTempPath()) "RealPathCheck.java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($realPathCheckFile, $realPathCheck, $utf8NoBom)
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& java $realPathCheckFile (Join-Path $root "README.md") 2>$null
$realPathExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($realPathExitCode -ne 0) {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        docker info *> $null
        $dockerAvailable = $LASTEXITCODE -eq 0
        $ErrorActionPreference = $previousErrorActionPreference

        if (-not $dockerAvailable) {
            throw "Lokales Java scheitert bei Path.toRealPath(), und Docker ist nicht gestartet. Starte Docker Desktop oder fuehre das Script in einer normalen PowerShell ausserhalb der eingeschraenkten Sandbox aus."
        }

        Write-Host "Lokales Java kann Path.toRealPath() nicht nutzen. Nutze Docker-Fallback mit JDK 21..."
        Push-Location $root
        try {
            docker run --rm -v "${root}\backend:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
            if ($LASTEXITCODE -ne 0) {
                throw "Backend tests failed in Docker."
            }
            exit 0
        } finally {
            Pop-Location
        }
    }

    throw "Lokales Java scheitert bei Path.toRealPath(). Starte Docker Desktop oder fuehre das Script in einer normalen PowerShell ausserhalb der eingeschraenkten Sandbox aus."
}

Push-Location $backend
try {
    mvn "-Dmaven.repo.local=.m2-jdk21/repository" test
} finally {
    Pop-Location
}
