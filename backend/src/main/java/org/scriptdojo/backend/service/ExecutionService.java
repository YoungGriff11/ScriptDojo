package org.scriptdojo.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.service.dto.ExecutionResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.*;

@Service
@Slf4j
public class ExecutionService {

    private static final int MAX_EXECUTION_TIME_SECONDS = 10;
    private static final int MAX_OUTPUT_LENGTH = 10000; // 10KB output limit

    /**
     * Execute compiled Java class
     */
    public ExecutionResult execute(String className, Path compiledClassPath) {
        log.info("════════════════════════════════════════════════════");
        log.info("🚀 EXECUTING JAVA CODE");
        log.info("   Class: {}", className);
        log.info("   Classpath: {}", compiledClassPath);

        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            // Build the command to run the Java class
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-cp",
                    compiledClassPath.toString(),
                    className
            );

            processBuilder.redirectErrorStream(false); // Keep stdout and stderr separate

            log.debug("   Command: {}", String.join(" ", processBuilder.command()));

            // Start the process
            Process process = processBuilder.start();

            // Read output in separate threads to avoid blocking
            Future<String> outputFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> errorFuture = executor.submit(() -> readStream(process.getErrorStream()));

            // Wait for process to complete (with timeout)
            boolean completed = process.waitFor(MAX_EXECUTION_TIME_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.warn("⚠️ Execution timeout - killed after {}s", MAX_EXECUTION_TIME_SECONDS);

                return ExecutionResult.builder()
                        .success(false)
                        .error("Execution timeout - program killed after " + MAX_EXECUTION_TIME_SECONDS + " seconds")
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .exitCode(-1)
                        .build();
            }

            // Get output
            String output = outputFuture.get(1, TimeUnit.SECONDS);
            String error = errorFuture.get(1, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;

            // Truncate output if too long
            if (output.length() > MAX_OUTPUT_LENGTH) {
                output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)";
            }
            if (error.length() > MAX_OUTPUT_LENGTH) {
                error = error.substring(0, MAX_OUTPUT_LENGTH) + "\n... (error output truncated)";
            }

            boolean success = exitCode == 0;

            if (success) {
                log.info("✅ EXECUTION SUCCESSFUL");
                log.info("   Exit code: {}", exitCode);
                log.info("   Execution time: {}ms", executionTime);
                log.info("   Output length: {} chars", output.length());
            } else {
                log.warn("❌ EXECUTION FAILED");
                log.warn("   Exit code: {}", exitCode);
                log.warn("   Error: {}", error);
            }

            log.info("════════════════════════════════════════════════════");

            return ExecutionResult.builder()
                    .success(success)
                    .output(output)
                    .error(error)
                    .exitCode(exitCode)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (TimeoutException e) {
            log.error("❌ Execution timeout: {}", e.getMessage());
            return ExecutionResult.builder()
                    .success(false)
                    .error("Execution timeout")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .exitCode(-1)
                    .build();

        } catch (Exception e) {
            log.error("❌ Execution error: {}", e.getMessage(), e);
            log.info("════════════════════════════════════════════════════");

            return ExecutionResult.builder()
                    .success(false)
                    .exceptionMessage(e.getMessage())
                    .error("Execution failed: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .exitCode(-1)
                    .build();

        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Read an input stream into a string
     */
    private String readStream(java.io.InputStream inputStream) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}