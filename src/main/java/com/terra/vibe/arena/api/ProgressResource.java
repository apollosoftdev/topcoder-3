package com.terra.vibe.arena.api;

import com.terra.vibe.arena.auth.TokenInfo;
import com.terra.vibe.arena.model.Progress;
import com.terra.vibe.arena.service.ProgressService;
import com.terra.vibe.arena.service.ProgressService.ProgressStats;
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
 * REST API endpoints for progress tracking.
 */
@Path("/progress")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProgressResource {

    private static final Logger logger = LoggerFactory.getLogger(ProgressResource.class);

    private final ProgressService progressService = ProgressService.getInstance();

    /**
     * Save progress checkpoint.
     */
    @POST
    @Path("/save")
    public Response saveProgress(Map<String, Object> request, @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            String competitionId = (String) request.get("competitionId");
            String checkpointId = (String) request.get("checkpointId");

            if (competitionId == null || competitionId.isEmpty()) {
                return badRequest("competitionId is required");
            }
            if (checkpointId == null || checkpointId.isEmpty()) {
                return badRequest("checkpointId is required");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.get("data");

            Progress progress = progressService.saveProgress(
                    competitionId, checkpointId, tokenInfo.getHandle(), tokenInfo.getUserId(), data);

            return Response.ok(progressToMap(progress)).build();

        } catch (Exception e) {
            logger.error("Error saving progress", e);
            return serverError("Failed to save progress");
        }
    }

    /**
     * Load progress for a competition.
     */
    @GET
    @Path("/load/{competitionId}")
    public Response loadProgress(@PathParam("competitionId") String competitionId,
                                 @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            List<Progress> progressList = progressService.loadAllProgress(competitionId, tokenInfo.getHandle());

            List<Map<String, Object>> result = progressList.stream()
                    .map(this::progressToMap)
                    .toList();

            return Response.ok(result).build();

        } catch (Exception e) {
            logger.error("Error loading progress", e);
            return serverError("Failed to load progress");
        }
    }

    /**
     * Load specific checkpoint progress.
     */
    @GET
    @Path("/load/{competitionId}/{checkpointId}")
    public Response loadCheckpoint(@PathParam("competitionId") String competitionId,
                                   @PathParam("checkpointId") String checkpointId,
                                   @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            Optional<Progress> progress = progressService.loadProgress(
                    competitionId, checkpointId, tokenInfo.getHandle());

            if (progress.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(errorResponse("Progress not found"))
                        .build();
            }

            return Response.ok(progressToMap(progress.get())).build();

        } catch (Exception e) {
            logger.error("Error loading checkpoint", e);
            return serverError("Failed to load checkpoint");
        }
    }

    /**
     * Get all progress history for current user.
     */
    @GET
    @Path("/history")
    public Response getProgressHistory(@Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            List<Progress> history = progressService.getProgressHistory(tokenInfo.getHandle());

            List<Map<String, Object>> result = history.stream()
                    .map(this::progressToMap)
                    .toList();

            return Response.ok(result).build();

        } catch (Exception e) {
            logger.error("Error getting progress history", e);
            return serverError("Failed to get progress history");
        }
    }

    /**
     * Clear progress for a competition.
     */
    @DELETE
    @Path("/clear/{competitionId}")
    public Response clearProgress(@PathParam("competitionId") String competitionId,
                                  @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            boolean success = progressService.clearProgress(competitionId, tokenInfo.getHandle());

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("competitionId", competitionId);

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Error clearing progress", e);
            return serverError("Failed to clear progress");
        }
    }

    /**
     * Get progress statistics for current user.
     */
    @GET
    @Path("/stats")
    public Response getProgressStats(@Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            ProgressStats stats = progressService.getProgressStats(tokenInfo.getHandle());

            Map<String, Object> response = new HashMap<>();
            response.put("competitionsCount", stats.getCompetitionsCount());
            response.put("checkpointsCount", stats.getCheckpointsCount());
            response.put("isAuthenticated", stats.isAuthenticated());

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Error getting progress stats", e);
            return serverError("Failed to get progress stats");
        }
    }

    private Map<String, Object> progressToMap(Progress p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("competitionId", p.getCompetitionId());
        map.put("checkpointId", p.getCheckpointId());
        map.put("memberHandle", p.getMemberHandle());
        map.put("data", p.getData());
        map.put("savedAt", p.getSavedAt().toString());
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
