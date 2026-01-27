package com.terra.vibe.arena.service;

import com.terra.vibe.arena.model.Progress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing user progress checkpoints.
 * Uses in-memory storage - can be replaced with database implementation.
 */
public class ProgressService {

    private static final Logger logger = LoggerFactory.getLogger(ProgressService.class);

    // In-memory storage: key = memberHandle:competitionId:checkpointId
    private static final Map<String, Progress> progressData = new ConcurrentHashMap<>();

    private static ProgressService instance;

    private ProgressService() {}

    public static synchronized ProgressService getInstance() {
        if (instance == null) {
            instance = new ProgressService();
        }
        return instance;
    }

    /**
     * Save progress checkpoint.
     */
    public Progress saveProgress(String competitionId, String checkpointId, String memberHandle,
                                  String memberId, Map<String, Object> data) {
        String key = generateKey(memberHandle, competitionId, checkpointId);

        Progress progress = Progress.builder()
                .competitionId(competitionId)
                .checkpointId(checkpointId)
                .memberHandle(memberHandle)
                .memberId(memberId)
                .data(data)
                .savedAt(Instant.now())
                .build();

        // Update if exists, insert if new
        Progress existing = progressData.get(key);
        if (existing != null) {
            progress.setId(existing.getId());
        }

        progressData.put(key, progress);
        logger.info("Progress saved for member: {} competition: {} checkpoint: {}",
                memberHandle, competitionId, checkpointId);

        return progress;
    }

    /**
     * Load progress for a specific checkpoint.
     */
    public Optional<Progress> loadProgress(String competitionId, String checkpointId, String memberHandle) {
        String key = generateKey(memberHandle, competitionId, checkpointId);
        return Optional.ofNullable(progressData.get(key));
    }

    /**
     * Load all progress for a competition and member.
     */
    public List<Progress> loadAllProgress(String competitionId, String memberHandle) {
        String prefix = memberHandle + ":" + competitionId + ":";
        return progressData.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(Progress::getSavedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get all progress history for a member.
     */
    public List<Progress> getProgressHistory(String memberHandle) {
        return progressData.values().stream()
                .filter(p -> memberHandle.equals(p.getMemberHandle()))
                .sorted(Comparator.comparing(Progress::getSavedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Clear progress for a competition.
     */
    public boolean clearProgress(String competitionId, String memberHandle) {
        String prefix = memberHandle + ":" + competitionId + ":";
        List<String> keysToRemove = progressData.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .collect(Collectors.toList());

        keysToRemove.forEach(progressData::remove);
        logger.info("Cleared {} progress entries for member: {} competition: {}",
                keysToRemove.size(), memberHandle, competitionId);

        return !keysToRemove.isEmpty();
    }

    /**
     * Get progress statistics for a member.
     */
    public ProgressStats getProgressStats(String memberHandle) {
        List<Progress> memberProgress = progressData.values().stream()
                .filter(p -> memberHandle.equals(p.getMemberHandle()))
                .collect(Collectors.toList());

        Set<String> competitions = memberProgress.stream()
                .map(Progress::getCompetitionId)
                .collect(Collectors.toSet());

        return new ProgressStats(competitions.size(), memberProgress.size(), true);
    }

    /**
     * Generate storage key.
     */
    private String generateKey(String memberHandle, String competitionId, String checkpointId) {
        return memberHandle + ":" + competitionId + ":" + checkpointId;
    }

    /**
     * Clear all data (for testing).
     */
    public void clearAll() {
        progressData.clear();
    }

    /**
     * Progress statistics DTO.
     */
    public static class ProgressStats {
        private final int competitionsCount;
        private final int checkpointsCount;
        private final boolean isAuthenticated;

        public ProgressStats(int competitionsCount, int checkpointsCount, boolean isAuthenticated) {
            this.competitionsCount = competitionsCount;
            this.checkpointsCount = checkpointsCount;
            this.isAuthenticated = isAuthenticated;
        }

        public int getCompetitionsCount() { return competitionsCount; }
        public int getCheckpointsCount() { return checkpointsCount; }
        public boolean isAuthenticated() { return isAuthenticated; }
    }
}
