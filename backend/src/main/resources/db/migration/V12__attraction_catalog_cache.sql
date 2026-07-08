CREATE TABLE catalog_cities (
  id BIGSERIAL PRIMARY KEY,
  city_key VARCHAR(180) NOT NULL,
  city_name VARCHAR(180) NOT NULL,
  country VARCHAR(180),
  country_code VARCHAR(16) NOT NULL DEFAULT '',
  wikidata_id VARCHAR(40),
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  source_version INTEGER NOT NULL,
  status VARCHAR(40) NOT NULL,
  message TEXT,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_catalog_city_key_country_version UNIQUE (city_key, country_code, source_version)
);

CREATE INDEX idx_catalog_cities_lookup
  ON catalog_cities(city_key, country_code, source_version, generated_at DESC);

CREATE TABLE catalog_attractions (
  id BIGSERIAL PRIMARY KEY,
  catalog_city_id BIGINT NOT NULL REFERENCES catalog_cities(id) ON DELETE CASCADE,
  catalog_id VARCHAR(220) NOT NULL,
  name TEXT NOT NULL,
  wikidata_id VARCHAR(40),
  wikipedia_project VARCHAR(80),
  wikipedia_title TEXT,
  primary_interest VARCHAR(40) NOT NULL,
  category VARCHAR(120) NOT NULL,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  rank_order INTEGER NOT NULL,
  description TEXT,
  public_attraction_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  pageviews BIGINT NOT NULL DEFAULT 0,
  sitelink_count INTEGER NOT NULL DEFAULT 0,
  source VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_catalog_attraction_city_catalog UNIQUE (catalog_city_id, catalog_id)
);

CREATE INDEX idx_catalog_attractions_city_rank
  ON catalog_attractions(catalog_city_id, rank_order);

CREATE INDEX idx_catalog_attractions_wikidata
  ON catalog_attractions(wikidata_id);
