package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result payload of an MCP (Model Context Protocol) Elicitation message.
 * 
 * <p>This record encapsulates the core content of an elicitation request, containing
 * the type of elicitation being performed and the collection of questions to be
 * presented to the user. The result is embedded within an {@link ElicitationMessage}
 * as part of the JSON-RPC message structure.</p>
 * 
 * <p>The elicitation result supports different types of information gathering:</p>
 * <ul>
 *   <li><strong>questions</strong> - Interactive Q&amp;A sessions with users</li>
 *   <li><strong>preferences</strong> - Collecting user preferences and settings</li>
 *   <li><strong>context</strong> - Gathering contextual information for better responses</li>
 *   <li><strong>validation</strong> - Confirming user intent or validating assumptions</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create multiple-choice question
 * var mcQuestion = new ElicitationQuestion(
 *     "lang_pref", 
 *     "Which programming language do you prefer?",
 *     List.of("Java", "Python", "JavaScript", "Go")
 * );
 * 
 * // Create open-ended question
 * var openQuestion = new ElicitationQuestion(
 *     "requirements", 
 *     "Please describe your specific requirements:",
 *     null
 * );
 * 
 * var result = new ElicitationResult("questions", List.of(mcQuestion, openQuestion));
 * }</pre>
 * 
 * @param type the type of elicitation being performed (e.g., "questions", "preferences", "context")
 * @param questions an ordered list of questions to be presented to the user, cannot be null but may be empty
 * 
 * @since 1.0
 * @see ElicitationMessage
 * @see ElicitationQuestion
 */
public record ElicitationResult(
        String type,
        List<ElicitationQuestion> questions
) {}