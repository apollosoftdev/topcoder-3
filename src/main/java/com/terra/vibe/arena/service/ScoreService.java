package com.terra.vibe.arena.service;

import com.terra.vibe.arena.model.Score;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing scores and leaderboards.
 * Uses in-memory storage - can be replaced with database implementation.
 */
public class ScoreService {

    private static final Logger logger = LoggerFactory.getLogger(ScoreService.class);

    // In-memory storage
    private static final Map<String, Score> scores = new ConcurrentHashMap<>();

    private static ScoreService instance;

    private ScoreService() {}

    public static synchronized ScoreService getInstance() {
        if (instance == null) {
            instance = new ScoreService();
        }
        return instance;
    }

    /**
     * Submit a new score.
     */
    public Score submitScore(String competitionId, String memberHandle, String memberId,
                             double scoreValue, Map<String, Object> metadata) {
        Score score = Score.builder()
                .competitionId(competitionId)
                .memberHandle(memberHandle)
                .memberId(memberId)
                .score(scoreValue)
                .metadata(metadata)
                .submittedAt(Instant.now())
                .build();

        scores.put(score.getId(), score);
        logger.info("Score submitted: {} by {} for competition: {}", scoreValue, memberHandle, competitionId);

        return score;
    }

    /**
     * Get score by ID.
     */
    public Optional<Score> getById(String id) {
        return Optional.ofNullable(scores.get(id));
    }

    /**
     * Get leaderboard for a competition (best score per member, sorted descending).
     */
    public List<LeaderboardEntry> getLeaderboard(String competitionId, int limit, int offset) {
        // Group by member, get best score for each
        Map<String, Score> bestScores = new HashMap<>();

        scores.values().stream()
                .filter(s -> competitionId.equals(s.getCompetitionId()))
                .forEach(s -> {
                    String key = s.getMemberHandle() != null ? s.getMemberHandle() : s.getId();
                    Score existing = bestScores.get(key);
                    if (existing == null || s.getScore() > existing.getScore()) {
                        bestScores.put(key, s);
                    }
                });

        // Sort by score descending and apply pagination
        List<Score> sortedScores = bestScores.values().stream()
                .sorted(Comparator.comparingDouble(Score::getScore).reversed())
                .collect(Collectors.toList());

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < sortedScores.size(); i++) {
            if (i >= offset && entries.size() < limit) {
                Score s = sortedScores.get(i);
                entries.add(new LeaderboardEntry(
                        i + 1, // rank
                        s.getMemberHandle() != null ? s.getMemberHandle() : "Anonymous",
                        s.getScore(),
                        s.getSubmittedAt()
                ));
            }
        }

        return entries;
    }

    /**
     * Get all scores for a member.
     */
    public List<Score> getScoresByMember(String memberHandle, String competitionId) {
        return scores.values().stream()
                .filter(s -> memberHandle.equals(s.getMemberHandle()))
                .filter(s -> competitionId == null || competitionId.equals(s.getCompetitionId()))
                .sorted(Comparator.comparing(Score::getSubmittedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get member's rank in a competition.
     */
    public Optional<RankInfo> getMemberRank(String competitionId, String memberHandle) {
        List<LeaderboardEntry> leaderboard = getLeaderboard(competitionId, Integer.MAX_VALUE, 0);

        for (int i = 0; i < leaderboard.size(); i++) {
            if (memberHandle.equals(leaderboard.get(i).getMemberHandle())) {
                LeaderboardEntry entry = leaderboard.get(i);
                return Optional.of(new RankInfo(i + 1, entry.getScore(), leaderboard.size()));
            }
        }

        return Optional.empty();
    }

    /**
     * Get best score for a member in a competition.
     */
    public Optional<Score> getBestScore(String competitionId, String memberHandle) {
        return scores.values().stream()
                .filter(s -> competitionId.equals(s.getCompetitionId()))
                .filter(s -> memberHandle.equals(s.getMemberHandle()))
                .max(Comparator.comparingDouble(Score::getScore));
    }

    /**
     * Clear all data (for testing).
     */
    public void clearAll() {
        scores.clear();
    }

    /**
     * Leaderboard entry DTO.
     */
    public static class LeaderboardEntry {
        private final int rank;
        private final String memberHandle;
        private final double score;
        private final Instant submittedAt;

        public LeaderboardEntry(int rank, String memberHandle, double score, Instant submittedAt) {
            this.rank = rank;
            this.memberHandle = memberHandle;
            this.score = score;
            this.submittedAt = submittedAt;
        }

        public int getRank() { return rank; }
        public String getMemberHandle() { return memberHandle; }
        public double getScore() { return score; }
        public Instant getSubmittedAt() { return submittedAt; }
    }

    /**
     * Rank info DTO.
     */
    public static class RankInfo {
        private final int rank;
        private final double score;
        private final int totalParticipants;

        public RankInfo(int rank, double score, int totalParticipants) {
            this.rank = rank;
            this.score = score;
            this.totalParticipants = totalParticipants;
        }

        public int getRank() { return rank; }
        public double getScore() { return score; }
        public int getTotalParticipants() { return totalParticipants; }
    }
}
