package de.travelmate.trip;

import de.travelmate.interest.InterestEntity;
import de.travelmate.user.UserEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "trips")
public class TripEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    public UserEntity user;

    @Column(nullable = false)
    public String city;

    public String country;

    @Column(name = "country_code")
    public String countryCode;

    public String state;

    public Double latitude;

    public Double longitude;

    @Column(name = "place_id")
    public String placeId;

    @Column(name = "days_count", nullable = false)
    public int daysCount;

    @Column(name = "start_date")
    public LocalDate startDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Column(name = "preferred_month", length = 7)
    public String preferredMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TripPace pace = TripPace.BALANCED;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_rhythm", nullable = false)
    public DayRhythm dayRhythm = DayRhythm.BALANCED;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_source", nullable = false)
    public DestinationSource destinationSource = DestinationSource.KNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TripStatus status = TripStatus.DRAFT;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    public List<TripDayEntity> days = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "trip_interests",
        joinColumns = @JoinColumn(name = "trip_id"),
        inverseJoinColumns = @JoinColumn(name = "interest_id")
    )
    public Set<InterestEntity> selectedInterests = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
