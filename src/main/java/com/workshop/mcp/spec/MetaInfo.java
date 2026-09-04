package com.workshop.mcp.spec;

/**
 * Represents metadata information for tracking progress in the MCP (Model Context Protocol) system.
 * <p>
 * MetaInfo provides additional context for operations, particularly for tracking
 * the progress of long-running operations through progress tokens. This allows
 * clients to monitor and report on the status of ongoing tasks.
 * </p>
 * <p>
 * On revision {@code 2026-07-28} the same {@code _meta} object also carries the
 * stateless lifecycle envelope. Those keys are namespaced identifiers that
 * cannot be record component names, so they are read separately by
 * {@link JsonRpcMessageDeserializer#deserializeEnvelope(JsonRpcRequest)}.
 * {@code progressToken} is boxed so its absence reads as null rather than
 * zero, which is a real token value.
 * </p>
 *
 * @param progressToken a unique token used to track the progress of an operation
 *
 * @see RequestEnvelope
 * @since 1.0
 */
public record MetaInfo(
    Integer progressToken
) {}
