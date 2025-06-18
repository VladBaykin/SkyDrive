package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.AuthRequest;
import com.baykin.cloud_storage.skydrive.dto.AuthResponse;
import com.baykin.cloud_storage.skydrive.exception.UserAlreadyExistsException;
import com.baykin.cloud_storage.skydrive.model.User;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

import java.util.Map;


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
     *
     * @param request DTO с данными для регистрации (логин и пароль)
     * @return ResponseEntity с информацией о созданном пользователе или ошибке
     */
    @Operation(summary = "Регистрация нового пользователя")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Username already taken")
    @PostMapping("/sign-up")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request) {
        try {
            User user = authService.register(request);
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(user.getUsername()));
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message","Ошибка регистрации: " + e.getMessage()));
        }
    }


    /**
     * Авторизация пользователя.
     *
     * @param request DTO с данными для авторизации (логин и пароль)
     * @return ResponseEntity с информацией об успешной авторизации или ошибке
     */
    @Operation(summary = "Авторизация пользователя")
    @ApiResponse(responseCode = "200", description = "User authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/sign-in")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return ResponseEntity.ok(new AuthResponse(request.getUsername()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message","Неверные учетные данные"));
        }
    }

    /**
     * Выход пользователя из аккаунта.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @return ResponseEntity с кодом 204 (No Content) при успешном выходе
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
}
