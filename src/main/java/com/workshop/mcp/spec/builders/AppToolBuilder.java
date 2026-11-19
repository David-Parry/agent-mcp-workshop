package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.AppMeta;
import com.workshop.mcp.spec.AppTool;
import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.UiMeta;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builder for creating {@link AppTool} objects with a fluent API.
 * <p>
 * An {@code AppTool} is a regular MCP tool that additionally declares a
 * {@code _meta.ui.resourceUri}, signalling to the host that an interactive
 * HTML UI is available when this tool is called.
 * </p>
 *
 * <p>Example usage — building a keyword search tool with UI:</p>
 * <pre>{@code
 * AppTool tool = AppToolBuilder.builder()
 *     .withName("key_word_search")
 *     .withDescription("Searches for a keyword across all project files.")
 *     .withInputSchema(schema -> schema
 *         .withType("object")
 *         .addProperty(PropertySchemaBuilder.builder()
 *             .withKey("keyword")
 *             .withType("string")
 *             .withDescription("The keyword to search for")
 *             .required()))
 *     .withResourceUri("ui://keyword-search/mcp-app.html")
 *     .build();
 * }</pre>
 *
 * @see AppTool
 * @see AppMeta
 * @see UiMeta
 * @since 1.0
 */
public class AppToolBuilder {

    private String name;
    private String description;
    private InputSchema inputSchema;
    private Map<String, Object> outputSchema;
    private String resourceUri;
    private List<String> visibility;

    /**
     * Private constructor to enforce use of the static factory method.
     */
    private AppToolBuilder() {
    }

    /**
     * Declares the JSON Schema the tool's {@code structuredContent} matches.
     * Optional; a tool that declares one must return matching structured
     * content on every successful result.
     *
     * @param outputSchema a JSON Schema object
     * @return this builder instance for method chaining
     */
    public AppToolBuilder withOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema;
        return this;
    }

    /**
     * Creates a new {@code AppToolBuilder} instance.
     *
     * @return a new builder ready to configure
     */
    public static AppToolBuilder builder() {
        return new AppToolBuilder();
    }

    /**
     * Sets the name of the tool.
     *
     * @param name the unique tool name used when invoking it
     * @return this builder instance for method chaining
     */
    public AppToolBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the human-readable description of the tool.
     *
     * @param description describes what the tool does and how to use it
     * @return this builder instance for method chaining
     */
    public AppToolBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the input schema for the tool directly.
     *
     * @param inputSchema the pre-built schema defining the tool's parameters
     * @return this builder instance for method chaining
     */
    public AppToolBuilder withInputSchema(InputSchema inputSchema) {
        this.inputSchema = inputSchema;
        return this;
    }

    /**
     * Sets the input schema using a consumer that configures an {@link InputSchemaBuilder}.
     * <p>
     * This allows inline schema construction with a lambda expression.
     * </p>
     *
     * @param schemaBuilder a consumer that configures the input schema
     * @return this builder instance for method chaining
     */
    public AppToolBuilder withInputSchema(Consumer<InputSchemaBuilder> schemaBuilder) {
        InputSchemaBuilder builder = InputSchemaBuilder.builder();
        schemaBuilder.accept(builder);
        this.inputSchema = builder.build();
        return this;
    }

    /**
     * Sets the {@code ui://} resource URI that points to the HTML app the host will render.
     * <p>
     * The URI must use the {@code ui://} scheme. The path is arbitrary — choose a
     * structure that makes sense for your app, for example:
     * {@code ui://keyword-search/mcp-app.html}.
     * </p>
     *
     * @param resourceUri the {@code ui://} URI for the app's HTML resource
     * @return this builder instance for method chaining
     */
    public AppToolBuilder withResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
        return this;
    }

    /**
     * Restricts who may invoke the tool. Without it, hosts let both the agent
     * and the page call the tool.
     *
     * @param visibility {@code "model"}, {@code "app"}, or both
     * @return this builder instance for method chaining
     */
    public AppToolBuilder withVisibility(String... visibility) {
        this.visibility = List.of(visibility);
        return this;
    }

    /**
     * Builds and returns the configured {@link AppTool}.
     * <p>
     * The resource URI is written under {@code _meta.ui.resourceUri}.
     * </p>
     *
     * @return a new AppTool with the configured name, description, schema, and UI metadata
     * @throws IllegalStateException if name, description, resourceUri or inputSchema is missing
     */
    public AppTool build() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("name is required for AppTool");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("description is required for AppTool");
        }
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new IllegalStateException("resourceUri is required for AppTool — use withResourceUri(\"ui://...\")");
        }
        if (inputSchema == null) {
            throw new IllegalStateException("inputSchema is required for AppTool, even one that takes no arguments");
        }
        return new AppTool(name, description, inputSchema, outputSchema,
                           visibility == null ? AppMeta.of(resourceUri) : AppMeta.of(resourceUri, visibility));
    }
}
