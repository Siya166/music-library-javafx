package dev.tatenda.musiclibrary.service;

import java.util.List;

/** Thrown when user input breaks a business rule. Carries every problem, not just the first. */
public class ValidationException extends RuntimeException {

    private final List<String> problems;

    public ValidationException(List<String> problems) {
        super(String.join("\n", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
