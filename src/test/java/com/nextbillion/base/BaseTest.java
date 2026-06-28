package com.nextbillion.base;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.BeforeSuite;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public abstract class BaseTest {

    protected static String                baseUri;
    protected static RequestSpecification requestSpec;

    @BeforeSuite(alwaysRun = true)
    public void initSuite() {
        baseUri = resolveBaseUri();
        RestAssured.config = RestAssuredConfig.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .defaultObjectMapperType(ObjectMapperType.JACKSON_2));

        requestSpec = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept("application/json")
                .setConfig(RestAssured.config)
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .build();
    }

    /**
     * Subclasses must provide their service-specific base URI.
     * Called during {@link #initSuite()} — use {@link #resolveUri} to implement.
     * <p>
     * Template Method pattern: this class defines the setup algorithm;
     * each service base class fills in this single variable part.
     */
    protected abstract String resolveBaseUri();

    /**
     * Generic URI resolver: sys-prop → env-var → config.properties key → fallback.
     * Reusable by any subclass for any service.
     *
     * @param propKey   System-property key (e.g. "booker.base.uri")
     * @param envKey    Environment-variable name (e.g. "BOOKER_BASE_URI")
     * @param fallback  Default URI if none of the above are set
     */
    protected final String resolveUri(String propKey, String envKey, String fallback) {
        String fromSysProp = System.getProperty(propKey);
        if (fromSysProp != null && !fromSysProp.isEmpty()) return fromSysProp;

        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isEmpty()) return fromEnv;

        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
                String fromFile = props.getProperty(propKey);
                if (fromFile != null && !fromFile.isEmpty()) return fromFile;
            }
        } catch (IOException e) {
            // fall through to fallback
        }

        return fallback;
    }
}
