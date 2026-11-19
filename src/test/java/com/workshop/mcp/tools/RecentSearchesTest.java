package com.workshop.mcp.tools;

import com.workshop.mcp.MutableClock;
import com.workshop.mcp.spec.ContentItem;
import com.workshop.mcp.spec.ToolCallResult;
import com.workshop.mcp.spec.builders.ToolCallResultBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the search history behind the app-only {@code recent_searches} tool.
 */
@Tag("chapter06")
@Tag("stretch")
class RecentSearchesTest {

    private final MutableClock clock = new MutableClock();
    private final RecentSearches recent = new RecentSearches(clock);

    @Test
    void anEmptyHistorySaysSoInTextAndAsAnEmptyList() {
        ToolCallResult result = recent.result();

        assertEquals(List.of("No searches yet."), texts(result));
        assertEquals(Map.of("searches", List.of()), result.structuredContent());
    }

    @Test
    void aSearchIsRecordedWithWhereAndWhenItRan() {
        recent.record(Set.of("/srv/code"), search("record", 3));

        RecentSearches.Search search = recent.list().get(0);
        assertEquals("record", search.keyword());
        assertEquals(List.of("/srv/code"), search.directories());
        assertEquals(3, search.totalMatches());
        assertEquals("2026-11-18T12:00:00Z", search.searchedAt());
        assertEquals(List.of("record in /srv/code, total_matches=3, at 2026-11-18T12:00:00Z"),
                     texts(recent.result()));
        assertEquals(Map.of("searches", List.of(search)), recent.result().structuredContent());
    }

    @Test
    void aResultWithoutSearchOutputIsNotRecorded() {
        recent.record(Set.of("/srv/code"), ToolCallResultBuilder.builder()
                .addTextContent("No search directory was resolved for this call.").asError().build());

        assertTrue(recent.list().isEmpty());
    }

    @Test
    void theNewestSearchesAreKeptNewestFirst() {
        for (int i = 1; i <= RecentSearches.CAPACITY + 2; i++) {
            recent.record(Set.of("/srv/code"), search("keyword" + i, i));
            clock.advance(Duration.ofSeconds(1));
        }

        List<RecentSearches.Search> kept = recent.list();
        assertEquals(RecentSearches.CAPACITY, kept.size());
        assertEquals("keyword" + (RecentSearches.CAPACITY + 2), kept.get(0).keyword());
        assertEquals("keyword3", kept.get(kept.size() - 1).keyword(), "the two oldest were dropped");
    }

    @Test
    void theToolTakesNoArguments() {
        assertEquals("object", RecentSearches.schema().type());
        assertTrue(RecentSearches.schema().properties().isEmpty());
    }

    private static ToolCallResult search(String keyword, int count) {
        return ToolCallResultBuilder.builder()
                .withStructuredContent(SearchOutput.of(keyword, List.of(new SearchOutput.FileMatch("/f", count))))
                .build();
    }

    private static List<String> texts(ToolCallResult result) {
        return result.content().stream().map(ContentItem::text).toList();
    }
}
