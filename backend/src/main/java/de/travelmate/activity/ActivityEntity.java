package de.travelmate.activity;

import de.travelmate.interest.InterestType;
import de.travelmate.quality.CanonicalCategory;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "activities", uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"}))
public class ActivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "external_id", nullable = false, columnDefinition = "TEXT")
    public String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ActivitySource source;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(nullable = false, length = 120)
    public String city;

    @Column(length = 120)
    public String category;

    @Column(length = 120)
    public String subcategory;
    public Double latitude;
    public Double longitude;
    @Column(length = 500)
    public String address;
    public Double rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_interest")
    public InterestType primaryInterest;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "import_version", nullable = false)
    public int importVersion = 1;

    @Column(name = "data_quality_score", nullable = false)
    public double dataQualityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "canonical_category")
    public CanonicalCategory canonicalCategory;

    @Column(name = "popularity_score", nullable = false)
    public double popularityScore;

    @Column(name = "notability_score", nullable = false)
    public double notabilityScore;

    @Column(name = "quality_score", nullable = false)
    public double qualityScore;

    @Column(name = "category_fit_score", nullable = false)
    public double categoryFitScore;

    @Column(name = "itinerary_fit_score", nullable = false)
    public double itineraryFitScore;

    @Column(name = "final_score", nullable = false)
    public double finalScore;

    @Column(name = "quality_reason_codes", columnDefinition = "TEXT")
    public String qualityReasonCodes;

    @Column(name = "last_synced_at")
    public LocalDateTime lastSyncedAt;

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ActivityInterestEntity> interestScores = new ArrayList<>();

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ActivityExternalRefEntity> externalRefs = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
