package de.travelmate.activity;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.List;

@Path("/activities")
@RolesAllowed({"USER", "ADMIN"})
public class ActivityResource {
    @Inject
    ActivityRepository activities;

    @GET
    public List<ActivityDto> list(@QueryParam("city") String city) {
        if (city == null || city.isBlank()) {
            return activities.listAll().stream().map(ActivityDto::from).toList();
        }
        return activities.findByCity(city).stream().map(ActivityDto::from).toList();
    }
}
