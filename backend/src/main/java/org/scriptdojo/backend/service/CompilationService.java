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

@Service
@Slf4j
public class CompilationService {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * Compile Java source code
     */
    public CompilationResult compile(String sourceCode, String className) {
        log.info("════════════════════════════════════════════════════");
        log.info("🔨 COMPILING JAVA CODE");
        log.info("   Class: {}", className);
        log.info("   Code length: {} characters", sourceCode.length());

        long startTime = System.currentTimeMillis();

        try {
            // Get the Java compiler
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                log.error("❌ Java compiler not available - is this a JDK?");
                return CompilationResult.builder()
                        .success(false)
                        .errorMessage("Java compiler not available. Please use a JDK, not JRE.")
                        .compilationTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Create diagnostic collector to capture errors
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            // Create file manager
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

            // Create temporary directory for compilation
            Path tempDir = Files.createTempDirectory("scriptdojo-compile-");
            File outputDir = tempDir.toFile();

            // Write source code to file
            File sourceFile = new File(outputDir, className + ".java");
            Files.write(sourceFile.toPath(), sourceCode.getBytes(StandardCharsets.UTF_8));

            log.debug("   Temp dir: {}", tempDir);
            log.debug("   Source file: {}", sourceFile);

            // Prepare compilation units
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile);

            // Set up compilation options
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(outputDir.getAbsolutePath());

            // Compile
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

                return CompilationResult.builder()
                        .success(true)
                        .className(className)
                        .compilationTimeMs(compilationTime)
                        .errors(Collections.emptyList())
                        .outputDirectory(outputDir.getAbsolutePath())
                        .build();
            } else {
                log.warn("❌ COMPILATION FAILED");
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
     * Extract class name from Java source code
     */
    public String extractClassName(String sourceCode) {
        // Simple regex to find "public class ClassName"
        String regex = "public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(sourceCode);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback: look for any "class ClassName"
        regex = "class\\s+([A-Za-z_][A-Za-z0-9_]*)";
        pattern = java.util.regex.Pattern.compile(regex);
        matcher = pattern.matcher(sourceCode);

        if (matcher.find()) {
            return matcher.group(1);
        }

        log.warn("⚠️ Could not extract class name from source code");
        return "Main";
    }
}