package com.nextbillion.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Generic CRUD engine — the single layer all microservice API clients extend.
 * <p>
 * Design intent:
 *   - Stateless: every method takes what it needs (spec, path, body …).
 *   - Type-safe extraction: callers decide the return type via Class<T>.
 *   - Auth-aware: optional token cookie applied when non-null.
 *   - Scalable: any future microservice creates its own thin client that
 *     extends ApiClient and adds service-specific helper methods on top.
 * </p>
 */
public abstract class ApiClient {

    protected final RequestSpecification requestSpec;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected ApiClient(RequestSpecification requestSpec) {
        this.requestSpec = requestSpec;
    }

    private String toJson(Object body) {
        if (body instanceof String) return (String) body;
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    // ------------------------------------------------------------------
    // GET
    // ------------------------------------------------------------------

    public Response get(String path) {
        return given().spec(requestSpec).when().get(path);
    }

    public Response get(String path, Map<String, Object> queryParams) {
        return given().spec(requestSpec).queryParams(queryParams).when().get(path);
    }

    public <T> T get(String path, Class<T> responseType) {
        return get(path).then().statusCode(200).extract().as(responseType);
    }

    // ------------------------------------------------------------------
    // POST
    // ------------------------------------------------------------------

    public Response post(String path, Object body) {
        return given().spec(requestSpec).body(toJson(body)).when().post(path);
    }

    public <T> T post(String path, Object body, int expectedStatus, Class<T> responseType) {
        return given().spec(requestSpec).body(toJson(body)).when().post(path)
                .then().statusCode(expectedStatus).extract().as(responseType);
    }

    // ------------------------------------------------------------------
    // PUT (full update — requires auth token cookie)
    // ------------------------------------------------------------------

    public Response put(String path, Object body, String token) {
        return given().spec(requestSpec).cookie("token", token).body(toJson(body)).when().put(path);
    }

    public <T> T put(String path, Object body, String token, int expectedStatus, Class<T> responseType) {
        return put(path, body, token).then().statusCode(expectedStatus).extract().as(responseType);
    }

    // ------------------------------------------------------------------
    // PATCH (partial update — requires auth token cookie)
    // ------------------------------------------------------------------

    public Response patch(String path, Object body, String token) {
        return given().spec(requestSpec).cookie("token", token).body(toJson(body)).when().patch(path);
    }

    public <T> T patch(String path, Object body, String token, int expectedStatus, Class<T> responseType) {
        return patch(path, body, token).then().statusCode(expectedStatus).extract().as(responseType);
    }

    // ------------------------------------------------------------------
    // DELETE (requires auth token cookie)
    // ------------------------------------------------------------------

    public Response delete(String path, String token) {
        return given().spec(requestSpec).cookie("token", token).when().delete(path);
    }

    public Response deleteWithoutAuth(String path) {
        return given().spec(requestSpec).when().delete(path);
    }

    // ------------------------------------------------------------------
    // Auth (POST without Content-Type override)
    // ------------------------------------------------------------------

    public Response authenticate(String path, Object body) {
        return given().spec(requestSpec).body(toJson(body)).when().post(path);
    }

    // ------------------------------------------------------------------
    // Unauthenticated mutations — for negative/security tests
    // ------------------------------------------------------------------

    public Response putWithoutAuth(String path, Object body) {
        return given().spec(requestSpec).body(toJson(body)).when().put(path);
    }

    public Response patchWithoutAuth(String path, Object body) {
        return given().spec(requestSpec).body(toJson(body)).when().patch(path);
    }

    public Response putWithBadToken(String path, Object body, String badToken) {
        return given().spec(requestSpec).cookie("token", badToken).body(toJson(body)).when().put(path);
    }

    public Response patchWithBadToken(String path, Object body, String badToken) {
        return given().spec(requestSpec).cookie("token", badToken).body(toJson(body)).when().patch(path);
    }
}
