package com.nextbillion.service.booker.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextbillion.core.HttpStatus;
import com.nextbillion.core.TestDataProvider;
import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingResponse;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end booking lifecycle test.
 * Exercises the full CRUD flow on a single booking ID in one test:
 *   POST → GET → PUT → GET → PATCH → GET → DELETE → GET (404)
 *
 * This is the primary Smoke signal for the Booking API:
 * if any step fails, core API functionality is broken.
 * All test data is loaded from {@code src/test/resources/testdata/booking-lifecycle.json}.
 */
public class BookingLifecycleTest extends BookerBaseTest {

    private static final JsonNode DATA = TestDataProvider.loadTree("booking-lifecycle.json");

    @Test(groups = {"Smoke", "Regression"},
          description = "Full lifecycle: POST → GET → PUT → PATCH → DELETE on the same booking ID, verifying response at each step")
    public void fullBookingLifecycle_createGetPutPatchDelete() {
        String token = cachedToken;

        Booking original   = TestDataProvider.getAs(DATA, "original", Booking.class);
        Booking putPayload = TestDataProvider.getAs(DATA, "putUpdate", Booking.class);
        Map<String, Object> patchFields = TestDataProvider.getAsMap(DATA, "patchFields");

        // ------------------------------------------------------------------
        // STEP 1: CREATE — POST /booking
        // ------------------------------------------------------------------
        BookingResponse createResponse = bookingClient.createBooking(original);
        int bookingId = createResponse.getBookingid();

        assertThat("Booking ID must be a positive integer", bookingId, greaterThan(0));
        assertThat(createResponse.getBooking().getFirstname(),                  equalTo(original.getFirstname()));
        assertThat(createResponse.getBooking().getLastname(),                   equalTo(original.getLastname()));
        assertThat(createResponse.getBooking().getTotalprice(),                 equalTo(original.getTotalprice()));
        assertThat(createResponse.getBooking().isDepositpaid(),                 equalTo(original.isDepositpaid()));
        assertThat(createResponse.getBooking().getBookingdates().getCheckin(),  equalTo(original.getBookingdates().getCheckin()));
        assertThat(createResponse.getBooking().getBookingdates().getCheckout(), equalTo(original.getBookingdates().getCheckout()));
        assertThat(createResponse.getBooking().getAdditionalneeds(),            equalTo(original.getAdditionalneeds()));

        // ------------------------------------------------------------------
        // STEP 2: READ — GET /booking/{id}
        // ------------------------------------------------------------------
        bookingClient.getBookingById(bookingId)
                .then()
                .statusCode(HttpStatus.OK)
                .time(lessThan(RESPONSE_TIME_SLA_MS))
                .body("firstname",             equalTo(original.getFirstname()))
                .body("lastname",              equalTo(original.getLastname()))
                .body("totalprice",            equalTo(original.getTotalprice()))
                .body("depositpaid",           equalTo(original.isDepositpaid()))
                .body("bookingdates.checkin",  equalTo(original.getBookingdates().getCheckin()))
                .body("bookingdates.checkout", equalTo(original.getBookingdates().getCheckout()))
                .body("additionalneeds",       equalTo(original.getAdditionalneeds()));

        // ------------------------------------------------------------------
        // STEP 3: FULL UPDATE — PUT /booking/{id}
        // ------------------------------------------------------------------
        bookingClient.updateBooking(bookingId, putPayload, token)
                .then()
                .statusCode(HttpStatus.OK)
                .body("firstname",             equalTo(putPayload.getFirstname()))
                .body("lastname",              equalTo(putPayload.getLastname()))
                .body("totalprice",            equalTo(putPayload.getTotalprice()))
                .body("depositpaid",           equalTo(putPayload.isDepositpaid()))
                .body("bookingdates.checkin",  equalTo(putPayload.getBookingdates().getCheckin()))
                .body("bookingdates.checkout", equalTo(putPayload.getBookingdates().getCheckout()))
                .body("additionalneeds",       equalTo(putPayload.getAdditionalneeds()));

        // GET after PUT — verify all fields were replaced and persisted
        bookingClient.getBookingById(bookingId)
                .then()
                .statusCode(HttpStatus.OK)
                .body("firstname",   equalTo(putPayload.getFirstname()))
                .body("lastname",    equalTo(putPayload.getLastname()))
                .body("totalprice",  equalTo(putPayload.getTotalprice()))
                .body("depositpaid", equalTo(putPayload.isDepositpaid()));

        // ------------------------------------------------------------------
        // STEP 4: PARTIAL UPDATE — PATCH /booking/{id}
        // ------------------------------------------------------------------
        String patchedFirstname = (String) patchFields.get("firstname");
        int    patchedPrice     = (int) patchFields.get("totalprice");

        bookingClient.partialUpdateBooking(bookingId, patchFields, token)
                .then()
                .statusCode(HttpStatus.OK)
                .body("firstname",   equalTo(patchedFirstname))          // patched
                .body("totalprice",  equalTo(patchedPrice))              // patched
                .body("lastname",    equalTo(putPayload.getLastname()))   // unchanged
                .body("depositpaid", equalTo(putPayload.isDepositpaid())); // unchanged

        // GET after PATCH — verify partial update persisted and untouched fields remain
        bookingClient.getBookingById(bookingId)
                .then()
                .statusCode(HttpStatus.OK)
                .body("firstname",  equalTo(patchedFirstname))
                .body("totalprice", equalTo(patchedPrice))
                .body("lastname",   equalTo(putPayload.getLastname()));

        // ------------------------------------------------------------------
        // STEP 5: DELETE — DELETE /booking/{id}
        // ------------------------------------------------------------------
        bookingClient.deleteBooking(bookingId, token)
                .then()
                .statusCode(HttpStatus.CREATED);

        // GET after DELETE — booking must no longer exist
        bookingClient.getBookingById(bookingId)
                .then()
                .statusCode(HttpStatus.NOT_FOUND);
    }
}
