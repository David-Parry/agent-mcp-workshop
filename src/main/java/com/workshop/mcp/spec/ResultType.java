package com.workshop.mcp.spec;

/**
 * The {@code resultType} discriminator required on every result in protocol
 * revision {@code 2026-07-28}.
 * <p>
 * Clients built on the reference SDK read this field off the raw JSON before
 * any schema validation runs, and reject a result that omits it. The
 * "absent means complete" bridge applies only to servers speaking an earlier
 * revision.
 * </p>
 * <p>
 * The core protocol defines two values, {@link #COMPLETE} and
 * {@link #INPUT_REQUIRED}. Extensions contribute the rest: {@link #TASK}
 * comes from {@code io.modelcontextprotocol/tasks}. The set a client will
 * accept is the core pair plus the values of whichever extensions it
 * negotiated, so a value is only legal on the wire once the extension that
 * defines it has been declared on both sides.
 * </p>
 *
 * @since 1.0
 */
public final class ResultType {

    private ResultType() {
        throw new AssertionError("ResultType class should not be instantiated");
    }

    /** An ordinary, final result. Defined by the core protocol. */
    public static final String COMPLETE = "complete";

    /**
     * An interim result asking the client for information the server needs
     * before it can finish. Defined by the core protocol.
     * See {@link InputRequiredResult}.
     */
    public static final String INPUT_REQUIRED = "input_required";

    /**
     * A handle to work the server is performing in the background.
     * Contributed by the {@code io.modelcontextprotocol/tasks} extension, so
     * it may only be sent to a client that declared that extension.
     */
    public static final String TASK = "task";
}
