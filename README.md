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

- Fuer echte Aktivitaetsimporte muss vorher `GEOAPIFY_API_KEY` gesetzt sein. Der kostenlose Key wird nicht eingecheckt.
- Frontend: http://localhost:3000
- Backend: http://localhost:8080

## MVP-Fokus

Nutzer waehlen Stadt, Anzahl der Reisetage und Interessen. Das Backend speichert Aktivitaeten zentral, mappt sie auf interne Interessen und erzeugt daraus einen regelbasierten Tagesplan.

Geoapify liefert die primaeren POIs. Wikidata und Wikipedia ergaenzen externe Referenzen und Beschreibungstexte. OpenStreetMap-Referenzen werden gespeichert; ein eigener OSM-Abruf ist standardmaessig deaktiviert. Die Daten werden lokal persistiert und bei wiederholten Importen ueber externe IDs beziehungsweise Name und Stadt dedupliziert.

Datenquellen und Attribution: [Geoapify](https://www.geoapify.com/), [Wikidata (CC0)](https://www.wikidata.org/wiki/Wikidata:Data_access), [Wikipedia](https://www.mediawiki.org/wiki/API:REST_API) und [OpenStreetMap](https://www.openstreetmap.org/copyright).
