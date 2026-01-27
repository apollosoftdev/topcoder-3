package com.terra.vibe.arena.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Score model representing a contestant's score in a competition.
 */
public class Score {

    private String id;
    private String competitionId;
    private String memberHandle;
    private String memberId;
    private double score;
    private Map<String, Object> metadata;
    private Instant submittedAt;

    public Score() {
        this.id = UUID.randomUUID().toString();
        this.submittedAt = Instant.now();
        this.metadata = new HashMap<>();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Score score = new Score();

        public Builder id(String id) {
            score.id = id;
            return this;
        }

        public Builder competitionId(String competitionId) {
            score.competitionId = competitionId;
            return this;
        }

        public Builder memberHandle(String memberHandle) {
            score.memberHandle = memberHandle;
            return this;
        }

        public Builder memberId(String memberId) {
            score.memberId = memberId;
            return this;
        }

        public Builder score(double scoreValue) {
            score.score = scoreValue;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            score.metadata = metadata != null ? metadata : new HashMap<>();
            return this;
        }

        public Builder submittedAt(Instant submittedAt) {
            score.submittedAt = submittedAt;
            return this;
        }

        public Score build() {
            return score;
        }
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(String competitionId) {
        this.competitionId = competitionId;
    }

    public String getMemberHandle() {
        return memberHandle;
    }

    public void setMemberHandle(String memberHandle) {
        this.memberHandle = memberHandle;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
