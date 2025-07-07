package com.workshop.mcp.spec;

/**
 * Represents parameters for reading a resource in the MCP (Model Context Protocol) system.
 * <p>
 * This record encapsulates the parameters needed to read a specific resource,
 * including the resource URI and optional metadata for tracking the operation.
 * </p>
 * 
 * @param _meta optional metadata information for tracking the read operation
 * @param uri the URI of the resource to read
 * 
 * @see ReadResourceResult
 * @see Resource
 * @see MetaInfo
 * @since 1.0
 */
public record ReadResourceParam(MetaInfo _meta, String uri) {
}
