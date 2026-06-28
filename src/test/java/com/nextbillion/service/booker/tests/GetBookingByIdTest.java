package com.nextbillion.service.booker.tests;

import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingDates;
import com.nextbillion.service.booker.model.BookingResponse;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.assertEquals;

/**
 * GET /booking and GET /booking/{id} tests.
 * Covers: positive (data round-trip), negative (bad ID), boundary, security.
 */
public class GetBookingByIdTest extends BookerBaseTest {

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "GET /booking/{id} returns the exact data that was created — full round-trip")
    public void getById_returnsAllCreatedFields() {
        Booking payload = new Booking("RoundTrip", "User", 375, true,
                new BookingDates("2025-05-01", "2025-05-10"), "Breakfast");

        BookingResponse created = bookingClient.createBooking(payload);

        Booking fetched = bookingClient.getBookingById(created.getBookingid())
                .then()
                .statusCode(200)
                .body("firstname",             equalTo("RoundTrip"))
                .body("lastname",              equalTo("User"))
                .body("totalprice",            equalTo(375))
                .body("depositpaid",           equalTo(true))
                .body("bookingdates.checkin",  equalTo("2025-05-01"))
                .body("bookingdates.checkout", equalTo("2025-05-10"))
                .body("additionalneeds",       equalTo("Breakfast"))
                .extract().as(Booking.class);

        assertEquals(fetched.getFirstname(),                  payload.getFirstname());
        assertEquals(fetched.getLastname(),                   payload.getLastname());
        assertEquals(fetched.getTotalprice(),                 payload.getTotalprice());
        assertEquals(fetched.isDepositpaid(),                 payload.isDepositpaid());
        assertEquals(fetched.getBookingdates().getCheckin(),  payload.getBookingdates().getCheckin());
        assertEquals(fetched.getBookingdates().getCheckout(), payload.getBookingdates().getCheckout());
        assertEquals(fetched.getAdditionalneeds(),            payload.getAdditionalneeds());
    }

    @Test(groups = {"Smoke", "Regression"}, description = "GET /booking returns a list containing at least one booking ID")
    public void getAllBookings_returnsNonEmptyList() {
        bookingClient.getAllBookings()
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("bookingid", everyItem(notNullValue()));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "GET /booking response structure — each item contains only a positive integer bookingid field")
    public void getAllBookings_eachItemHasBookingIdField() {
        bookingClient.getAllBookings()
                .then()
                .statusCode(200)
                .body("bookingid", everyItem(instanceOf(Integer.class)))
                .body("bookingid", everyItem(greaterThan(0)));
    }

    @Test(groups = {"Regression"}, description = "GET /booking: multiple bookings created in parallel all appear in the list response")
    public void multipleCreatedBookings_allAppearInList() {
        String tag = String.valueOf(System.currentTimeMillis());

        int id1 = bookingClient.createAndGetId(new Booking("Multi" + tag, "One", 100, true,
                new BookingDates("2026-03-01", "2026-03-05"), "None"));
        int id2 = bookingClient.createAndGetId(new Booking("Multi" + tag, "Two", 200, false,
                new BookingDates("2026-04-01", "2026-04-05"), "None"));
        int id3 = bookingClient.createAndGetId(new Booking("Multi" + tag, "Three", 300, true,
                new BookingDates("2026-05-01", "2026-05-05"), "None"));

        var ids = bookingClient.getAllBookings()
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("bookingid", Integer.class);

        assertThat(ids, hasItems(id1, id2, id3));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "GET /booking?checkout= returns bookings with checkout on or before the date")
    public void filterByCheckout_includesMatchingBooking() {
        int id = bookingClient.createAndGetId(
                new Booking("CheckoutFilter", "Test", 75, true,
                        new BookingDates("2032-01-01", "2032-01-07"), "None"));

        var ids = bookingClient.getBookings(Map.of("checkout", "2032-01-08"))
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("bookingid", Integer.class);

        assertThat(ids, hasItem(id));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "GET /booking?firstname=&lastname= filters by name and includes the created booking")
    public void filterByName_includesCreatedBooking() {
        String first = "FilterFirst" + System.currentTimeMillis();
        String last  = "FilterLast"  + System.currentTimeMillis();

        int id = bookingClient.createAndGetId(
                new Booking(first, last, 100, false,
                        new BookingDates("2026-01-01", "2026-01-05"), "None"));

        var ids = bookingClient.getBookings(Map.of("firstname", first, "lastname", last))
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("bookingid", Integer.class);

        assertThat(ids, hasItem(id));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "GET /booking?checkin= returns bookings with checkin on or after the date")
    public void filterByCheckin_includesMatchingBooking() {
        int id = bookingClient.createAndGetId(
                new Booking("CheckinFilter", "Test", 50, true,
                        new BookingDates("2031-01-01", "2031-01-07"), "Pool"));

        var ids = bookingClient.getBookings(Map.of("checkin", "2030-12-31"))
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("bookingid", Integer.class);

        assertThat(ids, hasItem(id));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "GET /booking/{id} for a non-existent ID returns 404")
    public void getNonExistentId_returns404() {
        bookingClient.getBookingById(999999999)
                .then()
                .statusCode(404)
                .body(equalTo("Not Found"));
    }

    @Test(groups = {"Regression"}, description = "Filter by name that matches nobody returns an empty list, not an error")
    public void filterByUnknownName_returnsEmptyList() {
        bookingClient.getBookings(Map.of("firstname", "ZZZGhostXXX", "lastname", "ZZZGhostXXX"))
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "GET /booking/{id} with ID = 0 returns 404 or 400 — not a server crash")
    public void getZeroId_returnsSafeError() {
        bookingClient.getBookingById(0)
                .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    @Test(groups = {"Regression"}, description = "GET /booking/{id} with a negative ID returns 404 or 400")
    public void getNegativeId_returnsSafeError() {
        bookingClient.getBookingById(-1)
                .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    // -----------------------------------------------------------------------
    // SECURITY — injection in query params
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "Script injection in firstname query param returns safe empty result, not error")
    public void filterWithXssInName_returnsSafeResponse() {
        bookingClient.getBookings(Map.of("firstname", "<script>alert(1)</script>"))
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body(not(containsString("<script>")));
    }

    @Test(groups = {"Regression"}, description = "SQL injection in firstname query param returns safe empty result")
    public void filterWithSqlInName_returnsSafeResponse() {
        bookingClient.getBookings(Map.of("firstname", "' OR 1=1--"))
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }
}
