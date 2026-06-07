package com.workshop.mcp.spec;

/**
 * Client-side {@code tasks} capability declaration, exchanged during
 * initialization. Symmetric to {@link TasksCapability}: clients tell the
 * server which operations they implement and which inbound server-initiated
 * requests they will accept task augmentation on.
 *
 * @param list     non-null = client implements {@code tasks/list}
 * @param cancel   non-null = client implements {@code tasks/cancel}
 * @param requests nested toggles for {@code sampling/*} and
 *                 {@code elicitation/*}
 *
 * @since 1.0
 */
public record ClientTasksCapability(
    Capability list,
    Capability cancel,
    ClientTaskRequests requests
) {}
