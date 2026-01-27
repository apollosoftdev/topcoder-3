package com.terra.vibe.arena.auth;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for requiring specific roles to access an endpoint.
 * Use on methods or classes to enforce role-based access control.
 *
 * Example usage:
 * <pre>
 * {@code
 * @RoleRequired({"administrator"})
 * @GET
 * @Path("/admin/users")
 * public Response getUsers() { ... }
 * }
 * </pre>
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RoleRequired {

    /**
     * The roles required to access the endpoint.
     * User must have at least one of the specified roles.
     */
    String[] value();
}
