package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.FileResourceDto;
import com.baykin.cloud_storage.skydrive.dto.ResourceType;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import com.baykin.cloud_storage.skydrive.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * GET /api/directory?path={folderPath}&recursive={true|false}
     */
    @Operation(summary = "Получение содержимого папки")
    @ApiResponse(responseCode = "200", description = "Содержимое папки получено")
    @GetMapping("/directory")
    public List<FileResourceDto> listDirectory(@RequestParam String path,
                                           @RequestParam(defaultValue = "false") boolean recursive) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        return fileStorageService.listDirectory(userId, path, recursive);
    }

    /**
     * Создание новой пустой папки.
     * POST /api/directory?path={newFolderPath}
     */
    @Operation(summary = "Создание новой пустой папки")
    @ApiResponse(responseCode = "201", description = "Папка создана")
    @PostMapping("/directory")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FileResourceDto createDirectory(@RequestParam String path) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        fileStorageService.createDirectory(userId, path);
        return new FileResourceDto(
                fileStorageService.getUserRoot(userId) + path,
                path.endsWith("/") ? path.substring(0, path.length() - 1) : path,
                null,
                ResourceType.DIRECTORY
        );
    }
}
