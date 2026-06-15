package de.travelmate.activity;

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

    @Column(name = "external_id", nullable = false)
    public String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ActivitySource source;

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(nullable = false)
    public String city;

    public String category;
    public String subcategory;
    public Double latitude;
    public Double longitude;
    public String address;
    public Double rating;

    @Column(name = "data_quality_score", nullable = false)
    public double dataQualityScore;

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
