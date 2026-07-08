package de.travelmate.activity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "activity_images",
    uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "source", "provider_ref"})
)
public class ActivityImageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "activity_id")
    public ActivityEntity activity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ActivitySource source;

    @Column(name = "provider_ref", nullable = false, columnDefinition = "TEXT")
    public String providerRef;

    @Column(columnDefinition = "TEXT")
    public String url;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String alt;

    @Column(columnDefinition = "TEXT")
    public String credit;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
