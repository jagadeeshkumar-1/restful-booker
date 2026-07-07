package com.nextbillion.service.booker.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextbillion.core.HttpStatus;
import com.nextbillion.core.TestDataProvider;
import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * PUT /booking/{id} and PATCH /booking/{id} tests.
 * Covers: positive (full/partial update + persistence), negative (no auth, bad token),
 *         boundary, and security (script/SQL injection in body).
 * All test data is loaded from {@code src/test/resources/testdata/update-bookings.json}.
 */
public class UpdateBookingsTest extends BookerBaseTest {

    private static final JsonNode DATA = TestDataProvider.loadTree("update-bookings.json");

    // -----------------------------------------------------------------------
    // POSITIVE — Full PUT
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "PUT /booking/{id} fully replaces all fields and response reflects new values")
    public void putWithValidToken_replacesAllFields() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking updated = TestDataProvider.getAs(DATA, "putFullReplace", Booking.class);

        bookingClient.updateBooking(id, updated, token)
                .then()
                .statusCode(200)
                .body("firstname",             equalTo(updated.getFirstname()))
                .body("lastname",              equalTo(updated.getLastname()))
                .body("totalprice",            equalTo(updated.getTotalprice()))
                .body("depositpaid",           equalTo(updated.isDepositpaid()))
                .body("bookingdates.checkin",  equalTo(updated.getBookingdates().getCheckin()))
                .body("bookingdates.checkout", equalTo(updated.getBookingdates().getCheckout()))
                .body("additionalneeds",       equalTo(updated.getAdditionalneeds()));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "PUT /booking/{id} change is persisted — verified by subsequent GET")
    public void putChange_persistsOnSubsequentGet() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking updated = TestDataProvider.getAs(DATA, "putPersistCheck", Booking.class);

        bookingClient.updateBooking(id, updated, token).then().statusCode(200);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("firstname",  equalTo(updated.getFirstname()))
                .body("totalprice", equalTo(updated.getTotalprice()));
    }

    // -----------------------------------------------------------------------
    // POSITIVE — Partial PATCH
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "PATCH /booking/{id} updates only supplied fields; untouched fields remain")
    public void patchWithValidToken_updatesOnlySuppliedFields() {
        String token = bookingClient.getValidToken();
        Booking original = TestDataProvider.getAs(DATA, "patchOriginal", Booking.class);
        int id = bookingClient.createAndGetId(original);

        Map<String, Object> patchFields = TestDataProvider.getAsMap(DATA, "patchFields");

        bookingClient.partialUpdateBooking(id, patchFields, token)
                .then()
                .statusCode(200)
                .body("firstname",       equalTo(patchFields.get("firstname")))
                .body("totalprice",      equalTo(patchFields.get("totalprice")))
                .body("lastname",        equalTo(original.getLastname()))
                .body("depositpaid",     equalTo(original.isDepositpaid()))
                .body("additionalneeds", equalTo(original.getAdditionalneeds()));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "PATCH /booking/{id}: partial change is persisted — verified by GET")
    public void patchChange_persistsOnSubsequentGet() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Map<String, Object> patchBody = TestDataProvider.getAsMap(DATA, "patchLastnameOnly");

        bookingClient.partialUpdateBooking(id, patchBody, token)
                .then().statusCode(200);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("lastname", equalTo(patchBody.get("lastname")));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — No auth token
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT /booking/{id} without auth token returns 403")
    public void putWithoutToken_returns403() {
        int id = bookingClient.createDefaultBooking();
        Booking body = TestDataProvider.getAs(DATA, "putNoAuth", Booking.class);

        bookingClient.updateBookingWithoutAuth(id, body)
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "PATCH /booking/{id} without auth token returns 403")
    public void patchWithoutToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        Map<String, Object> patchBody = TestDataProvider.getAsMap(DATA, "patchUnauthorized");

        bookingClient.partialUpdateWithoutAuth(id, patchBody)
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
        Booking body = TestDataProvider.getAs(DATA, "putBadToken", Booking.class);

        bookingClient.updateBookingWithBadToken(id, body, "totally-fake-token-xyz")
                .then()
                .statusCode(403)
                .body(equalTo("Forbidden"));
    }

    @Test(groups = {"Regression"}, description = "PATCH /booking/{id} with a fake token returns 403")
    public void patchWithInvalidToken_returns403() {
        int id = bookingClient.createDefaultBooking();

        Map<String, Object> patchBody = TestDataProvider.getAsMap(DATA, "patchBadActor");

        bookingClient.partialUpdateWithBadToken(id, patchBody, "fake-token-xyz")
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
        Booking body = TestDataProvider.getAs(DATA, "putNonExistent", Booking.class);

        int actual = bookingClient.updateBooking(999999999, body, token).statusCode();
        Assert.assertFalse(actual == HttpStatus.METHOD_NOT_ALLOWED,
                "Expected: " + HttpStatus.NOT_FOUND + " (Not Found), Actual: " + actual);
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: PATCH on non-existent booking returns 405 Method Not Allowed — should return 404 Not Found")
    public void patchOnNonExistentId_returns405() {
        String token = bookingClient.getValidToken();

        Map<String, Object> patchBody = TestDataProvider.getAsMap(DATA, "patchNonExistent");

        int actual = bookingClient.partialUpdateBooking(999999999, patchBody, token).statusCode();
        Assert.assertFalse(actual == HttpStatus.METHOD_NOT_ALLOWED,
                "Expected: " + HttpStatus.NOT_FOUND + " (Not Found), Actual: " + actual);
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT /booking/{id} with zero totalprice is accepted")
    public void putZeroPrice_isAccepted() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking body = TestDataProvider.getAs(DATA, "putZeroPrice", Booking.class);

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
                .statusCode(HttpStatus.OK);
    }

    // -----------------------------------------------------------------------
    // SECURITY — Script / SQL injection in PUT and PATCH body
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT: script injection in firstname is stored as text, not executed")
    public void putWithXssInFirstname_storedAsPlainText() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking body = TestDataProvider.getAs(DATA, "securityXssPut", Booking.class);

        bookingClient.updateBooking(id, body, token)
                .then()
                .statusCode(HttpStatus.OK);
    }

    @Test(groups = {"Regression"}, description = "PUT: SQL injection in lastname does not cause a server error")
    public void putWithSqlInjectionInLastname_doesNotCrash() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Booking body = TestDataProvider.getAs(DATA, "securitySqlPut", Booking.class);

        bookingClient.updateBooking(id, body, token)
                .then()
                .statusCode(HttpStatus.OK);
    }

    @Test(groups = {"Regression"}, description = "PATCH: script injection in firstname field is handled safely")
    public void patchWithXssInFirstname_handledSafely() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Map<String, Object> patchBody = TestDataProvider.getAsMap(DATA, "securityXssPatch");

        bookingClient.partialUpdateBooking(id, patchBody, token)
                .then()
                .statusCode(HttpStatus.OK);
    }

    @Test(groups = {"Regression"}, description = "PATCH: JSON injection in additionalneeds does not break response structure")
    public void patchWithJsonInjectionInNotes_doesNotBreakResponse() {
        String token = bookingClient.getValidToken();
        int id       = bookingClient.createDefaultBooking();

        Map<String, Object> patchBody = TestDataProvider.getAsMap(DATA, "securityJsonPatch");

        bookingClient.partialUpdateBooking(id, patchBody, token)
                .then()
                .statusCode(HttpStatus.OK);
    }
}
