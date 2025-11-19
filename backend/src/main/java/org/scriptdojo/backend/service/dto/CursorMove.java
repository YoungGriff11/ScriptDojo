package org.scriptdojo.backend.service.dto;

public record CursorMove(String username, int line, int column, String selection) {
}
