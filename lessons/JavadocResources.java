package com.workshop.mcp.resources;

import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.spec.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides methods to read and process javadoc HTML files from the JAR resources.
 * This class scans the javadoc directory structure and creates Resource entries
 * for each Java class documentation found.
 */
public class JavadocResources {
    private static final LogFile logger = LogFileWriter.getInstance();

    /**
     * Creates a list of Resource objects from HTML files that are already in the classpath.
     * The HTML files should be in the same JAR as this class.
     * 
     * @param resourcePaths array of resource paths relative to the classpath root
     *                      (e.g., "javadoc/index.html", "javadoc/com/example/MyClass.html")
     * @return a List of Resource objects representing the HTML files
     */
    public static List<Resource> createResourcesFromClasspath(String... resourcePaths) {
        List<Resource> resources = new ArrayList<>();
        
        for (String resourcePath : resourcePaths) {
            // Extract filename without extension for the name
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            String name = fileName.contains(".") ? 
                         fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            
            // Extract description from the HTML javadoc
            String description = extractJavadocDescription(resourcePath);
            
            // Create the resource with the path that can be used with getResourceAsStream
            Resource resource = new Resource(
                resourcePath,  // Use the path directly as the URI
                name,
                description,
                "text/html",
                null
            );
            
            resources.add(resource);
        }
        
        return resources;
    }
    
    /**
     * Reads the content of an HTML resource from the classpath.
     * This uses the standard Java way to read resources from the same JAR.
     * 
     * @param resourcePath the path to the resource (e.g., "javadoc/index.html")
     * @return the content of the HTML file as a String
     * @throws IOException if the resource cannot be read
     */
    public static String readResourceContent(String resourcePath) throws IOException {
        // Use getResourceAsStream to read from classpath (works in JAR)
        try (InputStream inputStream = JavadocResources.class.getClassLoader()
                                                             .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            
            // Read the content using BufferedReader for efficiency
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
    
    /**
     * Loads all HTML resources from a specific folder in the classpath.
     * This method expects a listing file that contains all HTML file paths in that folder.
     * The listing file should be generated at build time and included in the JAR.
     *
     * @param folderPath the folder path in the classpath (e.g., "javadoc")
     * @return a List of Resource objects for all HTML files listed
     * @throws IOException if the listing file cannot be read
     */
    public static List<Resource> loadAllHtmlResourcesFromFolder(String folderPath) {
        // The listing file should be at folderPath/html-files.txt
        String listingPath = folderPath + "/html-files.txt";
        return createResourcesFromListing(listingPath);
    }

    /**
     * Creates resources from a directory listing file.
     * The listing file should contain one HTML file path per line.
     * 
     * @param listingResourcePath path to a text file containing list of HTML resources
     * @return a List of Resource objects
     * @throws IOException if the listing file cannot be read
     */
    public static List<Resource> createResourcesFromListing(String listingResourcePath) {
        List<String> resourcePaths = new ArrayList<>();
        
        try (InputStream inputStream = JavadocResources.class.getClassLoader()
                                                             .getResourceAsStream(listingResourcePath)) {
            if (inputStream == null) {
                throw new IOException("Listing file not found: " + listingResourcePath);
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && line.endsWith(".html")) {
                        resourcePaths.add(line);
                    }
                }
            }
        } catch (IOException e) {
            logger.log("Error reading listing file: " + listingResourcePath, e);
        }

        return createResourcesFromClasspath(resourcePaths.toArray(new String[0]));
    }
    
    /**
     * Extracts the javadoc description from an HTML file using JSoup.
     * This method looks for the class description in the javadoc HTML structure.
     * 
     * @param resourcePath the path to the HTML resource
     * @return the extracted description or a default description if extraction fails
     */
    private static String extractJavadocDescription(String resourcePath) {
        try {
            String htmlContent = readResourceContent(resourcePath);
            Document doc = Jsoup.parse(htmlContent);
            
            // Try to find the class description in javadoc HTML
            // Javadoc typically puts the main description in a div with class "block"
            // that follows the class declaration
            Element descriptionBlock = doc.selectFirst("div.block");
            
            if (descriptionBlock != null) {
                // Get the text content and clean it up
                String description = descriptionBlock.text().trim();
                
                // Limit description length if it's too long
                if (description.length() > 200) {
                    description = description.substring(0, 197) + "...";
                }
                
                return description;
            }
            
            // If no description block found, try to get the title
            Element titleElement = doc.selectFirst("h2.title");
            if (titleElement != null) {
                return titleElement.text();
            }
            
        } catch (IOException e) {
            logger.log("Error extracting javadoc description from: " + resourcePath, e);
        }
        
        // Fallback to default description
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        String name = fileName.contains(".") ? 
                     fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return "HTML documentation for " + name;
    }
}