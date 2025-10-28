package com.workshop.mcp.spec;

/**
 * Represents an MCP (Model Context Protocol) Elicitation message in the JSON-RPC format.
 * 
 * <p>Elicitation messages are used to request information from users through interactive
 * questions. This is part of the MCP's capability to gather contextual information
 * that can help improve the quality of responses and interactions.</p>
 * 
 * <p>The elicitation process allows servers to:</p>
 * <ul>
 *   <li>Ask clarifying questions about user intent</li>
 *   <li>Gather additional context for better responses</li>
 *   <li>Present multiple-choice or open-ended questions</li>
 *   <li>Collect user preferences and constraints</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * var questions = List.of(
 *     new ElicitationQuestion("q1", "What is your preferred programming language?", 
 *                           List.of("Java", "Python", "JavaScript")),
 *     new ElicitationQuestion("q2", "Describe your project requirements:", null)
 * );
 * var result = new ElicitationResult("questions", questions);
 * var message = new ElicitationMessage("2.0", "elicit-001", result);
 * }</pre>
 * 
 * @param jsonrpc the JSON-RPC protocol version, must be "2.0" for MCP compliance
 * @param id unique identifier for this elicitation message, used for tracking responses
 * @param result the elicitation result containing the type and list of questions to present
 * 
 * @since 1.0
 * @see ElicitationResult
 * @see ElicitationQuestion
 */
public record ElicitationMessage(
        String jsonrpc,
        String id,
        ElicitationResult result
) {}