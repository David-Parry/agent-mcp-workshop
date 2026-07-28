package com.workshop.mcp.spec;

/**
 * Represents the role of a participant in the MCP (Model Context Protocol) conversation.
 * <p>
 * This enum defines the different types of participants that can send messages
 * in an MCP conversation. Each role has a specific purpose and behavior within
 * the protocol.
 * </p>
 * 
 * @see Message
 * @see Annotations
 * @since 1.0
 */
public enum Role {
    /**
     * Represents a human user or client application.
     * Messages with this role originate from the user side of the conversation.
     */
    USER("user"), 
    
    /**
     * Represents an AI assistant or server application.
     * Messages with this role are responses or outputs from the MCP server.
     */
    ASSISTANT("assistant");
    
    private final String value;

    /**
     * Constructs a Role with its string representation.
     * 
     * @param value the string value used in JSON serialization
     */
    Role(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of this role for serialization.
     * 
     * @return the string representation of this role
     */
    public String getValue() {
        return value;
    }

    /**
     * Converts a string value to its corresponding Role enum constant.
     * 
     * @param value the string value to convert
     * @return the matching Role enum constant, or null if no match is found
     */
    public Role fromValue(String value) {
        for (Role role : Role.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        return null;
    }

}
