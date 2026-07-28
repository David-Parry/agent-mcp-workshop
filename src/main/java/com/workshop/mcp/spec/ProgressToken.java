package com.workshop.mcp.spec;

/**
 * Represents a progress token, which a client puts in a request's
 * {@code _meta} so that progress notifications about that request can name it.
 * <p>
 * The specification allows a token to be either a string or an integer, the
 * same two forms as a {@link RequestId}, and for the same reason the two are
 * kept distinct: a notification must quote the token back in exactly the
 * form it arrived, or the client cannot match it to its request. Typing the
 * field as a Java number would reject every string token outright.
 * </p>
 * <p>
 * Exactly one of the two components is non-null. Use {@link #of(String)} or
 * {@link #of(long)} rather than the canonical constructor.
 * </p>
 *
 * @param stringValue the token when the client sent a JSON string, otherwise null
 * @param numberValue the token when the client sent a JSON integer, otherwise null
 *
 * @see ProgressTokenTypeAdapter
 * @see MetaInfo
 * @since 1.0
 */
public record ProgressToken(String stringValue, Long numberValue) {

    /**
     * Rejects a token that is both forms at once or neither, which would make
     * the serialized form ambiguous.
     */
    public ProgressToken {
        if ((stringValue == null) == (numberValue == null)) {
            throw new IllegalArgumentException(
                    "A ProgressToken is either a string or an integer, never both and never neither");
        }
    }

    /**
     * Creates a string-valued progress token.
     *
     * @param value the token as sent on the wire, must not be null
     * @return the wrapped token
     */
    public static ProgressToken of(String value) {
        return new ProgressToken(value, null);
    }

    /**
     * Creates an integer-valued progress token.
     *
     * @param value the token as sent on the wire
     * @return the wrapped token
     */
    public static ProgressToken of(long value) {
        return new ProgressToken(null, value);
    }

    /**
     * Indicates which of the two JSON forms this token must be serialized back as.
     *
     * @return true when the token is a JSON string, false when it is a JSON integer
     */
    public boolean isString() {
        return stringValue != null;
    }

    @Override
    public String toString() {
        return isString() ? stringValue : String.valueOf(numberValue);
    }
}
