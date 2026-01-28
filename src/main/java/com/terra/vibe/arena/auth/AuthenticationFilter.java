package com.terra.vibe.arena.auth;

import com.terra.vibe.arena.config.AuthConfig;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JAX-RS filter for authenticating requests using JWT tokens.
 * Extracts token from cookie or Authorization header, validates it,
 * and sets the user context.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    // Paths that don't require authentication
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/status",
            "/api/auth/member",
            "/api/health",
            "/api/public"
    );

    private final JwtTokenValidator tokenValidator;
    private final AuthConfig authConfig;

    public AuthenticationFilter() {
        this.tokenValidator = new JwtTokenValidator();
        this.authConfig = AuthConfig.getInstance();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            // Still try to set user context if token is present
            trySetUserContext(requestContext);
            return;
        }

        // Extract token
        String token = extractToken(requestContext);

        if (token == null || token.isEmpty()) {
            // No token, but might be optional authentication
            logger.debug("No authentication token provided for path: {}", path);
            return;
        }

        // Validate token
        Optional<TokenInfo> tokenInfoOpt = tokenValidator.validateToken(token);

        if (tokenInfoOpt.isEmpty()) {
            logger.debug("Invalid token for path: {}", path);
            abortUnauthorized(requestContext, "Invalid or expired token");
            return;
        }

        // Set security context with user info
        TokenInfo tokenInfo = tokenInfoOpt.get();
        setSecurityContext(requestContext, tokenInfo);

        // Store token info for downstream use
        requestContext.setProperty("tokenInfo", tokenInfo);
    }

    /**
     * Try to set user context without requiring authentication.
     */
    private void trySetUserContext(ContainerRequestContext requestContext) {
        String token = extractToken(requestContext);

        if (token != null && !token.isEmpty()) {
            Optional<TokenInfo> tokenInfoOpt = tokenValidator.validateToken(token);
            if (tokenInfoOpt.isPresent()) {
                setSecurityContext(requestContext, tokenInfoOpt.get());
                requestContext.setProperty("tokenInfo", tokenInfoOpt.get());
            }
        }
    }

    /**
     * Check if path is public (doesn't require authentication).
     */
    private boolean isPublicPath(String path) {
        if (path == null) {
            return false;
        }

        // Normalize path
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.startsWith("/api")) {
            path = "/api" + path;
        }

        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath) || path.startsWith(publicPath + "/")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract token from Authorization header or cookies.
     */
    private String extractToken(ContainerRequestContext requestContext) {
        // Try Authorization header first
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Try cookies
        Map<String, Cookie> cookies = requestContext.getCookies();

        // Try v3jwt cookie first
        Cookie v3Cookie = cookies.get(authConfig.getV3CookieName());
        if (v3Cookie != null && v3Cookie.getValue() != null && !v3Cookie.getValue().isEmpty()) {
            return v3Cookie.getValue();
        }

        // Try tcjwt cookie
        Cookie tcCookie = cookies.get(authConfig.getCookieName());
        if (tcCookie != null && tcCookie.getValue() != null && !tcCookie.getValue().isEmpty()) {
            return tcCookie.getValue();
        }

        return null;
    }

    /**
     * Set security context with user info from token.
     */
    private void setSecurityContext(ContainerRequestContext requestContext, TokenInfo tokenInfo) {
        final boolean isSecure = requestContext.getSecurityContext().isSecure();

        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return () -> tokenInfo.getHandle();
            }

            @Override
            public boolean isUserInRole(String role) {
                return tokenInfo.hasRole(role);
            }

            @Override
            public boolean isSecure() {
                return isSecure;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Abort request with 401 Unauthorized.
     * Uses proper JSON serialization to prevent injection.
     */
    private void abortUnauthorized(ContainerRequestContext requestContext, String message) {
        String jsonResponse;
        try {
            jsonResponse = JSON_MAPPER.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            // Fallback to safe hardcoded message
            jsonResponse = "{\"error\":\"Authentication failed\"}";
        }
        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity(jsonResponse)
                        .type("application/json")
                        .build()
        );
    }
}
