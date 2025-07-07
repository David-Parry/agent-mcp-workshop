package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.Tool;
import com.workshop.mcp.spec.ToolsListResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builder for creating {@link ToolsListResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of ToolsListResult objects that contain
 * lists of available tools. It provides multiple ways to add tools, including
 * direct addition, inline construction, and nested builder patterns.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * ToolsListResult result = ToolsListResultBuilder.builder()
 *     .addTool("search", "Search for items", schema -> schema
 *         .addProperty("query", "string", "Search query", true))
 *     .addToolWithBuilder(tool -> tool
 *         .withName("calculate")
 *         .withDescription("Perform calculations")
 *         .withInputSchema(schema -> schema
 *             .addProperty("expression", "string", "Math expression", true)))
 *     .build();
 * }</pre>
 * 
 * @see ToolsListResult
 * @see Tool
 * @see InputSchemaBuilder
 * @since 1.0
 */
public class ToolsListResultBuilder {
    private List<Tool> tools = new ArrayList<>();

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private ToolsListResultBuilder() {
    }

    /**
     * Creates a new ToolsListResultBuilder instance.
     * 
     * @return a new ToolsListResultBuilder instance
     */
    public static ToolsListResultBuilder builder() {
        return new ToolsListResultBuilder();
    }

    /**
     * Sets the complete list of tools, replacing any existing tools.
     * 
     * @param tools the list of Tool objects
     * @return this builder instance for method chaining
     */
    public ToolsListResultBuilder withTools(List<Tool> tools) {
        this.tools = tools;
        return this;
    }

    /**
     * Adds a single tool to the list.
     * 
     * @param tool the Tool to add
     * @return this builder instance for method chaining
     */
    public ToolsListResultBuilder addTool(Tool tool) {
        this.tools.add(tool);
        return this;
    }

    /**
     * Adds a tool with the specified properties.
     * 
     * @param name the name of the tool
     * @param description the description of the tool
     * @param inputSchema the input schema for the tool, may be null
     * @return this builder instance for method chaining
     */
    public ToolsListResultBuilder addTool(String name, String description, InputSchema inputSchema) {
        this.tools.add(new Tool(name, description, inputSchema));
        return this;
    }

    /**
     * Adds a tool with an InputSchema built using a consumer function.
     * <p>
     * This method allows inline construction of the input schema using
     * a lambda expression or method reference.
     * </p>
     * 
     * @param name the name of the tool
     * @param description the description of the tool
     * @param schemaBuilder a consumer that configures an InputSchemaBuilder
     * @return this builder instance for method chaining
     */
    public ToolsListResultBuilder addTool(String name, String description, Consumer<InputSchemaBuilder> schemaBuilder) {
        InputSchemaBuilder builder = InputSchemaBuilder.builder();
        schemaBuilder.accept(builder);
        return addTool(name, description, builder.build());
    }

    /**
     * Adds a tool using a fluent builder pattern for all components.
     * <p>
     * This method provides maximum flexibility by allowing complete
     * configuration of the tool through a nested builder.
     * </p>
     * 
     * @param toolBuilder a consumer that configures a ToolBuilder
     * @return this builder instance for method chaining
     */
    public ToolsListResultBuilder addToolWithBuilder(Consumer<ToolBuilder> toolBuilder) {
        ToolBuilder builder = new ToolBuilder();
        toolBuilder.accept(builder);
        return addTool(builder.build());
    }

    /**
     * Builds and returns the final {@link ToolsListResult} object.
     * 
     * @return a new ToolsListResult instance containing all added tools
     */
    public ToolsListResult build() {
        return new ToolsListResult(tools);
    }

    /**
     * Inner builder class for building {@link Tool} objects fluently.
     * <p>
     * This builder is used in conjunction with {@link #addToolWithBuilder(Consumer)}
     * to provide a nested builder pattern for constructing tools with all their properties.
     * </p>
     * 
     * @since 1.0
     */
    public static class ToolBuilder {
        private String name;
        private String description;
        private InputSchema inputSchema;

        /**
         * Creates a new ToolBuilder instance.
         */
        public ToolBuilder() {
        }

        /**
         * Sets the name of the tool being built.
         * 
         * @param name the tool name
         * @return this builder instance for method chaining
         */
        public ToolBuilder withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the tool being built.
         * 
         * @param description the tool description
         * @return this builder instance for method chaining
         */
        public ToolBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the input schema for the tool being built.
         * 
         * @param inputSchema the input schema
         * @return this builder instance for method chaining
         */
        public ToolBuilder withInputSchema(InputSchema inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        /**
         * Sets the input schema using a consumer function.
         * <p>
         * This allows inline construction of the input schema.
         * </p>
         * 
         * @param schemaBuilder a consumer that configures an InputSchemaBuilder
         * @return this builder instance for method chaining
         */
        public ToolBuilder withInputSchema(Consumer<InputSchemaBuilder> schemaBuilder) {
            InputSchemaBuilder builder = InputSchemaBuilder.builder();
            schemaBuilder.accept(builder);
            this.inputSchema = builder.build();
            return this;
        }

        /**
         * Builds and returns the final {@link Tool} object.
         * 
         * @return a new Tool instance with the configured properties
         * @throws IllegalStateException if name or description is not set
         */
        public Tool build() {
            if (name == null || description == null) {
                throw new IllegalStateException("Name and description are required for Tool");
            }
            return new Tool(name, description, inputSchema);
        }
    }
}