package com.nextbillion.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Utility to load test data from JSON files under {@code src/test/resources/testdata/}.
 * <p>
 * Provides helpers for:
 * <ul>
 *   <li>Loading a full JSON tree ({@link #loadTree(String)})</li>
 *   <li>Deserialising a sub-node into a POJO ({@link #getAs(JsonNode, String, Class)})</li>
 *   <li>Deserialising a sub-node into a Map ({@link #getAsMap(JsonNode, String)})</li>
 *   <li>Converting a JSON array node into a TestNG {@code Object[][]} DataProvider
 *       ({@link #toDataProvider(JsonNode, String)})</li>
 * </ul>
 */
public final class TestDataProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestDataProvider() {}

    /**
     * Loads a JSON file from the classpath (under {@code testdata/}) and returns
     * the root {@link JsonNode}.
     *
     * @param fileName file name relative to {@code testdata/}, e.g. "create-bookings.json"
     */
    public static JsonNode loadTree(String fileName) {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("testdata/" + fileName)) {
            if (is == null) {
                throw new IllegalArgumentException("Test data file not found: testdata/" + fileName);
            }
            return MAPPER.readTree(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test data: " + fileName, e);
        }
    }

    /**
     * Deserialises the given node directly into the given POJO type.
     */
    public static <T> T getAs(JsonNode node, Class<T> clazz) {
        try {
            return MAPPER.treeToValue(node, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialise node", e);
        }
    }

    /**
     * Deserialises a named child node into the given POJO type.
     */
    public static <T> T getAs(JsonNode root, String fieldName, Class<T> clazz) {
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode()) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in test data");
        }
        try {
            return MAPPER.treeToValue(node, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialise field '" + fieldName + "'", e);
        }
    }

    /**
     * Deserialises a named child node into a {@code Map<String, Object>}.
     */
    public static Map<String, Object> getAsMap(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode()) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in test data");
        }
        return MAPPER.convertValue(node, new TypeReference<>() {});
    }

    /**
     * Converts a JSON array node (at the given path) into a TestNG {@code Object[][]}
     * where each row is {@code [testName, body (Map), expectedStatus, description]}.
     */
    public static Object[][] toDataProvider(JsonNode root, String arrayField) {
        JsonNode arr = root.path(arrayField);
        if (!arr.isArray()) {
            throw new IllegalArgumentException("'" + arrayField + "' is not a JSON array");
        }

        Object[][] data = new Object[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonNode item = arr.get(i);
            String testName = item.path("testName").asText();
            Map<String, Object> body = MAPPER.convertValue(item.path("body"), new TypeReference<>() {});
            int expectedStatus = item.path("expectedStatus").asInt();
            String description = item.path("description").asText();
            data[i] = new Object[]{ testName, body, expectedStatus, description };
        }
        return data;
    }

    /**
     * Converts a JSON array node for ExistingDefect tests into a TestNG {@code Object[][]}
     * where each row is {@code [testName, body (Map), buggyStatus, expectedStatus, description]}.
     */
    public static Object[][] toDefectDataProvider(JsonNode root, String arrayField) {
        JsonNode arr = root.path(arrayField);
        if (!arr.isArray()) {
            throw new IllegalArgumentException("'" + arrayField + "' is not a JSON array");
        }

        Object[][] data = new Object[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonNode item = arr.get(i);
            String testName = item.path("testName").asText();
            Map<String, Object> body = MAPPER.convertValue(item.path("body"), new TypeReference<>() {});
            int buggyStatus = item.path("buggyStatus").asInt();
            int expectedStatus = item.path("expectedStatus").asInt();
            String description = item.path("description").asText();
            data[i] = new Object[]{ testName, body, buggyStatus, expectedStatus, description };
        }
        return data;
    }

    /**
     * Reads a named string field from the root node.
     */
    public static String getString(JsonNode root, String fieldName) {
        return root.path(fieldName).asText();
    }

    /**
     * Reads a named integer field from the root node.
     */
    public static int getInt(JsonNode root, String fieldName) {
        return root.path(fieldName).asInt();
    }
}
