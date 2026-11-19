package com.workshop.mcp.spec;

/**
 * Parameters of {@code resources/list}.
 * <p>
 * The cursor is the {@code nextCursor} of the page before, echoed back
 * unchanged. It is opaque: a client must not build one or read anything into
 * it, and an empty string is as valid a cursor as any other, so only an
 * absent cursor asks for the first page.
 * </p>
 *
 * @param _meta  request metadata, of which only the progress token is modelled here
 * @param cursor where the page starts, or null for the first page
 */
public record ResourcesListParams(MetaInfo _meta, String cursor) {}
