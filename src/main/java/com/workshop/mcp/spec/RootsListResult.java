package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of a roots list request in the MCP (Model Context Protocol) system.
 * <p>
 * RootsListResult contains the complete list of available roots that the server
 * exposes to clients. Each root provides a base location for organizing and
 * accessing resources within the MCP server.
 * </p>
 * 
 * @param roots the list of available roots in the server
 * 
 * @see Root
 * @since 1.0
 */
public record RootsListResult(
    List<Root> roots
) {}
