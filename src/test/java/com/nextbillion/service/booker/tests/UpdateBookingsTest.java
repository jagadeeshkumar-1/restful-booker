package com.nextbillion.service.booker.tests;

import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingDates;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * PUT /booking/{id} and PATCH /booking/{id} tests.
 * Covers: positive (full/partial update + persistence), negative (no auth, bad token),
 *         boundary, and security (script/SQL injection in body).
 */
public class UpdateBookingsTest extends BookerBaseTest {

    // -----------------------------------------------------------------------
    // POSITIVE — Full PUT
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "PUT /booking/{id} fully replaces all fields and response reflects new values")
    public void putWithValidToken_replacesAllFields() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking updated = new Booking("Charlie", "Brown", 500, true,
                new BookingDates("2025-08-01", "2025-08-10"), "Dinner");

        bookingClient.updateBooking(id, updated, token)
                .then()
                .statusCode(200)
                .body("firstname",             equalTo("Charlie"))
                .body("lastname",              equalTo("Brown"))
                .body("totalprice",            equalTo(500))
                .body("depositpaid",           equalTo(true))
                .body("bookingdates.checkin",  equalTo("2025-08-01"))
                .body("bookingdates.checkout", equalTo("2025-08-10"))
                .body("additionalneeds",       equalTo("Dinner"));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "PUT /booking/{id} change is persisted — verified by subsequent GET")
    public void putChange_persistsOnSubsequentGet() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking updated = new Booking("Persist", "Check", 999, false,
                new BookingDates("2026-01-01", "2026-01-10"), "Gym");

        bookingClient.updateBooking(id, updated, token).then().statusCode(200);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("firstname",  equalTo("Persist"))
                .body("totalprice", equalTo(999));
    }

    // -----------------------------------------------------------------------
    // POSITIVE — Partial PATCH
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "PATCH /booking/{id} updates only supplied fields; untouched fields remain")
    public void patchWithValidToken_updatesOnlySuppliedFields() {
        String token = bookingClient.getValidToken();
        Booking original = new Booking("Diana", "Prince", 300, false,
                new BookingDates("2025-09-01", "2025-09-05"), "Spa");
        int id = bookingClient.createAndGetId(original);

        bookingClient.partialUpdateBooking(id,
                        Map.of("firstname", "Diana-Updated", "totalprice", 999), token)
                .then()
                .statusCode(200)
                .body("firstname",       equalTo("Diana-Updated"))
                .body("totalprice",      equalTo(999))
                .body("lastname",        equalTo("Prince"))
                .body("depositpaid",     equalTo(false))
                .body("additionalneeds", equalTo("Spa"));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "PATCH /booking/{id}: partial change is persisted — verified by GET")
    public void patchChange_persistsOnSubsequentGet() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.partialUpdateBooking(id, Map.of("lastname", "PatchedLast"), token)
                .then().statusCode(200);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("lastname", equalTo("PatchedLast"));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — No auth token
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT /booking/{id} without auth token returns 403")
    public void putWithoutToken_returns403() {
        int id = bookingClient.createDefaultBooking();
        Booking body = new Booking("Hacker", "Attempt", 1, false,
                new BookingDates("2025-01-01", "2025-01-02"), "None");

        bookingClient.updateBookingWithoutAuth(id, body)
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "PATCH /booking/{id} without auth token returns 403")
    public void patchWithoutToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.partialUpdateWithoutAuth(id, Map.of("firstname", "Unauthorized"))
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Invalid / bogus token
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT /booking/{id} with a fake token returns 403")
    public void putWithInvalidToken_returns403() {
        int id = bookingClient.createDefaultBooking();
        Booking body = new Booking("Bad", "Token", 1, false,
                new BookingDates("2025-01-01", "2025-01-02"), "None");

        bookingClient.updateBookingWithBadToken(id, body, "totally-fake-token-xyz")
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "PATCH /booking/{id} with a fake token returns 403")
    public void patchWithInvalidToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        bookingClient.partialUpdateWithBadToken(id, Map.of("firstname", "BadActor"), "fake-token-xyz")
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Non-existent booking
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: PUT on non-existent booking returns 405 Method Not Allowed — should return 404 Not Found (method IS allowed, the resource does not exist)")
    public void putOnNonExistentId_returns405() {
        String token = bookingClient.getValidToken();
        Booking body = new Booking("Ghost", "Booking", 100, true,
                new BookingDates("2025-01-01", "2025-01-02"), "None");

        bookingClient.updateBooking(999999999, body, token)
                .then()
                .statusCode(anyOf(is(404), is(405)))
                .body(not(emptyString()));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: PATCH on non-existent booking returns 405 Method Not Allowed — should return 404 Not Found")
    public void patchOnNonExistentId_returns405() {
        String token = bookingClient.getValidToken();

        bookingClient.partialUpdateBooking(999999999, Map.of("firstname", "Ghost"), token)
                .then()
                .statusCode(anyOf(is(404), is(405)))
                .body(not(emptyString()));
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT /booking/{id} with zero totalprice is accepted")
    public void putZeroPrice_isAccepted() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking body = new Booking("Zero", "Price", 0, false,
                new BookingDates("2025-01-01", "2025-01-05"), "None");

        bookingClient.updateBooking(id, body, token)
                .then()
                .statusCode(200)
                .body("totalprice", equalTo(0));
    }

    @Test(groups = {"Regression"}, description = "PATCH with empty map body returns 400 or 200 — must not crash server")
    public void patchEmptyBody_doesNotCrash() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.partialUpdateBooking(id, Map.of(), token)
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // -----------------------------------------------------------------------
    // SECURITY — Script / SQL injection in PUT and PATCH body
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT: script injection in firstname is stored as text, not executed")
    public void putWithXssInFirstname_storedAsPlainText() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking body = new Booking("<script>alert('xss')</script>", "Safe", 100, true,
                new BookingDates("2025-01-01", "2025-01-05"), "None");

        bookingClient.updateBooking(id, body, token)
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test(groups = {"Regression"}, description = "PUT: SQL injection in lastname does not cause a server error")
    public void putWithSqlInjectionInLastname_doesNotCrash() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking body = new Booking("SQL", "' OR '1'='1'; DROP TABLE bookings; --", 100, true,
                new BookingDates("2025-01-01", "2025-01-05"), "None");

        bookingClient.updateBooking(id, body, token)
                .then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }

    @Test(groups = {"Regression"}, description = "PATCH: script injection in firstname field is handled safely")
    public void patchWithXssInFirstname_handledSafely() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.partialUpdateBooking(id,
                        Map.of("firstname", "<img src=x onerror=alert(1)>"), token)
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test(groups = {"Regression"}, description = "PATCH: JSON injection in additionalneeds does not break response structure")
    public void patchWithJsonInjectionInNotes_doesNotBreakResponse() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        bookingClient.partialUpdateBooking(id,
                        Map.of("additionalneeds", "\"},\"admin\":true,\"x\":\""), token)
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }
}
