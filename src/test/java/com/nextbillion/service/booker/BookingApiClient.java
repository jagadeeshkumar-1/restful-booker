package com.nextbillion.service.booker;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextbillion.core.ApiClient;
import com.nextbillion.core.HttpStatus;
import com.nextbillion.core.TestDataProvider;
import com.nextbillion.service.booker.model.Booking;
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

    private final String adminUsername;
    private final String adminPassword;

    public BookingApiClient(RequestSpecification requestSpec, String adminUsername, String adminPassword) {
        super(requestSpec);
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
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
        return post(AUTH_PATH, body);
    }

    public Response createTokenRaw(Object body) {
        return post(AUTH_PATH, body);
    }

    public String getValidToken() {
        return createToken(adminUsername, adminPassword)
                .then().statusCode(HttpStatus.OK).extract().path("token");
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
        JsonNode defaultData = TestDataProvider.loadTree("default-booking.json");
        Booking b = TestDataProvider.getAs(defaultData, Booking.class);
        return createAndGetId(b);
    }
}
