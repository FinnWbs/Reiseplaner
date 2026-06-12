package de.travelmate.interest;

public record InterestDto(Long id, String name) {
    public static InterestDto from(InterestEntity entity) {
        return new InterestDto(entity.id, entity.name);
    }
}
