package com.workshop.mcp.tools;

import com.workshop.mcp.spec.Completion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers prefix completion of the {@code search_keyword} prompt's keyword.
 */
@Tag("chapter04")
@Tag("stretch")
class KeywordCompletionsTest {

    private final KeywordCompletions completions = new KeywordCompletions();

    @Test
    void onlyWordsStartingWithWhatWasTypedAreOffered() {
        Completion completion = completions.complete("re").completion();

        assertTrue(completion.values().stream().allMatch(word -> word.startsWith("re")), completion.values().toString());
        assertTrue(completion.values().contains("record"));
        assertEquals(completion.values().size(), completion.total());
        assertEquals(Boolean.FALSE, completion.hasMore());
    }

    @Test
    void theMatchIgnoresCase() {
        assertEquals(completions.complete("ja").completion().values(),
                     completions.complete("JA").completion().values());
    }

    @Test
    void nothingTypedOffersEveryWordInOrder() {
        assertEquals(KeywordCompletions.VOCABULARY, completions.complete(null).completion().values());
        assertEquals(KeywordCompletions.VOCABULARY, completions.complete("").completion().values());
    }

    @Test
    void aPrefixNothingStartsWithOffersNothing() {
        Completion completion = completions.complete("zzz").completion();

        assertEquals(List.of(), completion.values());
        assertEquals(0, completion.total());
        assertFalse(completion.hasMore());
    }

    @Test
    void aLongMatchIsCutAtOneHundredAndSaysThereIsMore() {
        List<String> words = IntStream.range(0, 150).mapToObj(i -> "word" + i).toList();

        Completion completion = new KeywordCompletions(words).complete("word").completion();

        assertEquals(KeywordCompletions.MAX_VALUES, completion.values().size());
        assertEquals(150, completion.total(), "total counts every match, not only those sent");
        assertTrue(completion.hasMore());
    }
}
