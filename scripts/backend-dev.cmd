@echo off
setlocal

set "ROOT=%~dp0.."
set "BACKEND=%ROOT%\backend"
set "JAVA_HOME=%ROOT%\tools\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JWT_PUBLIC_KEY_LOCATION=%ROOT%\secrets\publicKey.pem"
set "JWT_PRIVATE_KEY_LOCATION=%ROOT%\secrets\privateKey.pem"

if exist "%BACKEND%\.env" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%BACKEND%\.env") do set "%%A=%%B"
)

cd /d "%ROOT%"
docker compose up -d postgres
if errorlevel 1 (
  echo PostgreSQL konnte nicht ueber Docker Compose gestartet werden.
  echo Starte Docker Desktop und fuehre dieses Script danach erneut aus.
  exit /b 1
)

echo Warte auf PostgreSQL auf localhost:5432...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(45); do { try { $tcp=[Net.Sockets.TcpClient]::new('127.0.0.1',5432); $tcp.Close(); exit 0 } catch { Start-Sleep -Seconds 1 } } while ((Get-Date) -lt $deadline); exit 1"
if errorlevel 1 (
  echo PostgreSQL ist nicht rechtzeitig auf localhost:5432 erreichbar.
  exit /b 1
)

cd /d "%BACKEND%"
mvn -Dmaven.repo.local=.m2-jdk21/repository -Dmaven.test.skip=true -Dquarkus.analytics.disabled=true -Dquarkus.enforceBuildGoal=false quarkus:dev > dev-8080.log 2> dev-8080.err.log
