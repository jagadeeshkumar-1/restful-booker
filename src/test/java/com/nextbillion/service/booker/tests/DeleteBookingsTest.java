package com.nextbillion.service.booker.tests;

import com.nextbillion.service.booker.BookerBaseTest;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * DELETE /booking/{id} tests.
 * Covers: positive (delete + 404 confirmation), negative (no auth, bad token,
 *         non-existent ID, double-delete), boundary, and security cases.
 */
public class DeleteBookingsTest extends BookerBaseTest {

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression", "ExistingDefect"}, description = "DEFECT: DELETE /booking/{id} with valid token returns 201 Created — should return 200 OK or 204 No Content (201 Created is semantically incorrect for a DELETE operation)")
    public void deleteWithValidToken_returns201() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token)
                .then()
                .statusCode(201);
    }

    @Test(groups = {"Smoke", "Regression"}, description = "DELETE /booking/{id}: deleted booking is no longer retrievable (GET returns 404)")
    public void deletedBooking_isNoLongerRetrievable() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token).then().statusCode(201);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(404);
    }

    @Test(groups = {"Smoke", "Regression"}, description = "DELETE /booking/{id}: deleted booking does not appear in GET /booking list")
    public void deletedBooking_removedFromList() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token).then().statusCode(201);

        var ids = bookingClient.getAllBookings()
                .then()
                .statusCode(200)
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
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "DELETE /booking/{id} without auth does NOT delete — booking still retrievable")
    public void deleteWithoutToken_doesNotRemoveBooking() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBookingWithoutAuth(id).then().statusCode(403);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200);
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Invalid token
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "DELETE /booking/{id} with a bogus token returns 403")
    public void deleteWithInvalidToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, "completely-fake-token-xyz")
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "DELETE /booking/{id} with empty string token returns 403")
    public void deleteWithEmptyToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, "")
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Non-existent / already deleted booking
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: DELETE on non-existent booking returns 405 Method Not Allowed — should return 404 Not Found (the method IS allowed, the resource does not exist)")
    public void deleteNonExistentId_returns405() {
        String token = bookingClient.getValidToken();

        bookingClient.deleteBooking(999999999, token)
                .then()
                .statusCode(405)
                .body(equalTo("Method Not Allowed"));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: Second DELETE on already-deleted booking returns 405 Method Not Allowed — should return 404 Not Found")
    public void doubleDelete_returns405() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token).then().statusCode(201);

        bookingClient.deleteBooking(id, token)
                .then()
                .statusCode(405)
                .body(equalTo("Method Not Allowed"));
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "DELETE /booking/0 — boundary ID returns 404 or 405, not a server crash")
    public void deleteZeroId_returnsSafeError() {
        String token = bookingClient.getValidToken();

        bookingClient.deleteBooking(0, token)
                .then()
                .statusCode(anyOf(is(400), is(404), is(405)))
                .body(not(emptyString()));
    }

    @Test(groups = {"Regression"}, description = "DELETE /booking/-1 — negative ID returns safe error response")
    public void deleteNegativeId_returnsSafeError() {
        String token = bookingClient.getValidToken();

        bookingClient.deleteBooking(-1, token)
                .then()
                .statusCode(anyOf(is(400), is(404), is(405)))
                .body(not(emptyString()));
    }

    // -----------------------------------------------------------------------
    // SECURITY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "DELETE with a crafted token containing special characters is rejected with 403")
    public void deleteWithXssToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, "<script>alert('xss')</script>")
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "DELETE with SQL injection pattern as token is rejected with 403")
    public void deleteWithSqlToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, "' OR '1'='1")
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }
}
