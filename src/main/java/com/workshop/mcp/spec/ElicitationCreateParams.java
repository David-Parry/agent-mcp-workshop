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
 * <p>
 * A {@code "url"} elicitation carries a {@code url} instead of a schema. The
 * user opens it outside the conversation, and the client answers with only an
 * {@code action}: whatever the page collected reaches the server some other
 * way, and the server finds it again through its {@code requestState}.
 * </p>
 *
 * @param mode            {@code "form"} for an in-conversation form, {@code "url"} for an out-of-band page
 * @param message         the prompt shown to the user
 * @param requestedSchema a flat JSON Schema object describing the fields to collect, in form mode
 * @param url             the page to open, in URL mode
 *
 * @see InputRequest
 * @see ElicitationCreateResult
 * @since 1.0
 */
public record ElicitationCreateParams(
    String mode,
    String message,
    Map<String, Object> requestedSchema,
    String url
) {
    /** The in-conversation form mode. */
    public static final String MODE_FORM = "form";

    /** The out-of-band URL mode. */
    public static final String MODE_URL = "url";

    /**
     * Creates elicitation parameters with no URL.
     *
     * @param mode            the elicitation mode
     * @param message         the prompt shown to the user
     * @param requestedSchema a flat JSON Schema object describing the fields to collect
     */
    public ElicitationCreateParams(String mode, String message, Map<String, Object> requestedSchema) {
        this(mode, message, requestedSchema, null);
    }

    /**
     * Creates form-mode elicitation parameters.
     *
     * @param message         the prompt shown to the user
     * @param requestedSchema a flat JSON Schema object describing the fields to collect
     */
    public ElicitationCreateParams(String message, Map<String, Object> requestedSchema) {
        this(MODE_FORM, message, requestedSchema);
    }

    /**
     * Creates URL-mode elicitation parameters.
     *
     * @param message what the page is for, shown before the user opens it
     * @param url     the page to open
     * @return the parameters
     */
    public static ElicitationCreateParams url(String message, String url) {
        return new ElicitationCreateParams(MODE_URL, message, null, url);
    }
}
