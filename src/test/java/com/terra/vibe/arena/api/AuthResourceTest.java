package com.terra.vibe.arena.api;

import com.terra.vibe.arena.auth.JwtTokenValidator;
import com.terra.vibe.arena.auth.TokenInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWT Token validation and authentication
 */
class AuthResourceTest {

    private JwtTokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        tokenValidator = new JwtTokenValidator();
    }

    private String createTestToken(long expSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = String.format(
                "{\"handle\":\"testuser\",\"userId\":\"12345\",\"email\":\"test@example.com\"," +
                "\"roles\":[\"Topcoder User\"],\"exp\":%d}",
                expSeconds);
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        return header + "." + payloadBase64 + ".test_signature";
    }

    private String createAdminToken(long expSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = String.format(
                "{\"handle\":\"adminuser\",\"userId\":\"99999\",\"email\":\"admin@example.com\"," +
                "\"roles\":[\"Topcoder User\",\"administrator\"],\"exp\":%d}",
                expSeconds);
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        return header + "." + payloadBase64 + ".test_signature";
    }

    private String createV3Token(long expSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = String.format(
                "{\"https://topcoder.com/claims/handle\":\"v3user\"," +
                "\"https://topcoder.com/claims/userId\":\"67890\"," +
                "\"https://topcoder.com/claims/roles\":[\"Topcoder User\"]," +
                "\"sub\":\"auth0|67890\",\"exp\":%d}", expSeconds);
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        return header + "." + payloadBase64 + ".test_signature";
    }

    @Test
    @DisplayName("TokenValidator validates V2 token")
    void tokenValidator_ValidatesV2Token() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestToken(exp);

        Optional<TokenInfo> result = tokenValidator.validateToken(token);

        assertTrue(result.isPresent());
        TokenInfo tokenInfo = result.get();
        assertEquals("testuser", tokenInfo.getHandle());
        assertEquals("12345", tokenInfo.getUserId());
        assertFalse(tokenInfo.isV3Token());
    }

    @Test
    @DisplayName("TokenValidator validates V3 token")
    void tokenValidator_ValidatesV3Token() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String v3Token = createV3Token(exp);

        Optional<TokenInfo> result = tokenValidator.validateToken(v3Token);

        assertTrue(result.isPresent());
        TokenInfo tokenInfo = result.get();
        assertEquals("v3user", tokenInfo.getHandle());
        assertEquals("67890", tokenInfo.getUserId());
        assertTrue(tokenInfo.isV3Token());
    }

    @Test
    @DisplayName("TokenValidator returns empty for expired token")
    void tokenValidator_ReturnsEmptyForExpiredToken() {
        long exp = System.currentTimeMillis() / 1000 - 3600;
        String token = createTestToken(exp);

        Optional<TokenInfo> result = tokenValidator.validateToken(token);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TokenValidator returns empty for malformed token")
    void tokenValidator_ReturnsEmptyForMalformedToken() {
        Optional<TokenInfo> result = tokenValidator.validateToken("not.a.valid.token");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TokenValidator returns empty for null token")
    void tokenValidator_ReturnsEmptyForNullToken() {
        Optional<TokenInfo> result = tokenValidator.validateToken(null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Admin token has administrator role")
    void tokenValidator_AdminTokenHasAdminRole() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String adminToken = createAdminToken(exp);

        Optional<TokenInfo> result = tokenValidator.validateToken(adminToken);

        assertTrue(result.isPresent());
        TokenInfo tokenInfo = result.get();
        assertTrue(tokenInfo.getRoles().contains("administrator"));
        assertTrue(tokenInfo.hasRole("administrator"));
    }

    @Test
    @DisplayName("Token includes email claim")
    void tokenValidator_IncludesEmail() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestToken(exp);

        Optional<TokenInfo> result = tokenValidator.validateToken(token);

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmail());
    }

    @Test
    @DisplayName("User token does not have admin role")
    void tokenValidator_UserTokenNoAdminRole() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestToken(exp);

        Optional<TokenInfo> result = tokenValidator.validateToken(token);

        assertTrue(result.isPresent());
        assertFalse(result.get().hasRole("administrator"));
    }
}
