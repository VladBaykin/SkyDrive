package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.FileResourceDto;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import com.baykin.cloud_storage.skydrive.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

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
     * GET /api/resource?path={resourcePath}
     */
    @Operation(summary = "Получение информации о ресурсе")
    @ApiResponse(responseCode = "200", description = "Информация получена")
    @ApiResponse(responseCode = "400", description = "Невалидный или отсутствующий путь")
    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    @ApiResponse(responseCode = "404", description = "Ресурс не найден")
    @GetMapping("/resource")
    public FileResourceDto getResource(@RequestParam String path) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        return fileStorageService.getResourceInfo(userId, path);
    }

    /**
     * Удаление ресурса.
     * DELETE /api/resource?path={resourcePath}
     */
    @Operation(summary = "Удаление ресурса")
    @ApiResponse(responseCode = "204", description = "Ресурс удалён")
    @DeleteMapping("/resource")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResource(@RequestParam String path) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        fileStorageService.deleteResource(userId, path);
    }

    /**
     * Скачивание ресурса.
     * GET /api/resource/download?path={filePath}
     */
    @Operation(summary = "Скачивание ресурса")
    @ApiResponse(responseCode = "200", description = "Ресурс скачан")
    @GetMapping(value = "/resource/download", params = "!zip")
    public void downloadResource(@RequestParam String path, HttpServletResponse response) throws Exception {
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
    }

    /**
     * Скачивание папки в виде ZIP-архива.
     * GET /api/resource/download?path={folderPath}&zip=true
     */
    @Operation(summary = "Скачивание папки в виде ZIP-архива")
    @ApiResponse(responseCode = "200", description = "Папка скачана в виде архива")
    @GetMapping(value = "/resource/download", params = "zip")
    public void downloadFolder(@RequestParam String path, HttpServletResponse response) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        try (InputStream is = fileStorageService.downloadFolderZip(userId, path)) {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"archive.zip\"");
            OutputStream os = response.getOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
        }
    }

    /**
     * Переименование/перемещение ресурса.
     * POST /api/resource/move?from={oldPath}&to={newPath}
     */
    @Operation(summary = "Переименование/перемещение ресурса")
    @ApiResponse(responseCode = "200", description = "Ресурс перемещён")
    @PostMapping("/resource/move")
    public FileResourceDto moveResource(@RequestParam String from, @RequestParam String to) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        return fileStorageService.moveResource(userId, from, to);
    }

    /**
     * Поиск ресурсов по запросу.
     * GET /api/resource/search?query={searchQuery}
     */
    @Operation(summary = "Поиск ресурсов")
    @ApiResponse(responseCode = "200", description = "Результаты поиска")
    @GetMapping("/resource/search")
    public List<FileResourceDto> searchResources(@RequestParam String query) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        return fileStorageService.search(userId, query);
    }

    /**
     * Загрузка файла(ов).
     * POST /api/resource?path={targetFolder}   (multipart/form-data)
     * Тело запроса должно содержать данные из file input в формате MultipartFile.
     */
    @Operation(summary = "Загрузка файла")
    @ApiResponse(responseCode = "201", description = "Файл загружен")
    @PostMapping(value = "/resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResourceDto uploadResource(@RequestParam String path,
                                            @RequestParam("file") MultipartFile file) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        return fileStorageService.uploadFile(userId, path, file);
    }
}
