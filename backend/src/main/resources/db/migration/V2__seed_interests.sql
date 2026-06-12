INSERT INTO interests(name) VALUES
  ('Kultur'),
  ('Geschichte'),
  ('Natur'),
  ('Food'),
  ('Shopping'),
  ('Nightlife'),
  ('Sport')
ON CONFLICT (name) DO NOTHING;
