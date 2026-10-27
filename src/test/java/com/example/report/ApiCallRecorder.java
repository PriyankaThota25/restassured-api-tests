package com.example.report;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RestAssured filter that remembers every HTTP call made by the running test, so the HTML
 * report can show it. Headers are not recorded, so bearer tokens never end up in the report.
 */
public class ApiCallRecorder implements Filter {

    public record Call(String method, String url, int status, long millis, String requestBody, String responseBody) {
    }

    private static final Map<String, List<Call>> CALLS = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>();

    static void startTest(String testId) {
        CURRENT_TEST.set(testId);
    }

    static void endTest() {
        CURRENT_TEST.remove();
    }

    static List<Call> callsFor(String testId) {
        return CALLS.getOrDefault(testId, List.of());
    }

    @Override
    public Response filter(FilterableRequestSpecification req, FilterableResponseSpecification res, FilterContext ctx) {
        Response response = ctx.next(req, res);
        String testId = CURRENT_TEST.get();
        if (testId != null) {
            Object body = req.getBody();
            CALLS.computeIfAbsent(testId, k -> new CopyOnWriteArrayList<>()).add(new Call(
                    req.getMethod(), req.getURI(), response.statusCode(), response.time(),
                    body == null ? "" : body.toString(), response.asString()));
        }
        return response;
    }
}
