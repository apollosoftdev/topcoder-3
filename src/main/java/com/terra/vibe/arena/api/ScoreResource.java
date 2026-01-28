package com.terra.vibe.arena.api;

import com.terra.vibe.arena.auth.TokenInfo;
import com.terra.vibe.arena.model.Score;
import com.terra.vibe.arena.service.ScoreService;
import com.terra.vibe.arena.service.ScoreService.LeaderboardEntry;
import com.terra.vibe.arena.service.ScoreService.RankInfo;
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
 * REST API endpoints for score management and leaderboards.
 */
@Path("/scores")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScoreResource {

    private static final Logger logger = LoggerFactory.getLogger(ScoreResource.class);

    private final ScoreService scoreService = ScoreService.getInstance();

    // Validation patterns to prevent injection and path traversal
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");
    private static final double MAX_SCORE = 1_000_000_000;
    private static final double MIN_SCORE = -1_000_000_000;

    /**
     * Submit a score.
     */
    @POST
    @Path("/submit")
    public Response submitScore(Map<String, Object> request, @Context ContainerRequestContext ctx) {
        try {
            String competitionId = getStringParam(request, "competitionId");
            if (competitionId == null || competitionId.isEmpty()) {
                return badRequest("competitionId is required");
            }

            // Validate competitionId format
            if (!isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
            }

            Object scoreObj = request.get("score");
            if (scoreObj == null) {
                return badRequest("score is required");
            }

            double scoreValue;
            if (scoreObj instanceof Number) {
                scoreValue = ((Number) scoreObj).doubleValue();
            } else {
                try {
                    scoreValue = Double.parseDouble(scoreObj.toString());
                } catch (NumberFormatException e) {
                    return badRequest("Invalid score value");
                }
            }

            // Validate score range
            if (Double.isNaN(scoreValue) || Double.isInfinite(scoreValue)) {
                return badRequest("Invalid score value");
            }
            if (scoreValue < MIN_SCORE || scoreValue > MAX_SCORE) {
                return badRequest("Score out of valid range");
            }

            // Get member info from token if authenticated
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            String memberHandle = tokenInfo != null ? tokenInfo.getHandle() : getStringParam(request, "memberHandle");
            String memberId = tokenInfo != null ? tokenInfo.getUserId() : getStringParam(request, "memberId");

            // Validate memberHandle if provided
            if (memberHandle != null && !isValidHandle(memberHandle)) {
                return badRequest("Invalid memberHandle format");
            }

            // Safely extract and validate metadata
            Map<String, Object> metadata = extractAndValidateMetadata(request.get("metadata"));

            Score score = scoreService.submitScore(competitionId, memberHandle, memberId, scoreValue, metadata);

            return Response.ok(scoreToMap(score)).build();

        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            logger.error("Error submitting score", e);
            return serverError("Failed to submit score");
        }
    }

    /**
     * Get leaderboard for a competition.
     */
    @GET
    @Path("/leaderboard/{competitionId}")
    public Response getLeaderboard(@PathParam("competitionId") String competitionId,
                                   @QueryParam("limit") @DefaultValue("50") int limit,
                                   @QueryParam("offset") @DefaultValue("0") int offset) {
        try {
            // Validate competitionId format
            if (!isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
            }

            // Validate pagination parameters
            if (limit < 1 || limit > 100) {
                limit = 50;
            }
            if (offset < 0) {
                offset = 0;
            }

            List<LeaderboardEntry> entries = scoreService.getLeaderboard(competitionId, limit, offset);

            List<Map<String, Object>> leaderboard = entries.stream()
                    .map(this::entryToMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("competitionId", competitionId);
            response.put("entries", leaderboard);
            response.put("limit", limit);
            response.put("offset", offset);

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Error getting leaderboard", e);
            return serverError("Failed to get leaderboard");
        }
    }

    /**
     * Get score history for current user.
     */
    @GET
    @Path("/my-scores")
    public Response getMyScores(@QueryParam("competitionId") String competitionId,
                                @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            // Validate competitionId if provided
            if (competitionId != null && !competitionId.isEmpty() && !isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
            }

            List<Score> scores = scoreService.getScoresByMember(tokenInfo.getHandle(), competitionId);

            List<Map<String, Object>> result = scores.stream()
                    .map(this::scoreToMap)
                    .toList();

            return Response.ok(result).build();

        } catch (Exception e) {
            logger.error("Error getting scores", e);
            return serverError("Failed to get scores");
        }
    }

    /**
     * Get scores by member handle.
     */
    @GET
    @Path("/by-handle/{memberHandle}")
    public Response getScoresByHandle(@PathParam("memberHandle") String memberHandle,
                                      @QueryParam("competitionId") String competitionId) {
        try {
            // Validate memberHandle format
            if (!isValidHandle(memberHandle)) {
                return badRequest("Invalid memberHandle format");
            }

            // Validate competitionId if provided
            if (competitionId != null && !competitionId.isEmpty() && !isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
            }

            List<Score> scores = scoreService.getScoresByMember(memberHandle, competitionId);

            if (scores.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(errorResponse("No scores found"))
                        .build();
            }

            List<Map<String, Object>> result = scores.stream()
                    .map(this::scoreToMap)
                    .toList();

            return Response.ok(result).build();

        } catch (Exception e) {
            logger.error("Error getting scores by handle", e);
            return serverError("Failed to get scores");
        }
    }

    /**
     * Get current user's rank in a competition.
     */
    @GET
    @Path("/my-rank/{competitionId}")
    public Response getMyRank(@PathParam("competitionId") String competitionId,
                              @Context ContainerRequestContext ctx) {
        try {
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            if (tokenInfo == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse("Authentication required"))
                        .build();
            }

            // Validate competitionId format
            if (!isValidId(competitionId)) {
                return badRequest("Invalid competitionId format");
            }

            Optional<RankInfo> rankInfo = scoreService.getMemberRank(competitionId, tokenInfo.getHandle());

            if (rankInfo.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(errorResponse("Not ranked"))
                        .build();
            }

            RankInfo info = rankInfo.get();
            Map<String, Object> response = new HashMap<>();
            response.put("rank", info.getRank());
            response.put("score", info.getScore());
            response.put("totalParticipants", info.getTotalParticipants());
            response.put("memberHandle", tokenInfo.getHandle());

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Error getting rank", e);
            return serverError("Failed to get rank");
        }
    }

    /**
     * Validate ID format to prevent path traversal and injection attacks.
     */
    private boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /**
     * Validate handle format.
     */
    private boolean isValidHandle(String handle) {
        return handle != null && HANDLE_PATTERN.matcher(handle).matches();
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
     * Safely extract and validate metadata object from request.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAndValidateMetadata(Object metadataObj) {
        if (metadataObj == null) {
            return null;
        }

        if (!(metadataObj instanceof Map)) {
            throw new IllegalArgumentException("metadata must be an object");
        }

        Map<String, Object> metadata = (Map<String, Object>) metadataObj;

        // Validate metadata size
        if (metadata.size() > 50) {
            throw new IllegalArgumentException("metadata has too many keys");
        }

        // Validate metadata keys
        for (String key : metadata.keySet()) {
            if (key == null || key.length() > 100) {
                throw new IllegalArgumentException("Invalid metadata key");
            }
        }

        return metadata;
    }

    private Map<String, Object> scoreToMap(Score s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("competitionId", s.getCompetitionId());
        map.put("memberHandle", s.getMemberHandle());
        map.put("score", s.getScore());
        map.put("metadata", s.getMetadata());
        map.put("submittedAt", s.getSubmittedAt().toString());
        return map;
    }

    private Map<String, Object> entryToMap(LeaderboardEntry e) {
        Map<String, Object> map = new HashMap<>();
        map.put("rank", e.getRank());
        map.put("memberHandle", e.getMemberHandle());
        map.put("score", e.getScore());
        map.put("submittedAt", e.getSubmittedAt().toString());
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
