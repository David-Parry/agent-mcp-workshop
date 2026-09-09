package com.workshop.mcp.resources;

import com.workshop.mcp.spec.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how {@code resources/list} and {@code resources/read} get their
 * content: javadoc pages are shipped inside the JAR, so they are reached
 * through the classpath rather than the filesystem, and a page that cannot be
 * read or parsed has to degrade into a usable {@link Resource} instead of
 * failing the whole listing.
 */
class JavadocResourcesTest {

    /** A page generated into main resources; its description runs past the 200-character limit. */
    private static final String SHIPPED_PAGE = "javadoc/com/workshop/mcp/spec/Unknown.html";

    /** The folder {@code IORouter} lists for {@code resources/list}. */
    private static final String SHIPPED_FOLDER = "javadoc/com/workshop/mcp/spec";

    private static final String FIXTURES = "javadoc-fixtures/";

    // --- reading a resource off the classpath --------------------------

    @Test
    void aPageShippedInsideTheJarIsReadThroughTheClassLoader() throws IOException {
        String content = JavadocResources.readResourceContent(SHIPPED_PAGE);

        assertTrue(content.contains("<html"), "expected an HTML page, got: " + content);
        assertTrue(content.contains("Represents an unknown or unrecognized JSON structure"));
    }

    @Test
    void aMultiLineResourceKeepsItsLineBreaks() throws IOException {
        String content = JavadocResources.readResourceContent(FIXTURES + "ShortBlock.html");

        assertTrue(content.startsWith("<!DOCTYPE HTML>\n<html lang=\"en\">"),
                   "lines must be rejoined with newlines, got: " + content);
    }

    @Test
    void readingAResourceThatIsNotOnTheClasspathNamesThePathItLookedFor() {
        IOException failure = assertThrows(IOException.class,
                                           () -> JavadocResources.readResourceContent("no/such/page.html"));

        assertEquals("Resource not found: no/such/page.html", failure.getMessage());
    }

    // --- description extraction ----------------------------------------

    @Test
    void aDescriptionLongerThanTheLimitIsTruncatedToExactlyTwoHundredCharacters() {
        String description = onlyResource(SHIPPED_PAGE).description();

        assertEquals(200, description.length());
        assertTrue(description.endsWith("..."), "a truncated description must be marked as one");
        assertTrue(description.startsWith("Represents an unknown or unrecognized JSON structure"),
                   "the truncation must keep the head of the description, got: " + description);
    }

    @Test
    void aDescriptionShortEnoughToFitIsUsedVerbatim() {
        assertEquals("A description short enough to survive intact.",
                     onlyResource(FIXTURES + "ShortBlock.html").description());
    }

    @Test
    void aPageWithNoDescriptionBlockFallsBackToItsTitleHeading() {
        assertEquals("Class TitleOnly", onlyResource(FIXTURES + "TitleOnly.html").description());
    }

    @Test
    void aPageWithNeitherADescriptionNorATitleIsDescribedByItsName() {
        assertEquals("HTML documentation for NoDescription",
                     onlyResource(FIXTURES + "NoDescription.html").description());
    }

    @Test
    void aPageThatCannotBeReadStillProducesAResourceRatherThanFailingTheListing() {
        // resources/list names every page in one answer, so one unreadable
        // page must not cost the client the other seventy-nine.
        Resource resource = onlyResource("no/such/Missing.html");

        assertEquals("HTML documentation for Missing", resource.description());
        assertEquals("no/such/Missing.html", resource.uri());
    }

    // --- naming ---------------------------------------------------------

    @Test
    void aResourceIsNamedByItsFileNameWithTheExtensionStripped() {
        Resource resource = onlyResource(SHIPPED_PAGE);

        assertEquals("Unknown", resource.name());
        assertEquals(SHIPPED_PAGE, resource.uri(), "the URI must be usable with getResourceAsStream");
        assertEquals("text/html", resource.mimeType());
        assertNull(resource.annotations());
    }

    @Test
    void aFileNameWithNoExtensionIsKeptWhole() {
        Resource resource = onlyResource(FIXTURES + "extensionless");

        assertEquals("extensionless", resource.name());
        assertEquals("HTML documentation for extensionless", resource.description());
    }

    @Test
    void noPathsProduceNoResources() {
        assertEquals(List.of(), JavadocResources.createResourcesFromClasspath());
    }

    @Test
    void everyRequestedPathProducesAResourceInOrder() {
        List<Resource> resources = JavadocResources.createResourcesFromClasspath(
                FIXTURES + "ShortBlock.html", FIXTURES + "TitleOnly.html");

        assertEquals(List.of("ShortBlock", "TitleOnly"), resources.stream().map(Resource::name).toList());
    }

    // --- listing files ---------------------------------------------------

    @Test
    void aListingContributesOnlyItsNonEmptyHtmlLines() {
        List<Resource> resources = JavadocResources.createResourcesFromListing(FIXTURES + "listing/html-files.txt");

        assertEquals(List.of(FIXTURES + "ShortBlock.html",
                             FIXTURES + "TitleOnly.html",
                             FIXTURES + "NoDescription.html"),
                     resources.stream().map(Resource::uri).toList(),
                     "the blank line and the non-HTML line must be skipped, and paths trimmed");
    }

    @Test
    void aMissingListingIsSwallowedAndYieldsNoResources() {
        // The listing is generated at build time. If it did not make it into
        // the JAR, resources/list has to answer with nothing rather than fail.
        assertEquals(List.of(), JavadocResources.createResourcesFromListing(FIXTURES + "no-such-listing.txt"));
    }

    @Test
    void aFolderIsReadThroughTheListingFileItIsExpectedToContain() {
        assertEquals(JavadocResources.createResourcesFromListing(FIXTURES + "listing/html-files.txt"),
                     JavadocResources.loadAllHtmlResourcesFromFolder(FIXTURES + "listing"));
    }

    @Test
    void theShippedSpecFolderListsThePagesItDocuments() {
        List<Resource> resources = JavadocResources.loadAllHtmlResourcesFromFolder(SHIPPED_FOLDER);

        assertTrue(resources.size() > 1, "the generated listing should name every documented class");
        assertTrue(resources.stream().allMatch(resource -> resource.uri().endsWith(".html")));
        assertTrue(resources.stream().anyMatch(resource -> "Unknown".equals(resource.name())));
    }

    @Test
    void theImplicitConstructorIsHarmless() {
        // Every member is static; the class only has a constructor because it
        // declares none, and instantiating it must not come to mean anything.
        assertNotNull(new JavadocResources());
    }

    /** The single resource created for one path, asserting there was exactly one. */
    private static Resource onlyResource(String resourcePath) {
        List<Resource> resources = JavadocResources.createResourcesFromClasspath(resourcePath);
        assertEquals(1, resources.size(), "expected exactly one resource, got " + resources);
        return resources.get(0);
    }
}
