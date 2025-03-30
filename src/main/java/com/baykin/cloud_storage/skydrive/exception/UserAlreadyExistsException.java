package com.baykin.cloud_storage.skydrive.exception;

/**
 * Исключение, если пользователь с таким именем уже существует.
 */
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
