package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents a single question within an MCP (Model Context Protocol) Elicitation process.
 * 
 * <p>Each question is designed to gather specific information from the user and can be
 * either multiple-choice (with predefined options) or open-ended (allowing free-form responses).
 * Questions are identified by unique IDs to enable proper response tracking and correlation.</p>
 * 
 * <p>Question types supported:</p>
 * <ul>
 *   <li><strong>Multiple Choice</strong> - Questions with a predefined set of options</li>
 *   <li><strong>Open-ended</strong> - Questions allowing free-form text responses</li>
 *   <li><strong>Yes/No</strong> - Binary choice questions</li>
 *   <li><strong>Rating Scale</strong> - Numeric or categorical rating questions</li>
 * </ul>
 * 
 * <p>Best practices for question design:</p>
 * <ul>
 *   <li>Use clear, concise prompts that are easy to understand</li>
 *   <li>Provide meaningful option choices for multiple-choice questions</li>
 *   <li>Use descriptive IDs that help identify the question's purpose</li>
 *   <li>Consider the user experience when ordering questions</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Multiple-choice question
 * var mcQuestion = new ElicitationQuestion(
 *     "experience_level",
 *     "What is your programming experience level?",
 *     List.of("Beginner", "Intermediate", "Advanced", "Expert")
 * );
 * 
 * // Open-ended question
 * var openQuestion = new ElicitationQuestion(
 *     "project_description",
 *     "Please describe your project in detail:",
 *     null
 * );
 * 
 * // Yes/No question
 * var binaryQuestion = new ElicitationQuestion(
 *     "use_frameworks",
 *     "Do you want to use existing frameworks?",
 *     List.of("Yes", "No")
 * );
 * }</pre>
 * 
 * @param id unique identifier for the question, used for response correlation and tracking
 * @param prompt the question text to be displayed to the user, should be clear and concise
 * @param options list of predefined answer choices for multiple-choice questions, 
 *               or null for open-ended questions allowing free-form responses
 * 
 * @since 1.0
 * @see ElicitationResult
 * @see ElicitationMessage
 */
public record ElicitationQuestion(
        String id,
        String prompt,
        List<String> options
) {}