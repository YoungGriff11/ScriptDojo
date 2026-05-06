package org.scriptdojo.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.service.dto.ExecutionResult;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * Service that executes compiled Java bytecode in an isolated subprocess.
 * Called by {@link org.scriptdojo.backend.controller.CompilerController} as the
 * second stage of the compile-and-run pipeline, after
 * {@link CompilationService} has produced a .class file.
 * Launches the compiled class via a new JVM process, captures stdout and stderr
 * independently, enforces a hard execution timeout, and truncates output that
 * exceeds the configured length limit before returning a structured
 * {@link ExecutionResult}.
 * Running user code in a subprocess provides basic isolation — the host JVM
 * is not affected by System.exit() calls or uncaught exceptions in the
 * submitted program.
 */
@Service
@Slf4j
public class ExecutionService {

    /**
     * Maximum time in seconds the subprocess is allowed to run before it is
     * forcibly terminated. Prevents infinite loops from blocking the server indefinitely.
     */
    private static final int MAX_EXECUTION_TIME_SECONDS = 10;

    /**
     * Maximum number of characters captured from stdout or stderr.
     * Output exceeding this limit is truncated and a notice is appended,
     * preventing excessively large payloads from being broadcast to the room.
     */
    private static final int MAX_OUTPUT_LENGTH = 10000;

    /**
     * Executes the named compiled Java class in an isolated subprocess and
     * returns the result.
     * Stdout and stderr are read concurrently on separate threads to prevent
     * the subprocess from blocking on a full output buffer. The process is
     * given {@value MAX_EXECUTION_TIME_SECONDS} seconds to complete before
     * being forcibly destroyed.
     * @param className         the fully unqualified name of the class to execute
     * @param compiledClassPath the directory containing the compiled .class file,
     *                          passed as the -cp argument to the JVM subprocess
     * @return an {@link ExecutionResult} containing the stdout output, stderr output,
     *         exit code, execution time, and a success flag
     */
    public ExecutionResult execute(String className, Path compiledClassPath) {
        log.info("════════════════════════════════════════════════════");
        log.info("🚀 EXECUTING JAVA CODE");
        log.info("   Class: {}", className);
        log.info("   Classpath: {}", compiledClassPath);

        long startTime = System.currentTimeMillis();

        // Single-threaded executor used to read stdout and stderr concurrently
        // without blocking the main thread on either stream
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            // ─ Process setup
            // Launch a new JVM subprocess to run the compiled class in isolation.
            // redirectErrorStream is false so stdout and stderr remain separate
            // streams that can be read independently and reported distinctly.
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-cp",
                    compiledClassPath.toString(),
                    className
            );

            processBuilder.redirectErrorStream(false);

            log.debug("   Command: {}", String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();

            // ─ Async stream reading
            // Reading stdout and stderr on separate futures prevents the subprocess
            // from deadlocking if either buffer fills up while the main thread
            // is waiting on the other stream
            Future<String> outputFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> errorFuture = executor.submit(() -> readStream(process.getErrorStream()));

            // ─ Timeout enforcement
            // If the process does not complete within the time limit, it is
            // forcibly destroyed to prevent infinite loops from blocking the server
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

            // ─ Output collection
            String output = outputFuture.get(1, TimeUnit.SECONDS);
            String error = errorFuture.get(1, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;

            // Truncate output exceeding the length limit to keep WebSocket
            // broadcast payloads within a reasonable size
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
            // Thrown by Future.get() if the stream reader threads do not complete
            // within the 1-second post-process window
            log.error("❌ Execution timeout: {}", e.getMessage());
            return ExecutionResult.builder()
                    .success(false)
                    .error("Execution timeout")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .exitCode(-1)
                    .build();

        } catch (Exception e) {
            // Catches all other failures (e.g. process spawn errors, interrupted
            // waits) and surfaces them via exceptionMessage on the result
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
            // Always shut down the executor to release the stream reader threads,
            // regardless of whether execution succeeded, timed out, or threw
            executor.shutdownNow();
        }
    }

    /**
     * Reads all lines from an input stream into a single string.
     * Used to drain both stdout and stderr from the subprocess asynchronously.
     * The stream is closed automatically via try-with-resources when reading completes.
     * @param inputStream the stream to read from
     * @return the full contents of the stream as a newline-delimited string
     * @throws Exception if an I/O error occurs while reading the stream
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