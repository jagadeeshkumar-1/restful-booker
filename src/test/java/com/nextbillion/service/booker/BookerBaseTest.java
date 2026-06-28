package com.nextbillion.service.booker;

import com.nextbillion.base.BaseTest;
import org.testng.annotations.BeforeSuite;

/**
 * Booker-service-specific base class.
 * <p>
 * Extends the generic {@link BaseTest} and adds booker-specific setup:
 *   1. Resolves the booker service URI via booker-specific property keys.
 *   2. Instantiates {@link BookingApiClient} for all booker test classes.
 * <p>
 * When a new microservice is added (e.g. Payment), create a sibling class
 * {@code service/payment/PaymentBaseTest} that extends {@link BaseTest},
 * overrides {@code resolveBaseUri()}, and instantiates its own API client.
 */
public class BookerBaseTest extends BaseTest {

    protected static BookingApiClient bookingClient;

    @Override
    @BeforeSuite(alwaysRun = true)
    public void initSuite() {
        super.initSuite();
        bookingClient = new BookingApiClient(requestSpec);

        int pingStatus = bookingClient.ping().statusCode();
        if (pingStatus != 201) {
            throw new RuntimeException(
                "[HealthCheck] FAILED — " + resolveBaseUri() + "/ping returned HTTP " +
                pingStatus + " (expected 201). Aborting suite.");
        }
        System.out.println("[HealthCheck] API is healthy at " + resolveBaseUri());
    }

    /**
     * Resolves the Restful Booker service URI.
     * Priority: sys-prop "booker.base.uri" → env "BOOKER_BASE_URI"
     *           → config.properties "booker.base.uri" → hardcoded default.
     */
    @Override
    protected String resolveBaseUri() {
        return resolveUri(
                "booker.base.uri",
                "BOOKER_BASE_URI",
                "https://restful-booker.herokuapp.com"
        );
    }
}
