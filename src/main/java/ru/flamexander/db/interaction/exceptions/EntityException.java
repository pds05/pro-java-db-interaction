package ru.flamexander.db.interaction.exceptions;

public class EntityException extends RuntimeException{
    public EntityException(String message) {
        super(message);
    }

    public EntityException() {
    }
}
