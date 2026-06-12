package de.travelmate.interest;

import jakarta.persistence.*;

@Entity
@Table(name = "interests")
public class InterestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String name;
}
