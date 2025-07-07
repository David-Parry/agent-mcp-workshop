package com.workshop.mcp.spec;

/**
 * Represents sampling capabilities in the MCP (Model Context Protocol) system.
 * <p>
 * SamplingCapability indicates support for sampling-related features. This empty
 * record serves as a marker to indicate that sampling capabilities are supported,
 * with the possibility of future extensions to include specific sampling parameters
 * or configurations.
 * </p>
 * 
 * @see ClientCapabilities
 * @see ServerCapabilities
 * @since 1.0
 */
public record SamplingCapability() {}
