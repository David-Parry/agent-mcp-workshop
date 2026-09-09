package com.workshop.mcp.tools;

import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.ToolCallParams;
import com.workshop.mcp.spec.ToolCallResult;

import java.util.Set;

/**
 * A tool this server exposes through {@code tools/list} and runs through
 * {@code tools/call}.
 * <p>
 * Implementations must be stateless. Revision {@code 2026-07-28} removed
 * protocol sessions, so nothing a caller supplies on one request may still be
 * around on the next. Anything the tool needs is either declared in its
 * {@link #schema()} and read from the call's arguments, or resolved by the
 * router for that one call and handed to {@link #call} — never held in a field.
 * </p>
 *
 * @since 1.0
 */
public interface Tool {

    /**
     * The tool's wire name, as it appears in {@code tools/list}.
     *
     * @return the tool name
     */
    String name();

    /**
     * A human-readable description for the model choosing whether to call it.
     *
     * @return the tool description
     */
    String description();

    /**
     * The JSON Schema for the tool's arguments.
     *
     * @return the input schema
     */
    InputSchema schema();

    /**
     * Runs the tool for exactly one call.
     *
     * @param toolCallParams the call parameters, whose arguments drive the run
     * @param directories    the directories resolved for this call — from the
     *                       {@code directory} argument, from an elicited path,
     *                       or the working-directory fallback. Never remembered
     *                       between calls; a tool that needs them next time is
     *                       given them again.
     * @return the result to hand back to the caller
     */
    ToolCallResult call(ToolCallParams toolCallParams, Set<String> directories);
}
