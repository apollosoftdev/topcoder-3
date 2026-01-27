package com.terra.vibe.arena.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Progress model representing a user's progress checkpoint in a competition.
 */
public class Progress {

    private String id;
    private String competitionId;
    private String checkpointId;
    private String memberHandle;
    private String memberId;
    private Map<String, Object> data;
    private Instant savedAt;

    public Progress() {
        this.id = UUID.randomUUID().toString();
        this.savedAt = Instant.now();
        this.data = new HashMap<>();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Progress progress = new Progress();

        public Builder id(String id) {
            progress.id = id;
            return this;
        }

        public Builder competitionId(String competitionId) {
            progress.competitionId = competitionId;
            return this;
        }

        public Builder checkpointId(String checkpointId) {
            progress.checkpointId = checkpointId;
            return this;
        }

        public Builder memberHandle(String memberHandle) {
            progress.memberHandle = memberHandle;
            return this;
        }

        public Builder memberId(String memberId) {
            progress.memberId = memberId;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            progress.data = data != null ? data : new HashMap<>();
            return this;
        }

        public Builder savedAt(Instant savedAt) {
            progress.savedAt = savedAt;
            return this;
        }

        public Progress build() {
            return progress;
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

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
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

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }
}
