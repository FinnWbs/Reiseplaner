# TravelMate Planner

Webbasiertes MVP zur automatisierten Erstellung personalisierter Reiseplaene.

## Stack

- Frontend: Nuxt 3
- Backend: Java 21, Quarkus
- Datenbank: PostgreSQL
- Authentifizierung: E-Mail, Passwort, JWT
- Deployment: Docker Compose

## Lokale Entwicklung

Das Backend ist auf Java 21 ausgelegt. JDK 25 kann auf Windows mit Maven/Javac beim Test-Compile Fehler wie `AccessDeniedException` auf JAR-Dateien ausloesen. JDK 25 muss nicht entfernt werden; fuer dieses Projekt wird JDK 21 gezielt genutzt.

Einmalig portables JDK 21 ins Projekt laden:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-jdk21.ps1
```

Backend-Tests mit JDK 21 ausfuehren:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backend-test.ps1
```

Falls kein lokales JDK 21 gefunden wird, nutzt das Test-Script automatisch Docker mit `maven:3.9.9-eclipse-temurin-21`.

Hinweis fuer eingeschraenkte Shells: Wenn Java schon bei `Path.toRealPath()` mit `AccessDeniedException` scheitert, ist das kein Maven- oder Projektfehler. In diesem Fall das Script in einer normalen PowerShell ausfuehren oder Docker Desktop starten, damit der Docker-Fallback genutzt werden kann.

## Start

Einmalig lokale JWT-Keys erzeugen:

```bash
node scripts/generate-jwt-keys.mjs
```

```bash
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080

## MVP-Fokus

Nutzer waehlen Stadt, Anzahl der Reisetage und Interessen. Das Backend speichert Aktivitaeten zentral, mappt sie auf interne Interessen und erzeugt daraus einen regelbasierten Tagesplan.

Externe Datenquellen sind architektonisch ueber den Sync-Service vorbereitet. Die aktuelle MVP-Version nutzt reproduzierbare Demo-Daten, damit die Anwendung ohne API-Schluessel und ohne fragile Live-Abhaengigkeiten laeuft.
