package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Prompt;
import com.workshop.mcp.spec.PromptArgument;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link Prompt} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of Prompt objects by providing
 * a chainable interface for setting properties and adding arguments.
 * The builder ensures that prompts are created with valid configurations
 * and provides convenient methods for adding arguments individually or in bulk.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * Prompt prompt = PromptBuilder.builder("search", "Search for items")
 *     .withArgument("query", "The search query", true)
 *     .withArgument("limit", "Maximum results", false)
 *     .build();
 * }</pre>
 * 
 * @see Prompt
 * @see PromptArgument
 * @since 1.0
 */
public class PromptBuilder {
    private final String name;
    private final String description;
    private final List<PromptArgument> arguments = new ArrayList<>();

    /**
     * Private constructor to enforce the use of the static builder method.
     * 
     * @param name the unique name of the prompt
     * @param description the description of the prompt's purpose
     */
    private PromptBuilder(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Creates a new PromptBuilder instance with the required name and description.
     * 
     * @param name the unique name of the prompt
     * @param description the description of the prompt's purpose
     * @return a new PromptBuilder instance
     * @throws NullPointerException if name or description is null
     */
    public static PromptBuilder builder(String name, String description) {
        return new PromptBuilder(name, description);
    }

    /**
     * Adds a single argument to the prompt being built.
     * 
     * @param name the name of the argument
     * @param description the description of the argument's purpose
     * @param required whether the argument is required
     * @return this builder instance for method chaining
     */
    public PromptBuilder withArgument(String name, String description, boolean required) {
        arguments.add(new PromptArgument(name, description, required));
        return this;
    }

    /**
     * Adds multiple arguments to the prompt being built.
     * 
     * @param arguments a list of PromptArgument objects to add
     * @return this builder instance for method chaining
     * @throws NullPointerException if the arguments list is null
     */
    public PromptBuilder withArguments(List<PromptArgument> arguments) {
        this.arguments.addAll(arguments);
        return this;
    }

    /**
     * Builds and returns the final {@link Prompt} object.
     * <p>
     * This method creates a new Prompt instance with the configured name,
     * description, and arguments. A defensive copy of the arguments list
     * is made to ensure immutability of the built object.
     * </p>
     * 
     * @return a new Prompt instance with the configured properties
     */
    public Prompt build() {
        return new Prompt(name, description, new ArrayList<>(arguments));
    }
}