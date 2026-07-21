package de.travelmate.feedback;

import de.travelmate.user.UserEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_entries")
public class FeedbackEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    public UserEntity user;

    @Column(name = "page_url", columnDefinition = "TEXT")
    public String pageUrl;

    @Column(name = "target_label", columnDefinition = "TEXT")
    public String targetLabel;

    @Column(name = "target_selector", columnDefinition = "TEXT")
    public String targetSelector;

    @Column(name = "screenshot_data_url", columnDefinition = "TEXT")
    public String screenshotDataUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String description;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
