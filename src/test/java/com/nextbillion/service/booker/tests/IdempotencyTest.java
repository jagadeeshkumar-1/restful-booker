package com.nextbillion.service.booker.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextbillion.core.HttpStatus;
import com.nextbillion.core.TestDataProvider;
import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Idempotency tests for all CRUD operations on the Booking API.
 *
 * HTTP idempotency reference (RFC 7231):
 *   - GET    : safe + idempotent — multiple identical requests produce the same response
 *   - PUT    : idempotent — calling twice with the same payload leaves the resource in the same state
 *   - PATCH  : idempotent when the operation sets a field to a fixed value (not relative/incremental)
 *   - DELETE : should be idempotent — first call removes the resource, subsequent calls return 404
 *   - POST   : NOT idempotent by design — each call creates a new distinct resource
 *
 * Feasibility notes:
 *   GET   → fully testable, response body must match on repeated calls.
 *   PUT   → fully testable, same payload twice must yield same response and same GET state.
 *   PATCH → testable for fixed-value patches; incremental patches (e.g. price += 10) are not idempotent by nature.
 *   DELETE → partially testable; the API has a known defect (returns 405 on second DELETE instead of 404),
 *            which is documented below as ExistingDefect.
 *   POST  → testable in the negative — two identical POSTs must produce two different booking IDs.
 * All test data is loaded from {@code src/test/resources/testdata/idempotency.json}.
 */
public class IdempotencyTest extends BookerBaseTest {

    private static final JsonNode DATA = TestDataProvider.loadTree("idempotency.json");

    // -----------------------------------------------------------------------
    // GET — safe and idempotent
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "GET /booking/{id} called twice returns identical response bodies — confirms read idempotency")
    public void get_calledTwice_returnsSameData() {
        int id = bookingClient.createDefaultBooking();

        String firstResponse = bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        String secondResponse = bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        assertThat(firstResponse, equalTo(secondResponse));
    }

    // -----------------------------------------------------------------------
    // PUT — idempotent
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PUT /booking/{id} with same payload applied twice returns identical response bodies — confirms PUT idempotency")
    public void put_samePayloadTwice_returnsSameResponse() {
        String token = bookingClient.getValidToken();
        int id = bookingClient.createDefaultBooking();

        Booking payload = TestDataProvider.getAs(DATA, "putPayload", Booking.class);

        String firstResponse = bookingClient.updateBooking(id, payload, token)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        String secondResponse = bookingClient.updateBooking(id, payload, token)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        assertThat(firstResponse, equalTo(secondResponse));
    }

    @Test(groups = {"Regression"}, description = "GET after two identical PUTs reflects same state — server-side resource is idempotent")
    public void put_samePayloadTwice_serverStateUnchanged() {
        String token = bookingClient.getValidToken();
        int id = bookingClient.createDefaultBooking();

        Booking payload = TestDataProvider.getAs(DATA, "putStatePayload", Booking.class);

        bookingClient.updateBooking(id, payload, token).then().statusCode(200);
        bookingClient.updateBooking(id, payload, token).then().statusCode(200);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("firstname",  equalTo(payload.getFirstname()))
                .body("lastname",   equalTo(payload.getLastname()))
                .body("totalprice", equalTo(payload.getTotalprice()));
    }

    // -----------------------------------------------------------------------
    // PATCH — idempotent when setting fixed values
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "PATCH /booking/{id} with same fixed field value applied twice leaves booking in the same state — confirms idempotency for non-incremental patches")
    public void patch_fixedValueTwice_serverStateUnchanged() {
        String token = bookingClient.getValidToken();
        int id = bookingClient.createDefaultBooking();

        Map<String, Object> patch = TestDataProvider.getAsMap(DATA, "patchField");

        bookingClient.partialUpdateBooking(id, patch, token)
                .then().statusCode(200);

        bookingClient.partialUpdateBooking(id, patch, token)
                .then().statusCode(200);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("firstname", equalTo(patch.get("firstname")));
    }

    // -----------------------------------------------------------------------
    // DELETE — should be idempotent, but returns 405 on second call (defect)
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: DELETE /booking/{id} is not idempotent — first call returns 201, second call returns 405 Method Not Allowed instead of 404 Not Found. RFC 7231 requires idempotent DELETE behaviour.")
    public void delete_calledTwice_secondCallReturns405InsteadOf404() {
        String token = bookingClient.getValidToken();
        int id = bookingClient.createDefaultBooking();

        bookingClient.deleteBooking(id, token)
                .then()
                .statusCode(HttpStatus.CREATED);

        int actual = bookingClient.deleteBooking(id, token).statusCode();
        Assert.assertFalse(actual == HttpStatus.METHOD_NOT_ALLOWED,
                "Expected: " + HttpStatus.NOT_FOUND + " (Not Found), Actual: " + actual);
    }

    // -----------------------------------------------------------------------
    // POST — not idempotent by design
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "POST /booking called twice with identical payload creates two distinct resources with different IDs — confirms POST is non-idempotent by design")
    public void post_samePayloadTwice_createsTwoDistinctBookings() {
        Booking payload = TestDataProvider.getAs(DATA, "postDuplicatePayload", Booking.class);

        BookingResponse first  = bookingClient.createBooking(payload);
        BookingResponse second = bookingClient.createBooking(payload);

        assertThat(first.getBookingid(),  greaterThan(0));
        assertThat(second.getBookingid(), greaterThan(0));
        assertThat(first.getBookingid(),  not(equalTo(second.getBookingid())));
    }
}
