package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of reading resources in the MCP (Model Context Protocol) system.
 * <p>
 * ReadResourceResult contains the contents of one or more resources that have been
 * read from the server. It includes an error flag to indicate whether the read
 * operation encountered any errors.
 * </p>
 * 
 * @param contents a list of text resources that were successfully read
 * @param isError indicates whether an error occurred during the read operation
 * 
 * @see TextReadResource
 * @since 1.0
 */
public record ReadResourceResult(List<TextReadResource> contents, boolean isError) {
    /**
     * Creates a successful read result with no errors.
     * 
     * @param contents the list of text resources that were read
     */
    public ReadResourceResult(List<TextReadResource> contents) {
        this(contents, false);
    }
}
