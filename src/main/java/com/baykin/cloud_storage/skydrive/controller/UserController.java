package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.service.AuthService;
import com.baykin.cloud_storage.skydrive.model.User;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Получает информацию о текущем пользователе.
     *
     * @return ResponseEntity с информацией о пользователе
     */
    @Operation(summary = "Получение информации о текущем пользователе")
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);
        return ResponseEntity.ok(Map.of("username", user.getUsername(), "id", user.getId()));
    }
}
