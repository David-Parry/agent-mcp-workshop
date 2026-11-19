package com.workshop.mcp.tools;

import com.google.gson.Gson;
import com.workshop.mcp.spec.ElicitationCreateResult;
import com.workshop.mcp.spec.InputRequest;
import com.workshop.mcp.spec.McpGson;
import com.workshop.mcp.spec.builders.ElicitationBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The questions the keyword-search tool asks when a call does not say what to
 * search for or where, and how their answers are read back.
 * <p>
 * Each question sits under its own key in {@code inputRequests}, and its
 * answer comes back under the same key in {@code inputResponses}, so one
 * round can ask for the keyword and the directory together.
 * </p>
 *
 * @see SearchContinuation
 */
public final class SearchQuestions {

    private static final Gson GSON = McpGson.create();

    /**
     * What the user answered.
     *
     * @param keyword   the keyword, or null when it was not asked or not given
     * @param directory the directory, or null when it was not given
     */
    public record Answers(String keyword, String directory) {

        /** Nothing was answered. */
        public static final Answers NONE = new Answers(null, null);
    }

    private SearchQuestions() {
    }

    /**
     * The form questions for one round, keyword first when it is asked.
     *
     * @param askKeyword whether the keyword is missing too
     * @return the questions, keyed as {@code inputRequests} carries them
     */
    public static Map<String, InputRequest> forms(boolean askKeyword) {
        Map<String, InputRequest> questions = new LinkedHashMap<>();
        if (askKeyword) {
            questions.put(SearchContinuation.KEY_KEYWORD,
                          InputRequest.elicitation(ElicitationBuilder.buildSearchKeywordElicitation()));
        }
        questions.put(SearchContinuation.KEY_DIRECTORY,
                      InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()));
        return questions;
    }

    /**
     * The URL-mode question that sends the user to a page.
     *
     * @param url the page's address
     * @return the question
     */
    public static InputRequest page(String url) {
        return InputRequest.elicitation(ElicitationBuilder.buildSearchPageElicitation(url));
    }

    /**
     * Reads the answers to the form questions of one round.
     *
     * @param responses       looks up the response under each key
     * @param askedForKeyword whether the keyword was asked, so its answer is read
     * @return the answers
     */
    public static Answers fromForms(Function<String, Object> responses, boolean askedForKeyword) {
        return new Answers(askedForKeyword ? field(responses.apply(SearchContinuation.KEY_KEYWORD), "keyword") : null,
                           field(responses.apply(SearchContinuation.KEY_DIRECTORY), "directory"));
    }

    /**
     * Reads the fields a page collected.
     *
     * @param submitted the submitted form fields
     * @return the answers
     */
    public static Answers fromPage(Map<String, String> submitted) {
        return new Answers(blankToNull(submitted.get("keyword")), blankToNull(submitted.get("directory")));
    }

    /**
     * Whether the user accepted a question rather than declining or
     * cancelling it. Accepting a URL-mode question carries no content: it only
     * says the user went to the page.
     *
     * @param response the response under the question's key, may be null
     * @return true for {@code "action": "accept"}
     */
    public static boolean accepted(Object response) {
        return acceptedResult(response) != null;
    }

    private static ElicitationCreateResult acceptedResult(Object response) {
        ElicitationCreateResult result = GSON.fromJson(GSON.toJsonTree(response), ElicitationCreateResult.class);
        return result != null && "accept".equalsIgnoreCase(result.action()) ? result : null;
    }

    private static String field(Object response, String name) {
        if (acceptedResult(response) instanceof ElicitationCreateResult result
            && result.content() instanceof Map<?, ?> content && content.get(name) instanceof String value) {
            return blankToNull(value);
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
