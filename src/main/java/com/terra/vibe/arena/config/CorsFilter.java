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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CORS filter for handling cross-origin requests.
 * Allows authentication requests from Topcoder domains.
 *
 * Security: Uses strict origin validation to prevent CORS bypass attacks.
 */
@Provider
@PreMatching
@Priority(Priorities.HEADER_DECORATOR)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(CorsFilter.class);

    // Explicitly allowed origins (exact match)
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://accounts.topcoder.com",
            "https://accounts-auth0.topcoder.com",
            "https://accounts.topcoder-dev.com",
            "https://accounts-auth0.topcoder-dev.com",
            "https://www.topcoder.com",
            "https://www.topcoder-dev.com",
            "https://topcoder.com",
            "https://topcoder-dev.com",
            "https://local.topcoder-dev.com"
    );

    // Pattern for valid topcoder subdomain (strict validation)
    // Only allows alphanumeric and hyphen in subdomain, must end with exact domain
    private static final Pattern TOPCODER_SUBDOMAIN_PATTERN = Pattern.compile(
            "^https://[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.topcoder(-dev)?\\.com$"
    );

    // Allowed headers
    private static final String ALLOWED_HEADERS =
            "Origin, Content-Type, Accept, Authorization, X-Requested-With, Cache-Control";

    // Allowed methods
    private static final String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS, HEAD";

    // Max age for preflight cache (24 hours)
    private static final String MAX_AGE = "86400";

    // Check if running in development mode
    private static final boolean IS_DEVELOPMENT = isDevelopmentMode();

    private static boolean isDevelopmentMode() {
        String env = System.getenv("NODE_ENV");
        if (env == null) {
            env = System.getProperty("node.env", "production");
        }
        return "development".equalsIgnoreCase(env) || "local".equalsIgnoreCase(env);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Handle preflight OPTIONS request
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {
            String origin = getAllowedOrigin(requestContext);
            logger.debug("Handling CORS preflight request from origin: {}", origin);

            // Return 403 for disallowed origins on preflight
            if (origin == null) {
                String requestedOrigin = requestContext.getHeaderString("Origin");
                if (requestedOrigin != null && !requestedOrigin.isEmpty()) {
                    logger.warn("CORS preflight rejected for origin: {}", requestedOrigin);
                    requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                    return;
                }
            }

            Response.ResponseBuilder responseBuilder = Response.ok();

            // Only add CORS headers if origin is allowed
            if (origin != null) {
                responseBuilder
                        .header("Access-Control-Allow-Origin", origin)
                        .header("Access-Control-Allow-Credentials", "true")
                        .header("Access-Control-Allow-Methods", ALLOWED_METHODS)
                        .header("Access-Control-Allow-Headers", ALLOWED_HEADERS)
                        .header("Access-Control-Max-Age", MAX_AGE);
            }

            requestContext.abortWith(responseBuilder.build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        String origin = getAllowedOrigin(requestContext);

        // Only add CORS headers if origin is allowed
        if (origin != null) {
            responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
            responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
            responseContext.getHeaders().add("Access-Control-Allow-Methods", ALLOWED_METHODS);
            responseContext.getHeaders().add("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            responseContext.getHeaders().add("Access-Control-Expose-Headers",
                    "Content-Type, Authorization, X-Total-Count");
        }
    }

    /**
     * Get the allowed origin for the request.
     * Uses strict validation to prevent CORS bypass attacks.
     * Returns null if origin is not allowed (no CORS headers will be set).
     */
    private String getAllowedOrigin(ContainerRequestContext requestContext) {
        String origin = requestContext.getHeaderString("Origin");

        // No origin header means same-origin request
        if (origin == null || origin.isEmpty()) {
            return null;
        }

        // Validate origin format first
        if (!isValidOriginFormat(origin)) {
            logger.warn("CORS: Invalid origin format: {}", sanitizeLogInput(origin));
            return null;
        }

        // Check if origin is in explicit allowed list (exact match)
        if (ALLOWED_ORIGINS.contains(origin)) {
            return origin;
        }

        // Check if origin matches valid topcoder subdomain pattern
        if (TOPCODER_SUBDOMAIN_PATTERN.matcher(origin).matches()) {
            return origin;
        }

        // Allow localhost ONLY in development mode
        if (IS_DEVELOPMENT && isLocalhost(origin)) {
            logger.debug("CORS: Allowing localhost origin in development mode: {}", origin);
            return origin;
        }

        logger.warn("CORS: Origin not allowed: {}", sanitizeLogInput(origin));
        return null;
    }

    /**
     * Validate origin URL format to prevent injection attacks.
     */
    private boolean isValidOriginFormat(String origin) {
        if (origin == null || origin.length() > 256) {
            return false;
        }

        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            // Must have valid scheme and host
            if (scheme == null || host == null) {
                return false;
            }

            // Only allow http/https schemes
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }

            // Host must not be empty and must not contain special characters
            if (host.isEmpty() || host.contains("@") || host.contains(":")) {
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Check if origin is localhost (only allowed in development).
     * Uses strict pattern matching to prevent bypass attacks.
     */
    private boolean isLocalhost(String origin) {
        if (origin == null) {
            return false;
        }

        // Strict localhost patterns with optional port
        return origin.matches("^https?://localhost(:\\d{1,5})?$") ||
                origin.matches("^https?://127\\.0\\.0\\.1(:\\d{1,5})?$");
    }

    /**
     * Sanitize input for logging to prevent log injection.
     */
    private String sanitizeLogInput(String input) {
        if (input == null) {
            return "null";
        }
        // Remove newlines and control characters, truncate long strings
        return input.replaceAll("[\\r\\n\\t]", "")
                .substring(0, Math.min(input.length(), 100));
    }
}
