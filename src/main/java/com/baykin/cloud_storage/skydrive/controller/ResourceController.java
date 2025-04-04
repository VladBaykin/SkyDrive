package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.FileResourceDto;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import com.baykin.cloud_storage.skydrive.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResourceController {
    private final FileStorageService fileStorageService;
    private final AuthService authService;

    public ResourceController(FileStorageService fileStorageService, AuthService authService) {
        this.fileStorageService = fileStorageService;
        this.authService = authService;
    }

    /**
     * Получение информации о ресурсе (файл или папка)
     * GET /api/resource?path=$path
     */
    @Operation(summary = "Получение информации о ресурсе")
    @ApiResponse(responseCode = "200", description = "Информация получена")
    @ApiResponse(responseCode = "400", description = "Невалидный или отсутствующий путь")
    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    @ApiResponse(responseCode = "404", description = "Ресурс не найден")
    @GetMapping("/resource")
    public ResponseEntity<?> getResource(@RequestParam String path) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            FileResourceDto resource = fileStorageService.getResourceInfo(userId, path);
            return ResponseEntity.ok(resource);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Ошибка получения ресурса: " + e.getMessage()));
        }
    }

    /**
     * Удаление ресурса.
     * DELETE /api/resource?path=$path
     */
    @Operation(summary = "Удаление ресурса")
    @ApiResponse(responseCode = "204", description = "Ресурс удалён")
    @DeleteMapping("/resource")
    public ResponseEntity<?> deleteResource(@RequestParam String path) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            fileStorageService.deleteResource(userId, path);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message","Ошибка удаления: " + e.getMessage()));
        }
    }

    /**
     * Скачивание ресурса.
     * GET /api/resource/download?path=$path
     */
    @Operation(summary = "Скачивание ресурса")
    @ApiResponse(responseCode = "200", description = "Ресурс скачан")
    @GetMapping(value = "/resource/download", params = "!zip")
    public void downloadResource(@RequestParam String path, HttpServletResponse response) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            OutputStream os;
            try (InputStream is = fileStorageService.downloadResource(userId, path)) {
                response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                os = response.getOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            os.flush();
        } catch (UnsupportedOperationException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Скачивание папки в виде ZIP-архива.
     * GET /api/resource/download?path=$path&zip=true
     */
    @Operation(summary = "Скачивание папки в виде ZIP-архива")
    @ApiResponse(responseCode = "200", description = "Папка скачана в виде архива")
    @GetMapping(value = "/resource/download", params = "zip")
    public void downloadFolder(@RequestParam String path, HttpServletResponse response) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            OutputStream os;
            try (InputStream is = fileStorageService.downloadFolderZip(userId, path)) {
                response.setContentType("application/zip");
                response.setHeader("Content-Disposition", "attachment; filename=\"archive.zip\"");
                os = response.getOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            os.flush();
        } catch (Exception e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Переименование/перемещение ресурса.
     * GET /api/resource/move?from=$from&to=$to
     */
    @Operation(summary = "Переименование/перемещение ресурса")
    @ApiResponse(responseCode = "200", description = "Ресурс перемещён")
    @PostMapping("/resource/move")
    public ResponseEntity<?> moveResource(@RequestParam String from, @RequestParam String to) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            FileResourceDto resource = fileStorageService.moveResource(userId, from, to);
            return ResponseEntity.ok(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message","Ошибка перемещения: " + e.getMessage()));
        }
    }

    /**
     * Поиск ресурсов по запросу.
     * GET /api/resource/search?query=$query
     */
    @Operation(summary = "Поиск ресурсов")
    @ApiResponse(responseCode = "200", description = "Результаты поиска")
    @GetMapping("/resource/search")
    public ResponseEntity<?> searchResources(@RequestParam String query) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            List<FileResourceDto> results = fileStorageService.search(userId, query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message","Ошибка поиска: " + e.getMessage()));
        }
    }

    /**
     * Загрузка файла(ов).
     * POST /api/resource?path=$path
     * Тело запроса должно содержать данные из file input в формате MultipartFile.
     */
    @Operation(summary = "Загрузка файла")
    @ApiResponse(responseCode = "201", description = "Файл загружен")
    @PostMapping(value = "/resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadResource(@RequestParam String path,
                                            @RequestParam("file") MultipartFile file) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = authService.getUserIdByUsername(username);
            FileResourceDto resource = fileStorageService.uploadFile(userId, path, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(resource);
        } catch (Exception e) {
            if(e.getMessage().contains("уже существует")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message","Конфликт: " + e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message","Ошибка загрузки: " + e.getMessage()));
        }
    }
}
