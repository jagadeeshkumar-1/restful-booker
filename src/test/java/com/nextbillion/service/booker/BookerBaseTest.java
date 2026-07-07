package com.nextbillion.service.booker;

import com.nextbillion.base.BaseTest;
import org.testng.annotations.BeforeSuite;

/**
 * Booker-service-specific base class.
 * <p>
 * Extends the generic {@link BaseTest} and adds booker-specific setup:
 *   1. Resolves the booker service URI via booker-specific property keys.
 *   2. Instantiates {@link BookingApiClient} for all booker test classes.
 *   3. Performs a health check on {@code GET /ping} before any test runs.
 *   4. Caches an auth token so individual tests do not need to re-authenticate.
 * <p>
 * When a new microservice is added (e.g. Payment), create a sibling class
 * {@code service/payment/PaymentBaseTest} that extends {@link BaseTest},
 * overrides {@code resolveBaseUri()}, and instantiates its own API client.
 */
public class BookerBaseTest extends BaseTest {

    /** Maximum acceptable response time in milliseconds (SLA). */
    protected static final long RESPONSE_TIME_SLA_MS = 5000L;

    protected static BookingApiClient bookingClient;
    protected static String           adminUsername;
    protected static String           adminPassword;
    protected static String           cachedToken;

    @Override
    @BeforeSuite(alwaysRun = true)
    public void initSuite() {
        super.initSuite();
        adminUsername = resolveUri("booker.admin.username", "BOOKER_ADMIN_USERNAME", "admin");
        adminPassword = resolveUri("booker.admin.password", "BOOKER_ADMIN_PASSWORD", "password123");
        bookingClient = new BookingApiClient(requestSpec, adminUsername, adminPassword);

        int pingStatus = bookingClient.ping().statusCode();
        if (pingStatus != 201) {
            throw new RuntimeException(
                "[HealthCheck] FAILED — " + resolveBaseUri() + "/ping returned HTTP " +
                pingStatus + " (expected 201). Aborting suite.");
        }
        LOG.info("[HealthCheck] API is healthy at {}", resolveBaseUri());

        cachedToken = bookingClient.getValidToken();
        LOG.info("[Auth] Token cached for suite — {} auth API calls saved", "all per-test");
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
