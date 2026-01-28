package com.terra.vibe.arena.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

import com.terra.vibe.arena.api.AuthResource;
import com.terra.vibe.arena.api.ContestantResource;
import com.terra.vibe.arena.api.ProgressResource;
import com.terra.vibe.arena.api.ScoreResource;
import com.terra.vibe.arena.auth.AuthenticationFilter;
import com.terra.vibe.arena.auth.RoleAuthorizationFilter;

/**
 * JAX-RS Application configuration for RESTEasy.
 * Registers all REST resources, filters, and providers.
 */
@ApplicationPath("/api")
public class RestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();

        // REST Resources
        classes.add(AuthResource.class);
        classes.add(ContestantResource.class);
        classes.add(ProgressResource.class);
        classes.add(ScoreResource.class);

        // Filters
        classes.add(CorsFilter.class);
        classes.add(AuthenticationFilter.class);
        classes.add(RoleAuthorizationFilter.class);

        return classes;
    }
}
