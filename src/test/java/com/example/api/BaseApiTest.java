package com.example.api;

import com.example.auth.AuthConfig;
import com.example.auth.BearerAuthFilter;
import com.example.report.ApiCallRecorder;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public abstract class BaseApiTest {

    protected static RequestSpecification spec;
    /** Same as spec, plus an auto-refreshing bearer token. Use for protected endpoints. */
    protected static RequestSpecification authSpec;

    @BeforeAll
    static void setUp() throws IOException {
        Properties props = new Properties();
        try (InputStream in = BaseApiTest.class.getResourceAsStream("/config.properties")) {
            props.load(in);
        }
        // Optional per-developer overrides (credentials etc.); git-ignored, absent in CI
        try (InputStream local = BaseApiTest.class.getResourceAsStream("/config.local.properties")) {
            if (local != null) {
                props.load(local);
            }
        }
        // -Dbase.uri=... overrides the file, e.g. for other environments
        String baseUri = System.getProperty("base.uri", props.getProperty("base.uri"));

        spec = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .log(LogDetail.URI)
                .addFilter(new ApiCallRecorder())
                .build();

        authSpec = new RequestSpecBuilder()
                .addRequestSpecification(spec)
                .addFilter(new BearerAuthFilter(AuthConfig.tokenManager(props)))
                .build();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
}
