package com.workshop.mcp.spec;

import java.util.List;
import java.util.Map;

/**
 * Represents a response containing a list of roots in the MCP (Model Context Protocol) system.
 * <p>
 * This response is returned when querying for available roots, providing a list of
 * all root directories or resource locations that are accessible through the MCP server.
 * Each root represents a base location from which resources can be accessed.
 * </p>
 * 
 * @param roots the list of available root locations
 * 
 * @see Root
 * @see RootsListResult
 * @since 1.0
 */
public record RootsResponse(List<Root> roots) {
}
