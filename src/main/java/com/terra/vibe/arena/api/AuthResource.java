package com.terra.vibe.arena.api;

import com.terra.vibe.arena.auth.JwtTokenValidator;
import com.terra.vibe.arena.auth.TokenInfo;
import com.terra.vibe.arena.config.AuthConfig;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * REST API endpoints for authentication status and member profile.
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);

    // Valid handle pattern: alphanumeric, underscore, hyphen, 1-50 chars
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

    private final JwtTokenValidator tokenValidator;
    private final AuthConfig authConfig;

    public AuthResource() {
        this.tokenValidator = new JwtTokenValidator();
        this.authConfig = AuthConfig.getInstance();
    }

    /**
     * Get authentication status.
     * Returns authenticated user info or 401 if not authenticated.
     *
     * @param headers HTTP headers containing Authorization header or cookies
     * @return Authentication status with member info
     */
    @GET
    @Path("/status")
    public Response getAuthStatus(@Context HttpHeaders headers) {
        try {
            // Extract token from Authorization header or cookie
            String token = extractToken(headers);

            if (token == null || token.isEmpty()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("No authentication token provided"))
                        .build();
            }

            // Validate token
            Optional<TokenInfo> tokenInfoOpt = tokenValidator.validateToken(token);

            if (tokenInfoOpt.isEmpty()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Invalid or expired token"))
                        .build();
            }

            TokenInfo tokenInfo = tokenInfoOpt.get();

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("memberInfo", createMemberInfoMap(tokenInfo));

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Error checking auth status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Authentication service error"))
                    .build();
        }
    }

    /**
     * Get member profile by handle.
     *
     * @param handle The member handle
     * @return Member profile information
     */
    @GET
    @Path("/member/{handle}")
    public Response getMemberProfile(@PathParam("handle") String handle) {
        try {
            if (handle == null || handle.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("Handle is required"))
                        .build();
            }

            // Validate handle format to prevent injection attacks
            if (!HANDLE_PATTERN.matcher(handle).matches()) {
                logger.warn("Invalid handle format attempted: {}", handle.substring(0, Math.min(handle.length(), 20)));
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("Invalid handle format"))
                        .build();
            }

            // In a real implementation, this would fetch from a member service/database
            // For now, return a basic profile structure
            Map<String, Object> profile = new HashMap<>();
            profile.put("handle", handle);
            profile.put("status", "active");

            return Response.ok(profile).build();

        } catch (Exception e) {
            logger.error("Error fetching member profile for handle: {}", handle, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Failed to fetch member profile"))
                    .build();
        }
    }

    /**
     * Extract token from Authorization header or cookies.
     */
    private String extractToken(HttpHeaders headers) {
        // Try Authorization header first
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Try cookies
        Map<String, Cookie> cookies = headers.getCookies();

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
     * Create member info map from TokenInfo.
     */
    private Map<String, Object> createMemberInfoMap(TokenInfo tokenInfo) {
        Map<String, Object> memberInfo = new HashMap<>();
        memberInfo.put("handle", tokenInfo.getHandle());
        memberInfo.put("userId", tokenInfo.getUserId());
        memberInfo.put("roles", tokenInfo.getRoles());
        memberInfo.put("isV3Token", tokenInfo.isV3Token());
        return memberInfo;
    }

    /**
     * Create error response map.
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("authenticated", false);
        error.put("error", message);
        return error;
    }
}
