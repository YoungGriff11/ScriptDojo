package org.scriptdojo.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all React client-side routes to the React entry point (index.html),
 * enabling full client-side routing in a Single Page Application (SPA) served
 * by Spring Boot.
 *
 * Problem this solves:
 * When a user navigates directly to a React route (e.g. by typing
 * http://localhost:8080/dashboard into their browser or refreshing the page),
 * the request hits the Spring Boot server rather than being handled by the
 * React Router in the browser. Without this controller, Spring Boot would
 * return a 404 because it has no handler registered for /dashboard.
 */
@Controller
public class SpaController {

    /**
     * Forwards the request to /index.html for all registered React client-side routes.
     *
     * Uses a server-side forward rather than an HTTP redirect so that:
     * - The browser URL remains unchanged (React Router sees the original path)
     * - No extra round-trip is made to the client
     * - Spring Security's already-evaluated permit rules are honoured
     *
     * The /room/** wildcard covers all room URLs regardless of the room ID,
     * which is a dynamic segment generated at runtime and not known at startup.
     *
     * @param request the incoming HTTP request (available for logging or
     *                diagnostics if needed; not used in the current implementation)
     * @return a Spring MVC forward directive to /index.html
     */
    @RequestMapping(value = {
            "/",           // Root — redirects to the React landing or login page
            "/login",      // Login page
            "/signup",     // Registration page
            "/dashboard",  // Host dashboard (authenticated)
            "/editor",     // Standalone editor view (authenticated)
            "/room/**",    // Guest/host collaborative room — dynamic room ID segment
            "/welcome"     // Welcome/landing page
    })
    public String forwardToIndex(HttpServletRequest request) {
        // "forward:" instructs Spring MVC to internally forward the request to
        // the static resource handler for /index.html, which serves the React bundle.
        // React Router then takes over and renders the correct page in the browser.
        return "forward:/index.html";
    }
}