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
     * GET /api/directory?path={directoryPath}&recursive={true|false}
     * Параметр path - путь к папке, например: "user-1-files/folder"
     * Параметр recursive - если true, возвращает содержимое всех вложенных папок
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
     * POST /api/directory?path={directoryPath}
     * Параметр path - путь к новой папке, например: "user-1-files/folder/new-folder"
     */
    @Operation(summary = "Создание новой пустой папки")
    @ApiResponse(responseCode = "201", description = "Папка создана")
    @PostMapping("/directory")
    @ResponseStatus(HttpStatus.CREATED)
    public List<FileResourceDto> createDirectory(@RequestParam String path) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        fileStorageService.createDirectory(userId, path);
        FileResourceDto dto = new FileResourceDto(
                path.endsWith("/") ? path : path + "/",
                path.endsWith("/")
                        ? path.substring(path.lastIndexOf("/", path.length() - 2) + 1, path.length() - 1)
                        : path.substring(path.lastIndexOf("/") + 1),
                null,
                ResourceType.DIRECTORY
        );
        return List.of(dto);
    }
}
