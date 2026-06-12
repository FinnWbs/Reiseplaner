package de.travelmate.activity;

import de.travelmate.interest.InterestEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "activity_interests", uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "interest_id"}))
public class ActivityInterestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "activity_id")
    public ActivityEntity activity;

    @ManyToOne(optional = false)
    @JoinColumn(name = "interest_id")
    public InterestEntity interest;

    @Column(nullable = false)
    public int score;
}
