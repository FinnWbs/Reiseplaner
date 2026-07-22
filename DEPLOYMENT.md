# Kostenloses Deployment

Die empfohlene Architektur ist:

- Frontend: Vercel Hobby
- Backend: Render Free
- PostgreSQL: Neon Free

Der Laptop muss nach dem Deployment nicht eingeschaltet sein. Vercel und Render deployen neue Commits automatisch aus GitHub.

## Voraussetzungen

1. Die gewünschte Branch muss auf GitHub liegen. Für das dauerhafte Production-Deployment sollte sie nach `main` gemergt werden.
2. Der Google-Places-Key muss in der Google Cloud Console deaktiviert oder gelöscht sein. Google Places ist im Projekt fest deaktiviert und darf bei keinem Anbieter als Variable hinterlegt werden.
3. Die Dateien `secrets/publicKey.pem` und `secrets/privateKey.pem` müssen lokal vorhanden sein. Sie bleiben durch `.gitignore` vom Repository ausgeschlossen.

## 1. Neon-PostgreSQL anlegen

1. In Neon ein kostenloses Projekt in einer möglichst nahen europäischen Region anlegen.
2. Im Neon-Dashboard `Connect` öffnen.
3. Die direkte, nicht gepoolte Connection String kopieren. Flyway führt beim Backendstart Datenbankmigrationen aus; hierfür ist die direkte Verbindung die robuste Wahl.
4. Die Adresse sicher verwahren. Sie hat ungefähr dieses Format:

   ```text
   postgresql://USER:PASSWORT@HOST/DATENBANK?sslmode=require
   ```

## 2. Backend über Render Blueprint anlegen

1. Im Render-Dashboard `New` und anschließend `Blueprint` auswählen.
2. Das GitHub-Repository `FinnWbs/Reiseplaner` verbinden.
3. Render erkennt die Datei `render.yaml` im Repository-Stamm.
4. Bei der erstmaligen Einrichtung diese geheimen Variablen eintragen:

   | Variable | Wert |
   |---|---|
   | `DATABASE_URL` | direkte Neon Connection String |
   | `GEOAPIFY_API_KEY` | vorhandener Geoapify-Key |
   | `CORS_ORIGINS` | zunächst `https://example.invalid` |
   | `WIKIMEDIA_USER_AGENT` | z. B. `TravelMate/1.0 (kontakt@deine-domain.de)` |

5. Den Blueprint anlegen. Der erste Start kann noch fehlschlagen, solange die JWT-Dateien fehlen.
6. Im neuen Render-Service `Environment` öffnen und unter `Secret Files` zwei Dateien anlegen:

   | Dateiname | Inhalt aus lokaler Datei |
   |---|---|
   | `publicKey.pem` | `secrets/publicKey.pem` |
   | `privateKey.pem` | `secrets/privateKey.pem` |

   Render stellt sie im Container als `/etc/secrets/publicKey.pem` und `/etc/secrets/privateKey.pem` bereit. Die Inhalte niemals als GitHub-Dateien committen.

7. `Manual Deploy` und `Deploy latest commit` ausführen.
8. Nach erfolgreichem Start muss folgende Adresse JSON mit `{"status":"ok"}` liefern:

   ```text
   https://DEIN-RENDER-SERVICE.onrender.com/health
   ```

## 3. Frontend auf Vercel anlegen

1. In Vercel `Add New Project` wählen und dasselbe GitHub-Repository importieren.
2. Als Root Directory `frontend` auswählen.
3. Framework Preset `Nuxt` verwenden. Build Command und Output Directory automatisch erkennen lassen.
4. Unter Environment Variables für Production setzen:

   ```text
   NUXT_PUBLIC_API_BASE=https://DEIN-RENDER-SERVICE.onrender.com
   ```

5. Deployment starten und die endgültige Vercel-Adresse kopieren, beispielsweise:

   ```text
   https://reiseplaner.vercel.app
   ```

## 4. CORS endgültig setzen

Im Render-Service unter `Environment` den Wert von `CORS_ORIGINS` durch die echte Vercel-Adresse ersetzen:

```text
https://reiseplaner.vercel.app
```

Keine abschließenden Schrägstriche verwenden. Nach dem Speichern startet Render den Service mit der neuen Konfiguration neu.

## 5. Funktionstest

1. `https://DEIN-RENDER-SERVICE.onrender.com/health` antwortet mit Status 200.
2. Die Vercel-Seite öffnet ohne CORS-Fehler.
3. Registrierung und Anmeldung funktionieren.
4. Eine Reise lässt sich anlegen und planen.
5. Aktivitäten lassen sich hinzufügen und löschen.
6. Die Aktivitätsgalerie zeigt ausschließlich lokale Kategoriebilder und kontaktiert Google Places nicht.

## Automatische Folge-Deployments

- Vercel baut das Frontend bei passenden Commits auf der verbundenen Production-Branch neu.
- Render baut das Backend bei Änderungen unter `backend/` neu.
- Flyway führt neue Datenbankmigrationen beim Backendstart automatisch aus.

Bei kostenlosen Instanzen sind Cold Starts normal. Render kann das Backend nach Inaktivität schlafen legen; der erste Aufruf danach dauert entsprechend länger.
