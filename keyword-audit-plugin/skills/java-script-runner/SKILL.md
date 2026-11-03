---
name: java-script-runner
description: Execute a Java source file through the JVM without a build step, or run the built-in FileAnalyzer to structurally analyze any source file
---

# java-script-runner

Runs a `.java` file directly through the JVM using Java's source-file launcher (JEP 330, Java 11+): `java File.java` compiles the file in memory and runs it. No `javac`, no Gradle, no classpath setup.

The skill has a built-in **FileAnalyzer** — Java source embedded directly here. When invoked with `--analyze`, Claude writes it to a temp file, runs it, and cleans up. No external file needed. FileAnalyzer is an ordinary class with a `public static void main`, so the same command runs it on every JDK from 21 on.

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileAnalyzer {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java FileAnalyzer.java <absolute-path-to-file>");
            System.exit(1);
        }

        Path path = Path.of(args[0]);
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
        for (String kw : List.of("TODO", "FIXME", "deprecated", "import", "class")) {
            long count = lines.stream().mapToLong(l -> occurrences(l, kw)).sum();
            System.out.printf("    %-14s %d occurrence%s%n", kw + ":", count, count == 1 ? "" : "s");
        }
        System.out.println();
        System.out.println("  Top 5 Longest Lines:");
        longest.forEach(l -> {
            String text = l.strip();
            System.out.printf("    [%3d chars]  %s%n", l.length(),
                text.length() > 72 ? text.substring(0, 69) + "..." : text);
        });
        System.out.println("═".repeat(60));
    }

    // Counted the way key_word_search counts: case-sensitive, non-overlapping.
    private static long occurrences(String line, String keyword) {
        long count = 0;
        for (int i = line.indexOf(keyword); i != -1; i = line.indexOf(keyword, i + keyword.length())) {
            count++;
        }
        return count;
    }
}
```

## Instructions for Claude

### If `--analyze <path>` is given:

1. Extract the target file path
2. Write, run and delete the FileAnalyzer in **one** Bash command. Each Bash call starts a new shell, so `$$` names a different file in a second call:
   ```bash
   F=/tmp/FileAnalyzer_$$.java
   cat > "$F" << 'JAVA_EOF'
   <paste full embedded source above>
   JAVA_EOF
   java "$F" <target-path>; rm -f "$F"
   ```
3. Display the full output

### If a `<path-to-script.java>` is given:

1. Resolve the path from the project root
2. Read the file to show the user what will be executed
3. Pick the launch command:
   - First line starts with `///usr/bin/env` → `chmod +x <path>`, then run `./<path>`. That line is not a shebang, which starts with `#!`. A file without one is run by the shell as a script, so the shell executes the line as a command, while Java reads it as a `//` comment.
   - A top-level `void main(` with no class around it → a compact source file. JDK 25 and later run it with `java <path>` (JEP 512). JDK 21 to 24 only have it as a preview feature (JEP 445 in 21, JEP 463 in 22, JEP 477 in 23, JEP 495 in 24), so run `java --enable-preview --source <N> <path>`, where `<N>` is the feature release `java -version` reports.
   - Otherwise → `java <path>`
4. Execute via Bash, capture stdout and stderr
5. Treat the preview-feature note as informational, not an error
6. On failure: show stderr and identify whether it is a compile or runtime error
