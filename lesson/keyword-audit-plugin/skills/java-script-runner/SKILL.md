---
name: java-script-runner
description: Execute a Java source file through the JVM without a build step, or run the built-in FileAnalyzer to structurally analyze any source file
---

# java-script-runner

Runs a `.java` file directly through the JVM using Java's single-file source execution (JEP 330, Java 11+) and unnamed classes (JEP 463, Java 21 preview). No `javac`, no Gradle, no classpath setup.

The skill has a built-in **FileAnalyzer** — Java source embedded directly here. When invoked with `--analyze`, Claude writes it to a temp file, runs it, and cleans up. No external file needed.

## Usage

Analyze a file using the built-in FileAnalyzer:
```
/keyword-audit-plugin:java-script-runner --analyze <absolute-path-to-file>
```

Run any `.java` file by path:
```
/keyword-audit-plugin:java-script-runner <path-to-script.java> [args...]
```

## Built-in FileAnalyzer Source

```java
///usr/bin/env java --enable-preview --source 21 "$0" "$@"; exit $?

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

void main(String[] args) throws IOException {
    if (args.length == 0) {
        System.err.println("Usage: java FileAnalyzer.java <absolute-path-to-file>");
        System.exit(1);
    }

    Path path = Paths.get(args[0]);
    if (!Files.exists(path)) {
        System.err.println("File not found: " + path);
        System.exit(1);
    }

    List<String> lines = Files.readAllLines(path);

    long total    = lines.size();
    long blank    = lines.stream().filter(String::isBlank).count();
    long comments = lines.stream().filter(l -> l.stripLeading().startsWith("//")
                                           || l.stripLeading().startsWith("*")
                                           || l.stripLeading().startsWith("/*")).count();
    long code     = total - blank - comments;

    List<String> keywords = List.of("TODO", "FIXME", "deprecated", "import", "class");
    Map<String, Long> freq = keywords.stream().collect(Collectors.toMap(
        kw -> kw,
        kw -> lines.stream().filter(l -> l.contains(kw)).count()
    ));

    List<String> longest = lines.stream()
        .filter(l -> !l.isBlank())
        .sorted((a, b) -> Integer.compare(b.length(), a.length()))
        .limit(5)
        .toList();

    System.out.println("═".repeat(60));
    System.out.println("  File Analysis: " + path.getFileName());
    System.out.println("  Path:          " + path.toAbsolutePath());
    System.out.println("═".repeat(60));
    System.out.printf("  %-20s %d%n", "Total lines:",   total);
    System.out.printf("  %-20s %d%n", "Code lines:",    code);
    System.out.printf("  %-20s %d%n", "Comment lines:", comments);
    System.out.printf("  %-20s %d%n", "Blank lines:",   blank);
    System.out.println();
    System.out.println("  Keyword Frequency:");
    freq.forEach((kw, count) ->
        System.out.printf("    %-14s %d occurrence%s%n", kw + ":", count, count == 1 ? "" : "s"));
    System.out.println();
    System.out.println("  Top 5 Longest Lines:");
    longest.forEach(l ->
        System.out.printf("    [%3d chars]  %s%n", l.length(),
            l.length() > 72 ? l.substring(0, 69) + "..." : l.strip()));
    System.out.println("═".repeat(60));
}
```

## Instructions for Claude

### If `--analyze <path>` is given:

1. Extract the target file path
2. Write the embedded FileAnalyzer source to a temp file via Bash:
   ```bash
   cat > /tmp/FileAnalyzer_$$.java << 'JAVA_EOF'
   <paste full embedded source above>
   JAVA_EOF
   ```
3. Run it: `java --enable-preview --source 21 /tmp/FileAnalyzer_$$.java <target-path>`
4. Display the full output
5. Delete the temp file: `rm -f /tmp/FileAnalyzer_$$.java`

### If a `<path-to-script.java>` is given:

1. Resolve the path from the project root
2. Read the file to show the user what will be executed
3. Detect execution mode:
   - Starts with `///usr/bin/env` → `chmod +x` then execute directly
   - Contains `void main(` without a surrounding `class` → `java --enable-preview --source 21 <path>`
   - Otherwise → `java <path>`
4. Execute via Bash, capture stdout and stderr
5. Treat the preview-feature note as informational, not an error
6. On failure: show stderr and identify whether it is a compile or runtime error
