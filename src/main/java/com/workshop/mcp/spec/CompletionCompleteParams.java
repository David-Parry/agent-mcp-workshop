package com.workshop.mcp.spec;

/**
 * Represents parameters for a completion request in the MCP (Model Context Protocol) system.
 * <p>
 * CompletionCompleteParams contains all the information needed to generate a completion,
 * including the argument to complete, an optional reference to a prompt template,
 * and metadata for tracking the operation.
 * </p>
 * 
 * @param _meta optional metadata information for the completion request, including progress tracking
 * @param argument the completion argument containing the context for completion
 * @param ref an optional reference to a prompt template to use for completion
 * 
 * @see CompletionArgument
 * @see PromptRef
 * @see MetaInfo
 * @since 1.0
 */
public record CompletionCompleteParams(
    MetaInfo _meta,
    CompletionArgument argument,
    PromptRef ref
) {}
