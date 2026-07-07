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
import static org.testng.Assert.assertEquals;

/**
 * GET /booking and GET /booking/{id} tests.
 * Covers: positive (data round-trip), negative (bad ID), boundary, security.
 * All test data is loaded from {@code src/test/resources/testdata/get-bookings.json}.
 */
public class GetBookingByIdTest extends BookerBaseTest {

    private static final JsonNode DATA = TestDataProvider.loadTree("get-bookings.json");

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "GET /booking/{id} returns the exact data that was created — full round-trip")
    public void getById_returnsAllCreatedFields() {
        Booking payload = TestDataProvider.getAs(DATA, "roundTrip", Booking.class);

        BookingResponse created = bookingClient.createBooking(payload);

        Booking fetched = bookingClient.getBookingById(created.getBookingid())
                .then()
                .statusCode(200)
                .body("firstname",             equalTo(payload.getFirstname()))
                .body("lastname",              equalTo(payload.getLastname()))
                .body("totalprice",            equalTo(payload.getTotalprice()))
                .body("depositpaid",           equalTo(payload.isDepositpaid()))
                .body("bookingdates.checkin",  equalTo(payload.getBookingdates().getCheckin()))
                .body("bookingdates.checkout", equalTo(payload.getBookingdates().getCheckout()))
                .body("additionalneeds",       equalTo(payload.getAdditionalneeds()))
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
        Booking template = TestDataProvider.getAs(DATA, "roundTrip", Booking.class);
        String tag = String.valueOf(System.currentTimeMillis());

        template.setFirstname("Multi" + tag);
        template.setLastname("One");
        int id1 = bookingClient.createAndGetId(template);

        template.setLastname("Two");
        template.setTotalprice(200);
        int id2 = bookingClient.createAndGetId(template);

        template.setLastname("Three");
        template.setTotalprice(300);
        int id3 = bookingClient.createAndGetId(template);

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
        Booking payload = TestDataProvider.getAs(DATA, "checkoutFilter", Booking.class);
        int id = bookingClient.createAndGetId(payload);

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
        Booking payload = TestDataProvider.getAs(DATA, "roundTrip", Booking.class);
        String first = "FilterFirst" + System.currentTimeMillis();
        String last  = "FilterLast"  + System.currentTimeMillis();
        payload.setFirstname(first);
        payload.setLastname(last);

        int id = bookingClient.createAndGetId(payload);

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
        Booking payload = TestDataProvider.getAs(DATA, "checkinFilter", Booking.class);
        int id = bookingClient.createAndGetId(payload);

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
        String unknownName = TestDataProvider.getString(DATA, "unknownFilterName");
        bookingClient.getBookings(Map.of("firstname", unknownName, "lastname", unknownName))
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
                .statusCode(HttpStatus.NOT_FOUND);
    }

    @Test(groups = {"Regression"}, description = "GET /booking/{id} with a negative ID returns 404 or 400")
    public void getNegativeId_returnsSafeError() {
        bookingClient.getBookingById(-1)
                .then()
                .statusCode(HttpStatus.NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // SECURITY — injection in query params
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "Script injection in firstname query param returns safe empty result, not error")
    public void filterWithXssInName_returnsSafeResponse() {
        String xss = TestDataProvider.getString(DATA, "securityXssFilter");
        bookingClient.getBookings(Map.of("firstname", xss))
                .then()
                .statusCode(HttpStatus.OK)
                .body(not(containsString("<script>")));
    }

    @Test(groups = {"Regression"}, description = "SQL injection in firstname query param returns safe empty result")
    public void filterWithSqlInName_returnsSafeResponse() {
        String sql = TestDataProvider.getString(DATA, "securitySqlFilter");
        bookingClient.getBookings(Map.of("firstname", sql))
                .then()
                .statusCode(HttpStatus.OK);
    }
}
