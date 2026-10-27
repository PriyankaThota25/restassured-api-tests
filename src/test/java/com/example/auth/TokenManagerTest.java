package com.example.auth;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs against an in-process fake auth server + protected API, so no real credentials needed. */
@Tag("auth")
@Tag("regression")
class TokenManagerTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();
    private volatile int tokenStatus = 200;
    private volatile String lastQuery;
    private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/token", ex -> {
            int n = tokenCalls.incrementAndGet();
            String body;
            if (tokenStatus != 200) {
                body = "{\"error\":\"invalid_client\"}";
                send(ex, tokenStatus, body);
                return;
            }
            String token = "token-" + n;
            validTokens.add(token);
            send(ex, 200, "{\"access_token\":\"" + token + "\",\"expires_in\":3600}");
        });
        server.createContext("/api/me", ex -> {
            lastQuery = ex.getRequestURI().getRawQuery();
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            boolean ok = auth != null && auth.startsWith("Bearer ")
                    && validTokens.contains(auth.substring(7));
            send(ex, ok ? 200 : 401, ok ? "{\"user\":\"me\"}" : "{\"error\":\"unauthorized\"}");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        RestAssured.reset();
    }

    private TokenManager manager() {
        return new TokenManager(base + "/oauth/token", "id", "secret", "", Duration.ofSeconds(30), () -> now);
    }

    @Test
    void cachesTokenWhileValid() {
        TokenManager tm = manager();
        String first = tm.getToken();
        now = now.plusSeconds(3000);
        assertEquals(first, tm.getToken());
        assertEquals(1, tokenCalls.get());
    }

    @Test
    void fetchesNewTokenOnceExpired() {
        TokenManager tm = manager();
        String first = tm.getToken();
        now = now.plusSeconds(3600 - 29); // inside the 30s skew window
        String second = tm.getToken();
        assertNotEquals(first, second);
        assertEquals(2, tokenCalls.get());
    }

    @Test
    void on401RefreshesTokenAndRetriesOnce() {
        TokenManager tm = manager();
        given().filter(new BearerAuthFilter(tm)).get(base + "/api/me").then().statusCode(200);

        validTokens.clear(); // server revokes the token before its stated expiry
        given().filter(new BearerAuthFilter(tm)).queryParam("a", "1").get(base + "/api/me")
                .then().statusCode(200);
        assertEquals(2, tokenCalls.get());
        assertEquals("a=1", lastQuery);
    }

    @Test
    void failedTokenRequestGivesClearError() {
        tokenStatus = 401;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> manager().getToken());
        assertEquals(true, e.getMessage().contains("HTTP 401"));
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
