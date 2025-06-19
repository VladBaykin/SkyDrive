package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.FileResourceDto;
import com.baykin.cloud_storage.skydrive.dto.ResourceType;
import com.baykin.cloud_storage.skydrive.exception.ResourceNotFoundException;
import com.baykin.cloud_storage.skydrive.service.AuthService;
import com.baykin.cloud_storage.skydrive.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
     * Получение информации о ресурсе.
     * GET /api/resource?path={resourcePath}
     * Параметр path - путь к ресурсу, например: "user-1-files/folder/file.txt"
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
     * Параметр path - путь к ресурсу, например: "user-1-files/folder/file.txt"
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
     * GET /api/resource/download?path={resourcePath}&zip={true|false}
     * Параметр path - путь к ресурсу, например: "user-1-files/folder/file.txt"
     * Параметр zip - если true, то папка будет скачана в виде zip-архива
     */
    @Operation(summary = "Скачивание ресурса")
    @ApiResponse(responseCode = "200", description = "Ресурс скачан")
    @GetMapping(value = "/resource/download")
    public void download(@RequestParam String path,
                         @RequestParam(defaultValue = "false") boolean zip,
                         HttpServletResponse response) throws Exception {
        Long userId = authService.getUserIdByUsername(authService.getCurrentUsername());
        String originalPath = path;
        boolean isDirectory;
        try {
            isDirectory = fileStorageService
                    .getResourceInfo(userId, path)
                    .getType() == ResourceType.DIRECTORY;
        } catch (ResourceNotFoundException ex) {
            if (!path.endsWith("/")) {
                isDirectory = fileStorageService
                        .getResourceInfo(userId, path + "/")
                        .getType() == ResourceType.DIRECTORY;
                path = path + "/";
            } else throw ex;
        }
        if (isDirectory || zip) {
            try (InputStream is = fileStorageService.downloadFolderZip(userId, path)) {
                String dirName = originalPath.endsWith("/")
                        ? originalPath.substring(0, originalPath.length() - 1)
                        : originalPath;
                int slash = dirName.lastIndexOf('/');
                if (slash >= 0) dirName = dirName.substring(slash + 1);
                String archive = dirName + ".zip";
                String encoded = URLEncoder.encode(archive, StandardCharsets.UTF_8)
                        .replaceAll("\\+", "%20");
                response.setContentType("application/zip");
                response.setHeader("Content-Disposition",
                        "attachment; filename=\"" + archive + "\"; " +
                                "filename*=UTF-8''" + encoded);
                StreamUtils.copy(is, response.getOutputStream());
            }
            return;
        }
        try (InputStream is = fileStorageService.downloadResource(userId, path)) {
            String fileName = originalPath.contains("/")
                    ? originalPath.substring(originalPath.lastIndexOf('/') + 1)
                    : originalPath;
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; " +
                            "filename*=UTF-8''" + encoded);
            StreamUtils.copy(is, response.getOutputStream());
        }
    }

    /**
     * Переименование/перемещение ресурса.
     * GET /api/resource/move?from={oldPath}&to={newPath}
     * Параметры from и to - пути к ресурсу, например: "user-1-files/folder/file.txt" и "user-1-files/folder/new-file.txt"
     */
    @Operation(summary = "Переименование/перемещение ресурса")
    @ApiResponse(responseCode = "200", description = "Ресурс перемещён")
    @GetMapping("/resource/move")
    public FileResourceDto moveResource(@RequestParam String from, @RequestParam String to) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        return fileStorageService.moveResource(userId, from, to);
    }

    /**
     * Поиск ресурсов по запросу.
     * GET /api/resource/search?query={searchQuery}
     * Параметр query - строка для поиска, например: "file.txt"
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
     * Загрузка файлов.
     * POST /api/resource
     * Параметр path - путь к папке, куда будут загружены файлы, например: "user-1-files/folder/"
     * Файлы передаются в теле запроса как multipart/form-data
     */
    @Operation(summary = "Загрузка файла")
    @ApiResponse(responseCode = "201", description = "Файл загружен")
    @PostMapping(value = "/resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<FileResourceDto> uploadResource(
            @RequestParam(value = "path", required = false, defaultValue = "") String path,
            @RequestPart("file") MultipartFile[] files) throws Exception {
        String username = authService.getCurrentUsername();
        Long userId = authService.getUserIdByUsername(username);
        List<FileResourceDto> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            uploaded.add(fileStorageService.uploadFile(userId, path, file));
        }
        return uploaded;
    }
}
