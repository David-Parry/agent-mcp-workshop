package com.workshop.mcp.resources;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Cuts a list into pages joined by cursors only this server can read.
 * <p>
 * A cursor is opaque to the client, which may only echo back a
 * {@code nextCursor} it was given. Here a cursor names the offset its page
 * starts at, Base64-encoded so that a client is not tempted to build one.
 * </p>
 * <p>
 * A value this server did not issue is refused rather than read as the first
 * page: a client sending one has lost its place, and starting again from the
 * top would hand it pages it has already seen as if they were new. Rather
 * than decode what arrives, the pager compares it with every cursor it would
 * issue for a list that long, so nothing else can be mistaken for one.
 * </p>
 */
public final class Pagination {

    /** How many entries each page carries. */
    public static final int PAGE_SIZE = 20;

    private Pagination() {
    }

    /**
     * One page of a list.
     *
     * @param <T>        the entry type
     * @param items      the entries on this page
     * @param nextCursor the cursor of the page after this one, or null on the last
     */
    public record Page<T>(List<T> items, String nextCursor) {}

    /**
     * Returns the page a cursor names.
     *
     * @param <T>    the entry type
     * @param all    the whole list, in a stable order
     * @param cursor null for the first page, otherwise a {@code nextCursor} this pager issued
     * @return the page, or null when the cursor is not one this pager would
     *         issue for a list this long
     */
    public static <T> Page<T> page(List<T> all, String cursor) {
        int offset = cursor == null ? 0 : issuedOffset(cursor, all.size());
        if (offset < 0) {
            return null;
        }
        int end = Math.min(offset + PAGE_SIZE, all.size());
        return new Page<>(List.copyOf(all.subList(offset, end)), end < all.size() ? cursorFor(end) : null);
    }

    static String cursorFor(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("offset=" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int issuedOffset(String cursor, int size) {
        for (int offset = PAGE_SIZE; offset < size; offset += PAGE_SIZE) {
            if (cursorFor(offset).equals(cursor)) {
                return offset;
            }
        }
        return -1;
    }
}
