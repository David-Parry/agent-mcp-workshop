package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents the capabilities declared by an MCP client.
 * <p>
 * Before revision {@code 2026-07-28} a client sent this once, inside
 * {@code initialize}. It now arrives on every request under
 * {@code params._meta["io.modelcontextprotocol/clientCapabilities"]}, so the
 * server reads it per request rather than remembering it.
 * </p>
 * <p>
 * Capabilities beyond the core protocol live in {@code extensions}, keyed by
 * identifier — {@code io.modelcontextprotocol/tasks} and
 * {@code io.modelcontextprotocol/ui} are the two this workshop cares about.
 * The pre-{@code 2026-07-28} top-level {@code tasks} slot is gone.
 * </p>
 *
 * @param sampling    the sampling support declared by the client
 * @param roots       the roots support declared by the client
 * @param elicitation the elicitation modes the client can render
 * @param extensions  optional extension declarations, keyed by identifier
 *
 * @see SamplingCapability
 * @see RootsCapability
 * @see Elicitation
 * @see RequestEnvelope
 * @since 1.0
 */
public record ClientCapabilities(
    SamplingCapability sampling,
    RootsCapability roots,
    Elicitation elicitation,
    Map<String, Object> extensions
) {

    /**
     * Reports whether the client declared the given extension on this request.
     *
     * @param identifier the extension identifier, e.g. {@link MetaKeys#TASKS_EXTENSION}
     * @return true when the client declared it
     */
    public boolean hasExtension(String identifier) {
        return extensions != null && extensions.containsKey(identifier);
    }
}
