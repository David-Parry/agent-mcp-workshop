package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents a response containing a list of roots in the MCP (Model Context Protocol) system.
 * <p>
 * This is the answer to a {@code roots/list} request, listing the base
 * locations a client has made available. Each root represents a base location
 * from which resources can be accessed.
 * </p>
 * <p>
 * On revision {@code 2026-07-28} a server never receives this as a JSON-RPC
 * response, because it never sends the request. It arrives as one entry of
 * {@code params.inputResponses} when a client retries a call the server
 * answered with an {@link InputRequiredResult} — the bare result, with no
 * JSON-RPC envelope around it.
 * </p>
 *
 * @param roots the list of available root locations
 *
 * @see Root
 * @see InputRequest#rootsList()
 * @since 1.0
 */
public record RootsResponse(List<Root> roots) {
}
