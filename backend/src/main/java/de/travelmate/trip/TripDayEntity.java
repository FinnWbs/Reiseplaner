package de.travelmate.trip;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_days", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "day_number"}))
public class TripDayEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id")
    public TripEntity trip;

    @Column(name = "day_number", nullable = false)
    public int dayNumber;

    @Column(name = "travel_date")
    public LocalDate travelDate;

    @Column(name = "available_from", nullable = false)
    public int availableFrom = 540;

    @Column(name = "available_until", nullable = false)
    public int availableUntil = 1200;

    @OneToMany(mappedBy = "tripDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    public List<TripDayActivityEntity> activities = new ArrayList<>();
}
