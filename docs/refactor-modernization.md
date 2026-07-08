# TravelMate Refactor Baseline

Diese Notiz beschreibt den aktuellen Verhaltensrahmen fuer verhaltensstabile Refactors.
Sie ist keine Produkt-Spezifikation und fuehrt keine neuen Features ein.

## Kernflows, die stabil bleiben muessen

- Trip-Erstellung: Das Backend loest die ausgewaehlten Interessen auf, synchronisiert bei Bedarf aktive Aktivitaeten und erzeugt Tagesplaene mit bestehenden Quoten-, Zeit- und Raumregeln.
- Import/Sync: Geoapify bleibt die primaere Kandidatenquelle. Wikidata und Wikipedia reichern importierte Kandidaten an. OSM bleibt vorbereitet, aber standardmaessig deaktiviert.
- Planung: Neue Plaene nutzen nur aktive Aktivitaeten der aktuellen Importversion. Locked Items und bestehende gespeicherte Trip-Day-Aktivitaeten bleiben sichtbar.
- Katalog: Der Highlight-Katalog ist manuell. Katalog-Aktivitaeten werden bei Bedarf als inaktive, referenzierbare Aktivitaeten angelegt und locked zu einem Tag hinzugefuegt.
- Bilder: Aktivitaetsbilder sind optional. Bild-Enrichment darf die Trip-Ansicht nicht blockieren.

## Aktuelle Refactor-Hotspots

- `PlanningService` mischt Orchestrierung, Scoring, Quoten, Slot-Auswahl, Zeitplanung und Diagnose.
- `GeoapifyActivityProvider` mischt API-Pagination, JSON-Mapping, Tag-Uebernahme, Deduplizierung, Filterung und Density-Signale.
- `PoiQualityEngine` und Eligibility-/Signal-Klassen enthalten viele Business-Regeln, die nur mit Regressionstests veraendert werden duerfen.
- `useTripWorkspace` buendelt Trip-Mutationen, Katalog, Bild-Preloading und Fehlerbehandlung.
- Globale CSS-Dateien sind gross und sollten nur strukturell, nicht visuell, geschnitten werden.

## Parity-Checks

Vor und nach jedem Refactor-Pass:

```powershell
cd backend
mvn clean test
cd ../frontend
npm run build
```

Nach groesseren Pass-Gruppen:

```powershell
docker compose up -d --build
docker compose logs --tail 80 backend
```

Manuell pruefen: bestehende Reise oeffnen, Tag wechseln, Karte bedienen, Katalog oeffnen,
Highlight hinzufuegen und Aktivitaetsbilder laden.
