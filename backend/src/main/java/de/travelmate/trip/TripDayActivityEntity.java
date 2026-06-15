package de.travelmate.trip;

import de.travelmate.activity.ActivityEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "trip_day_activities", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_day_id", "position"}))
public class TripDayActivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_day_id")
    public TripDayEntity tripDay;

    @ManyToOne(optional = false)
    @JoinColumn(name = "activity_id")
    public ActivityEntity activity;

    @Column(nullable = false)
    public int position;

    @Column(nullable = false)
    public boolean locked;

    @Column(name = "scheduled_start", nullable = false)
    public int scheduledStart = 540;

    @Column(name = "duration_minutes", nullable = false)
    public int durationMinutes = 90;

    @Column(columnDefinition = "TEXT")
    public String notes;
}
