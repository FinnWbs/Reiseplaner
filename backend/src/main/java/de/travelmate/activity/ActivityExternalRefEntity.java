package de.travelmate.activity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "activity_external_refs",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"})
)
public class ActivityExternalRefEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "activity_id")
    public ActivityEntity activity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ActivitySource source;

    @Column(name = "external_id", nullable = false, columnDefinition = "TEXT")
    public String externalId;
}
