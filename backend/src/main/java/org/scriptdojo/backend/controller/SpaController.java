package org.scriptdojo.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    @RequestMapping(value = {
            "/",
            "/login",
            "/signup",
            "/dashboard",
            "/editor",
            "/room/**",
            "/welcome"
    })
    public String forwardToIndex(HttpServletRequest request) {
        return "forward:/index.html";
    }
}