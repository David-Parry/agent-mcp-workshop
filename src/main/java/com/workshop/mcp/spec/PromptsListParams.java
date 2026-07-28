package com.workshop.mcp.spec;

/**
 * Represents parameters for listing available prompts in the MCP (Model Context Protocol) system.
 * <p>
 * PromptsListParams is used when requesting a list of all prompts that the server
 * provides. The parameters may include metadata for tracking the progress of
 * the listing operation.
 * </p>
 * 
 * @param _meta optional metadata information for the prompts list request, including progress tracking
 * 
 * @see PromptsListResult
 * @see MetaInfo
 * @since 1.0
 */
public record PromptsListParams(
    MetaInfo _meta
) {}
