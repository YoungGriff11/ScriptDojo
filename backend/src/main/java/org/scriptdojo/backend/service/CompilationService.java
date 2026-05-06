package org.scriptdojo.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.service.dto.CompilationError;
import org.scriptdojo.backend.service.dto.CompilationResult;
import org.springframework.stereotype.Service;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service that compiles Java source code at runtime using the
 * {@link javax.tools.JavaCompiler} API available in the JDK.
 * Called by {@link org.scriptdojo.backend.controller.CompilerController}
 * as the first stage of the compile-and-run pipeline. Writes the source code
 * to a temporary directory, invokes the compiler, and returns a structured
 * {@link CompilationResult} containing either the output directory path on
 * success or a list of {@link CompilationError} entries on failure.
 * Requires a JDK at runtime — ToolProvider.getSystemJavaCompiler() returns
 * null when running on a JRE, which is surfaced as an explicit error result.
 */
@Service
@Slf4j
public class CompilationService {

    /**
     * The system temporary directory used as the root for per-compilation
     * working directories. Each compile run creates its own subdirectory
     * via Files.createTempDirectory to avoid class file collisions between
     * concurrent compilation requests.
     */
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * Compiles the given Java source code and returns the result.
     * Writes the source to a temporary file named {className}.java, invokes
     * the system Java compiler with diagnostic collection enabled, and returns
     * a {@link CompilationResult} indicating success or failure.
     * On success, the output directory path is included in the result so that
     * {@link ExecutionService} can locate the compiled .class file.
     * On failure, structured {@link CompilationError} entries are extracted from
     * the compiler diagnostics and included in the result for broadcast to the room.
     * @param sourceCode the full Java source code to compile
     * @param className  the name of the public class in the source, used to name
     *                   the .java file — must match the class name in the source
     * @return a {@link CompilationResult} indicating success or failure with details
     */
    public CompilationResult compile(String sourceCode, String className) {
        log.info("════════════════════════════════════════════════════");
        log.info("🔨 COMPILING JAVA CODE");
        log.info("   Class: {}", className);
        log.info("   Code length: {} characters", sourceCode.length());

        long startTime = System.currentTimeMillis();

        try {
            // ─ Compiler availability check
            // getSystemJavaCompiler() returns null when running on a JRE rather
            // than a JDK — surfaced early as an explicit error result
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                log.error("❌ Java compiler not available - is this a JDK?");
                return CompilationResult.builder()
                        .success(false)
                        .errorMessage("Java compiler not available. Please use a JDK, not JRE.")
                        .compilationTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // ─ Diagnostic collection
            // DiagnosticCollector captures all compiler errors and warnings as
            // structured Diagnostic objects rather than raw stderr text
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

            // ─ Temporary working directory
            // A unique subdirectory is created per compilation run to isolate
            // .java and .class files from concurrent compilation requests
            Path tempDir = Files.createTempDirectory("scriptdojo-compile-");
            File outputDir = tempDir.toFile();

            // Write the source code to a file named {className}.java — the Java
            // compiler requires the filename to match the public class name
            File sourceFile = new File(outputDir, className + ".java");
            Files.write(sourceFile.toPath(), sourceCode.getBytes(StandardCharsets.UTF_8));

            log.debug("   Temp dir: {}", tempDir);
            log.debug("   Source file: {}", sourceFile);

            // ─ Compilation
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile);

            // -d directs the compiler to write .class files to the temp directory
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(outputDir.getAbsolutePath());

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    compilationUnits
            );

            boolean success = task.call();
            long compilationTime = System.currentTimeMillis() - startTime;

            fileManager.close();

            if (success) {
                log.info("✅ COMPILATION SUCCESSFUL");
                log.info("   Time: {}ms", compilationTime);
                log.info("   Output dir: {}", outputDir.getAbsolutePath());
                log.info("════════════════════════════════════════════════════");

                // Include the output directory path so ExecutionService can
                // locate the compiled .class file without re-deriving the path
                return CompilationResult.builder()
                        .success(true)
                        .className(className)
                        .compilationTimeMs(compilationTime)
                        .errors(Collections.emptyList())
                        .outputDirectory(outputDir.getAbsolutePath())
                        .build();
            } else {
                log.warn("❌ COMPILATION FAILED");

                // Convert each compiler diagnostic into a structured CompilationError
                // so the frontend can display precise line/column error information
                List<CompilationError> errors = new ArrayList<>();
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    CompilationError error = CompilationError.builder()
                            .lineNumber(diagnostic.getLineNumber())
                            .columnNumber(diagnostic.getColumnNumber())
                            .message(diagnostic.getMessage(null))
                            .kind(diagnostic.getKind().toString())
                            .code(diagnostic.getCode())
                            .build();

                    errors.add(error);
                    log.warn("   Line {}: {}", diagnostic.getLineNumber(), diagnostic.getMessage(null));
                }

                log.info("   Total errors: {}", errors.size());
                log.info("════════════════════════════════════════════════════");

                return CompilationResult.builder()
                        .success(false)
                        .errors(errors)
                        .compilationTimeMs(compilationTime)
                        .build();
            }

        } catch (IOException e) {
            // IO failures (e.g. unable to create the temp directory or write the
            // source file) are caught separately and surfaced as a high-level
            // error message rather than structured compiler diagnostics
            log.error("❌ Compilation error: {}", e.getMessage(), e);
            log.info("════════════════════════════════════════════════════");

            return CompilationResult.builder()
                    .success(false)
                    .errorMessage("IO Error during compilation: " + e.getMessage())
                    .compilationTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Extracts the class name from Java source code using regex matching.
     * First attempts to match a public class declaration, then falls back to
     * any class declaration if no public class is found. Defaults to "Main"
     * if neither pattern matches, which allows compilation to proceed even
     * for malformed or incomplete source snippets.
     * @param sourceCode the Java source code to inspect
     * @return the extracted class name, or "Main" if none could be determined
     */
    public String extractClassName(String sourceCode) {
        // Primary match: "public class ClassName" — the most common case
        String regex = "public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(sourceCode);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback: any "class ClassName" declaration (e.g. package-private classes)
        regex = "class\\s+([A-Za-z_][A-Za-z0-9_]*)";
        pattern = java.util.regex.Pattern.compile(regex);
        matcher = pattern.matcher(sourceCode);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Default fallback — allows the pipeline to continue with a best-guess name
        log.warn("⚠️ Could not extract class name from source code");
        return "Main";
    }
}