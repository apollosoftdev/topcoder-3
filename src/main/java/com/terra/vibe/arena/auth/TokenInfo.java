package com.terra.vibe.arena.auth;

import java.util.Collections;
import java.util.List;

/**
 * Container for decoded JWT token information.
 * Supports both V2 (HS256) and V3 (RS256) token formats.
 */
public class TokenInfo {

    private final String handle;
    private final String userId;
    private final String email;
    private final List<String> roles;
    private final long expirationTime;
    private final boolean isV3Token;

    private TokenInfo(Builder builder) {
        this.handle = builder.handle;
        this.userId = builder.userId;
        this.email = builder.email;
        this.roles = builder.roles != null ? Collections.unmodifiableList(builder.roles) : Collections.emptyList();
        this.expirationTime = builder.expirationTime;
        this.isV3Token = builder.isV3Token;
    }

    public String getHandle() {
        return handle;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public boolean isV3Token() {
        return isV3Token;
    }

    /**
     * Check if the token is expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() / 1000 >= expirationTime;
    }

    /**
     * Check if the token is expired considering an offset.
     */
    public boolean isExpired(int offsetSeconds) {
        return System.currentTimeMillis() / 1000 >= (expirationTime - offsetSeconds);
    }

    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(String role) {
        if (role == null || roles == null) {
            return false;
        }
        return roles.stream()
                .anyMatch(r -> r.equalsIgnoreCase(role));
    }

    @Override
    public String toString() {
        return "TokenInfo{" +
                "handle='" + handle + '\'' +
                ", userId='" + userId + '\'' +
                ", email='" + (email != null ? "[REDACTED]" : "null") + '\'' +
                ", roles=" + roles +
                ", isV3Token=" + isV3Token +
                '}';
    }

    /**
     * Builder for TokenInfo.
     */
    public static class Builder {
        private String handle;
        private String userId;
        private String email;
        private List<String> roles;
        private long expirationTime;
        private boolean isV3Token;

        public Builder handle(String handle) {
            this.handle = handle;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder expirationTime(long expirationTime) {
            this.expirationTime = expirationTime;
            return this;
        }

        public Builder isV3Token(boolean isV3Token) {
            this.isV3Token = isV3Token;
            return this;
        }

        public TokenInfo build() {
            return new TokenInfo(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
