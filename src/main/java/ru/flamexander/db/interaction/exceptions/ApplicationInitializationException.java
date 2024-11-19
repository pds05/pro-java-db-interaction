package ru.flamexander.db.interaction.exceptions;

public class ApplicationInitializationException extends ApplicationException {
    public ApplicationInitializationException() {
    }

    public ApplicationInitializationException(String message) {
        super(message);
    }

    public ApplicationInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
