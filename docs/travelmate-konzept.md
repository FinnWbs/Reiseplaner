# TravelMate Planner: fachliches und technisches Konzept

Stand: 18.06.2026

## 1. Kurzüberblick

TravelMate Planner ist ein webbasiertes MVP zur Erstellung personalisierter Städtereisepläne. Nutzer registrieren sich, wählen oder suchen ein Reiseziel, beantworten ein kurzes Reiseinterview und erhalten daraus einen gespeicherten, interaktiv anpassbaren Tagesplan.

Der Fokus liegt bewusst nicht auf Buchungen, Hotels, Flügen, Wetter, echter KI-Reiseplanung oder perfektem Routing. Stattdessen entsteht ein nachvollziehbarer, regelbasierter Planer, der externe Aktivitätsdaten importiert, lokal speichert und mit Interessen, Tageszeitprofilen und verfügbaren Zeitfenstern kombiniert.

## 2. Fachliches Zielbild

Das System unterstützt den Kernablauf:

1. Nutzer legt einen Account an oder meldet sich an.
2. Nutzer startet ein Reiseinterview.
3. Stadt wird manuell eingegeben, per Autocomplete ausgewählt oder aus Vorschlägen übernommen.
4. Reisezeitraum oder reine Reisedauer wird festgelegt.
5. Bei bekanntem Zeitraum werden konkrete Planungstage ausgewählt.
6. Interessen, Tempo und Tagesrhythmus werden gewählt.
7. Das Backend erzeugt daraus einen Reiseplan.
8. Der Nutzer kann Zeitfenster je Tag anpassen, Aktivitäten verschieben, entfernen oder einzeln neu generieren.

Das MVP soll zeigen, dass aus Stadt, Zeitraum, Interessen und externen Daten ein stabil gespeicherter, wieder abrufbarer Reiseplan entstehen kann.

## 3. Aktuelle Funktionen

- Registrierung und Login per E-Mail/Passwort mit JWT.
- Sessionbasierte Speicherung des Tokens im Frontend.
- Reiseinterview mit sieben Schritten.
- Geoapify-basierter Stadt-Autocomplete über das Backend.
- Auswahl von Reisezeitraum, Planungstagen, Interessen, Tempo und Tagesrhythmus.
- Regelbasierte Reiseplanerstellung.
- Interaktive Tagespläne mit Startzeit, Dauer, Kategorie-Icon, Bewertung und Adresse.
- Tageszeitfenster je Reisetag über einen 0-24-Uhr-Regler.
- Drag-and-drop von Aktivitäten innerhalb eines Tages und zwischen Tagen.
- Einzelnes Entfernen und Regenerieren von Aktivitäten.
- Dark Mode und Sprungbutton.
- Speicherung mehrerer Reisen pro Nutzer.
- Löschen ganzer Reisen anhand stabiler IDs.
- Import echter Aktivitäten aus externen Quellen.

## 4. Domänenmodell

Die zentrale Domäne besteht aus:

- User: Account, Profil, Rolle.
- Interest: interne Interessen wie Kultur, Geschichte, Natur, Food, Shopping, Nightlife und Sport.
- UserInterest: Nutzerpräferenzen.
- Activity: lokal gespeicherte Aktivität.
- ActivityExternalRef: externe Referenzen, z. B. Geoapify, Wikidata, Wikipedia oder OSM.
- ActivityInterest: Mapping von Aktivitäten auf interne Interessen mit Score.
- Trip: Reise eines Nutzers.
- TripDay: einzelner Planungstag, optional mit Datum und Zeitfenster.
- TripDayActivity: konkrete Aktivität im Tagesplan mit Position, Startzeit, Dauer und Lock-Status.

Interne Interessen entkoppeln die Fachlogik von externen Kategorien. Dadurch kann das System später weitere Datenquellen ergänzen, ohne die Planung direkt an eine externe API zu koppeln.

## 5. Datenquellen

Die aktuelle Datenquellenstrategie ist:

- Geoapify Places und Geocoding: primäre Quelle für POIs, Stadtauflösung und Autocomplete.
- Wikidata: externe IDs, Klassifikation und ergänzende Metadaten.
- Wikipedia: Beschreibungstexte und Kurzinfos.
- OpenStreetMap: als vorbereiteter, deaktivierbarer Provider und Referenzquelle.

Der Geoapify-Key bleibt ausschließlich im Backend. Das Frontend ruft keine externen APIs direkt auf.

Aktivitäten werden lokal in PostgreSQL gespeichert. Wiederholte Importe derselben Stadt sollen keine Duplikate erzeugen. Dedupliziert wird über externe Referenzen oder als Fallback über normalisierten Namen und Stadt.

## 6. Planungslogik

Die Planung ist regelbasiert. Aktivitäten werden anhand folgender Kriterien bewertet und ausgewählt:

- Passung zu ausgewählten Interessen.
- Kategorie und Tageszeitprofil.
- Datenqualität und vorhandene Metadaten.
- Optional vorhandene Bewertung.
- Vermeidung bereits verwendeter Aktivitäten innerhalb einer Reise.

Aktivitäten besitzen fachliche Zeitprofile:

- Natur: eher tagsüber.
- Kultur, Geschichte, Shopping und Sport: vor allem Tages- und frühe Abendzeiten.
- Food: bevorzugt mittags oder abends.
- Nightlife: bevorzugt abends.

Zusätzlich haben Aktivitäten Standarddauern, z. B. Food 90 Minuten, Natur 120 Minuten und Nightlife 180 Minuten. Zwischen aufeinanderfolgenden Stopps wird ein Abstand eingeplant.

Wenn ein Nutzer das Zeitfenster eines Tages verändert, werden unpassende Aktivitäten sichtbar markiert, aber nicht automatisch gelöscht. So bleiben Nutzerentscheidungen nachvollziehbar.

## 7. Frontend-Architektur

Das Frontend basiert auf Nuxt 3 und ist nach dem letzten Refactor modularer aufgebaut.

Zentrale Bereiche:

- pages/auth.vue: Registrierung und Login.
- pages/index.vue: schlanke Seitenhülle für den Planer.
- components/TripInterview.vue: Reiseinterview.
- components/LocationAutocomplete.vue: Stadtvorschläge.
- components/TripPlan.vue: Reiseplan-Container.
- components/TripDaySchedule.vue: Tagesplan mit Drag-and-drop.
- components/AvailabilityRange.vue: Zeitfenster-Regler.
- components/TripList.vue: gespeicherte Reisen.
- components/FloatingControls.vue: Dark Mode und Scrollbutton.

Die Zustands- und API-Logik liegt in Composables:

- useAuth: Authentifizierung und Session.
- useApi: API-Requests mit Bearer Token.
- useTripPlanner: Reiseinterview, Reiseverwaltung und Plan-Aktionen.
- useLocationAutocomplete: Stadt-Autocomplete.
- usePlannerTheme: Dark Mode und Scrollsteuerung.

Gemeinsame TypeScript-Typen liegen in types/trip.ts.

## 8. Backend-Architektur

Das Backend basiert auf Quarkus mit Java 21, REST, JWT und PostgreSQL.

Wichtige Pakete:

- auth: Registrierung, Login, JWT und Passwort-Hashing.
- user: Nutzerprofil und Nutzerinteressen.
- interest: interne Interessen.
- activity: Aktivitätsmodell, Persistenz, Importantworten und Kategorie-Mapping.
- datasource: Provider für Geoapify, Wikidata, Wikipedia und vorbereiteten OSM-Zugriff.
- location: Stadt-Autocomplete über Geoapify.
- planning: Scoring, Tageszeitregeln und Planerstellung.
- trip: Reiseverwaltung, Tagespläne, Scheduling und DTOs.
- sync/admin: vorbereitete Synchronisation und Admin-Schnittstellen.

TripService ist für Reise-Orchestrierung zuständig. Schedule- und Drag-and-drop-Logik liegt in TripScheduleService. Dadurch ist die riskante Positionslogik klarer getrennt.

## 9. REST-API

Aktuelle Kernendpunkte:

- POST /auth/register
- POST /auth/login
- GET /users/me
- PUT /users/me
- GET /interests
- PUT /users/me/interests
- GET /locations/autocomplete?query=...
- GET /activities?city=...
- GET /activities/{id}
- POST /activities/import?city=...
- POST /trips
- GET /trips
- GET /trips/{id}
- DELETE /trips/{id}
- POST /trips/{id}/generate-plan
- PUT /trips/{id}/dates
- PUT /trips/{id}/schedule
- PUT /trips/{id}/days/{dayId}/availability
- DELETE /trips/{id}/days/{dayId}/activities/{itemId}
- PUT /trips/{id}/days/{dayId}/activities/{itemId}
- POST /trips/{id}/days/{dayId}/activities
- POST /trips/{id}/days/{dayId}/activities/{itemId}/regenerate

API-Responses laufen über DTOs, damit Datenbankstruktur und externe Darstellung getrennt bleiben.

## 10. Datenbank und Migrationen

PostgreSQL ist die zentrale Wahrheit. Flyway verwaltet das Schema.

Aktuelle Migrationen:

- V1 initiales Schema.
- V2 Seed-Daten für Interessen.
- V3 externe Aktivitätsreferenzen.
- V4 datumsbasierte und interaktive Reiseplanung.
- V5 Standort-Metadaten für Reisen.

Wichtige Constraints:

- Aktivitäten werden über externe Referenzen und Fallback-Logik dedupliziert.
- TripDayActivity-Positionen sind eindeutig pro Tag.
- Beim Drag-and-drop werden temporäre positive Positionen verwendet, um Unique- und Check-Constraint-Probleme zu vermeiden.

## 11. Qualität und Tests

Der aktuelle Testfokus liegt auf Backend-Domänenregeln:

- Kategorie-Mapping.
- Tageszeitprofile und Aktivitätsdauer.
- Planungsservice und Regenerieren.
- Datumsvalidierung.
- Location-Suggestion-Mapping.
- Schedule-Positionslogik.

Frontend-Build wurde erfolgreich geprüft. Backend-Tests sind auf JDK 21 ausgelegt. In eingeschränkten Windows-Sandboxen kann Java durch Path.toRealPath-Zugriffsprobleme blockiert werden; in normaler PowerShell oder per Docker-Fallback sollen die Tests laufen.

## 12. Technische Begründung

Die gewählte Architektur ist für ein Semester-MVP sinnvoll, weil sie:

- externe Datenquellen kapselt,
- API-Key-Geheimnisse im Backend hält,
- Aktivitäten lokal wiederverwendbar speichert,
- Planung nachvollziehbar und ohne KI hält,
- Nutzerentscheidungen nicht still überschreibt,
- spätere Erweiterungen wie Öffnungszeiten, Wetter oder Routing vorbereitet, aber noch nicht erzwingt.

Der aktuelle Stand ist damit kein vollständiges Reiseprodukt, sondern ein solides MVP-Fundament: Auth, Datenimport, lokale Persistenz, regelbasierte Planung und interaktive Nachbearbeitung sind vorhanden.

## 13. Sinnvolle nächste Schritte

Kurzfristig:

- Backend-Tests außerhalb der Sandbox oder mit Docker-Fallback vollständig ausführen.
- Manuelle UI-Prüfung nach dem Refactor: Interview, Autocomplete, Drag-and-drop, Regler, Dark Mode, Löschen und Regenerieren.
- Getrackte Dev-Logs aus dem Git-Index entfernen.

Danach:

- Frontend-Tests für kritische Nutzerflüsse ergänzen.
- Activity-Import fachlich weiter filtern, damit unsinnige POIs reduziert werden.
- Öffnungszeiten oder grobe Tageszeitvalidierung pro Quelle verbessern.
- Optional Kartenansicht oder Distanzlogik ergänzen, ohne direkt komplexes Routing einzubauen.
