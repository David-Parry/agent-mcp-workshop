package com.workshop.mcp.spec;

import java.util.List;

/**
 * The nested UI metadata of an MCP App tool.
 * <p>
 * {@code visibility} controls who may invoke the tool: {@code "model"} lets
 * the language model call it, {@code "app"} lets the rendered UI call it. When
 * omitted, hosts assume both.
 * </p>
 * <p>
 * The {@code csp} and {@code permissions} settings belong on the UI resource
 * rather than the tool; hosts ignore them here.
 * </p>
 *
 * @param resourceUri the {@code ui://} URI of the app's HTML resource
 * @param visibility  who may invoke the tool, or null for the host default
 *
 * @see AppMeta
 * @since 1.0
 */
public record UiMeta(String resourceUri, List<String> visibility) {}
