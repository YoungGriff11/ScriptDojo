package org.scriptdojo.backend.parser;

import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.scriptdojo.backend.service.dto.SyntaxError;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ErrorListener extends BaseErrorListener {

    private final List<SyntaxError> errors = new ArrayList<>();

    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e
    ) {
        log.warn("🔴 Syntax error at {}:{} - {}", line, charPositionInLine, msg);

        SyntaxError error = new SyntaxError();
        error.setLine(line);
        error.setColumn(charPositionInLine);
        error.setMessage(msg);
        error.setSeverity("ERROR");

        // Calculate offsets if symbol is available
        if (offendingSymbol instanceof Token) {
            Token token = (Token) offendingSymbol;
            error.setStartOffset(token.getStartIndex());
            error.setEndOffset(token.getStopIndex());
        }

        errors.add(error);
    }

    public List<SyntaxError> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}