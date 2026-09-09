package com.workshop.mcp.spec;

/**
 * Represents a JSON-RPC request identifier, which the specification allows to
 * be either a string or a number.
 * <p>
 * MCP clients mix both forms on a single connection. The MCP Inspector, for
 * example, mints the string id {@code "server-discover-probe-1"} for its
 * {@code server/discover} probe and {@code "listen:0"} for
 * {@code subscriptions/listen}, while using a plain integer counter for every
 * other request. A server must echo the id back in exactly the form it
 * arrived, so the two forms are kept distinct rather than coerced to a single
 * Java type.
 * </p>
 * <p>
 * Exactly one of the two components is non-null. Use {@link #of(String)} or
 * {@link #of(long)} rather than the canonical constructor.
 * </p>
 *
 * @param stringValue the id when the client sent a JSON string, otherwise null
 * @param numberValue the id when the client sent a JSON number, otherwise null
 *
 * @see RequestIdTypeAdapter
 * @since 1.0
 */
public record RequestId(String stringValue, Long numberValue) {

    /**
     * Rejects an id that is both forms at once or neither, which would make
     * the serialized form ambiguous.
     */
    public RequestId {
        if ((stringValue == null) == (numberValue == null)) {
            throw new IllegalArgumentException(
                    "A RequestId is either a string or a number, never both and never neither");
        }
    }

    /**
     * Creates a string-valued request id.
     *
     * @param value the id as sent on the wire, must not be null
     * @return the wrapped id
     */
    public static RequestId of(String value) {
        return new RequestId(value, null);
    }

    /**
     * Creates a number-valued request id.
     *
     * @param value the id as sent on the wire
     * @return the wrapped id
     */
    public static RequestId of(long value) {
        return new RequestId(null, value);
    }

    /**
     * Indicates which of the two JSON forms this id must be serialized back as.
     *
     * @return true when the id is a JSON string, false when it is a JSON number
     */
    public boolean isString() {
        return stringValue != null;
    }

    @Override
    public String toString() {
        return isString() ? stringValue : String.valueOf(numberValue);
    }
}
