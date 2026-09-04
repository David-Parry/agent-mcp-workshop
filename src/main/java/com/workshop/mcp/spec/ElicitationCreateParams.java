package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Parameters of an {@code elicitation/create} request asking the user for
 * structured input.
 * <p>
 * On revision {@code 2026-07-28} this is never sent as a JSON-RPC request. It
 * is embedded in an {@link InputRequiredResult} via
 * {@link InputRequest#elicitation(ElicitationCreateParams)}.
 * </p>
 * <p>
 * {@code mode} is optional and clients treat its absence as {@code "form"};
 * it is set explicitly here so the intent is visible on the wire. The
 * {@code elicitationId} field of the previous revision is gone, along with the
 * {@code notifications/elicitation/complete} notification it correlated —
 * under Multi Round-Trip Requests the client reports the outcome by retrying
 * the original request.
 * </p>
 * <p>
 * {@code requestedSchema} is restricted to a flat object: top-level properties
 * only, each a primitive, with no nesting.
 * </p>
 *
 * @param mode            {@code "form"} for an in-conversation form, {@code "url"} for an out-of-band page
 * @param message         the prompt shown to the user
 * @param requestedSchema a flat JSON Schema object describing the fields to collect
 *
 * @see InputRequest
 * @see ElicitationCreateResult
 * @since 1.0
 */
public record ElicitationCreateParams(
    String mode,
    String message,
    Map<String, Object> requestedSchema
) {
    /** The in-conversation form mode. */
    public static final String MODE_FORM = "form";

    /** The out-of-band URL mode. */
    public static final String MODE_URL = "url";

    /**
     * Creates form-mode elicitation parameters.
     *
     * @param message         the prompt shown to the user
     * @param requestedSchema a flat JSON Schema object describing the fields to collect
     */
    public ElicitationCreateParams(String message, Map<String, Object> requestedSchema) {
        this(MODE_FORM, message, requestedSchema);
    }
}
