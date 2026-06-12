package de.travelmate.admin;

import de.travelmate.activity.ActivityDto;
import de.travelmate.sync.ActivitySyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.List;

@Path("/admin/sync")
@RolesAllowed("ADMIN")
public class AdminSyncResource {
    @Inject
    ActivitySyncService sync;

    @POST
    @Path("/activities")
    public List<ActivityDto> syncActivities(@QueryParam("city") String city) {
        return sync.syncCity(city);
    }
}
