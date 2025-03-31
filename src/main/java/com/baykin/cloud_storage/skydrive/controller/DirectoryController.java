package com.baykin.cloud_storage.skydrive.controller;

import com.baykin.cloud_storage.skydrive.dto.FileResourceDto;
import com.baykin.cloud_storage.skydrive.dto.ResourceType;
import com.baykin.cloud_storage.skydrive.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DirectoryController {

    private final FileStorageService fileStorageService;

    public DirectoryController(FileStorageService fileStorageService) {
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
            String userRoot = fileStorageService.getUserRoot(username);
            String folderPath = userRoot + (path.endsWith("/") ? path : path + "/");
            MultipartFile emptyFile = new EmptyMultipartFile(new byte[0], folderPath);
            fileStorageService.uploadFile(username, "", emptyFile);
            FileResourceDto dto = new FileResourceDto(folderPath, path.substring(path.lastIndexOf("/") + 1), null, ResourceType.DIRECTORY);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Ошибка создания папки: " + e.getMessage()));
        }
    }

    /**
     * Вспомогательный класс для создания пустого файла в MultipartFile.
     */
    static class EmptyMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final byte[] content;
        private final String name;

        public EmptyMultipartFile(byte[] content, String name) {
            this.content = content;
            this.name = name;
        }

        @Override
        public String getName() {
            return "empty";
        }
        @Override
        public String getOriginalFilename() {
            return "empty";
        }
        @Override
        public String getContentType() {
            return "application/octet-stream";
        }
        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }
        @Override
        public long getSize() {
            return content.length;
        }
        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }
        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            try (OutputStream os = new FileOutputStream(dest)) {
                os.write(content);
            }
        }
    }
}
