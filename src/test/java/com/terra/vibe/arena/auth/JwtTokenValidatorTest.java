package com.terra.vibe.arena.auth;

import com.terra.vibe.arena.config.AuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenValidator
 */
class JwtTokenValidatorTest {

    private JwtTokenValidator validator;

    // Test JWT tokens (base64 encoded payloads)
    private String createTestToken(String payload, long expSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        return header + "." + payloadBase64 + ".test_signature";
    }

    @BeforeEach
    void setUp() {
        validator = new JwtTokenValidator();
    }

    @Test
    @DisplayName("Should return empty for null token")
    void validateToken_NullToken_ReturnsEmpty() {
        Optional<TokenInfo> result = validator.validateToken(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty for empty token")
    void validateToken_EmptyToken_ReturnsEmpty() {
        Optional<TokenInfo> result = validator.validateToken("");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty for invalid token format")
    void validateToken_InvalidFormat_ReturnsEmpty() {
        Optional<TokenInfo> result = validator.validateToken("invalid.token");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle Bearer prefix")
    void validateToken_BearerPrefix_Handled() {
        // Create a valid-looking token
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = String.format(
                "{\"handle\":\"testuser\",\"userId\":\"12345\",\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        // Without HS256 secret configured, it will decode without verification
        Optional<TokenInfo> result = validator.validateToken("Bearer " + token);

        // Should successfully extract token info
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getHandle());
    }

    @Test
    @DisplayName("Should decode V2 token format with direct claims")
    void decodeTokenWithoutVerification_V2Format_ExtractsInfo() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = String.format(
                "{\"handle\":\"v2user\",\"userId\":\"54321\",\"email\":\"v2@test.com\"," +
                "\"roles\":[\"Topcoder User\"],\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        Optional<TokenInfo> result = validator.decodeTokenWithoutVerification(token);

        assertTrue(result.isPresent());
        TokenInfo info = result.get();
        assertEquals("v2user", info.getHandle());
        assertEquals("54321", info.getUserId());
        assertEquals("v2@test.com", info.getEmail());
        assertTrue(info.getRoles().contains("Topcoder User"));
        assertFalse(info.isV3Token());
    }

    @Test
    @DisplayName("Should decode V3 token format with namespaced claims")
    void decodeTokenWithoutVerification_V3Format_ExtractsInfo() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = String.format(
                "{\"https://topcoder.com/claims/handle\":\"v3user\"," +
                "\"https://topcoder.com/claims/userId\":\"67890\"," +
                "\"https://topcoder.com/claims/email\":\"v3@test.com\"," +
                "\"https://topcoder.com/claims/roles\":[\"administrator\"]," +
                "\"sub\":\"auth0|67890\",\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        Optional<TokenInfo> result = validator.decodeTokenWithoutVerification(token);

        assertTrue(result.isPresent());
        TokenInfo info = result.get();
        assertEquals("v3user", info.getHandle());
        assertEquals("67890", info.getUserId());
        assertEquals("v3@test.com", info.getEmail());
        assertTrue(info.getRoles().contains("administrator"));
        assertTrue(info.isV3Token());
    }

    @Test
    @DisplayName("Should identify expired token")
    void isTokenExpired_ExpiredToken_ReturnsTrue() {
        long exp = System.currentTimeMillis() / 1000 - 3600; // Expired 1 hour ago
        String payload = String.format("{\"handle\":\"expired\",\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        assertTrue(validator.isTokenExpired(token));
    }

    @Test
    @DisplayName("Should identify valid (non-expired) token")
    void isTokenExpired_ValidToken_ReturnsFalse() {
        long exp = System.currentTimeMillis() / 1000 + 3600; // Expires in 1 hour
        String payload = String.format("{\"handle\":\"valid\",\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        assertFalse(validator.isTokenExpired(token));
    }

    @Test
    @DisplayName("Should check role correctly")
    void tokenInfo_HasRole_WorksCorrectly() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = String.format(
                "{\"handle\":\"admin\",\"roles\":[\"Topcoder User\",\"administrator\"],\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        Optional<TokenInfo> result = validator.decodeTokenWithoutVerification(token);

        assertTrue(result.isPresent());
        TokenInfo info = result.get();
        assertTrue(info.hasRole("administrator"));
        assertTrue(info.hasRole("ADMINISTRATOR")); // Case insensitive
        assertTrue(info.hasRole("Topcoder User"));
        assertFalse(info.hasRole("nonexistent"));
    }

    @Test
    @DisplayName("Should handle token without roles")
    void decodeToken_NoRoles_ReturnsEmptyRolesList() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = String.format("{\"handle\":\"noroles\",\"userId\":\"111\",\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        Optional<TokenInfo> result = validator.decodeTokenWithoutVerification(token);

        assertTrue(result.isPresent());
        assertNotNull(result.get().getRoles());
        assertTrue(result.get().getRoles().isEmpty());
    }

    @Test
    @DisplayName("Should use sub claim as userId when userId not present")
    void decodeToken_NoUserId_UsesSub() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = String.format("{\"handle\":\"subuser\",\"sub\":\"sub123\",\"exp\":%d}", exp);
        String token = createTestToken(payload, exp);

        Optional<TokenInfo> result = validator.decodeTokenWithoutVerification(token);

        assertTrue(result.isPresent());
        assertEquals("sub123", result.get().getUserId());
    }

    @Test
    @DisplayName("TokenInfo should report expired correctly")
    void tokenInfo_IsExpired_WorksCorrectly() {
        TokenInfo expired = TokenInfo.builder()
                .handle("test")
                .expirationTime(System.currentTimeMillis() / 1000 - 100)
                .build();

        TokenInfo valid = TokenInfo.builder()
                .handle("test")
                .expirationTime(System.currentTimeMillis() / 1000 + 3600)
                .build();

        assertTrue(expired.isExpired());
        assertFalse(valid.isExpired());
    }

    @Test
    @DisplayName("TokenInfo should check expiration with offset")
    void tokenInfo_IsExpiredWithOffset_WorksCorrectly() {
        // Token expires in 30 seconds
        TokenInfo nearExpiry = TokenInfo.builder()
                .handle("test")
                .expirationTime(System.currentTimeMillis() / 1000 + 30)
                .build();

        // Without offset, not expired
        assertFalse(nearExpiry.isExpired());

        // With 60 second offset, considered expired
        assertTrue(nearExpiry.isExpired(60));
    }

    @Test
    @DisplayName("TokenInfo toString should not expose email")
    void tokenInfo_ToString_RedactsEmail() {
        TokenInfo info = TokenInfo.builder()
                .handle("test")
                .email("secret@email.com")
                .build();

        String str = info.toString();

        assertFalse(str.contains("secret@email.com"));
        assertTrue(str.contains("[REDACTED]"));
    }
}
