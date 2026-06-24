package de.travelmate.sync;

import de.travelmate.interest.InterestType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_import_states")
public class ActivityImportStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_code", nullable = false)
    public InterestType interest;

    @Column(name = "import_version", nullable = false)
    public int importVersion;

    @Column(name = "synced_at", nullable = false)
    public LocalDateTime syncedAt;
}
