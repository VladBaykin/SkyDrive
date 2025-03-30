package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.AuthRequest;
import com.baykin.cloud_storage.skydrive.dto.AuthResponse;
import com.baykin.cloud_storage.skydrive.model.User;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер для обработки запросов аутентификации.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager, AuthService authService) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
    }

    /**
     * Регистрация нового пользователя.
     */
    @Operation(summary = "Регистрация нового пользователя")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Username already taken")
    @PostMapping("/sign-up")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            User user = authService.register(request);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());
            Authentication authentication = authenticationManager.authenticate(authToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(user.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Ошибка регистрации: " + e.getMessage()));
        }
    }

    /**
     * Авторизация пользователя.
     */
    @Operation(summary = "Авторизация пользователя")
    @ApiResponse(responseCode = "200", description = "User authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/sign-in")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return ResponseEntity.ok(new AuthResponse(request.getUsername()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Неверные учетные данные"));
        }
    }

    /**
     * Выход из аккаунта.
     */
    @Operation(summary = "Выход пользователя из аккаунта")
    @ApiResponse(responseCode = "204", description = "User logged out")
    @PostMapping("/sign-out")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Вспомогательный класс для ошибок
     */
    @Setter
    @Getter
    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

    }
}
