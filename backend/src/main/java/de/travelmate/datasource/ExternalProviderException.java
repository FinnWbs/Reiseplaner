package de.travelmate.datasource;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class ExternalProviderException extends WebApplicationException {
    public ExternalProviderException(String message) {
        super(message, Response.Status.BAD_GATEWAY);
    }

    public ExternalProviderException(String message, Response.Status status) {
        super(message, status);
    }

    public ExternalProviderException(String message, Throwable cause) {
        super(message, cause, Response.Status.BAD_GATEWAY);
    }
}
