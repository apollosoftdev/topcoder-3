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

/**
 * REST API endpoints for score management and leaderboards.
 */
@Path("/scores")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScoreResource {

    private static final Logger logger = LoggerFactory.getLogger(ScoreResource.class);

    private final ScoreService scoreService = ScoreService.getInstance();

    /**
     * Submit a score.
     */
    @POST
    @Path("/submit")
    public Response submitScore(Map<String, Object> request, @Context ContainerRequestContext ctx) {
        try {
            String competitionId = (String) request.get("competitionId");
            if (competitionId == null || competitionId.isEmpty()) {
                return badRequest("competitionId is required");
            }

            Object scoreObj = request.get("score");
            if (scoreObj == null) {
                return badRequest("score is required");
            }

            double scoreValue;
            if (scoreObj instanceof Number) {
                scoreValue = ((Number) scoreObj).doubleValue();
            } else {
                scoreValue = Double.parseDouble(scoreObj.toString());
            }

            // Get member info from token if authenticated
            TokenInfo tokenInfo = (TokenInfo) ctx.getProperty("tokenInfo");
            String memberHandle = tokenInfo != null ? tokenInfo.getHandle() : (String) request.get("memberHandle");
            String memberId = tokenInfo != null ? tokenInfo.getUserId() : (String) request.get("memberId");

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

            Score score = scoreService.submitScore(competitionId, memberHandle, memberId, scoreValue, metadata);

            return Response.ok(scoreToMap(score)).build();

        } catch (NumberFormatException e) {
            return badRequest("Invalid score value");
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
