package com.terra.vibe.arena.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.terra.vibe.arena.config.AuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * JWT Token Validator supporting both V2 (HS256) and V3 (RS256) token formats.
 */
public class JwtTokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenValidator.class);

    private final AuthConfig config;
    private final JwkProvider jwkProvider;
    private final String claimsNamespace;

    public JwtTokenValidator() {
        this(AuthConfig.getInstance());
    }

    public JwtTokenValidator(AuthConfig config) {
        this.config = config;
        this.claimsNamespace = config.getClaimsNamespace();
        this.jwkProvider = buildJwkProvider(config.getJwksUrl());
    }

    /**
     * Build JWK provider with caching for RS256 token validation.
     */
    private JwkProvider buildJwkProvider(String jwksUrl) {
        try {
            return new JwkProviderBuilder(new URL(jwksUrl))
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to initialize JWK provider", e);
            return null;
        }
    }

    /**
     * Validate a JWT token and extract user information.
     *
     * @param token The JWT token string
     * @return Optional containing TokenInfo if valid, empty if invalid
     */
    public Optional<TokenInfo> validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }

        // Remove "Bearer " prefix if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            // Decode without verification first to check algorithm
            DecodedJWT decodedJWT = JWT.decode(token);
            String algorithm = decodedJWT.getAlgorithm();

            // Validate based on algorithm
            DecodedJWT verifiedJWT;
            if ("RS256".equals(algorithm)) {
                verifiedJWT = validateRS256Token(token, decodedJWT);
            } else if ("HS256".equals(algorithm)) {
                verifiedJWT = validateHS256Token(token);
            } else {
                logger.warn("Unsupported token algorithm: {}", algorithm);
                return Optional.empty();
            }

            if (verifiedJWT == null) {
                return Optional.empty();
            }

            // Extract token info
            return Optional.of(extractTokenInfo(verifiedJWT));

        } catch (JWTVerificationException e) {
            logger.debug("Token verification failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error validating token", e);
            return Optional.empty();
        }
    }

    /**
     * Validate RS256 (V3) token using JWKS.
     */
    private DecodedJWT validateRS256Token(String token, DecodedJWT decodedJWT) {
        if (jwkProvider == null) {
            logger.error("JWK provider not initialized");
            return null;
        }

        try {
            String keyId = decodedJWT.getKeyId();
            Jwk jwk = jwkProvider.get(keyId);
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWTVerifier verifier = JWT.require(algorithm)
                    .acceptLeeway(config.getTokenExpirationOffset())
                    .build();

            return verifier.verify(token);
        } catch (Exception e) {
            logger.debug("RS256 token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validate HS256 (V2) token using secret.
     */
    private DecodedJWT validateHS256Token(String token) {
        String secret = System.getenv("TC_AUTH_SECRET");
        if (secret == null || secret.isEmpty()) {
            secret = System.getProperty("topcoder.auth.secret");
        }

        if (secret == null || secret.isEmpty()) {
            logger.warn("HS256 secret not configured, skipping signature verification");
            // For development, just decode without verification
            return JWT.decode(token);
        }

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm)
                    .acceptLeeway(config.getTokenExpirationOffset())
                    .build();

            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            logger.debug("HS256 token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract TokenInfo from decoded JWT.
     * Supports both V2 (direct claims) and V3 (namespaced claims) formats.
     */
    private TokenInfo extractTokenInfo(DecodedJWT jwt) {
        Map<String, Claim> claims = jwt.getClaims();

        // Check for V3 token format (namespaced claims)
        String handleKey = claimsNamespace + "handle";
        boolean isV3Token = claims.containsKey(handleKey);

        String handle;
        String userId;
        String email;
        List<String> roles;

        if (isV3Token) {
            // V3 token format
            handle = getClaimValue(claims, handleKey);
            userId = getClaimValue(claims, claimsNamespace + "userId");
            if (userId == null) {
                userId = jwt.getSubject();
            }
            email = getClaimValue(claims, claimsNamespace + "email");
            if (email == null) {
                email = getClaimValue(claims, "email");
            }
            roles = getClaimList(claims, claimsNamespace + "roles");
        } else {
            // V2 token format
            handle = getClaimValue(claims, "handle");
            userId = getClaimValue(claims, "userId");
            if (userId == null) {
                userId = jwt.getSubject();
            }
            email = getClaimValue(claims, "email");
            roles = getClaimList(claims, "roles");
        }

        long expirationTime = jwt.getExpiresAt() != null
                ? jwt.getExpiresAt().getTime() / 1000
                : 0;

        return TokenInfo.builder()
                .handle(handle)
                .userId(userId)
                .email(email)
                .roles(roles)
                .expirationTime(expirationTime)
                .isV3Token(isV3Token)
                .build();
    }

    /**
     * Get string value from claim.
     */
    private String getClaimValue(Map<String, Claim> claims, String key) {
        Claim claim = claims.get(key);
        if (claim != null && !claim.isNull()) {
            return claim.asString();
        }
        return null;
    }

    /**
     * Get list value from claim.
     */
    private List<String> getClaimList(Map<String, Claim> claims, String key) {
        Claim claim = claims.get(key);
        if (claim != null && !claim.isNull()) {
            try {
                List<String> list = claim.asList(String.class);
                return list != null ? list : new ArrayList<>();
            } catch (Exception e) {
                // Try as single value
                String value = claim.asString();
                if (value != null) {
                    List<String> list = new ArrayList<>();
                    list.add(value);
                    return list;
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            if (jwt.getExpiresAt() == null) {
                return false;
            }
            long expirationTime = jwt.getExpiresAt().getTime() / 1000;
            long now = System.currentTimeMillis() / 1000;
            return now >= (expirationTime - config.getTokenExpirationOffset());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Decode token without verification (for debugging).
     */
    public Optional<TokenInfo> decodeTokenWithoutVerification(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            DecodedJWT jwt = JWT.decode(token);
            return Optional.of(extractTokenInfo(jwt));
        } catch (Exception e) {
            logger.debug("Failed to decode token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
