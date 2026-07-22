package de.travelmate.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "catalog_cities",
    uniqueConstraints = @UniqueConstraint(columnNames = {"city_key", "country_code", "source_version"})
)
public class CatalogCityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "city_key", nullable = false, length = 180)
    public String cityKey;

    @Column(name = "city_name", nullable = false, length = 180)
    public String cityName;

    @Column(length = 180)
    public String country;

    @Column(name = "country_code", nullable = false, length = 16)
    public String countryCode = "";

    @Column(name = "wikidata_id", length = 40)
    public String wikidataId;

    public Double latitude;
    public Double longitude;

    @Column(name = "source_version", nullable = false)
    public int sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public CatalogGenerationStatus status = CatalogGenerationStatus.GENERATED;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(name = "generated_at", nullable = false)
    public LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "catalogCity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    public List<CatalogAttractionEntity> attractions = new ArrayList<>();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
