package com.baykin.cloud_storage.skydrive.dto;

import lombok.Data;

/**
 * DTO для запросов авторизации/регистрации.
 */
@Data
public class AuthRequest {
    private String username;
    private String password;
}
