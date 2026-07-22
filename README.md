# TravelMate Planner

TravelMate ist eine Webanwendung, die aus Reiseziel, Zeitraum, Interessen und gewünschtem Reisetempo einen persönlichen Tagesplan erstellt. Die Anwendung importiert passende Orte, bewertet deren Qualität, plant sie räumlich sinnvoll ein und ergänzt Bilder bei Bedarf nachträglich.

Dieses Dokument richtet sich an Personen, die das Projekt auf einem neuen Rechner starten oder weiterentwickeln möchten.

## Inhalt

- [Funktionsumfang](#funktionsumfang)
- [Technischer Aufbau](#technischer-aufbau)
- [Voraussetzungen](#voraussetzungen)
- [Schnellstart mit Docker](#schnellstart-mit-docker)
- [API-Keys und Konfiguration](#api-keys-und-konfiguration)
- [Lokale Entwicklung ohne vollständigen Docker-Stack](#lokale-entwicklung-ohne-vollständigen-docker-stack)
- [Tests und Builds](#tests-und-builds)
- [Typische Abläufe](#typische-abläufe)
- [Fehlersuche](#fehlersuche)
- [Projektstruktur](#projektstruktur)

## Funktionsumfang

- Registrierung und Anmeldung mit JWT-Authentifizierung
- geführtes Reiseinterview für Ziel, Zeitraum, Interessen, Tempo und Tagesrhythmus
- feste Reisedaten oder flexible Reisedauer mit Wunschmonat
- Kalenderansicht mit geplanten und ungeplanten Reisen
- regelbasierte Tagesplanung mit zeitlicher und räumlicher Verteilung
- Verschieben, Ersetzen, Entfernen und Neuplanen von Aktivitäten
- Kartenansicht auf Basis von Leaflet und OpenStreetMap
- nachträgliche Bildanreicherung über Google Places mit Kategorie-Platzhaltern als Fallback
- Highlight-Katalog für touristisch relevante Orte

## Technischer Aufbau

| Bereich | Technologie | Aufgabe |
|---|---|---|
| Frontend | Nuxt 3, Vue 3, TypeScript | Oberfläche, Kalender, Tagesplan, Karte und Galerie |
| Backend | Java 21, Quarkus | REST-API, Authentifizierung, Import, Planung und Bildanreicherung |
| Datenbank | PostgreSQL 16 | Nutzer, Reisen, Tage, Aktivitäten, Bilder und externe Referenzen |
| Infrastruktur | Docker Compose | Startet Datenbank, Backend und Frontend gemeinsam |
| Ortsdaten | Geoapify | Stadt-Autocomplete und primäre POI-Daten |
| Wissensdaten | Wikidata, Wikipedia, Wikimedia Pageviews | Referenzen, Beschreibungen, Bekanntheit und Highlights |
| Bilder | Google Places | Fotos für konkrete Aktivitäten, serverseitig aufgelöst |

Eine ausführlichere, präsentationsfähige Übersicht des Datenflusses befindet sich in [docs/TravelMate-Architektur-und-Datenfluss.pdf](docs/TravelMate-Architektur-und-Datenfluss.pdf).

## Voraussetzungen

Für den empfohlenen Docker-Start werden benötigt:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) mit laufender Docker Engine
- Git
- Node.js 22 oder neuer, nur zum einmaligen Erzeugen lokaler JWT-Schlüssel

Für die lokale Entwicklung ohne Backend-Container zusätzlich:

- JDK 21
- Maven 3.9 oder neuer
- Node.js 22 oder neuer

> Das Backend ist auf Java 21 ausgelegt. JDK 25 kann unter Windows beim Test-Compile zu `AccessDeniedException` oder `Path.toRealPath()`-Fehlern führen. Deshalb verwendet das Repository bewusst JDK 21.

## Schnellstart mit Docker

Die folgenden Befehle sind für PowerShell unter Windows formuliert.

### 1. Repository klonen

```powershell
git clone <REPOSITORY-URL> Reiseplaner
cd Reiseplaner
```

### 2. Lokale Konfiguration anlegen

Die Datei `.env` enthält nur lokale Konfiguration und wird nicht committed.

```powershell
Copy-Item .env.example .env
```

Öffne danach `.env` und trage mindestens einen Geoapify-Key ein:

```dotenv
GEOAPIFY_API_KEY=dein_geoapify_key
```

Ohne Geoapify startet die Anwendung zwar, aber Stadtvorschläge und echte Aktivitätsimporte stehen nicht zuverlässig zur Verfügung.

### 3. JWT-Schlüssel erzeugen

Die Schlüssel werden für Anmeldung und geschützte API-Endpunkte benötigt.

```powershell
node scripts/generate-jwt-keys.mjs
```

Dadurch entstehen diese lokalen, ignorierten Dateien:

```text
secrets/privateKey.pem
secrets/publicKey.pem
```

> Der Befehl erzeugt keine vorhandenen Schlüssel erneut. Sollten Schlüssel bewusst ersetzt werden, sichere oder entferne die beiden Dateien zuerst.

### 4. Gesamten Stack starten

```powershell
docker compose up --build
```

Beim ersten Start lädt Docker die Basisimages, installiert Frontend-Abhängigkeiten, baut das Quarkus-Backend und führt Datenbankmigrationen automatisch aus.

Danach sind die Dienste erreichbar:

| Dienst | Adresse | Zweck |
|---|---|---|
| Frontend | http://localhost:3000 | TravelMate im Browser |
| Backend | http://localhost:8080 | REST-API |
| PostgreSQL | localhost:5432 | Datenbank, Benutzer `travelmate`, Datenbank `travelmate` |

Zum Beenden im Terminal `Ctrl+C` drücken. Um die Container im Hintergrund zu stoppen:

```powershell
docker compose down
```

Die Datenbank bleibt dabei im Docker-Volume erhalten. Für einen vollständigen lokalen Neustart inklusive aller Daten:

```powershell
docker compose down -v
```

## API-Keys und Konfiguration

Alle lokalen Schlüssel gehören in `.env` oder in eine sichere Deployment-Konfiguration – niemals in den Quellcode oder ein Git-Commit.

| Variable | Pflicht | Wirkung |
|---|---:|---|
| `GEOAPIFY_API_KEY` | Für echte Ortsdaten ja | Stadt-Autocomplete und Aktivitätsimport |
| `CATALOG_WIKIMEDIA_ENABLED` | Optional | aktiviert den Highlight-Katalog; Standard `true` |
| `WIKIMEDIA_USER_AGENT` | Empfohlen | Kontaktkennung für Wikimedia-Anfragen |

Beispielkonfiguration:

```dotenv
GEOAPIFY_API_KEY=dein_geoapify_key
CATALOG_WIKIMEDIA_ENABLED=true
WIKIMEDIA_USER_AGENT=TravelMate/1.0 (kontakt@deine-domain.de)
```

Google Places ist deaktiviert und es wird kein Google-Places-Key benötigt.

Weitere Import-, Radius-, Cache- und räumliche Planungsparameter sind in [.env.example](.env.example) dokumentiert. Die Standardwerte sind für die lokale Entwicklung geeignet; ändere sie erst, wenn du die Auswirkungen auf Importmenge und Planung verstehst.

## Lokale Entwicklung ohne vollständigen Docker-Stack

Diese Variante eignet sich, wenn Frontend und Backend getrennt entwickelt werden sollen. PostgreSQL läuft weiterhin bequem über Docker.

### JDK 21 bereitstellen

Falls JDK 21 nicht installiert ist, kann das Repository eine portable Version laden:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-jdk21.ps1
```

### Datenbank starten

```powershell
docker compose up -d postgres
```

### Backend starten

Erstelle bei Bedarf `backend/.env` für lokale Backend-Variablen, etwa `GEOAPIFY_API_KEY` oder Google-Places-Konfiguration. Anschließend:

```powershell
scripts\backend-dev.cmd
```

Das Script verwendet JDK 21 aus `tools/jdk-21`, startet PostgreSQL falls nötig und startet Quarkus im Entwicklungsmodus auf Port 8080.

### Frontend starten

In einem zweiten Terminal:

```powershell
scripts\frontend-dev.cmd
```

Alternativ direkt:

```powershell
cd frontend
npm install
npm.cmd run dev -- --host 127.0.0.1 --port 3000
```

Das Frontend erwartet das Backend standardmäßig unter `http://localhost:8080`. Für einen anderen API-Endpunkt kann vor dem Frontend-Start `NUXT_PUBLIC_API_BASE` gesetzt werden.

## Tests und Builds

### Frontend

```powershell
cd frontend
npm install
npm.cmd run build
```

Optionaler Lint-Lauf:

```powershell
npm.cmd run lint
```

### Backend

Aus dem Repository-Stammverzeichnis:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backend-test.ps1
```

Das Script sucht zuerst nach JDK 21. Falls keines vorhanden ist, verwendet es Docker mit einem Java-21-Maven-Image.

## Typische Abläufe

### Eine Reise erstellen

1. Nutzer registriert sich oder meldet sich an.
2. Im Interview werden Ziel, Zeitraum, Interessen, Tempo und Tagesrhythmus gewählt.
3. Das Frontend sendet einen `POST /trips`-Request mit einem JWT im `Authorization`-Header.
4. Das Backend legt Reise und Tage an.
5. Es prüft, ob für Stadt und Interessen bereits ausreichend aktuelle Aktivitäten lokal gespeichert sind.
6. Falls nötig, importiert Geoapify neue Orte und Wikidata/Wikipedia ergänzen Qualitäts- und Beschreibungssignale.
7. Die Planungslogik verteilt passende Aktivitäten auf Tage und Zeitfenster.
8. Das Frontend erhält den fertigen Plan und zeigt Liste, Karte und Galerie.

### Bilder laden

Die Reiseplanung wartet nicht auf Bilder:

1. Beim Öffnen eines Tages prüft die Galerie, ob zur Aktivität echte Bilder vorhanden sind.
2. Fehlen Bilder, ruft das Frontend `POST /activities/{id}/images` auf.
3. Das Backend sucht mit Name, Adresse und Koordinaten nach einem passenden Google-Places-Ort.
4. Bis zu drei Bildreferenzen werden lokal gespeichert und an das Frontend zurückgegeben.
5. Das Frontend lädt zuerst Bilder des sichtbaren Tages und danach die nächsten zwei Tage vor.
6. Bis ein echtes Bild erfolgreich geladen ist, bleibt ein Kategorie-Platzhalter sichtbar.

### Highlights öffnen

Beim Klick auf „Highlights entdecken“ lädt das Frontend `GET /trips/{id}/catalog-attractions`. Der Backend-Katalog wird pro Stadt und Land zwischengespeichert. Bei einem Cache-Miss werden Wikidata-Kandidaten ermittelt, mit Wikimedia-Pageviews ergänzt und nach touristischer Relevanz sortiert.

## Projektstruktur

```text
Reiseplaner/
├── frontend/                 # Nuxt-3-Anwendung
│   ├── components/           # UI-Bausteine, Kalender, Galerie, Karte
│   ├── composables/          # API-, Reise-, Bild- und Auth-Logik
│   ├── pages/                # Routen: Start, Login, Kalender, Planner, Reise
│   └── types/                # gemeinsame TypeScript-DTOs
├── backend/                  # Quarkus-Anwendung
│   └── src/main/java/de/travelmate/
│       ├── auth/             # Registrierung, Login, JWT
│       ├── trip/             # Reisen, Tage, Zeitplan und REST-Endpunkte
│       ├── planning/         # Auswahl, Quoten, Cluster und Tagesplanung
│       ├── activity/         # Aktivitäten, Persistenz und Bildanreicherung
│       ├── datasource/       # Geoapify, Wikidata, Wikipedia, OSM
│       ├── catalog/          # Highlight-Katalog und Cache
│       └── quality/          # Qualitätsfilter und Ranking
├── docs/                     # Architektur- und Fachkonzepte
├── scripts/                  # JDK-, Start-, Test- und Schlüssel-Skripte
├── docker-compose.yml        # vollständiger lokaler Stack
└── .env.example              # dokumentierte Umgebungsvariablen
```

## Fehlersuche

### Docker kann nicht gestartet werden

- Prüfe, ob Docker Desktop läuft.
- Prüfe den Status mit `docker ps`.
- Starte bei Problemen zunächst nur die Datenbank mit `docker compose up -d postgres`.

### Das Frontend lädt, aber Anmeldung oder Reisen funktionieren nicht

- Prüfe, ob `http://localhost:8080` erreichbar ist.
- Prüfe die Backend-Logs mit `docker compose logs backend`.
- Stelle sicher, dass die JWT-Dateien in `secrets/` vorhanden sind.
- Prüfe, ob Frontend und Backend beide auf `localhost` laufen und nicht unterschiedliche Ports erwarten.

### Keine Stadtvorschläge oder keine interessanten Aktivitäten

- Prüfe, ob `GEOAPIFY_API_KEY` in der rootweiten `.env` gesetzt ist.
- Starte die Backend-Container nach einer Änderung von `.env` neu:

```powershell
docker compose up -d --build backend
```

- Prüfe die Logs: `docker compose logs -f backend`.

### Aktivitätsbilder

Die Galerie verwendet die mitgelieferten lokalen Kategoriebilder. Google Places ist deaktiviert.

### Backend-Tests scheitern unter Windows

- Nutze nicht blind ein globales JDK 25.
- Führe `scripts/backend-test.ps1` aus; dieses bevorzugt Java 21 und kann auf Docker ausweichen.
- Falls `Path.toRealPath()` oder `AccessDeniedException` auftritt, starte Docker Desktop oder führe das Script in einer normalen PowerShell aus.

## Nützliche Links

- [Qualitätsmodell für POIs](docs/poi-quality-engine.md)
- [Architektur und Datenfluss als PDF](docs/TravelMate-Architektur-und-Datenfluss.pdf)
- [Docker Compose](docker-compose.yml)
- [Beispielkonfiguration](.env.example)

## Sicherheit

- `.env` und `secrets/` sind absichtlich durch `.gitignore` geschützt.
- Keine API-Keys, JWT-Schlüssel, Datenbank-Dumps oder Logs committen.
- Für produktive Deployments eigene Datenbank-Zugangsdaten, starke JWT-Schlüssel und restriktive API-Key-Regeln verwenden.
