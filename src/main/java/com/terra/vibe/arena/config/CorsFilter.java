package com.terra.vibe.arena.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * CORS filter for handling cross-origin requests.
 * Allows authentication requests from Topcoder domains.
 */
@Provider
@PreMatching
@Priority(Priorities.HEADER_DECORATOR)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(CorsFilter.class);

    // Allowed origins for CORS
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://accounts.topcoder.com",
            "https://accounts-auth0.topcoder.com",
            "https://accounts.topcoder-dev.com",
            "https://accounts-auth0.topcoder-dev.com",
            "https://www.topcoder.com",
            "https://www.topcoder-dev.com"
    );

    // Allowed headers
    private static final String ALLOWED_HEADERS =
            "Origin, Content-Type, Accept, Authorization, X-Requested-With, Cache-Control";

    // Allowed methods
    private static final String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS, HEAD";

    // Max age for preflight cache (24 hours)
    private static final String MAX_AGE = "86400";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Handle preflight OPTIONS request
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {
            logger.debug("Handling CORS preflight request");
            requestContext.abortWith(
                    Response.ok()
                            .header("Access-Control-Allow-Origin", getAllowedOrigin(requestContext))
                            .header("Access-Control-Allow-Credentials", "true")
                            .header("Access-Control-Allow-Methods", ALLOWED_METHODS)
                            .header("Access-Control-Allow-Headers", ALLOWED_HEADERS)
                            .header("Access-Control-Max-Age", MAX_AGE)
                            .build()
            );
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        String origin = getAllowedOrigin(requestContext);

        // Add CORS headers to response
        responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", ALLOWED_METHODS);
        responseContext.getHeaders().add("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        responseContext.getHeaders().add("Access-Control-Expose-Headers",
                "Content-Type, Authorization, X-Total-Count");
    }

    /**
     * Get the allowed origin for the request.
     * Returns the request origin if it's in the allowed list,
     * or allows localhost for development.
     */
    private String getAllowedOrigin(ContainerRequestContext requestContext) {
        String origin = requestContext.getHeaderString("Origin");

        if (origin == null) {
            return "*";
        }

        // Check if origin is in allowed list
        if (ALLOWED_ORIGINS.contains(origin)) {
            return origin;
        }

        // Allow any topcoder.com subdomain
        if (origin.endsWith(".topcoder.com") || origin.endsWith(".topcoder-dev.com")) {
            return origin;
        }

        // Allow localhost for development
        if (isLocalhost(origin)) {
            return origin;
        }

        logger.debug("Origin not in allowed list: {}", origin);
        return "*";
    }

    /**
     * Check if origin is localhost (for development).
     */
    private boolean isLocalhost(String origin) {
        if (origin == null) {
            return false;
        }

        return origin.startsWith("http://localhost") ||
                origin.startsWith("https://localhost") ||
                origin.startsWith("http://127.0.0.1") ||
                origin.startsWith("https://127.0.0.1");
    }
}
