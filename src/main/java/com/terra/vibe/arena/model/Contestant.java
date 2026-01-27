package com.terra.vibe.arena.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Contestant model representing a registered participant in a competition.
 */
public class Contestant {

    private String id;
    private String competitionId;
    private String memberHandle;
    private String memberId;
    private String displayName;
    private boolean authenticated;
    private Instant registeredAt;

    public Contestant() {
        this.id = UUID.randomUUID().toString();
        this.registeredAt = Instant.now();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Contestant contestant = new Contestant();

        public Builder id(String id) {
            contestant.id = id;
            return this;
        }

        public Builder competitionId(String competitionId) {
            contestant.competitionId = competitionId;
            return this;
        }

        public Builder memberHandle(String memberHandle) {
            contestant.memberHandle = memberHandle;
            return this;
        }

        public Builder memberId(String memberId) {
            contestant.memberId = memberId;
            return this;
        }

        public Builder displayName(String displayName) {
            contestant.displayName = displayName;
            return this;
        }

        public Builder authenticated(boolean authenticated) {
            contestant.authenticated = authenticated;
            return this;
        }

        public Builder registeredAt(Instant registeredAt) {
            contestant.registeredAt = registeredAt;
            return this;
        }

        public Contestant build() {
            return contestant;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }
}
