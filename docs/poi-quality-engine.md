# POI Quality Engine

Die POI Quality Engine bewertet Geoapify-Kandidaten vor der Persistenz und liefert stabile Scores fuer die Planung. Sie ersetzt keinen echten Besucherzaehler, sondern kombiniert verfuegbare Signale aus Geoapify, OSM, Wikidata und Wikipedia.

## Score-Komponenten

- `popularityScore`: Wikipedia-Pageviews, Wikidata-Notabilitaet, Quellen-Konsens, Geo-Prominenz und Kategorie-Prior.
- `notabilityScore`: Wikidata/Wikipedia-Referenzen, Sitelink-Anzahl, Bild, Website, Heritage-Status und UNESCO-Hinweise.
- `qualityScore`: Koordinaten, Adresse, Oeffnungszeiten, Website, Beschreibung, Cuisine und Quellen-Konsens.
- `categoryFitScore`: passt der kanonische POI-Typ zum angefragten Interesse.
- `itineraryFitScore`: einfache Reiseplan-Tauglichkeit, aktuell vor allem Distanz und Sonderfall Nachtleben.
- `finalScore`: gewichtete Kombination aus Kategorie-Fit, Popularitaet, Qualitaet, Reiseplan-Fit und Diversitaet abzueglich Penalties.

Alle Score-Gewichte liegen zentral in `PoiQualityWeights`.

## Harte Regeln

- Natur akzeptiert nur echte Natur-/Outdoor-Kategorien wie Parks, Gaerten, Schutzgebiete, Wald-/Gruenflaechen, Straende, Wasserfaelle, Aussichtspunkte und Nationalparks.
- Brunnen und Wasserobjekte koennen nicht als Natur geplant werden.
- Kleine Memorials, Statuen, Plaques und aehnliche Low-Level-Objekte werden ausgeschlossen, wenn keine belastbare Notabilitaet vorliegt.
- Ein bekannter Brunnen kann als Sightseeing akzeptiert werden, wenn Wikidata/Wikipedia/Pageview-Signale stark genug sind.
- Kandidaten ohne Namen oder Koordinaten werden immer ausgeschlossen.

## Planung

Neue Aktivitaeten speichern ihre kanonische Kategorie, Score-Komponenten und Reason-Codes. Die Planung verwendet weiterhin die ausgewogene Interessenquote und sortiert innerhalb der Quote nach dem neuen finalen Score. Bestehende gespeicherte Reiseplan-Eintraege bleiben unveraendert sichtbar.

## Betrieb

Die Engine nutzt keine Live-Abfragen in Tests. Wikipedia-Pageviews sind optional und koennen spaeter durch einen separaten Enrichment-Schritt gesetzt werden. Wenn Signale fehlen, bleiben Scores konservativ und die Planung faellt auf Datenqualitaet, Kategorie-Fit und Distanz zurueck.
