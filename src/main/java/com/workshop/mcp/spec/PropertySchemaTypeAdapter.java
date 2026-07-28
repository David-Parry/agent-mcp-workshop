package com.workshop.mcp.spec;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Gson adapter that writes a {@link PropertySchema} as a JSON Schema property,
 * emitting only the keywords JSON Schema defines.
 * <p>
 * A {@code PropertySchema} carries two components that exist for the builder's
 * benefit and not for the wire. {@link PropertySchema#key()} names the property,
 * but a property is already named by its position in
 * {@link InputSchema#properties()}. {@link PropertySchema#isRequired()} marks it
 * as mandatory, but that is already expressed by
 * {@link InputSchema#required()}. Gson's default reflective serialization has no
 * way to know this and writes both, so the same two facts appeared twice in
 * every {@code tools/list} result:
 * </p>
 * <pre>{@code
 * "keyword": {
 *   "key": "keyword",          // already the enclosing map key
 *   "type": "string",
 *   "description": "...",
 *   "isRequired": true         // already listed in "required"
 * }
 * }</pre>
 * <p>
 * Beyond being redundant, neither is a JSON Schema keyword, so a validating
 * client is entitled to reject them. {@code isRequired} is the more dangerous
 * of the two: JSON Schema draft-03 defined a per-property {@code required}
 * boolean before the draft-04 {@code required} array replaced it, so a property
 * that says {@code "isRequired": false} beside a schema whose {@code required}
 * array names it is ambiguous rather than merely noisy. This adapter writes
 * {@code type} and {@code description} and nothing else.
 * </p>
 *
 * @see PropertySchema
 * @see InputSchema
 * @see McpGson
 * @since 1.0
 */
public class PropertySchemaTypeAdapter extends TypeAdapter<PropertySchema> {

    /**
     * Creates the adapter. Registered by {@link McpGson#create()} rather than
     * constructed directly.
     */
    public PropertySchemaTypeAdapter() {
    }

    @Override
    public void write(JsonWriter out, PropertySchema value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        if (value.type() != null) {
            out.name("type").value(value.type());
        }
        if (value.description() != null) {
            out.name("description").value(value.description());
        }
        out.endObject();
    }

    /**
     * Reads a JSON Schema property.
     * <p>
     * The result carries a null {@link PropertySchema#key()} and a false
     * {@link PropertySchema#isRequired()}, because neither is recoverable from
     * the property object alone — both live in the enclosing
     * {@link InputSchema}. Nothing in this server parses a schema it was sent,
     * so this exists to keep the adapter symmetric rather than to be relied on.
     * </p>
     *
     * @param in the reader positioned at the property object
     * @return the property, less the two components the enclosing schema owns
     * @throws IOException if the underlying reader fails
     */
    @Override
    public PropertySchema read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String type = null;
        String description = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "type" -> type = in.nextString();
                case "description" -> description = in.nextString();
                default -> in.skipValue();
            }
        }
        in.endObject();
        return new PropertySchema(null, type, description, false);
    }
}
