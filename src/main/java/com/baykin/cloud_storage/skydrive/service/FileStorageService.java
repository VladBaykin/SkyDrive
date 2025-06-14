package com.baykin.cloud_storage.skydrive.service;

import com.baykin.cloud_storage.skydrive.dto.FileResourceDto;
import com.baykin.cloud_storage.skydrive.dto.ResourceType;
import com.baykin.cloud_storage.skydrive.exception.AccessDeniedException;
import com.baykin.cloud_storage.skydrive.exception.InvalidPathException;
import com.baykin.cloud_storage.skydrive.exception.ResourceAlreadyExistsException;
import com.baykin.cloud_storage.skydrive.exception.ResourceNotFoundException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileStorageService {

    private final MinioClient minioClient;
    private final AuthService authService;

    @Value("${minio.bucket-name}")
    private String bucket;

    public FileStorageService(MinioClient minioClient, AuthService authService) {
        this.minioClient = minioClient;
        this.authService = authService;
    }

    /**
     * Инициализация бакета: если бакет не существует, создаём его.
     */
    @PostConstruct
    public void init() throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * Формирует корневой путь пользователя.
     * Например: user-1-files/
     */
    private String getUserRoot(Long userId) {
        return authService.getUserRoot(userId);
    }

    /**
     * Проверка того, что запрашиваемый путь начинается с корневой папки пользователя.
     */
    private void checkUserAuthorization(String relativePath) {
        if (relativePath == null) return;
        if (relativePath.contains("..") || relativePath.startsWith("/")) {
            throw new InvalidPathException("Недопустимый путь");
        }
    }

    /**
     * Загрузка файла в указанную папку.
     * Если в имени файла есть вложенные директории, они будут созданы автоматически.
     * Если файл уже существует, выбрасывается исключение.
     */
    public FileResourceDto uploadFile(Long userId, String relativePath, MultipartFile file) throws Exception {
        checkUserAuthorization(relativePath);
        String userRoot = getUserRoot(userId);
        String dir = (relativePath == null || relativePath.isBlank()) ? "" : (relativePath.endsWith("/") ? relativePath : relativePath + "/");
        String objectName = userRoot + dir + file.getOriginalFilename();
        if (!objectName.startsWith(userRoot)) {
            throw new AccessDeniedException("Доступ запрещён: некорректный путь");
        }
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            throw new ResourceAlreadyExistsException("Файл с таким именем уже существует");
        } catch (ErrorResponseException e) {
            if (!e.errorResponse().code().equals("NoSuchKey") &&
                    !e.errorResponse().code().equals("NotFound")) {
                throw e;
            }
        }

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
        return new FileResourceDto(
                dir,
                file.getOriginalFilename(),
                file.getSize(),
                ResourceType.FILE
        );
    }

    /**
     * Получение информации о ресурсе (файл или папка) по его полному пути.
     */
    public FileResourceDto getResourceInfo(Long userId, String relativePath) throws Exception {
        checkUserAuthorization(relativePath);
        int lastSlash = relativePath.lastIndexOf("/");
        String path = relativePath.substring(0, lastSlash + 1);
        String name = relativePath.substring(lastSlash + 1);
        try {
            StatObjectResponse stat =
                    minioClient.statObject(StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(relativePath)
                            .build()
                    );
            return new FileResourceDto(path, name, stat.size(), ResourceType.FILE);
        } catch (ErrorResponseException e) {
            Iterable<Result<Item>> results =
                    minioClient.listObjects(ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(relativePath)
                            .recursive(false)
                            .build());
            if (results.iterator().hasNext()) {
                return new FileResourceDto(path, name, null, ResourceType.DIRECTORY);
            } else {
                throw new ResourceNotFoundException("Ресурс не найден: " + relativePath);
            }
        }
    }

    /**
     * Удаление ресурса (файл или папка).
     * Если ресурс – папка, удаляются все объекты с данным префиксом.
     */
    public void deleteResource(Long userId, String relativePath) throws Exception {
        checkUserAuthorization(relativePath);
        if (relativePath.endsWith("/")) {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(relativePath)
                            .recursive(true)
                            .build());
            for (Result<Item> result : results) {
                String objectName = result.get().objectName();
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
            }
        } else {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(relativePath)
                            .build());
        }
    }

    /**
     * Перемещение (переименование) ресурса.
     * Реализуется через копирование и удаление исходного объекта.
     */
    public FileResourceDto moveResource(Long userId, String from, String to) throws Exception {
        if (!to.startsWith(getUserRoot(userId))) {
            throw new AccessDeniedException("Новый путь не принадлежит текущему пользователю");
        }
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(bucket)
                .object(to)
                .source(CopySource.builder().bucket(bucket).object(from).build())
                .build());
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(from)
                .build());
        return getResourceInfo(userId, to);
    }

    /**
     * Скачивание ресурса.
     * Если это файл – возвращаем InputStream для его чтения.
     * Если это папка – выбрасываем исключение, так как используется метод downloadFolderZip.
     */
    public InputStream downloadResource(Long userId, String relativePath) throws Exception {
        if (relativePath == null || relativePath.isBlank()) {
            throw new InvalidPathException("Путь не может быть пустым");
        }
        if (relativePath.endsWith("/")) {
            throw new InvalidPathException("Для скачивания папки используйте метод downloadFolderZip");
        }
        checkUserAuthorization(relativePath);
        String objectName = getUserRoot(userId) + relativePath;
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new ResourceNotFoundException("Файл не найден: " + relativePath);
            }
            throw e;
        }
    }

    /**
     * Скачивание папки в виде ZIP-архива.
     * Метод находит все объекты с заданным префиксом и архивирует их.
     */
    public InputStream downloadFolderZip(Long userId, String relativePath) throws Exception {
        if (relativePath == null) {
            throw new InvalidPathException("Путь не может быть пустым");
        }
        String normalized = relativePath.endsWith("/") ? relativePath : relativePath + "/";
        checkUserAuthorization(normalized);
        String prefix = getUserRoot(userId) + normalized;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) continue;
                try (InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucket)
                                .object(item.objectName())
                                .build())) {
                    String entryName = item.objectName().substring(prefix.length());
                    zos.putNextEntry(new ZipEntry(entryName));

                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Получение содержимого папки.
     * Если recursive = false — возвращает только объекты первого уровня,
     * иначе — все объекты с указанным префиксом.
     */
    public List<FileResourceDto> listDirectory(Long userId, String folderPath, boolean recursive) throws Exception {
        checkUserAuthorization(folderPath);
        String userRoot = getUserRoot(userId);
        String fullFolderPath = userRoot + (folderPath != null ? folderPath : "");
        if (!fullFolderPath.endsWith("/")) {
            fullFolderPath += "/";
        }
        List<FileResourceDto> resultsList = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucket).prefix(fullFolderPath).recursive(recursive).build());
        for (Result<Item> result : results) {
            Item item = result.get();
            String objectName = item.objectName();
            String relativePath = objectName.substring(userRoot.length());
            int lastSlash = relativePath.lastIndexOf('/');
            String path = relativePath.substring(0, lastSlash + 1);
            String name = relativePath.substring(lastSlash + 1);

            if (item.isDir() || relativePath.isEmpty()) {
                resultsList.add(new FileResourceDto(path, name, null, ResourceType.DIRECTORY));
            } else {
                resultsList.add(new FileResourceDto(path, name, item.size(), ResourceType.FILE));
            }
        }
        return resultsList;
    }

    /**
     * Поиск ресурсов по префиксу.
     * Ищем объекты, имена которых содержат заданный запрос.
     */
    public List<FileResourceDto> search(Long userId, String query) throws Exception {
        String userRoot = getUserRoot(userId);
        List<FileResourceDto> resultsList = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(userRoot)
                        .recursive(true)
                        .build());
        for (Result<Item> result : results) {
            Item item = result.get();
            if (item.objectName().toLowerCase().contains(query.toLowerCase())) {
                String fullName = item.objectName();
                int lastSlash = fullName.lastIndexOf('/');
                String path = fullName.substring(0, lastSlash + 1);
                String name = fullName.substring(lastSlash + 1);
                resultsList.add(new FileResourceDto(path, name, item.size(), ResourceType.FILE));
            }
        }
        return resultsList;
    }

    public void createDirectory(Long userId, String path) throws Exception {
        checkUserAuthorization(path);
        String userRoot = getUserRoot(userId);
        if (!path.endsWith("/")) {
            path += "/";
        }
        String objectName = userRoot + path;
        if (!objectName.startsWith(userRoot)) {
            throw new InvalidPathException("Невалидный путь");
        }
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
    }
}