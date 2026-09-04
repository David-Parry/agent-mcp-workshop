package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents the capabilities offered by an MCP server.
 * <p>
 * Revision {@code 2026-07-28} fixes this object at exactly these seven
 * optional slots. Note what is absent: the pre-{@code 2026-07-28} top-level
 * {@code tasks} slot was dropped, and anything beyond the core protocol —
 * tasks included — is now declared under {@code extensions}, keyed by
 * identifier.
 * </p>
 * <p>
 * {@code resources.subscribe} survives even though the
 * {@code resources/subscribe} method itself was removed; it now gates the
 * {@code resourceSubscriptions} field of {@code subscriptions/listen}.
 * </p>
 *
 * @param experimental non-standard capabilities, keyed by name
 * @param logging      non-null when the server can emit {@code notifications/message}
 * @param completions  non-null when the server answers {@code completion/complete}
 * @param prompts      prompt support, optionally with {@code listChanged}
 * @param resources    resource support, optionally with {@code listChanged} and {@code subscribe}
 * @param tools        tool support, optionally with {@code listChanged}
 * @param extensions   extension declarations, keyed by identifier
 *
 * @see DiscoverResult
 * @see MetaKeys#TASKS_EXTENSION
 * @since 1.0
 */
public record ServerCapabilities(
    Map<String, Object> experimental,
    Capability logging,
    Capability completions,
    Capability prompts,
    Capability resources,
    Capability tools,
    Map<String, Object> extensions
) {}
