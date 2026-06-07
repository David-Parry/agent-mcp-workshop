package com.workshop.mcp.spec;

/**
 * Per-tool execution hints advertised in {@code tools/list} results.
 * <p>
 * Currently carries only {@code taskSupport}, the fine-grained signal that
 * sits in addition to the server-level {@link TasksCapability}. Allowed
 * values for {@code taskSupport}:
 * </p>
 * <ul>
 *   <li>{@code "required"}  — client MUST task-augment calls to this tool</li>
 *   <li>{@code "optional"} — client MAY task-augment</li>
 *   <li>{@code "forbidden"} — client MUST NOT task-augment (this is also the
 *       default when the field is absent)</li>
 * </ul>
 *
 * @param taskSupport one of {@code "required"}, {@code "optional"},
 *                    {@code "forbidden"}, or {@code null}
 *
 * @see AppTool
 * @since 1.0
 */
public record ToolExecution(String taskSupport) {

    /** Convenience constant for the {@code "optional"} declaration. */
    public static ToolExecution optional() {
        return new ToolExecution("optional");
    }

    /** Convenience constant for the {@code "required"} declaration. */
    public static ToolExecution required() {
        return new ToolExecution("required");
    }

    /** Convenience constant for the {@code "forbidden"} declaration. */
    public static ToolExecution forbidden() {
        return new ToolExecution("forbidden");
    }
}
