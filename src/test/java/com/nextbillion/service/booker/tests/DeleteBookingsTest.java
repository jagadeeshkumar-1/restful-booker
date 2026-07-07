package com.nextbillion.service.booker.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextbillion.core.HttpStatus;
import com.nextbillion.core.TestDataProvider;
import com.nextbillion.service.booker.BookerBaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * DELETE /booking/{id} tests.
 * Covers: positive (delete + 404 confirmation), negative (no auth, bad token,
 *         non-existent ID, double-delete), boundary, and security cases.
 * All test data is loaded from {@code src/test/resources/testdata/delete-bookings.json}.
 */
public class DeleteBookingsTest extends BookerBaseTest {

    private static final JsonNode DATA = TestDataProvider.loadTree("delete-bookings.json");
    private static final JsonNode TOKENS = DATA.path("securityTokens");
    private static final int NON_EXISTENT_ID = TestDataProvider.getInt(DATA, "nonExistentId");

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression", "ExistingDefect"}, description = "DEFECT: DELETE /booking/{id} with valid token returns 201 Created — should return 200 OK or 204 No Content (201 Created is semantically incorrect for a DELETE operation)")
    public void deleteWithValidToken_returns201() {
        String token = cachedToken;
        int id       = bookingClient.createDefaultBooking();

        int actual = bookingClient.deleteBooking(id, token).statusCode();
        Assert.assertFalse(actual == HttpStatus.CREATED,
                "Expected: " + HttpStatus.OK + " (OK) or " + HttpStatus.NO_CONTENT + " (No Content), Actual: " + actual);
    }

    @Test(groups = {"Smoke", "Regression"}, description = "DELETE /booking/{id}: deleted booking is no longer retrievable (GET returns 404)")
    public void deletedBooking_isNoLongerRetrievable() {
        String token = cachedToken;
        int id       = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token).then().statusCode(HttpStatus.CREATED);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(HttpStatus.NOT_FOUND);
    }

    @Test(groups = {"Smoke", "Regression"}, description = "DELETE /booking/{id}: deleted booking does not appear in GET /booking list")
    public void deletedBooking_removedFromList() {
        String token = cachedToken;
        int id       = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token).then().statusCode(HttpStatus.CREATED);

        var ids = bookingClient.getAllBookings()
                .then()
                .statusCode(HttpStatus.OK)
                .extract()
                .jsonPath()
                .getList("bookingid", Integer.class);

        assertThat(ids, not(hasItem(id)));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — No auth
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "DELETE /booking/{id} without any auth token returns 403")
    public void deleteWithoutToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBookingWithoutAuth(id)
                .then()
                .statusCode(HttpStatus.FORBIDDEN)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "DELETE /booking/{id} without auth does NOT delete — booking still retrievable")
    public void deleteWithoutToken_doesNotRemoveBooking() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBookingWithoutAuth(id).then().statusCode(HttpStatus.FORBIDDEN);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(HttpStatus.OK);
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Invalid token
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "DELETE /booking/{id} with a bogus token returns 403")
    public void deleteWithInvalidToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, TOKENS.path("fake").asText())
                .then()
                .statusCode(HttpStatus.FORBIDDEN)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "DELETE /booking/{id} with empty string token returns 403")
    public void deleteWithEmptyToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, TOKENS.path("empty").asText())
                .then()
                .statusCode(HttpStatus.FORBIDDEN)
                .body(equalTo("Forbidden"));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Non-existent / already deleted booking
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: DELETE on non-existent booking returns 405 Method Not Allowed — should return 404 Not Found (the method IS allowed, the resource does not exist)")
    public void deleteNonExistentId_returns405() {
        String token = cachedToken;

        int actual = bookingClient.deleteBooking(NON_EXISTENT_ID, token).statusCode();
        Assert.assertFalse(actual == HttpStatus.METHOD_NOT_ALLOWED,
                "Expected: " + HttpStatus.NOT_FOUND + " (Not Found), Actual: " + actual);
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Second DELETE on already-deleted booking returns 405 Method Not Allowed — should return 404 Not Found")
    public void doubleDelete_returns405() {
        String token = cachedToken;
        int id       = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token).then().statusCode(HttpStatus.CREATED);

        int actual = bookingClient.deleteBooking(id, token).statusCode();
        Assert.assertFalse(actual == HttpStatus.METHOD_NOT_ALLOWED,
                "Expected: " + HttpStatus.NOT_FOUND + " (Not Found), Actual: " + actual);
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "DELETE /booking/0 — boundary ID returns 404 or 405, not a server crash")
    public void deleteZeroId_returnsSafeError() {
        String token = cachedToken;

        bookingClient.deleteBooking(0, token)
                .then()
                .statusCode(HttpStatus.METHOD_NOT_ALLOWED)
                .body(not(emptyString()));
    }

    @Test(groups = {"Regression"}, description = "DELETE /booking/-1 — negative ID returns safe error response")
    public void deleteNegativeId_returnsSafeError() {
        String token = cachedToken;

        bookingClient.deleteBooking(-1, token)
                .then()
                .statusCode(HttpStatus.METHOD_NOT_ALLOWED)
                .body(not(emptyString()));
    }

    // -----------------------------------------------------------------------
    // SECURITY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "DELETE with a crafted token containing special characters is rejected with 403")
    public void deleteWithXssToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, TOKENS.path("xss").asText())
                .then()
                .statusCode(HttpStatus.FORBIDDEN)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "DELETE with SQL injection pattern as token is rejected with 403")
    public void deleteWithSqlToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, TOKENS.path("sql").asText())
                .then()
                .statusCode(HttpStatus.FORBIDDEN)
                .body(equalTo("Forbidden"));
    }
}
