package com.nextbillion.service.booker.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextbillion.core.HttpStatus;
import com.nextbillion.core.TestDataProvider;
import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingResponse;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.assertEquals;

/**
 * POST /booking tests.
 * Covers: positive (round-trip), negative (missing/invalid fields), boundary, security (injection).
 * All test data is loaded from {@code src/test/resources/testdata/create-bookings.json}.
 */
public class CreateBookingsTest extends BookerBaseTest {

    private static final JsonNode DATA = TestDataProvider.loadTree("create-bookings.json");

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with all valid fields returns 200 and created booking in response body")
    public void createWithAllFields_returnsFullResponse() {
        Booking payload = TestDataProvider.getAs(DATA.path("positive"), "allFields", Booking.class);

        BookingResponse response = bookingClient.createBooking(payload);

        assertThat(response.getBookingid(), greaterThan(0));
        assertEquals(response.getBooking().getFirstname(),                  payload.getFirstname());
        assertEquals(response.getBooking().getLastname(),                   payload.getLastname());
        assertEquals(response.getBooking().getTotalprice(),                 payload.getTotalprice());
        assertEquals(response.getBooking().isDepositpaid(),                 payload.isDepositpaid());
        assertEquals(response.getBooking().getBookingdates().getCheckin(),  payload.getBookingdates().getCheckin());
        assertEquals(response.getBooking().getBookingdates().getCheckout(), payload.getBookingdates().getCheckout());
        assertEquals(response.getBooking().getAdditionalneeds(),            payload.getAdditionalneeds());
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking: created booking is retrievable by its returned ID")
    public void createdBooking_isRetrievableById() {
        Booking payload = TestDataProvider.getAs(DATA.path("positive"), "retrievable", Booking.class);

        int id = bookingClient.createAndGetId(payload);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("firstname",             equalTo(payload.getFirstname()))
                .body("lastname",              equalTo(payload.getLastname()))
                .body("totalprice",            equalTo(payload.getTotalprice()))
                .body("depositpaid",           equalTo(payload.isDepositpaid()))
                .body("bookingdates.checkin",  equalTo(payload.getBookingdates().getCheckin()))
                .body("bookingdates.checkout", equalTo(payload.getBookingdates().getCheckout()))
                .body("additionalneeds",       equalTo(payload.getAdditionalneeds()));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with depositpaid=false is persisted correctly")
    public void createWithDepositFalse_persistsCorrectly() {
        Booking payload = TestDataProvider.getAs(DATA.path("positive"), "depositFalse", Booking.class);

        BookingResponse response = bookingClient.createBooking(payload);
        assertThat(response.getBooking().isDepositpaid(), is(false));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with zero price is accepted and round-trips correctly")
    public void createWithZeroPrice_isAccepted() {
        Booking payload = TestDataProvider.getAs(DATA.path("positive"), "zeroPrice", Booking.class);

        BookingResponse response = bookingClient.createBooking(payload);
        assertEquals(response.getBooking().getTotalprice(), 0);
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with very long additionalneeds string is handled gracefully")
    public void createWithLongNotes_isHandledGracefully() {
        Booking payload = TestDataProvider.getAs(DATA.path("positive"), "longNotes", Booking.class);
        String notes = payload.getAdditionalneeds();
        if (notes != null && notes.startsWith("LONG_REPEAT:")) {
            String[] parts = notes.substring("LONG_REPEAT:".length()).split(":");
            payload.setAdditionalneeds(parts[0].repeat(Integer.parseInt(parts[1])).trim());
        }

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(HttpStatus.OK);
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — DataProvider-driven
    // -----------------------------------------------------------------------

    @DataProvider(name = "negativeData")
    public Object[][] negativeData() {
        return TestDataProvider.toDataProvider(DATA, "negative");
    }

    @Test(groups = {"Regression"}, dataProvider = "negativeData",
          description = "POST /booking with invalid data returns expected error status")
    public void createWithInvalidData_returnsExpectedStatus(String testName, Map<String, Object> body,
                                                             int expectedStatus, String description) {
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(expectedStatus);
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Missing individual required fields (DataProvider)
    // -----------------------------------------------------------------------

    @DataProvider(name = "missingFieldsData")
    public Object[][] missingFieldsData() {
        return TestDataProvider.toDataProvider(DATA, "negativeMissingFields");
    }

    @Test(groups = {"Regression"}, dataProvider = "missingFieldsData",
          description = "POST /booking without a required field returns error")
    public void createWithMissingField_returnsError(String testName, Map<String, Object> body,
                                                     int expectedStatus, String description) {
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(expectedStatus);
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — ExistingDefect: wrong types / missing sub-fields (DataProvider)
    // -----------------------------------------------------------------------

    @DataProvider(name = "existingDefectData")
    public Object[][] existingDefectData() {
        return TestDataProvider.toDefectDataProvider(DATA, "negativeExistingDefects");
    }

    @Test(groups = {"Regression", "ExistingDefect"}, dataProvider = "existingDefectData",
          description = "DEFECT: POST /booking with invalid data accepted or crashes — assert fails on buggy status")
    public void createWithDefectiveValidation_failsOnBuggyStatus(String testName, Map<String, Object> body,
                                                                  int buggyStatus, int expectedStatus, String description) {
        int actual = bookingClient.createBookingRaw(body).statusCode();
        Assert.assertFalse(actual == buggyStatus,
                description + " | Expected: " + expectedStatus + " (correct), Actual: " + actual);
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "POST /booking with same checkin and checkout date is handled gracefully")
    public void createWithSameDates_isHandledGracefully() {
        Booking payload = TestDataProvider.getAs(DATA.path("boundary"), "sameDates", Booking.class);

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(HttpStatus.OK);
    }

    @Test(groups = {"Regression"}, description = "POST /booking with maximum integer price does not crash server")
    public void createWithMaxIntPrice_doesNotCrash() {
        Booking payload = TestDataProvider.getAs(DATA.path("boundary"), "maxIntPrice", Booking.class);

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(HttpStatus.OK);
    }

    // -----------------------------------------------------------------------
    // SECURITY — script / SQL injection in body fields (DataProvider)
    // -----------------------------------------------------------------------

    @DataProvider(name = "securityData")
    public Object[][] securityData() {
        return TestDataProvider.toDataProvider(DATA, "security");
    }

    @Test(groups = {"Regression"}, dataProvider = "securityData",
          description = "POST /booking with injection payload is handled safely")
    public void createWithInjection_handledSafely(String testName, Map<String, Object> body,
                                                   int expectedStatus, String description) {
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(expectedStatus);
    }

    // -----------------------------------------------------------------------
    // DATE FORMAT VARIATIONS (DataProvider)
    // -----------------------------------------------------------------------

    @DataProvider(name = "dateFormatData")
    public Object[][] dateFormatData() {
        return TestDataProvider.toDataProvider(DATA, "dateFormats");
    }

    @Test(groups = {"Regression"}, dataProvider = "dateFormatData",
          description = "POST /booking with various date formats is accepted")
    public void createWithVariousDateFormats_isAccepted(String testName, Map<String, Object> body,
                                                        int expectedStatus, String description) {
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(expectedStatus);
    }
}
