package de.travelmate.interest;

public record InterestDto(Long id, String key, String name) {
    public static InterestDto from(InterestEntity entity) {
        return new InterestDto(entity.id, entity.code, entity.name);
    }
}
