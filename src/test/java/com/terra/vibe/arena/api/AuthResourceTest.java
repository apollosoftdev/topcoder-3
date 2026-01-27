package com.terra.vibe.arena.api;

import com.terra.vibe.arena.auth.AuthenticationFilter;
import com.terra.vibe.arena.config.CorsFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AuthResource API endpoints
 */
class AuthResourceTest extends JerseyTest {

    @Override
    protected Application configure() {
        return new ResourceConfig()
                .register(AuthResource.class)
                .register(CorsFilter.class)
                .register(AuthenticationFilter.class);
    }

    @BeforeEach
    public void setUpTest() throws Exception {
        super.setUp();
    }

    @AfterEach
    public void tearDownTest() throws Exception {
        super.tearDown();
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

    @Test
    @DisplayName("GET /api/auth/status without token returns 401")
    void getAuthStatus_NoToken_Returns401() {
        Response response = target("/auth/status")
                .request()
                .get();

        assertEquals(401, response.getStatus());

        Map<String, Object> body = response.readEntity(Map.class);
        assertFalse((Boolean) body.get("authenticated"));
        assertNotNull(body.get("error"));
    }

    @Test
    @DisplayName("GET /api/auth/status with valid token returns 200 and member info")
    void getAuthStatus_ValidToken_Returns200() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestToken(exp);

        Response response = target("/auth/status")
                .request()
                .header("Authorization", "Bearer " + token)
                .get();

        assertEquals(200, response.getStatus());

        Map<String, Object> body = response.readEntity(Map.class);
        assertTrue((Boolean) body.get("authenticated"));

        @SuppressWarnings("unchecked")
        Map<String, Object> memberInfo = (Map<String, Object>) body.get("memberInfo");
        assertEquals("testuser", memberInfo.get("handle"));
        assertEquals("12345", memberInfo.get("userId"));
    }

    @Test
    @DisplayName("GET /api/auth/status with expired token returns 401")
    void getAuthStatus_ExpiredToken_Returns401() {
        long exp = System.currentTimeMillis() / 1000 - 3600; // Expired 1 hour ago
        String token = createTestToken(exp);

        Response response = target("/auth/status")
                .request()
                .header("Authorization", "Bearer " + token)
                .get();

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("GET /api/auth/status with malformed token returns 401")
    void getAuthStatus_MalformedToken_Returns401() {
        Response response = target("/auth/status")
                .request()
                .header("Authorization", "Bearer invalid.token")
                .get();

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("GET /api/auth/member/{handle} returns member profile")
    void getMemberProfile_ValidHandle_Returns200() {
        Response response = target("/auth/member/testuser")
                .request()
                .get();

        assertEquals(200, response.getStatus());

        Map<String, Object> profile = response.readEntity(Map.class);
        assertEquals("testuser", profile.get("handle"));
        assertEquals("active", profile.get("status"));
    }

    @Test
    @DisplayName("GET /api/auth/member with empty handle returns 404")
    void getMemberProfile_EmptyHandle_Returns404() {
        Response response = target("/auth/member/")
                .request()
                .get();

        // Empty path segment typically results in 404
        assertEquals(404, response.getStatus());
    }

    @Test
    @DisplayName("CORS preflight request returns proper headers")
    void corsPreflightRequest_ReturnsHeaders() {
        Response response = target("/auth/status")
                .request()
                .header("Origin", "https://accounts.topcoder.com")
                .header("Access-Control-Request-Method", "GET")
                .options();

        assertEquals(200, response.getStatus());
        assertNotNull(response.getHeaderString("Access-Control-Allow-Origin"));
        assertNotNull(response.getHeaderString("Access-Control-Allow-Methods"));
        assertNotNull(response.getHeaderString("Access-Control-Allow-Headers"));
    }

    @Test
    @DisplayName("CORS allows topcoder.com origin")
    void corsAllowsTopcoderOrigin() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestToken(exp);

        Response response = target("/auth/status")
                .request()
                .header("Origin", "https://www.topcoder.com")
                .header("Authorization", "Bearer " + token)
                .get();

        String allowedOrigin = response.getHeaderString("Access-Control-Allow-Origin");
        assertTrue(allowedOrigin != null &&
                (allowedOrigin.equals("https://www.topcoder.com") || allowedOrigin.equals("*")));
    }

    @Test
    @DisplayName("CORS allows localhost for development")
    void corsAllowsLocalhostOrigin() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestToken(exp);

        Response response = target("/auth/status")
                .request()
                .header("Origin", "http://localhost:8080")
                .header("Authorization", "Bearer " + token)
                .get();

        String allowedOrigin = response.getHeaderString("Access-Control-Allow-Origin");
        assertTrue(allowedOrigin != null &&
                (allowedOrigin.equals("http://localhost:8080") || allowedOrigin.equals("*")));
    }

    @Test
    @DisplayName("Auth status includes V3 token flag")
    void getAuthStatus_ReturnsV3TokenFlag() {
        // Create V3 token with namespaced claims
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = String.format(
                "{\"https://topcoder.com/claims/handle\":\"v3user\"," +
                "\"https://topcoder.com/claims/userId\":\"67890\"," +
                "\"https://topcoder.com/claims/roles\":[\"Topcoder User\"]," +
                "\"sub\":\"auth0|67890\",\"exp\":%d}", exp);
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        String v3Token = header + "." + payloadBase64 + ".test_signature";

        Response response = target("/auth/status")
                .request()
                .header("Authorization", "Bearer " + v3Token)
                .get();

        assertEquals(200, response.getStatus());

        Map<String, Object> body = response.readEntity(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> memberInfo = (Map<String, Object>) body.get("memberInfo");
        assertTrue((Boolean) memberInfo.get("isV3Token"));
    }
}
