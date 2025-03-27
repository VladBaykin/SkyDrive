package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.AuthRequest;
import com.baykin.cloud_storage.skydrive.model.User;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(201).body(user);
    }
}
