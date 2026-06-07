package com.workshop.mcp.spec;

/**
 * Client-side toggle indicating which {@code sampling/*} request types may be
 * task-augmented by a server.
 *
 * @param createMessage non-null = server may augment {@code sampling/createMessage}
 *
 * @since 1.0
 */
public record SamplingTaskRequests(Capability createMessage) {}
