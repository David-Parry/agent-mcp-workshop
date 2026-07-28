package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.ElicitationCreateParams;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for the elicitation forms this workshop server asks for.
 *
 * @see ElicitationCreateParams
 * @since 1.0
 */
public class ElicitationBuilder {

    /**
     * This factory holds only static members and is not meant to be
     * instantiated.
     */
    public ElicitationBuilder() {
    }

    /**
     * Creates an {@link ElicitationCreateParams} that asks the user for an
     * absolute path to a directory the keyword-search tool should search in.
     * <p>
     * The server asks for this only when the call carried no {@code directory}
     * argument to search in. Because the result is embedded in an
     * {@code InputRequiredResult} rather than sent as a request, the answer
     * arrives on the client's retry of the original {@code tools/call} — the
     * server does not store it, since there is no session to store it in.
     * </p>
     * <p>
     * The schema's maps keep the order their fields are put in, so every run
     * of the server sends the same bytes.
     * </p>
     *
     * @return parameters describing a single-field form prompting for an
     *         absolute directory path
     */
    public static ElicitationCreateParams buildSearchDirectoryElicitation() {
        Map<String, Object> directoryProperty = new LinkedHashMap<>();
        directoryProperty.put("type", "string");
        directoryProperty.put("title", "Search Directory");
        directoryProperty.put("description", "Absolute path to the directory the keyword search should look in");
        Map<String, Object> requestedSchema = new LinkedHashMap<>();
        requestedSchema.put("type", "object");
        requestedSchema.put("properties", Map.of("directory", directoryProperty));
        requestedSchema.put("required", List.of("directory"));
        return new ElicitationCreateParams(
            "Pick a directory for the keyword-search tool to search in:",
            requestedSchema
        );
    }
}
