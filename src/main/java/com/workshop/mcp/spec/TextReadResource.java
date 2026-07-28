package com.workshop.mcp.spec;

/**
 * Represents a text resource that has been read from the MCP (Model Context Protocol) system.
 * <p>
 * TextReadResource contains the actual content of a resource after it has been
 * retrieved. It includes the resource's URI for identification, MIME type for
 * content type information, and the actual text content.
 * </p>
 * 
 * @param uri the URI identifying the resource
 * @param mimeType the MIME type of the resource content
 * @param text the actual text content of the resource
 * 
 * @see ReadResourceResult
 * @see Resource
 * @since 1.0
 */
public record TextReadResource(String uri, String mimeType, String text) {

}
