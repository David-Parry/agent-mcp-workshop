package com.workshop.mcp.tools;

import com.workshop.mcp.spec.ElicitationCreateParams;
import com.workshop.mcp.spec.InputRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the questions the keyword-search tool asks and how it reads their
 * answers back.
 */
@Tag("chapter05")
@Tag("stretch")
class SearchQuestionsTest {

    @Test
    void aCallWithAKeywordIsAskedOnlyForADirectory() {
        assertEquals(List.of(SearchContinuation.KEY_DIRECTORY),
                     List.copyOf(SearchQuestions.forms(false).keySet()));
    }

    @Test
    void aCallWithNeitherIsAskedForTheKeywordFirstAndTheDirectorySecond() {
        Map<String, InputRequest> questions = SearchQuestions.forms(true);

        assertEquals(List.of(SearchContinuation.KEY_KEYWORD, SearchContinuation.KEY_DIRECTORY),
                     List.copyOf(questions.keySet()));
        assertEquals("elicitation/create", questions.get(SearchContinuation.KEY_KEYWORD).method());
    }

    @Test
    void thePageQuestionIsAUrlModeElicitation() {
        InputRequest question = SearchQuestions.page("http://127.0.0.1:1/elicit/t");

        ElicitationCreateParams params = (ElicitationCreateParams) question.params();
        assertEquals(ElicitationCreateParams.MODE_URL, params.mode());
        assertEquals("http://127.0.0.1:1/elicit/t", params.url());
    }

    @Test
    void bothFormAnswersAreReadWhenBothWereAsked() {
        Map<String, Object> responses = Map.of(
                SearchContinuation.KEY_KEYWORD, accept("keyword", "needle"),
                SearchContinuation.KEY_DIRECTORY, accept("directory", "/tmp"));

        assertEquals(new SearchQuestions.Answers("needle", "/tmp"),
                     SearchQuestions.fromForms(responses::get, true));
    }

    @Test
    void aKeywordAnswerIsIgnoredWhenTheKeywordWasNotAsked() {
        Map<String, Object> responses = Map.of(
                SearchContinuation.KEY_KEYWORD, accept("keyword", "planted"),
                SearchContinuation.KEY_DIRECTORY, accept("directory", "/tmp"));

        assertEquals(new SearchQuestions.Answers(null, "/tmp"), SearchQuestions.fromForms(responses::get, false));
    }

    @Test
    void declinedBlankMissingOrMistypedAnswersReadAsNothing() {
        assertEquals(SearchQuestions.Answers.NONE, SearchQuestions.fromForms(Map.<String, Object>of()::get, true));
        assertEquals(SearchQuestions.Answers.NONE, SearchQuestions.fromForms(Map.<String, Object>of(
                SearchContinuation.KEY_KEYWORD, Map.of("action", "decline"),
                SearchContinuation.KEY_DIRECTORY, accept("directory", "   "))::get, true));
        assertEquals(SearchQuestions.Answers.NONE, SearchQuestions.fromForms(Map.<String, Object>of(
                SearchContinuation.KEY_KEYWORD, Map.of("action", "accept", "content", "not an object"),
                SearchContinuation.KEY_DIRECTORY, Map.of("action", "accept", "content", Map.of("directory", 7)))
                ::get, true));
    }

    @Test
    void pageFieldsAreReadAndBlankOnesDropped() {
        assertEquals(new SearchQuestions.Answers("needle", "/tmp"),
                     SearchQuestions.fromPage(Map.of("keyword", "needle", "directory", "/tmp")));
        assertEquals(SearchQuestions.Answers.NONE, SearchQuestions.fromPage(Map.of("keyword", "")));
    }

    @Test
    void onlyAnAcceptCountsAsGoingToThePage() {
        assertTrue(SearchQuestions.accepted(Map.of("action", "accept")));
        assertTrue(SearchQuestions.accepted(Map.of("action", "ACCEPT")));
        assertFalse(SearchQuestions.accepted(Map.of("action", "decline")));
        assertFalse(SearchQuestions.accepted(Map.of("action", "cancel")));
        assertFalse(SearchQuestions.accepted(null));
    }

    private static Map<String, Object> accept(String field, Object value) {
        return Map.of("action", "accept", "content", Map.of(field, value));
    }
}
