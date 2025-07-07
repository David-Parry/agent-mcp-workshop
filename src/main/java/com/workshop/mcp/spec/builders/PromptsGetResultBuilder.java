package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Message;
import com.workshop.mcp.spec.MessageContent;
import com.workshop.mcp.spec.PromptsGetResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating {@link PromptsGetResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of PromptsGetResult objects that represent
 * the expanded content of a prompt template. It provides convenient methods for
 * adding messages and handling prompt arguments.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * PromptsGetResult result = PromptsGetResultBuilder.builder()
 *     .withDescription("Search prompt for finding items")
 *     .addTextMessage("user", "Please search for the following:")
 *     .addTextMessage("assistant", "I'll help you search for that.")
 *     .build();
 * }</pre>
 * 
 * @see PromptsGetResult
 * @see Message
 * @see MessageContent
 * @since 1.0
 */
public class PromptsGetResultBuilder {
    private String description;
    private List<Message> messages = new ArrayList<>();

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private PromptsGetResultBuilder() {
    }

    /**
     * Creates a new PromptsGetResultBuilder instance.
     * 
     * @return a new PromptsGetResultBuilder instance
     */
    public static PromptsGetResultBuilder builder() {
        return new PromptsGetResultBuilder();
    }

    /**
     * Sets the description for the prompt result.
     * 
     * @param description a description of the prompt result or its purpose
     * @return this builder instance for method chaining
     */
    public PromptsGetResultBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the complete list of messages, replacing any existing messages.
     * 
     * @param messages the list of Message objects
     * @return this builder instance for method chaining
     */
    public PromptsGetResultBuilder withMessages(List<Message> messages) {
        this.messages = messages;
        return this;
    }

    /**
     * Adds a single message to the result.
     * 
     * @param message the Message to add
     * @return this builder instance for method chaining
     */
    public PromptsGetResultBuilder addMessage(Message message) {
        this.messages.add(message);
        return this;
    }

    /**
     * Adds a message with the specified role and content.
     * 
     * @param role the role of the message sender (e.g., "user", "assistant")
     * @param content the MessageContent object containing the message data
     * @return this builder instance for method chaining
     */
    public PromptsGetResultBuilder addMessage(String role, MessageContent content) {
        this.messages.add(new Message(role, content));
        return this;
    }

    /**
     * Adds a text message with the specified role.
     * <p>
     * This is a convenience method that creates a MessageContent with type "text".
     * </p>
     * 
     * @param role the role of the message sender (e.g., "user", "assistant")
     * @param text the text content of the message
     * @return this builder instance for method chaining
     */
    public PromptsGetResultBuilder addTextMessage(String role, String text) {
        this.messages.add(new Message(role, new MessageContent(text, "text")));
        return this;
    }

    /**
     * Adds a text message with argument substitution.
     * <p>
     * This method is specifically designed to handle prompt arguments,
     * particularly for keyword-based prompts. If arguments are provided
     * and contain a "keyword" entry, it will be appended to the text
     * in single quotes.
     * </p>
     * 
     * @param role the role of the message sender
     * @param text the base text of the message
     * @param arguments a map of arguments to potentially substitute
     * @return this builder instance for method chaining
     */
    public PromptsGetResultBuilder addTextMessage(String role, String text, Map<String,String> arguments) {
        if (arguments != null && !arguments.isEmpty()) {
            text += "'" + arguments.get("keyword") + "'";
        }
        this.messages.add(new Message(role, new MessageContent(text, "text")));
        return this;
    }

    /**
     * Builds and returns the final {@link PromptsGetResult} object.
     * <p>
     * This method validates that the required description field is set
     * before creating the result.
     * </p>
     * 
     * @return a new PromptsGetResult instance with the configured properties
     * @throws IllegalStateException if description is not set
     */
    public PromptsGetResult build() {
        if (description == null) {
            throw new IllegalStateException("Description is required");
        }
        return new PromptsGetResult(description, messages);
    }
}