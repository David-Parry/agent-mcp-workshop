package com.workshop.mcp.spec;

/**
 * Represents metadata information for tracking progress in the MCP (Model Context Protocol) system.
 * <p>
 * MetaInfo provides additional context for operations, particularly for tracking
 * the progress of long-running operations through progress tokens. This allows
 * clients to monitor and report on the status of ongoing tasks.
 * </p>
 * 
 * @param progressToken a unique token used to track the progress of an operation
 * 
 * @since 1.0
 */
public record MetaInfo(
    int progressToken
) {}
