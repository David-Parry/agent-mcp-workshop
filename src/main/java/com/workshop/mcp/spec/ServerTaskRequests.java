package com.workshop.mcp.spec;

/**
 * Server-side {@code tasks.requests} declaration. Exhaustive per spec: a
 * request category that is absent here cannot be task-augmented.
 *
 * @param tools nested toggle for {@code tools/*} task augmentation
 *
 * @since 1.0
 */
public record ServerTaskRequests(ToolsTaskRequests tools) {}
