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
 *
 * @since 1.0
 */
public final class ResultType {

    private ResultType() {
        throw new AssertionError("ResultType class should not be instantiated");
    }

    /** An ordinary, final result. */
    public static final String COMPLETE = "complete";

    /**
     * An interim result asking the client for information the server needs
     * before it can finish. See {@link InputRequiredResult}.
     */
    public static final String INPUT_REQUIRED = "input_required";

    /** A handle to work the server is performing in the background. */
    public static final String TASK = "task";
}
