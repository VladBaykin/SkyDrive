package com.baykin.cloud_storage.skydrive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO для ответа при успешной авторизации.
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    private String username;
}
