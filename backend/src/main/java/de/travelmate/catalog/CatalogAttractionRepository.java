package de.travelmate.catalog;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CatalogAttractionRepository implements PanacheRepository<CatalogAttractionEntity> {}
