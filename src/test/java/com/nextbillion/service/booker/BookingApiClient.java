package com.nextbillion.service.booker;

import com.nextbillion.core.ApiClient;
import com.nextbillion.service.booker.model.Booking;
import com.nextbillion.service.booker.model.BookingDates;
import com.nextbillion.service.booker.model.BookingResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Booker-service-specific API client.
 * <p>
 * Sits on top of the generic {@link ApiClient} engine and exposes
 * strongly-typed methods that speak "Booking" domain language.
 * When a new microservice appears (e.g. PaymentService), a sibling
 * class extends ApiClient the same way — the core engine never changes.
 * </p>
 */
public class BookingApiClient extends ApiClient {

    private static final String AUTH_PATH    = "/auth";
    private static final String BOOKING_PATH = "/booking";

    public BookingApiClient(RequestSpecification requestSpec) {
        super(requestSpec);
    }

    // ------------------------------------------------------------------
    // Health check
    // ------------------------------------------------------------------

    public Response ping() {
        return get("/ping");
    }

    // ------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------

    public Response createToken(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        return authenticate(AUTH_PATH, body);
    }

    public String getValidToken() {
        return createToken("admin", "password123")
                .then().statusCode(200).extract().path("token");
    }

    // ------------------------------------------------------------------
    // Booking CRUD — typed wrappers
    // ------------------------------------------------------------------

    public BookingResponse createBooking(Booking booking) {
        return post(BOOKING_PATH, booking, 200, BookingResponse.class);
    }

    public Response createBookingRaw(Object body) {
        return post(BOOKING_PATH, body);
    }

    public Response getBookingById(int id) {
        return get(BOOKING_PATH + "/" + id);
    }

    public Response getBookings(Map<String, Object> queryParams) {
        return get(BOOKING_PATH, queryParams);
    }

    public Response getAllBookings() {
        return get(BOOKING_PATH);
    }

    public Response updateBooking(int id, Booking booking, String token) {
        return put(BOOKING_PATH + "/" + id, booking, token);
    }

    public Response updateBookingWithoutAuth(int id, Object body) {
        return putWithoutAuth(BOOKING_PATH + "/" + id, body);
    }

    public Response updateBookingWithBadToken(int id, Object body, String badToken) {
        return putWithBadToken(BOOKING_PATH + "/" + id, body, badToken);
    }

    public Response partialUpdateBooking(int id, Object body, String token) {
        return patch(BOOKING_PATH + "/" + id, body, token);
    }

    public Response partialUpdateWithoutAuth(int id, Object body) {
        return patchWithoutAuth(BOOKING_PATH + "/" + id, body);
    }

    public Response partialUpdateWithBadToken(int id, Object body, String badToken) {
        return patchWithBadToken(BOOKING_PATH + "/" + id, body, badToken);
    }

    public Response deleteBooking(int id, String token) {
        return delete(BOOKING_PATH + "/" + id, token);
    }

    public Response deleteBookingWithoutAuth(int id) {
        return deleteWithoutAuth(BOOKING_PATH + "/" + id);
    }

    // ------------------------------------------------------------------
    // Test-setup helper: create a booking and return only the ID
    // ------------------------------------------------------------------

    public int createAndGetId(Booking booking) {
        return createBooking(booking).getBookingid();
    }

    public int createDefaultBooking() {
        Booking b = new Booking("Setup", "Helper", 100, true,
                new BookingDates("2025-01-01", "2025-01-05"), "None");
        return createAndGetId(b);
    }
}
