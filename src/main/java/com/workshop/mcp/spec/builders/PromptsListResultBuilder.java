package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Prompt;
import com.workshop.mcp.spec.PromptArgument;
import com.workshop.mcp.spec.PromptsListResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link PromptsListResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of PromptsListResult objects that contain
 * lists of available prompts. It provides multiple ways to add prompts, including
 * direct addition, inline construction, and a stateful prompt building pattern.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * PromptsListResult result = PromptsListResultBuilder.builder()
 *     .withPrompt("search", "Search for items")
 *         .withPromptArgument("query", "Search query", true)
 *         .withPromptArgument("limit", "Max results", false)
 *     .withPrompt("analyze", "Analyze data")
 *         .withPromptArgument("data", "Data to analyze", true)
 *     .withNextCursor("next-page-token")
 *     .build();
 * }</pre>
 * 
 * @see PromptsListResult
 * @see Prompt
 * @see PromptArgument
 * @see PromptBuilder
 * @since 1.0
 */
public class PromptsListResultBuilder {
    private List<Prompt> prompts = new ArrayList<>();
    private String nextCursor;
    private PromptBuilder currentPromptBuilder;

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private PromptsListResultBuilder() {
    }

    /**
     * Creates a new PromptsListResultBuilder instance.
     * 
     * @return a new PromptsListResultBuilder instance
     */
    public static PromptsListResultBuilder builder() {
        return new PromptsListResultBuilder();
    }

    /**
     * Sets the complete list of prompts, replacing any existing prompts.
     * <p>
     * Note: This will also finalize any prompt currently being built via
     * the stateful building methods.
     * </p>
     * 
     * @param prompts the list of Prompt objects
     * @return this builder instance for method chaining
     */
    public PromptsListResultBuilder withPrompts(List<Prompt> prompts) {
        this.prompts = prompts;
        return this;
    }

    /**
     * Adds a single prompt to the list.
     * 
     * @param prompt the Prompt to add
     * @return this builder instance for method chaining
     */
    public PromptsListResultBuilder addPrompt(Prompt prompt) {
        this.prompts.add(prompt);
        return this;
    }

    /**
     * Adds a prompt with the specified properties.
     * 
     * @param name the name of the prompt
     * @param description the description of the prompt
     * @param arguments the list of arguments for the prompt
     * @return this builder instance for method chaining
     */
    public PromptsListResultBuilder addPrompt(String name, String description, List<PromptArgument> arguments) {
        this.prompts.add(new Prompt(name, description, arguments));
        return this;
    }

    /**
     * Adds a prompt using a PromptBuilder.
     * <p>
     * This allows for external construction of prompts using the PromptBuilder.
     * </p>
     * 
     * @param promptBuilder the PromptBuilder to build and add
     * @return this builder instance for method chaining
     */
    public PromptsListResultBuilder addPromptBuilder(PromptBuilder promptBuilder) {
        this.prompts.add(promptBuilder.build());
        return this;
    }

    /**
     * Sets the cursor for pagination.
     * 
     * @param nextCursor the cursor string for retrieving the next page of results
     * @return this builder instance for method chaining
     */
    public PromptsListResultBuilder withNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
        return this;
    }

    /**
     * Starts building a new prompt with the given name and description.
     * <p>
     * This method begins a stateful prompt building process. Any previously
     * started prompt will be finalized and added to the list. Use
     * {@link #withPromptArgument(String, String, boolean)} to add arguments
     * to this prompt.
     * </p>
     * 
     * @param name the name of the prompt
     * @param description the description of the prompt
     * @return this builder instance for method chaining
     */
    public PromptsListResultBuilder withPrompt(String name, String description) {
        // Finalize any current prompt being built
        if (currentPromptBuilder != null) {
            prompts.add(currentPromptBuilder.build());
        }
        // Start building a new prompt
        currentPromptBuilder = PromptBuilder.builder(name, description);
        return this;
    }

    /**
     * Adds an argument to the prompt currently being built.
     * <p>
     * This method must be called after {@link #withPrompt(String, String)}
     * to add arguments to the current prompt.
     * </p>
     * 
     * @param name the name of the argument
     * @param description the description of the argument
     * @param required whether the argument is required
     * @return this builder instance for method chaining
     * @throws IllegalStateException if no prompt is currently being built
     */
    public PromptsListResultBuilder withPromptArgument(String name, String description, boolean required) {
        if (currentPromptBuilder == null) {
            throw new IllegalStateException("Must call withPrompt() before adding arguments");
        }
        currentPromptBuilder.withArgument(name, description, required);
        return this;
    }

    /**
     * Builds and returns the final {@link PromptsListResult} object.
     * <p>
     * This method finalizes any prompt currently being built and creates
     * the result with all added prompts and the pagination cursor.
     * </p>
     * 
     * @return a new PromptsListResult instance with the configured prompts and cursor
     */
    public PromptsListResult build() {
        // Finalize any current prompt being built
        if (currentPromptBuilder != null) {
            prompts.add(currentPromptBuilder.build());
            currentPromptBuilder = null;
        }
        return new PromptsListResult(prompts, nextCursor);
    }
}