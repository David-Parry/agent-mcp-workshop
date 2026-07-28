package com.workshop.mcp.spec;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Gson adapter that reads and writes a {@link ProgressToken} without changing
 * its JSON form.
 * <p>
 * This is the {@link RequestIdTypeAdapter} pattern applied to the other value
 * the specification lets be a string or an integer. Without it, a request
 * whose {@code _meta.progressToken} is a string could not be read into its
 * params record at all.
 * </p>
 *
 * @see ProgressToken
 * @see McpGson
 * @since 1.0
 */
public class ProgressTokenTypeAdapter extends TypeAdapter<ProgressToken> {

    /**
     * Creates the adapter. Registered by {@link McpGson#create()} rather than
     * constructed directly.
     */
    public ProgressTokenTypeAdapter() {
    }

    @Override
    public void write(JsonWriter out, ProgressToken value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else if (value.isString()) {
            out.value(value.stringValue());
        } else {
            out.value(value.numberValue());
        }
    }

    @Override
    public ProgressToken read(JsonReader in) throws IOException {
        return switch (in.peek()) {
            case NULL -> {
                in.nextNull();
                yield null;
            }
            case STRING -> ProgressToken.of(in.nextString());
            case NUMBER -> {
                try {
                    yield ProgressToken.of(in.nextLong());
                } catch (NumberFormatException e) {
                    throw new JsonParseException("A numeric progress token must be an integer", e);
                }
            }
            default -> throw new JsonParseException(
                    "A progress token must be a string or an integer, but was " + in.peek());
        };
    }
}
