package com.nextbillion.service.booker.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextbillion.core.HttpStatus;
import com.nextbillion.core.TestDataProvider;
import com.nextbillion.service.booker.BookerBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Authentication endpoint tests.
 * Covers: positive, negative, boundary, and security (script-injection) cases.
 * All test data is loaded from {@code src/test/resources/testdata/authentication.json}.
 */
public class AuthenticationTest extends BookerBaseTest {

    private static final JsonNode DATA = TestDataProvider.loadTree("authentication.json");

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "Valid admin credentials return a non-empty token")
    public void validCredentials_returnsToken() {
        bookingClient.createToken(adminUsername, adminPassword)
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("token", not(emptyString()))
                .body("token.length()", greaterThan(5));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — wrong credentials (DataProvider)
    // -----------------------------------------------------------------------

    @DataProvider(name = "negativeCredentials")
    public Object[][] negativeCredentials() {
        JsonNode arr = DATA.path("negativeCredentials");
        Object[][] result = new Object[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonNode item = arr.get(i);
            String username = item.has("usernameKey") && "ADMIN".equals(item.path("usernameKey").asText())
                    ? adminUsername : item.path("username").asText();
            String password = item.has("passwordKey") && "ADMIN".equals(item.path("passwordKey").asText())
                    ? adminPassword : item.path("password").asText();
            int buggyStatus = item.path("buggyStatus").asInt();
            int expectedStatus = item.path("expectedStatus").asInt();
            String description = item.path("description").asText();
            result[i] = new Object[]{ item.path("testName").asText(), username, password,
                    buggyStatus, expectedStatus, description };
        }
        return result;
    }

    @Test(groups = {"Regression", "ExistingDefect"}, dataProvider = "negativeCredentials",
          description = "DEFECT: Invalid credentials return 200 — should return 401")
    public void invalidCredentials_failsOnBuggyStatus(String testName, String username, String password,
                                                       int buggyStatus, int expectedStatus, String description) {
        int actual = bookingClient.createToken(username, password).statusCode();
        Assert.assertFalse(actual == buggyStatus,
                description + " | Expected: " + expectedStatus + ", Actual: " + actual);
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — missing fields (DataProvider)
    // -----------------------------------------------------------------------

    @DataProvider(name = "negativeMissingFields")
    public Object[][] negativeMissingFields() {
        JsonNode arr = DATA.path("negativeMissingFields");
        Object[][] result = new Object[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonNode item = arr.get(i);
            Map<String, Object> body = new HashMap<>();
            JsonNode bodyNode = item.path("body");
            bodyNode.fields().forEachRemaining(e -> {
                String val = e.getValue().asText();
                if ("ADMIN_USERNAME".equals(val)) body.put(e.getKey(), adminUsername);
                else if ("ADMIN_PASSWORD".equals(val)) body.put(e.getKey(), adminPassword);
                else body.put(e.getKey(), val);
            });
            int buggyStatus = item.path("buggyStatus").asInt();
            int expectedStatus = item.path("expectedStatus").asInt();
            String description = item.path("description").asText();
            result[i] = new Object[]{ item.path("testName").asText(), body,
                    buggyStatus, expectedStatus, description };
        }
        return result;
    }

    @Test(groups = {"Regression", "ExistingDefect"}, dataProvider = "negativeMissingFields",
          description = "DEFECT: Missing auth fields return 200 — should return 400")
    public void missingAuthFields_failsOnBuggyStatus(String testName, Map<String, Object> body,
                                                      int buggyStatus, int expectedStatus, String description) {
        int actual = bookingClient.createTokenRaw(body).statusCode();
        Assert.assertFalse(actual == buggyStatus,
                description + " | Expected: " + expectedStatus + ", Actual: " + actual);
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "Single character username and password returns Bad credentials")
    public void singleCharCredentials_returnsBadCredentials() {
        JsonNode boundary = DATA.path("boundary").path("singleChar");
        bookingClient.createToken(boundary.path("username").asText(), boundary.path("password").asText())
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"));
    }

    @Test(groups = {"Regression"}, description = "Very long username (1000 chars) does not cause server error")
    public void veryLongUsername_doesNotCrashServer() {
        int repeatCount = DATA.path("boundary").path("longUsernameRepeat").asInt();
        String longName = "a".repeat(repeatCount);
        bookingClient.createToken(longName, adminPassword)
                .then()
                .statusCode(HttpStatus.OK)
                .body("reason", equalTo("Bad credentials"));
    }

    // -----------------------------------------------------------------------
    // SECURITY — script injection (DataProvider)
    // -----------------------------------------------------------------------

    @DataProvider(name = "securityData")
    public Object[][] securityData() {
        JsonNode arr = DATA.path("security");
        Object[][] result = new Object[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonNode item = arr.get(i);
            result[i] = new Object[]{
                    item.path("testName").asText(),
                    item.path("username").asText(),
                    item.path("password").asText(),
                    item.path("description").asText()
            };
        }
        return result;
    }

    @Test(groups = {"Regression"}, dataProvider = "securityData",
          description = "Injection in credentials is handled safely — no token issued")
    public void injectionInCredentials_noTokenIssued(String testName, String username,
                                                      String password, String description) {
        bookingClient.createToken(username, password)
                .then()
                .statusCode(200)
                .body("reason", equalTo("Bad credentials"))
                .body("token", nullValue());
    }
}
