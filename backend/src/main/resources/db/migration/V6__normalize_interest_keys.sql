ALTER TABLE interests ADD COLUMN code VARCHAR(40);

UPDATE interests SET name = 'Sehenswürdigkeiten', code = 'SIGHTSEEING' WHERE name = 'Geschichte';
UPDATE interests SET name = 'Kultur & Museen', code = 'CULTURE' WHERE name = 'Kultur';
UPDATE interests SET name = 'Natur & Outdoor', code = 'NATURE' WHERE name = 'Natur';
UPDATE interests SET name = 'Essen & Cafés', code = 'FOOD' WHERE name = 'Food';
UPDATE interests SET name = 'Shopping & Märkte', code = 'SHOPPING' WHERE name = 'Shopping';
UPDATE interests SET name = 'Nachtleben & Unterhaltung', code = 'NIGHTLIFE' WHERE name = 'Nightlife';
UPDATE interests SET name = 'Abenteuer', code = 'ADVENTURE' WHERE name = 'Sport';

INSERT INTO interests(name, code) VALUES
  ('Geschichte & Architektur', 'HISTORY'),
  ('Entspannung', 'RELAXATION'),
  ('Familienfreundlich', 'FAMILY')
ON CONFLICT (name) DO UPDATE SET code = EXCLUDED.code;

ALTER TABLE interests ALTER COLUMN code SET NOT NULL;
ALTER TABLE interests ADD CONSTRAINT uq_interests_code UNIQUE (code);
