package com.terra.vibe.arena.api;

import com.terra.vibe.arena.auth.TokenInfo;
import com.terra.vibe.arena.model.Contestant;
import com.terra.vibe.arena.service.ContestantService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API endpoints for contestant registration management.
 */
@Path("/contestants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContestantResource {

    private static final Logger logger = LoggerFactory.getLogger(ContestantResource.class);

    private final ContestantService contestantService = ContestantService.getInstance();

    /**
     * Register a contestant for a competition.
     */
    @POST
    @Path("/register")
    public Response register(Map<String, Object> request, @Context ContainerRequestContext ctx) {
        try {
            String competitionId = (String) request.get("competitionId");
            if (competitionId == null || competitionId.isEmpty()) {
                return badRequest("competitionId is required");
            }

            // Get member info from token if authenticated
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            String memberHandle = tokenInfo != null ? tokenInfo.getHandle() : (String) request.get("memberHandle");
            String memberId = tokenInfo != null ? tokenInfo.getUserId() : (String) request.get("memberId");
            String displayName = (String) request.get("displayName");
            boolean authenticated = tokenInfo != null;

            Contestant contestant = contestantService.register(
                    competitionId, memberHandle, memberId, displayName, authenticated);

            return Response.ok(contestantToMap(contestant)).build();

        } catch (Exception e) {
            logger.error("Error registering contestant", e);
            return serverError("Failed to register contestant");
        }
    }

    /**
     * Get registration status for a competition.
     */
    @GET
    @Path("/registration/{competitionId}")
    public Response getRegistration(@PathParam("competitionId") String competitionId,
                                    @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            Optional<Contestant> registration = contestantService.getRegistration(
                    competitionId, tokenInfo.getHandle(), true);

            if (registration.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(errorResponse("Not registered"))
                        .build();
            }

            return Response.ok(contestantToMap(registration.get())).build();

        } catch (Exception e) {
            logger.error("Error getting registration", e);
            return serverError("Failed to get registration");
        }
    }

    /**
     * Get all registrations for current user.
     */
    @GET
    @Path("/my-registrations")
    public Response getMyRegistrations(@Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            List<Contestant> registrations = contestantService.getRegistrationsByMember(tokenInfo.getHandle());

            List<Map<String, Object>> result = registrations.stream()
                    .map(this::contestantToMap)
                    .toList();

            return Response.ok(result).build();

        } catch (Exception e) {
            logger.error("Error getting registrations", e);
            return serverError("Failed to get registrations");
        }
    }

    /**
     * Unregister from a competition.
     */
    @DELETE
    @Path("/unregister/{competitionId}")
    public Response unregister(@PathParam("competitionId") String competitionId,
                               @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            boolean success = contestantService.unregister(competitionId, tokenInfo.getHandle(), true);

            if (!success) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(errorResponse("Registration not found"))
                        .build();
            }

            return Response.ok(Map.of("success", true)).build();

        } catch (Exception e) {
            logger.error("Error unregistering", e);
            return serverError("Failed to unregister");
        }
    }

    /**
     * Get all contestants for a competition (public).
     */
    @GET
    @Path("/competition/{competitionId}")
    public Response getContestants(@PathParam("competitionId") String competitionId) {
        try {
            List<Contestant> contestants = contestantService.getContestantsByCompetition(competitionId);

            List<Map<String, Object>> result = contestants.stream()
                    .map(this::contestantToMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("competitionId", competitionId);
            response.put("contestants", result);
            response.put("total", contestants.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Error getting contestants", e);
            return serverError("Failed to get contestants");
        }
    }

    private Map<String, Object> contestantToMap(Contestant c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("competitionId", c.getCompetitionId());
        map.put("memberHandle", c.getMemberHandle());
        map.put("displayName", c.getDisplayName());
        map.put("authenticated", c.isAuthenticated());
        map.put("registeredAt", c.getRegisteredAt().toString());
        return map;
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse(message))
                .build();
    }

    private Response serverError(String message) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errorResponse(message))
                .build();
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
