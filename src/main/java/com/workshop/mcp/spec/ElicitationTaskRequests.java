package com.workshop.mcp.spec;

/**
 * Client-side toggle indicating which {@code elicitation/*} request types may
 * be task-augmented by a server.
 *
 * @param create non-null = server may augment {@code elicitation/create}
 *
 * @since 1.0
 */
public record ElicitationTaskRequests(Capability create) {}
