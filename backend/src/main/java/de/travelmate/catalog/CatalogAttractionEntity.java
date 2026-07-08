package de.travelmate.catalog;

import de.travelmate.interest.InterestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "catalog_attractions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"catalog_city_id", "catalog_id"})
)
public class CatalogAttractionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_city_id")
    public CatalogCityEntity catalogCity;

    @Column(name = "catalog_id", nullable = false, length = 220)
    public String catalogId;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String name;

    @Column(name = "wikidata_id", length = 40)
    public String wikidataId;

    @Column(name = "wikipedia_project", length = 80)
    public String wikipediaProject;

    @Column(name = "wikipedia_title", columnDefinition = "TEXT")
    public String wikipediaTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_interest", nullable = false, length = 40)
    public InterestType primaryInterest;

    @Column(nullable = false, length = 120)
    public String category;

    public Double latitude;
    public Double longitude;

    @Column(name = "rank_order", nullable = false)
    public int rank;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "public_attraction_score", nullable = false)
    public double publicAttractionScore;

    @Column(nullable = false)
    public long pageviews;

    @Column(name = "sitelink_count", nullable = false)
    public int sitelinkCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public AttractionCatalogSource source = AttractionCatalogSource.WIKIMEDIA;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    AttractionCatalogEntry toEntry() {
        return new AttractionCatalogEntry(
            catalogId,
            name,
            catalogCity.cityName,
            wikidataId,
            wikipediaProject,
            wikipediaTitle,
            primaryInterest,
            category,
            latitude,
            longitude,
            rank,
            description,
            publicAttractionScore,
            pageviews,
            sitelinkCount,
            source.name()
        );
    }
}
