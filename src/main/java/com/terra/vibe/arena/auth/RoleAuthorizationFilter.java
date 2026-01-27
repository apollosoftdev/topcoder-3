package com.terra.vibe.arena.auth;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * JAX-RS filter for enforcing role-based access control.
 * Works with @RoleRequired annotation to restrict endpoint access.
 */
@Provider
@RoleRequired({})
@Priority(Priorities.AUTHORIZATION)
public class RoleAuthorizationFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RoleAuthorizationFilter.class);

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Get the @RoleRequired annotation from the method or class
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        RoleRequired roleRequired = method.getAnnotation(RoleRequired.class);
        if (roleRequired == null) {
            roleRequired = resourceClass.getAnnotation(RoleRequired.class);
        }

        if (roleRequired == null || roleRequired.value().length == 0) {
            // No role requirement
            return;
        }

        // Check if user is authenticated
        SecurityContext securityContext = requestContext.getSecurityContext();
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            logger.debug("Access denied: user not authenticated");
            abortUnauthorized(requestContext);
            return;
        }

        // Check if user has required role
        String[] requiredRoles = roleRequired.value();
        boolean hasRole = false;

        for (String role : requiredRoles) {
            if (securityContext.isUserInRole(role)) {
                hasRole = true;
                break;
            }
        }

        if (!hasRole) {
            String username = securityContext.getUserPrincipal().getName();
            logger.debug("Access denied: user {} does not have required role(s): {}",
                    username, String.join(", ", requiredRoles));
            abortForbidden(requestContext);
        }
    }

    /**
     * Abort request with 401 Unauthorized.
     */
    private void abortUnauthorized(ContainerRequestContext requestContext) {
        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\": \"Authentication required\"}")
                        .type("application/json")
                        .build()
        );
    }

    /**
     * Abort request with 403 Forbidden.
     */
    private void abortForbidden(ContainerRequestContext requestContext) {
        requestContext.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\": \"Insufficient permissions\"}")
                        .type("application/json")
                        .build()
        );
    }
}
