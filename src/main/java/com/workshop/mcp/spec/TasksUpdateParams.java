package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Parameters of {@code tasks/update}.
 * <p>
 * This method replaced the blocking {@code tasks/result} of the previous
 * revision, and its purpose is narrow: it delivers answers to a task that is
 * sitting in {@link TaskStatus#INPUT_REQUIRED}. Results are no longer fetched
 * with a blocking call; a client polls {@code tasks/get} instead.
 * </p>
 * <p>
 * A task's {@code inputRequests} are a separate mechanism from the Multi
 * Round-Trip Requests of {@link InputRequiredResult}, even though they share a
 * shape. Task input is surfaced by {@code tasks/get} and answered here, never
 * by retrying the original call.
 * </p>
 *
 * @param taskId         the task to deliver input to
 * @param inputResponses the answers, keyed by the identifiers the task asked under
 *
 * @since 1.0
 */
public record TasksUpdateParams(String taskId, Map<String, Object> inputResponses) {}
