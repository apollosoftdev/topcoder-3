package com.terra.vibe.arena.service;

import com.terra.vibe.arena.model.Contestant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing contestant registrations.
 * Uses in-memory storage - can be replaced with database implementation.
 */
public class ContestantService {

    private static final Logger logger = LoggerFactory.getLogger(ContestantService.class);

    // In-memory storage: key = competitionId:memberHandle or competitionId:anonymousId
    private static final Map<String, Contestant> contestants = new ConcurrentHashMap<>();

    private static ContestantService instance;

    private ContestantService() {}

    public static synchronized ContestantService getInstance() {
        if (instance == null) {
            instance = new ContestantService();
        }
        return instance;
    }

    /**
     * Register a contestant for a competition.
     */
    public Contestant register(String competitionId, String memberHandle, String memberId,
                               String displayName, boolean authenticated) {
        String key = generateKey(competitionId, memberHandle, authenticated);

        // Check if already registered
        if (contestants.containsKey(key)) {
            logger.info("Contestant already registered: {}", key);
            return contestants.get(key);
        }

        Contestant contestant = Contestant.builder()
                .competitionId(competitionId)
                .memberHandle(memberHandle)
                .memberId(memberId)
                .displayName(displayName != null ? displayName : (memberHandle != null ? memberHandle : "Anonymous"))
                .authenticated(authenticated)
                .registeredAt(Instant.now())
                .build();

        contestants.put(key, contestant);
        logger.info("Registered contestant: {} for competition: {}", contestant.getId(), competitionId);

        return contestant;
    }

    /**
     * Get registration for a specific competition and member.
     */
    public Optional<Contestant> getRegistration(String competitionId, String memberHandle, boolean authenticated) {
        String key = generateKey(competitionId, memberHandle, authenticated);
        return Optional.ofNullable(contestants.get(key));
    }

    /**
     * Get registration by ID.
     */
    public Optional<Contestant> getById(String id) {
        return contestants.values().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst();
    }

    /**
     * Get all registrations for a member.
     */
    public List<Contestant> getRegistrationsByMember(String memberHandle) {
        if (memberHandle == null) {
            return Collections.emptyList();
        }

        return contestants.values().stream()
                .filter(c -> memberHandle.equals(c.getMemberHandle()))
                .sorted(Comparator.comparing(Contestant::getRegisteredAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get all contestants for a competition.
     */
    public List<Contestant> getContestantsByCompetition(String competitionId) {
        return contestants.values().stream()
                .filter(c -> competitionId.equals(c.getCompetitionId()))
                .sorted(Comparator.comparing(Contestant::getRegisteredAt))
                .collect(Collectors.toList());
    }

    /**
     * Unregister a contestant from a competition.
     */
    public boolean unregister(String competitionId, String memberHandle, boolean authenticated) {
        String key = generateKey(competitionId, memberHandle, authenticated);
        Contestant removed = contestants.remove(key);
        if (removed != null) {
            logger.info("Unregistered contestant: {} from competition: {}", removed.getId(), competitionId);
            return true;
        }
        return false;
    }

    /**
     * Generate storage key.
     */
    private String generateKey(String competitionId, String memberHandle, boolean authenticated) {
        if (authenticated && memberHandle != null) {
            return competitionId + ":" + memberHandle;
        } else {
            // For anonymous users, use a session-based key (simplified)
            return competitionId + ":anon:" + (memberHandle != null ? memberHandle : UUID.randomUUID().toString());
        }
    }

    /**
     * Get total count of contestants for a competition.
     */
    public long getContestantCount(String competitionId) {
        return contestants.values().stream()
                .filter(c -> competitionId.equals(c.getCompetitionId()))
                .count();
    }

    /**
     * Clear all data (for testing).
     */
    public void clearAll() {
        contestants.clear();
    }
}
