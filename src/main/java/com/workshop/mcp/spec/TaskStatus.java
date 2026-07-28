package com.workshop.mcp.spec;

/**
 * Enumerates the lifecycle states of a {@link Task}.
 * <p>
 * State transitions defined by the {@code io.modelcontextprotocol/tasks}
 * extension:
 * </p>
 * <pre>
 *   working &lt;-&gt; input_required
 *     |              |
 *     v              v
 *   { completed | failed | cancelled }   (terminal)
 * </pre>
 * <p>
 * The string values returned by {@link #getValue()} are the on-wire JSON
 * forms — record serialization uses these strings, not the enum constant
 * names, by storing them via the {@code String status} field of {@link Task}.
 * </p>
 *
 * @see Task
 * @since 1.0
 */
public enum TaskStatus {
    /** The request is currently being processed by the receiver. */
    WORKING("working"),

    /** The receiver needs additional input from the requestor to continue. */
    INPUT_REQUIRED("input_required"),

    /** Terminal: the request completed successfully and results are available. */
    COMPLETED("completed"),

    /** Terminal: the request did not complete successfully. */
    FAILED("failed"),

    /** Terminal: the request was cancelled before completion. */
    CANCELLED("cancelled");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the on-wire form of this status.
     *
     * @return the on-wire JSON string for this status
     */
    public String getValue() {
        return value;
    }

    /**
     * Reports whether this status is final, meaning no further transition can
     * follow it.
     *
     * @return {@code true} if this status is one of the three terminal states
     *         ({@code completed}, {@code failed}, {@code cancelled})
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Case-insensitive reverse lookup.
     *
     * @param value the on-wire string value
     * @return matching constant or {@code null}
     */
    public static TaskStatus fromValue(String value) {
        if (value == null) return null;
        for (TaskStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        return null;
    }
}
