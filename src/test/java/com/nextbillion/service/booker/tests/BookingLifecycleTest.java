package com.nextbillion.service.booker.tests;

import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingDates;
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
 */
public class BookingLifecycleTest extends BookerBaseTest {

    @Test(groups = {"Smoke", "Regression"},
          description = "Full lifecycle: POST → GET → PUT → PATCH → DELETE on the same booking ID, verifying response at each step")
    public void fullBookingLifecycle_createGetPutPatchDelete() {
        String token = bookingClient.getValidToken();

        // ------------------------------------------------------------------
        // STEP 1: CREATE — POST /booking
        // ------------------------------------------------------------------
        Booking original = new Booking("Alice", "Smith", 250, true,
                new BookingDates("2025-06-01", "2025-06-07"), "Breakfast");

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
                .statusCode(200)
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
        Booking putPayload = new Booking("Bob", "Jones", 500, false,
                new BookingDates("2025-08-01", "2025-08-10"), "Dinner");

        bookingClient.updateBooking(bookingId, putPayload, token)
                .then()
                .statusCode(200)
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
                .statusCode(200)
                .body("firstname",   equalTo(putPayload.getFirstname()))
                .body("lastname",    equalTo(putPayload.getLastname()))
                .body("totalprice",  equalTo(putPayload.getTotalprice()))
                .body("depositpaid", equalTo(putPayload.isDepositpaid()));

        // ------------------------------------------------------------------
        // STEP 4: PARTIAL UPDATE — PATCH /booking/{id}
        // ------------------------------------------------------------------
        String patchedFirstname = "Charlie";
        int    patchedPrice     = 999;

        bookingClient.partialUpdateBooking(bookingId,
                        Map.of("firstname", patchedFirstname, "totalprice", patchedPrice), token)
                .then()
                .statusCode(200)
                .body("firstname",   equalTo(patchedFirstname))          // patched
                .body("totalprice",  equalTo(patchedPrice))              // patched
                .body("lastname",    equalTo(putPayload.getLastname()))   // unchanged
                .body("depositpaid", equalTo(putPayload.isDepositpaid())); // unchanged

        // GET after PATCH — verify partial update persisted and untouched fields remain
        bookingClient.getBookingById(bookingId)
                .then()
                .statusCode(200)
                .body("firstname",  equalTo(patchedFirstname))
                .body("totalprice", equalTo(patchedPrice))
                .body("lastname",   equalTo(putPayload.getLastname()));

        // ------------------------------------------------------------------
        // STEP 5: DELETE — DELETE /booking/{id}
        // ------------------------------------------------------------------
        bookingClient.deleteBooking(bookingId, token)
                .then()
                .statusCode(201);

        // GET after DELETE — booking must no longer exist
        bookingClient.getBookingById(bookingId)
                .then()
                .statusCode(404);
    }
}
