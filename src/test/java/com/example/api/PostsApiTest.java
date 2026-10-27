package com.example.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class PostsApiTest extends BaseApiTest {

    @Test
    void getPost_returnsExpectedFields() {
        given().spec(spec)
        .when()
            .get("/posts/1")
        .then()
            .statusCode(200)
            .body("id", equalTo(1))
            .body("userId", notNullValue())
            .body("title", notNullValue());
    }

    @Test
    void listPosts_returnsCollection() {
        given().spec(spec)
        .when()
            .get("/posts")
        .then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }

    @Test
    void listPostsByUser_filtersWithQueryParam() {
        given().spec(spec).queryParam("userId", 1)
        .when()
            .get("/posts")
        .then()
            .statusCode(200)
            .body("userId", everyItemEqualTo(1));
    }

    @Test
    void createPost_returns201WithEchoedBody() {
        Map<String, Object> payload = Map.of(
                "title", "foo",
                "body", "bar",
                "userId", 1);

        given().spec(spec).body(payload)
        .when()
            .post("/posts")
        .then()
            .statusCode(201)
            .body("title", equalTo("foo"))
            .body("id", notNullValue());
    }

    @Test
    void getUnknownPost_returns404() {
        given().spec(spec)
        .when()
            .get("/posts/999999")
        .then()
            .statusCode(404);
    }

    private static org.hamcrest.Matcher<Iterable<? extends Integer>> everyItemEqualTo(int value) {
        return org.hamcrest.Matchers.everyItem(equalTo(value));
    }
}
