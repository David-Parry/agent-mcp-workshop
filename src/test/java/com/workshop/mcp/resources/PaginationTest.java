package com.workshop.mcp.resources;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers how a list is cut into pages, and which cursors are refused.
 */
@Tag("chapter04")
@Tag("stretch")
class PaginationTest {

    private static List<Integer> numbers(int count) {
        return IntStream.range(0, count).boxed().toList();
    }

    @Test
    void followingEveryCursorVisitsEachEntryOnceInOrder() {
        List<Integer> all = numbers(45);
        List<Integer> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            Pagination.Page<Integer> page = Pagination.page(all, cursor);
            seen.addAll(page.items());
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertEquals(all, seen);
        assertEquals(3, pages);
    }

    @Test
    void aListThatEndsOnAPageBoundaryOffersNoEmptyPageAfterIt() {
        Pagination.Page<Integer> second = Pagination.page(numbers(40), Pagination.page(numbers(40), null).nextCursor());

        assertEquals(20, second.items().size());
        assertNull(second.nextCursor());
    }

    @Test
    void anEmptyListIsOneEmptyPage() {
        Pagination.Page<Integer> page = Pagination.page(List.of(), null);

        assertEquals(List.of(), page.items());
        assertNull(page.nextCursor());
    }

    @Test
    void theCursorIsOpaqueRatherThanAnOffsetAClientCouldGuess() {
        String cursor = Pagination.page(numbers(45), null).nextCursor();

        assertNotNull(cursor);
        assertNotEquals("20", cursor);
        assertEquals("offset=20", new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
    }

    @Test
    void aCursorThisPagerWouldNeverIssueIsRefused() {
        List<Integer> all = numbers(45);
        for (String cursor : List.of("", "page-2", Pagination.cursorFor(0), Pagination.cursorFor(7),
                                     Pagination.cursorFor(60), Pagination.cursorFor(-20))) {
            assertNull(Pagination.page(all, cursor), "'" + cursor + "'");
        }
    }

    @Test
    void aCursorPastTheEndOfAShorterListIsRefused() {
        String cursor = Pagination.page(numbers(45), null).nextCursor();

        assertNull(Pagination.page(numbers(15), cursor));
    }
}
