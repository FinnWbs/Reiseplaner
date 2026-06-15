package de.travelmate.datasource;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ExternalProviderExceptionMapper implements ExceptionMapper<ExternalProviderException> {
    @Override
    public Response toResponse(ExternalProviderException exception) {
        int status = exception.getResponse().getStatus();
        return Response.status(status)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ErrorResponse(status, exception.getMessage()))
            .build();
    }

    public record ErrorResponse(int status, String message) {}
}
