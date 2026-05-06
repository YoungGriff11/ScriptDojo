package org.scriptdojo.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ScriptDojo Spring Boot backend application.
 * {@link SpringBootApplication} enables auto-configuration, component scanning
 * from this package downwards, and Spring Boot's opinionated defaults.
 */
@SpringBootApplication
public class BackendApplication {

    /**
     * Bootstraps and launches the Spring application context.
     * @param args command-line arguments passed through to the Spring context
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}