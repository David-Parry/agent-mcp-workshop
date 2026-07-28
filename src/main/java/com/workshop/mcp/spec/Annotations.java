package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents annotations that provide additional metadata for resources in the MCP (Model Context Protocol) system.
 * <p>
 * Annotations allow resources to be tagged with supplementary information such as
 * intended audience and priority levels. This metadata helps clients make informed
 * decisions about how to present or process resources.
 * </p>
 * 
 * @param audience a list of roles indicating the intended audience for the annotated resource
 * @param priority an optional priority value for the resource, where higher values indicate higher priority
 * 
 * @see Role
 * @see Resource
 * @since 1.0
 */
public record Annotations(List<Role> audience,
                          Double priority) {
}
