package com.baykin.cloud_storage.skydrive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для запросов авторизации/регистрации.
 */
@Data
public class AuthRequest {

    @NotBlank(message = "Username can't be empty")
    @Size(min = 2, max = 50, message = "The username must be between 2 and 50 characters long")
    private String username;

    @NotBlank(message = "Password can't be empty")
    @Size(min = 5, max = 100, message = "The password must be at least 5 characters long")
    private String password;
}
