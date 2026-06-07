package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.ElicitationCreateParams;
import java.util.Map;
import java.util.List;

public class ElicitationBuilder {

    /**
     * Creates an {@link ElicitationCreateParams} that asks the user for an
     * absolute path to a directory the keyword-search tool should search in.
     * <p>
     * The server sends this elicitation when the client did not (yet) supply
     * any roots via the {@code roots/list} mechanism. The submitted directory
     * is added to the server's roots set and used for subsequent
     * {@code tools/call} invocations.
     * </p>
     *
     * @return parameters describing a single-field form prompting for an
     *         absolute directory path
     */
    public static ElicitationCreateParams buildSearchDirectoryElicitation() {
        Map<String, Object> directoryProperty = Map.of(
            "type", "string",
            "title", "Search Directory",
            "description", "Absolute path to the directory the keyword search should look in"
        );

        Map<String, Object> properties = Map.of(
            "directory", directoryProperty
        );

        Map<String, Object> requestedSchema = Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("directory")
        );

        return new ElicitationCreateParams(
            "Pick a directory for the keyword-search tool to search in:",
            requestedSchema
        );
    }
}
