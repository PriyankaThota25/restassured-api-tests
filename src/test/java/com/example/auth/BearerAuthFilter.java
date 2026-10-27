package com.example.auth;

import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;

/**
 * Adds "Authorization: Bearer <token>" to every request. If the server still answers 401
 * (token revoked or expired early), it fetches a fresh token and retries the request once.
 */
public class BearerAuthFilter implements Filter {

    /** True while the retry is in flight, so a second 401 is returned instead of retried forever. */
    private static final ThreadLocal<Boolean> RETRYING = ThreadLocal.withInitial(() -> false);

    private final TokenManager tokens;

    public BearerAuthFilter(TokenManager tokens) {
        this.tokens = tokens;
    }

    @Override
    public Response filter(FilterableRequestSpecification req,
                           FilterableResponseSpecification res,
                           FilterContext ctx) {
        String token = tokens.getToken();
        setBearer(req, token);
        Response response = ctx.next(req, res);

        if (response.statusCode() == 401 && !RETRYING.get()) {
            tokens.invalidate(token);
            setBearer(req, tokens.getToken());
            // ctx.next() can only be called once per request, so the retry is a fresh request
            // built from the same spec (it runs through this filter again, now flagged as a retry).
            RETRYING.set(true);
            try {
                RequestSpecification retry = RestAssured.given().spec(req);
                // the full URI below already carries the query string
                FilterableRequestSpecification retrySpec = (FilterableRequestSpecification) retry;
                new ArrayList<>(retrySpec.getQueryParams().keySet()).forEach(retrySpec::removeQueryParam);
                response = retry.request(req.getMethod(), req.getURI());
            } finally {
                RETRYING.remove();
            }
        }
        return response;
    }

    private static void setBearer(FilterableRequestSpecification req, String token) {
        req.removeHeader("Authorization");
        req.header("Authorization", "Bearer " + token);
    }
}
