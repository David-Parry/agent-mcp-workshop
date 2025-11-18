package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.ElicitationCreateParams;

import java.util.List;
import java.util.Map;

public class ElicitationBuilder {
    
    /**
     * Creates an ElicitationCreateParams with questions about Jira project summarization.
     * 
     * @return ElicitationCreateParams containing the proper MCP elicitation request format
     */
    public static ElicitationCreateParams buildJiraProjectElicitation() {
        // Create the JSON schema for the requested data
        Map<String, Object> projectKeyProperty = Map.of(
            "type", "string",
            "title", "Project Key",
            "description", "The Jira project key to summarize",
            "enum", List.of("ENG", "HR", "OPS"),
            "enumNames", List.of("Engineering", "Human Resources", "Operations")
        );
        
        Map<String, Object> timeRangeProperty = Map.of(
            "type", "string", 
            "title", "Time Range",
            "description", "The time period to include in the summary",
            "enum", List.of("last 7 days", "last 30 days", "custom"),
            "enumNames", List.of("Last 7 Days", "Last 30 Days", "Custom Range")
        );
        
        Map<String, Object> properties = Map.of(
            "projectKey", projectKeyProperty,
            "timeRange", timeRangeProperty
        );
        
        Map<String, Object> requestedSchema = Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("projectKey", "timeRange")
        );
        
        return new ElicitationCreateParams(
            "Please provide the Jira project details for summarization:",
            requestedSchema
        );
    }
}
