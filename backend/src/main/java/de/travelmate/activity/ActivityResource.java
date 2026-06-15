package de.travelmate.activity;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;

@Path("/activities")
@RolesAllowed({"USER", "ADMIN"})
public class ActivityResource {
    @Inject
    ActivityRepository activities;

    @Inject
    ActivityImportService importer;

    @GET
    public List<ActivityDto> list(@QueryParam("city") String city) {
        String normalizedCity = ActivityImportService.normalizeCity(city);
        return activities.findByCity(normalizedCity).stream().map(ActivityDto::from).toList();
    }

    @GET
    @Path("/{id}")
    public ActivityDto get(@PathParam("id") Long id) {
        return activities.findByIdOptional(id)
            .map(ActivityDto::from)
            .orElseThrow(() -> new NotFoundException("Aktivitaet nicht gefunden."));
    }

    @POST
    @Path("/import")
    public ActivityImportResponse importCity(@QueryParam("city") String city) {
        return importer.importCity(city);
    }
}
