package com.workshop.mcp.spec;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Gson adapter that reads and writes a {@link RequestId} without changing its
 * JSON form.
 * <p>
 * Gson's default handling would force a choice: typing the field as
 * {@code Long} rejects string ids outright, while typing it as {@code Object}
 * parses every number as a {@code Double} and would echo the id {@code 1} back
 * as {@code 1.0}. Both break correlation on the client. This adapter reads
 * whichever form arrived and writes the same form back.
 * </p>
 *
 * @see RequestId
 * @see McpGson
 * @since 1.0
 */
public class RequestIdTypeAdapter extends TypeAdapter<RequestId> {

    /**
     * Creates the adapter. Registered by {@link McpGson#create()} rather than
     * constructed directly.
     */
    public RequestIdTypeAdapter() {
    }

    @Override
    public void write(JsonWriter out, RequestId value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else if (value.isString()) {
            out.value(value.stringValue());
        } else {
            out.value(value.numberValue());
        }
    }

    @Override
    public RequestId read(JsonReader in) throws IOException {
        return switch (in.peek()) {
            case NULL -> {
                in.nextNull();
                yield null;
            }
            case STRING -> RequestId.of(in.nextString());
            case NUMBER -> RequestId.of(in.nextLong());
            default -> throw new JsonParseException(
                    "A JSON-RPC id must be a string or a number, but was " + in.peek());
        };
    }
}
