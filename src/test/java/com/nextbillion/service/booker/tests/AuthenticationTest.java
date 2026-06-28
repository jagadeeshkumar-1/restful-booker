package com.nextbillion.service.booker.tests;

import com.nextbillion.service.booker.BookerBaseTest;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Authentication endpoint tests.
 * Covers: positive, negative, boundary, and security (script-injection) cases.
 */
public class AuthenticationTest extends BookerBaseTest {

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "Valid admin credentials return a non-empty token")
    public void validCredentials_returnsToken() {
        bookingClient.createToken("admin", "password123")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("token", not(emptyString()))
                .body("token.length()", greaterThan(5));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Wrong password returns HTTP 200 with Bad credentials reason — should return 401 Unauthorized per RFC 7235")
    public void wrongPassword_returnsBadCredentials() {
        bookingClient.createToken("admin", "wrongpassword")
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"))
                .body("token", nullValue());
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Wrong username returns HTTP 200 with Bad credentials reason — should return 401 Unauthorized")
    public void wrongUsername_returnsBadCredentials() {
        bookingClient.createToken("notexist", "password123")
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Both wrong credentials return HTTP 200 with Bad credentials reason — should return 401 Unauthorized")
    public void bothWrongCredentials_returnsBadCredentials() {
        bookingClient.createToken("nobody", "nothing")
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Missing password field returns HTTP 200 with Bad credentials reason — should return 400 Bad Request")
    public void missingPassword_returnsBadCredentials() {
        bookingClient.authenticate("/auth", Map.of("username", "admin"))
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Missing username field returns HTTP 200 with Bad credentials reason — should return 400 Bad Request")
    public void missingUsername_returnsBadCredentials() {
        bookingClient.authenticate("/auth", Map.of("password", "password123"))
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Completely empty auth body returns HTTP 200 with Bad credentials reason — should return 400 Bad Request")
    public void emptyBody_returnsBadCredentials() {
        bookingClient.authenticate("/auth", Map.of())
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"));
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "Single character username and password returns Bad credentials")
    public void singleCharCredentials_returnsBadCredentials() {
        bookingClient.createToken("a", "b")
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"));
    }

    @Test(groups = {"Regression"}, description = "Very long username (1000 chars) does not cause server error")
    public void veryLongUsername_doesNotCrashServer() {
        String longName = "a".repeat(1000);
        bookingClient.createToken(longName, "password123")
                .then()
                .statusCode(anyOf(is(200), is(400), is(413)))
                .body(anyOf(
                        containsString("Bad credentials"),
                        containsString("error"),
                        not(emptyString())
                ));
    }

    // -----------------------------------------------------------------------
    // SECURITY — script injection
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "Script injection in username is handled safely — no token issued")
    public void xssInUsername_noTokenIssued() {
        bookingClient.createToken("<script>alert('xss')</script>", "password123")
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"))
                .body("token", nullValue());
    }

    @Test(groups = {"Regression"}, description = "SQL injection pattern in password does not grant a token")
    public void sqlInjectionInPassword_noTokenGranted() {
        bookingClient.createToken("admin", "' OR '1'='1")
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"))
                .body("token", nullValue());
    }

    @Test(groups = {"Regression"}, description = "JSON injection attempt in credentials body does not bypass auth")
    public void jsonInjectionInCredentials_authNotBypassed() {
        bookingClient.createToken("admin\",\"extra\":\"injected", "password123")
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"))
                .body("token", nullValue());
    }
}
