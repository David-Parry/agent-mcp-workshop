package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Response shape returned by a receiver that accepts a task-augmented request.
 * <p>
 * Per MCP 2025-11-25 spec § "Creating Tasks", instead of returning the
 * underlying request's normal result, the receiver replies with this envelope
 * which carries the freshly created {@link Task} and (optionally) an
 * {@code _meta} bag carrying the
 * {@code io.modelcontextprotocol/related-task} key.
 * </p>
 *
 * <p>Wire example:</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "result": {
 *     "task": {
 *       "taskId": "786512e2-9e0d-44bd-8f29-789f320fe840",
 *       "status": "working",
 *       "createdAt": "2025-11-25T10:30:00Z",
 *       "lastUpdatedAt": "2025-11-25T10:30:00Z",
 *       "ttl": 60000,
 *       "pollInterval": 5000
 *     },
 *     "_meta": {
 *       "io.modelcontextprotocol/related-task": { "taskId": "786512e2-..." }
 *     }
 *   }
 * }
 * }</pre>
 *
 * @param task  the newly created task; never null when wrapping a successful
 *              create response
 * @param _meta open metadata bag; carries the {@code related-task} key as
 *              required by the spec. Modeled as {@link Map} because the key
 *              contains dot/slash characters that cannot be used as Java
 *              record component names.
 *
 * @see Task
 * @since 1.0
 */
public record CreateTaskResult(Task task, Map<String, Object> _meta) {}
