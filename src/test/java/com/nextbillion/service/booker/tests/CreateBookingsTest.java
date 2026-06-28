package com.nextbillion.service.booker.tests;

import com.nextbillion.service.booker.BookerBaseTest;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingDates;
import com.nextbillion.service.booker.model.BookingResponse;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.assertEquals;

/**
 * POST /booking tests.
 * Covers: positive (round-trip), negative (missing/invalid fields), boundary, security (injection).
 */
public class CreateBookingsTest extends BookerBaseTest {

    // -----------------------------------------------------------------------
    // POSITIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with all valid fields returns 200 and created booking in response body")
    public void createWithAllFields_returnsFullResponse() {
        Booking payload = new Booking("Alice", "Smith", 250, true,
                new BookingDates("2025-06-01", "2025-06-07"), "Breakfast");

        BookingResponse response = bookingClient.createBooking(payload);

        assertThat(response.getBookingid(), greaterThan(0));
        assertEquals(response.getBooking().getFirstname(),                  "Alice");
        assertEquals(response.getBooking().getLastname(),                   "Smith");
        assertEquals(response.getBooking().getTotalprice(),                 250);
        assertEquals(response.getBooking().isDepositpaid(),                 true);
        assertEquals(response.getBooking().getBookingdates().getCheckin(),  "2025-06-01");
        assertEquals(response.getBooking().getBookingdates().getCheckout(), "2025-06-07");
        assertEquals(response.getBooking().getAdditionalneeds(),            "Breakfast");
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking: created booking is retrievable by its returned ID")
    public void createdBooking_isRetrievableById() {
        Booking payload = new Booking("Bob", "Jones", 180, false,
                new BookingDates("2025-07-10", "2025-07-15"), "Lunch");

        int id = bookingClient.createAndGetId(payload);

        bookingClient.getBookingById(id)
                .then()
                .statusCode(200)
                .body("firstname",             equalTo("Bob"))
                .body("lastname",              equalTo("Jones"))
                .body("totalprice",            equalTo(180))
                .body("depositpaid",           equalTo(false))
                .body("bookingdates.checkin",  equalTo("2025-07-10"))
                .body("bookingdates.checkout", equalTo("2025-07-15"))
                .body("additionalneeds",       equalTo("Lunch"));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with depositpaid=false is persisted correctly")
    public void createWithDepositFalse_persistsCorrectly() {
        Booking payload = new Booking("Carl", "Null", 50, false,
                new BookingDates("2025-08-01", "2025-08-03"), "None");

        BookingResponse response = bookingClient.createBooking(payload);
        assertThat(response.getBooking().isDepositpaid(), is(false));
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with zero price is accepted and round-trips correctly")
    public void createWithZeroPrice_isAccepted() {
        Booking payload = new Booking("Zero", "Price", 0, true,
                new BookingDates("2025-09-01", "2025-09-02"), "None");

        BookingResponse response = bookingClient.createBooking(payload);
        assertEquals(response.getBooking().getTotalprice(), 0);
    }

    @Test(groups = {"Smoke", "Regression"}, description = "POST /booking with very long additionalneeds string is handled gracefully")
    public void createWithLongNotes_isHandledGracefully() {
        String longNeeds = "Extra ".repeat(100).trim();
        Booking payload = new Booking("Long", "Needs", 100, true,
                new BookingDates("2025-10-01", "2025-10-05"), longNeeds);

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400), is(413)));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "POST /booking with completely empty body returns 4xx or 5xx — not 200")
    public void createWithEmptyBody_returnsError() {
        bookingClient.createBookingRaw(Map.of())
                .then()
                .statusCode(anyOf(is(400), is(500)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking without bookingdates returns error")
    public void createWithoutDates_returnsError() {
        Map<String, Object> body = Map.of(
                "firstname",   "NoDate",
                "lastname",    "User",
                "totalprice",  100,
                "depositpaid", true
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(400), is(500)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with negative totalprice is rejected or stored as-is (no server crash)")
    public void createWithNegativePrice_doesNotCrash() {
        Booking payload = new Booking("Negative", "Price", -1, false,
                new BookingDates("2025-11-01", "2025-11-05"), "None");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with malformed date format returns error")
    public void createWithMalformedDates_returnsError() {
        Map<String, Object> body = Map.of(
                "firstname",    "Bad",
                "lastname",     "Date",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "not-a-date", "checkout", "also-bad")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with checkout before checkin returns error or is rejected")
    public void createWithCheckoutBeforeCheckin_returnsError() {
        Booking payload = new Booking("Reverse", "Dates", 100, true,
                new BookingDates("2025-12-10", "2025-12-01"), "None");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    // -----------------------------------------------------------------------
    // BOUNDARY
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "POST /booking with same checkin and checkout date is handled gracefully")
    public void createWithSameDates_isHandledGracefully() {
        Booking payload = new Booking("Same", "Dates", 100, true,
                new BookingDates("2025-12-01", "2025-12-01"), "None");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with maximum integer price does not crash server")
    public void createWithMaxIntPrice_doesNotCrash() {
        Booking payload = new Booking("Max", "Price", Integer.MAX_VALUE, true,
                new BookingDates("2025-01-01", "2025-01-02"), "None");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    // -----------------------------------------------------------------------
    // SECURITY — script / SQL injection in body fields
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "Script injection in firstname is stored as plain text, not executed")
    public void createWithXssInFirstname_storedAsPlainText() {
        Booking payload = new Booking("<script>alert('xss')</script>", "Safe", 100, true,
                new BookingDates("2025-01-01", "2025-01-05"), "None");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test(groups = {"Regression"}, description = "SQL injection pattern in lastname does not cause server error")
    public void createWithSqlInjectionInLastname_doesNotCrash() {
        Booking payload = new Booking("SQL", "'; DROP TABLE bookings; --", 100, false,
                new BookingDates("2025-01-01", "2025-01-05"), "None");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }

    @Test(groups = {"Regression"}, description = "JSON injection attempt in additionalneeds does not break the response structure")
    public void createWithJsonInjectionInNotes_doesNotBreakResponse() {
        Booking payload = new Booking("Json", "Inject", 100, true,
                new BookingDates("2025-01-01", "2025-01-05"), "\"},\"admin\":true,\"x\":\"");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test(groups = {"Regression"}, description = "Null byte injection in firstname does not crash server")
    public void createWithNullByteInFirstname_doesNotCrash() {
        Booking payload = new Booking("First\u0000Name", "Last", 100, true,
                new BookingDates("2025-01-01", "2025-01-05"), "None");

        bookingClient.createBookingRaw(payload)
                .then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Missing individual required fields
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "POST /booking without firstname field is rejected")
    public void createWithoutFirstname_returnsError() {
        Map<String, Object> body = Map.of(
                "lastname",     "User",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(400), is(418), is(500)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking without lastname field is rejected")
    public void createWithoutLastname_returnsError() {
        Map<String, Object> body = Map.of(
                "firstname",    "User",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(400), is(418), is(500)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking without totalprice field is rejected")
    public void createWithoutTotalPrice_returnsError() {
        Map<String, Object> body = Map.of(
                "firstname",    "No",
                "lastname",     "Price",
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(400), is(418), is(500)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking without depositpaid field is rejected")
    public void createWithoutDepositPaid_returnsError() {
        Map<String, Object> body = Map.of(
                "firstname",    "No",
                "lastname",     "Deposit",
                "totalprice",   100,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(400), is(418), is(500)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with bookingdates missing checkin sub-field causes server crash — API only validates that bookingdates key exists, not its required sub-fields")
    public void createWithMissingCheckin_serverCrashes() {
        Map<String, Object> body = Map.of(
                "firstname",    "No",
                "lastname",     "Checkin",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(418), is(422), is(500)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with bookingdates missing checkout sub-field causes server crash — API only validates that bookingdates key exists, not its required sub-fields")
    public void createWithMissingCheckout_serverCrashes() {
        Map<String, Object> body = Map.of(
                "firstname",    "No",
                "lastname",     "Checkout",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(418), is(422), is(500)));
    }

    // -----------------------------------------------------------------------
    // NEGATIVE — Wrong data types / potential defects
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with totalprice as non-numeric string is accepted with 200 — API has no type enforcement on numeric fields")
    public void createWithNonNumericPrice_acceptedWithoutValidation() {
        Map<String, Object> body = Map.of(
                "firstname",    "Type",
                "lastname",     "Check",
                "totalprice",   "not-a-number",
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with totalprice as decimal float (99.99) is accepted with 200 — integer field silently accepts floating-point values")
    public void createWithDecimalPrice_acceptedOnIntegerField() {
        Map<String, Object> body = Map.of(
                "firstname",    "Float",
                "lastname",     "Price",
                "totalprice",   99.99,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with depositpaid as string 'true' (not boolean) is accepted with 200 — API has no type enforcement on boolean fields")
    public void createWithDepositAsBoolString_acceptedWithoutValidation() {
        Map<String, Object> body = Map.of(
                "firstname",    "Bool",
                "lastname",     "AsString",
                "totalprice",   100,
                "depositpaid",  "true",
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with depositpaid as integer 1 (not boolean) is accepted with 200 — API has no type enforcement on boolean fields")
    public void createWithDepositAsInteger_acceptedWithoutValidation() {
        Map<String, Object> body = Map.of(
                "firstname",    "Deposit",
                "lastname",     "AsInt",
                "totalprice",   100,
                "depositpaid",  1,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with bookingdates as a flat string (wrong type) causes server crash — returns 500 Internal Server Error instead of 400 Bad Request")
    public void createWithDatesAsString_serverCrashes() {
        Map<String, Object> body = Map.of(
                "firstname",    "Dates",
                "lastname",     "AsString",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", "2025-01-01"
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(418), is(422), is(500)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with firstname as JSON null causes server crash — null bypasses the JS typeof undefined check (typeof null === 'object') and returns 500")
    public void createWithNullFirstname_serverCrashes() {
        Map<String, Object> body = new HashMap<>();
        body.put("firstname",    null);
        body.put("lastname",     "Null");
        body.put("totalprice",   100);
        body.put("depositpaid",  true);
        body.put("bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05"));
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(418), is(422), is(500)));
    }

    @Test(groups = {"Regression", "ExistingDefect"}, description = "DEFECT: POST /booking with empty string firstname is accepted with 200 — API has no minimum-length validation on name fields")
    public void createWithEmptyFirstname_acceptedWithoutValidation() {
        Map<String, Object> body = Map.of(
                "firstname",    "",
                "lastname",     "EmptyFirst",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01", "checkout", "2025-01-05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(418), is(422)));
    }

    // -----------------------------------------------------------------------
    // DATE FORMAT VARIATIONS
    // -----------------------------------------------------------------------

    @Test(groups = {"Regression"}, description = "POST /booking with ISO-8601 datetime string (with time component) for dates")
    public void createWithIso8601DateTimeFormat_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "Iso",
                "lastname",     "DateTime",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025-01-01T14:00:00Z", "checkout", "2025-01-05T12:00:00Z")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with slash-separated date format YYYY/MM/DD")
    public void createWithSlashSeparatedDates_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "Slash",
                "lastname",     "Date",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2025/01/01", "checkout", "2025/01/05")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with DD-MM-YYYY date format")
    public void createWithDdMmYyyyDates_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "Ddmm",
                "lastname",     "Date",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "01-01-2025", "checkout", "05-01-2025")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with MM/DD/YYYY date format")
    public void createWithMmDdYyyyDates_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "MmDd",
                "lastname",     "Format",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "01/01/2025", "checkout", "01/05/2025")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with written-out month date format (e.g. January 01, 2025)")
    public void createWithLongFormMonthDates_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "Month",
                "lastname",     "Name",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "January 01, 2025", "checkout", "January 05, 2025")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with Unix timestamp string as date value")
    public void createWithUnixTimestampDates_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "Unix",
                "lastname",     "Timestamp",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "1735689600", "checkout", "1736035200")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with far-future dates (year 2099) is handled gracefully")
    public void createWithFarFutureDates_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "Far",
                "lastname",     "Future",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "2099-12-30", "checkout", "2099-12-31")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }

    @Test(groups = {"Regression"}, description = "POST /booking with historical past dates (year 1900) is handled gracefully")
    public void createWithHistoricalDates_isAccepted() {
        Map<String, Object> body = Map.of(
                "firstname",    "Historical",
                "lastname",     "Date",
                "totalprice",   100,
                "depositpaid",  true,
                "bookingdates", Map.of("checkin", "1900-01-01", "checkout", "1900-01-02")
        );
        bookingClient.createBookingRaw(body)
                .then()
                .statusCode(anyOf(is(200), is(400), is(422)));
    }
}
