package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.AppMeta;
import com.workshop.mcp.spec.AppTool;
import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.ToolExecution;
import com.workshop.mcp.spec.UiMeta;

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
    private String resourceUri;
    private ToolExecution execution;

    /**
     * Private constructor to enforce use of the static factory method.
     */
    private AppToolBuilder() {
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
     * Sets the per-tool task-execution declaration that appears in
     * {@code tools/list} under {@code execution.taskSupport}.
     * <p>
     * Per MCP 2025-11-25 spec § "Tool-Level Negotiation", this signals whether
     * task augmentation is {@code "required"}, {@code "optional"}, or
     * {@code "forbidden"} for this tool. Servers that do not also declare
     * {@code tasks.requests.tools.call} in their capabilities will have this
     * value ignored by clients.
     * </p>
     *
     * @param execution the per-tool execution hint
     * @return this builder instance for method chaining
     * @see ToolExecution
     */
    public AppToolBuilder withExecution(ToolExecution execution) {
        this.execution = execution;
        return this;
    }

    /**
     * Builds and returns the configured {@link AppTool}.
     *
     * @return a new AppTool with the configured name, description, schema, and UI metadata
     * @throws IllegalStateException if name, description, or resourceUri is missing
     */
    public AppTool build() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("name is required for AppTool");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("description is required for AppTool");
        }
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new IllegalStateException("resourceUri is required for AppTool — use withResourceUri(\"ui://...\")" );
        }
        AppMeta meta = new AppMeta(new UiMeta(resourceUri));
        return new AppTool(name, description, inputSchema, meta, execution);
    }
}
