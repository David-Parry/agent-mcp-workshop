package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.CreateSamplingMessage;
import com.workshop.mcp.spec.Message;
import com.workshop.mcp.spec.ModelPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder class for creating CreateSamplingMessage instances following the Builder pattern.
 * This builder ensures that at least one Message is present in the messages list,
 * while all other fields can be null.
 */
public class CreateSamplingMessageBuilder {
    private List<Message> messages;
    private ModelPreferences modelPreferences;
    private String systemPrompt;
    private String includeContext;
    private Double temperature;
    private int maxTokens;
    private List<String> stopSequences;
    private Map<String, Object> metadata;

    /**
     * Creates a new CreateSamplingMessageBuilder instance.
     */
    public CreateSamplingMessageBuilder() {
        this.messages = new ArrayList<>();
    }

    /**
     * Adds a message to the messages list.
     * 
     * @param message the message to add
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder addMessage(Message message) {
        if (message != null) {
            this.messages.add(message);
        }
        return this;
    }

    /**
     * Sets the messages list. This replaces any existing messages.
     * 
     * @param messages the list of messages
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder messages(List<Message> messages) {
        if (messages != null) {
            this.messages = new ArrayList<>(messages);
        }
        return this;
    }

    /**
     * Sets the model preferences.
     * 
     * @param modelPreferences the model preferences
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder modelPreferences(ModelPreferences modelPreferences) {
        this.modelPreferences = modelPreferences;
        return this;
    }

    /**
     * Sets the system prompt.
     * 
     * @param systemPrompt the system prompt
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /**
     * Sets the include context.
     * 
     * @param includeContext the include context
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder includeContext(String includeContext) {
        this.includeContext = includeContext;
        return this;
    }

    /**
     * Sets the temperature.
     * 
     * @param temperature the temperature value
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    /**
     * Sets the maximum tokens.
     * 
     * @param maxTokens the maximum number of tokens
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    /**
     * Sets the stop sequences.
     * 
     * @param stopSequences the list of stop sequences
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder stopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
        return this;
    }

    /**
     * Adds a single stop sequence to the stop sequences list.
     * 
     * @param stopSequence the stop sequence to add
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder addStopSequence(String stopSequence) {
        if (this.stopSequences == null) {
            this.stopSequences = new ArrayList<>();
        }
        if (stopSequence != null) {
            this.stopSequences.add(stopSequence);
        }
        return this;
    }

    /**
     * Sets the metadata map.
     * 
     * @param metadata the metadata map
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder metadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Adds a single metadata entry.
     * 
     * @param key the metadata key
     * @param value the metadata value
     * @return this builder instance for method chaining
     */
    public CreateSamplingMessageBuilder addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        if (key != null) {
            this.metadata.put(key, value);
        }
        return this;
    }

    /**
     * Builds the CreateSamplingMessage instance.
     * 
     * @return the built CreateSamplingMessage instance
     * @throws IllegalStateException if no messages have been added
     */
    public CreateSamplingMessage build() {
        if (messages.isEmpty()) {
            throw new IllegalStateException("At least one Message must be provided");
        }
        
        return new CreateSamplingMessage(
            new ArrayList<>(messages), // Create defensive copy
            modelPreferences,
            systemPrompt,
            includeContext,
            temperature,
            maxTokens,
            stopSequences != null ? new ArrayList<>(stopSequences) : null,
            metadata != null ? new HashMap<>(metadata) : null
        );
    }
}