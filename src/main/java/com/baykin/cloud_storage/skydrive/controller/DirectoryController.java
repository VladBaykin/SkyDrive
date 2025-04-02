package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.FileResourceDto;
import com.baykin.cloud_storage.skydrive.dto.ResourceType;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import com.baykin.cloud_storage.skydrive.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DirectoryController {

    private final AuthService authService;
    private final FileStorageService fileStorageService;

    public DirectoryController(AuthService authService, FileStorageService fileStorageService) {
        this.authService = authService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Получение содержимого папки.
     * GET /api/directory?path=$path
     */
    @Operation(summary = "Получение содержимого папки")
    @ApiResponse(responseCode = "200", description = "Содержимое папки получено")
    @GetMapping("/directory")
    public ResponseEntity<?> listDirectory(@RequestParam String path,
                                           @RequestParam(defaultValue = "false") boolean recursive) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            List<FileResourceDto> contents = fileStorageService.listDirectory(username, path, recursive);
            return ResponseEntity.ok(contents);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Ошибка получения содержимого папки: " + e.getMessage()));
        }
    }

    /**
     * Создание новой пустой папки.
     * POST /api/directory?path=$path
     */
    @Operation(summary = "Создание новой пустой папки")
    @ApiResponse(responseCode = "201", description = "Папка создана")
    @PostMapping("/directory")
    public ResponseEntity<?> createDirectory(@RequestParam String path) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            fileStorageService.createDirectory(userId, path);
            FileResourceDto dto = new FileResourceDto(path, path, null, ResourceType.DIRECTORY);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Ошибка: " + e.getMessage()));
        }
    }
}
