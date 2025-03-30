package com.baykin.cloud_storage.skydrive.exception;

/**
 * Исключение, если пользователь не найден.
 */
public class UserNotFoundException extends  RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
