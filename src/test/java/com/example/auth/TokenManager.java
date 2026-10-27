package com.example.auth;

import io.restassured.response.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;

/**
 * Fetches, caches and refreshes a bearer token (OAuth2 client-credentials).
 * Thread-safe: parallel tests share one token and only one thread refreshes it.
 */
public class TokenManager {

    private static final long DEFAULT_TTL_SECONDS = 300;

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final Duration skew;
    private final Supplier<Instant> clock;

    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public TokenManager(String tokenUrl, String clientId, String clientSecret, String scope, Duration skew) {
        this(tokenUrl, clientId, clientSecret, scope, skew, Instant::now);
    }

    TokenManager(String tokenUrl, String clientId, String clientSecret, String scope,
                 Duration skew, Supplier<Instant> clock) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.skew = skew;
        this.clock = clock;
    }

    /** Returns a valid token, fetching a new one if none is cached or it is about to expire. */
    public synchronized String getToken() {
        if (accessToken == null || !clock.get().isBefore(expiresAt.minus(skew))) {
            fetchNewToken();
        }
        return accessToken;
    }

    /**
     * Drops the cached token, e.g. after the server answered 401. Only drops it if it is still
     * the rejected one, so concurrent threads don't each trigger a refresh.
     */
    public synchronized void invalidate(String rejectedToken) {
        if (rejectedToken != null && rejectedToken.equals(accessToken)) {
            accessToken = null;
            expiresAt = Instant.EPOCH;
        }
    }

    private void fetchNewToken() {
        var request = given()
                .contentType("application/x-www-form-urlencoded")
                .auth().preemptive().basic(clientId, clientSecret)
                .formParam("grant_type", "client_credentials");
        if (scope != null && !scope.isBlank()) {
            request.formParam("scope", scope);
        }

        Response response = request.post(tokenUrl);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token request failed: HTTP " + response.statusCode()
                    + " " + response.asString());
        }

        String token = response.jsonPath().getString("access_token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Token response has no access_token: " + response.asString());
        }
        Number expiresIn = response.jsonPath().get("expires_in");
        long ttl = expiresIn != null ? expiresIn.longValue() : DEFAULT_TTL_SECONDS;

        this.accessToken = token;
        this.expiresAt = clock.get().plusSeconds(ttl);
    }
}
