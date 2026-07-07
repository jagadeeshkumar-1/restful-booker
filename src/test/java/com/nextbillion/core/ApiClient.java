package com.nextbillion.core;

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

    protected ApiClient(RequestSpecification requestSpec) {
        this.requestSpec = requestSpec;
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

    // ------------------------------------------------------------------
    // POST
    // ------------------------------------------------------------------

    public Response post(String path, Object body) {
        return given().spec(requestSpec).body(body).when().post(path);
    }

    public <T> T post(String path, Object body, int expectedStatus, Class<T> responseType) {
        return given().spec(requestSpec).body(body).when().post(path)
                .then().statusCode(expectedStatus).extract().as(responseType);
    }

    // ------------------------------------------------------------------
    // PUT (full update — requires auth token cookie)
    // ------------------------------------------------------------------

    public Response put(String path, Object body, String token) {
        return given().spec(requestSpec).cookie("token", token).body(body).when().put(path);
    }

    // ------------------------------------------------------------------
    // PATCH (partial update — requires auth token cookie)
    // ------------------------------------------------------------------

    public Response patch(String path, Object body, String token) {
        return given().spec(requestSpec).cookie("token", token).body(body).when().patch(path);
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
    // Unauthenticated mutations — for negative/security tests
    // ------------------------------------------------------------------

    public Response putWithoutAuth(String path, Object body) {
        return given().spec(requestSpec).body(body).when().put(path);
    }

    public Response patchWithoutAuth(String path, Object body) {
        return given().spec(requestSpec).body(body).when().patch(path);
    }

    public Response putWithBadToken(String path, Object body, String badToken) {
        return given().spec(requestSpec).cookie("token", badToken).body(body).when().put(path);
    }

    public Response patchWithBadToken(String path, Object body, String badToken) {
        return given().spec(requestSpec).cookie("token", badToken).body(body).when().patch(path);
    }
}
