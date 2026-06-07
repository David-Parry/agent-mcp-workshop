package com.workshop.mcp.spec;

/**
 * Client-side {@code tasks.requests} declaration. Mirror of
 * {@link ServerTaskRequests} but listing the client-handled request types.
 *
 * @param sampling     nested toggle for {@code sampling/*} task augmentation
 * @param elicitation  nested toggle for {@code elicitation/*} task augmentation
 *
 * @since 1.0
 */
public record ClientTaskRequests(
    SamplingTaskRequests sampling,
    ElicitationTaskRequests elicitation
) {}
