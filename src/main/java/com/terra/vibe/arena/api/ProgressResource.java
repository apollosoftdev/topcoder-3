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
import java.util.regex.Pattern;

/**
 * REST API endpoints for progress tracking.
 */
@Path("/progress")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProgressResource {

    private static final Logger logger = LoggerFactory.getLogger(ProgressResource.class);

    private final ProgressService progressService = ProgressService.getInstance();

    // Validation patterns to prevent injection and path traversal
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");
    private static final int MAX_DATA_DEPTH = 5;
    private static final int MAX_DATA_SIZE = 10000; // Max characters when serialized

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

            String competitionId = getStringParam(request, "competitionId");
            String checkpointId = getStringParam(request, "checkpointId");

            // Validate required fields
            if (competitionId == null || competitionId.isEmpty()) {
                return badRequest("competitionId is required");
            }
            if (checkpointId == null || checkpointId.isEmpty()) {
                return badRequest("checkpointId is required");
            }

            // Validate format to prevent path traversal and injection
            if (!isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
            }
            if (!isValidId(checkpointId)) {
                return badRequest("Invalid checkpointId format");
            }

            // Safely extract and validate data object
            Map<String, Object> data = extractAndValidateData(request.get("data"));

            Progress progress = progressService.saveProgress(
                    competitionId, checkpointId, tokenInfo.getHandle(), tokenInfo.getUserId(), data);

            return Response.ok(progressToMap(progress)).build();

        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
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

            // Validate format to prevent path traversal
            if (!isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
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

            // Validate format to prevent path traversal
            if (!isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
            }
            if (!isValidId(checkpointId)) {
                return badRequest("Invalid checkpointId format");
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

            // Validate format to prevent path traversal
            if (!isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
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

    /**
     * Validate ID format to prevent path traversal and injection attacks.
     */
    private boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /**
     * Safely extract string parameter from request map.
     */
    private String getStringParam(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    /**
     * Safely extract and validate data object from request.
     * Prevents deserialization attacks by validating structure and depth.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAndValidateData(Object dataObj) {
        if (dataObj == null) {
            return null;
        }

        if (!(dataObj instanceof Map)) {
            throw new IllegalArgumentException("data must be an object");
        }

        Map<String, Object> data = (Map<String, Object>) dataObj;

        // Validate data depth to prevent deeply nested attacks
        validateDataDepth(data, 0);

        // Validate total size
        String serialized = data.toString();
        if (serialized.length() > MAX_DATA_SIZE) {
            throw new IllegalArgumentException("data exceeds maximum size");
        }

        return data;
    }

    /**
     * Recursively validate data depth.
     */
    @SuppressWarnings("unchecked")
    private void validateDataDepth(Object obj, int depth) {
        if (depth > MAX_DATA_DEPTH) {
            throw new IllegalArgumentException("data exceeds maximum nesting depth");
        }

        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                // Validate key format
                if (entry.getKey() == null || entry.getKey().length() > 100) {
                    throw new IllegalArgumentException("Invalid data key");
                }
                validateDataDepth(entry.getValue(), depth + 1);
            }
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.size() > 1000) {
                throw new IllegalArgumentException("data array exceeds maximum size");
            }
            for (Object item : list) {
                validateDataDepth(item, depth + 1);
            }
        }
        // Primitive types (String, Number, Boolean, null) are safe
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
