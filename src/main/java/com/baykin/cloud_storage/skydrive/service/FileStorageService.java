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
     * Инициализация сервиса: проверка существования бакета и его создание при необходимости.
     */
    @PostConstruct
    public void init() throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * Получает корневой путь пользователя в облачном хранилище.
     *
     * @param userId идентификатор пользователя
     * @return корневой путь пользователя
     */
    private String getUserRoot(Long userId) {
        return authService.getUserRoot(userId);
    }

    /**
     * Проверяет, что относительный путь не содержит недопустимых символов и не выходит за пределы корневой папки пользователя.
     *
     * @param relativePath относительный путь к ресурсу
     * @throws InvalidPathException если путь некорректен
     */
    private void checkUserAuthorization(String relativePath) {
        if (relativePath == null) return;
        if (relativePath.contains("..") || relativePath.startsWith("/")) {
            throw new InvalidPathException("Недопустимый путь");
        }
    }

    /**
     * Загружает файл в облачное хранилище пользователя.
     *
     * @param userId идентификатор пользователя
     * @param relativePath относительный путь к папке
     * @param file файл для загрузки
     * @return DTO с информацией о загруженном файле
     * @throws AccessDeniedException если путь не принадлежит пользователю
     * @throws InvalidPathException если путь некорректен
     * @throws ResourceAlreadyExistsException если файл уже существует
     * @throws Exception при ошибках MinIO или ввода-вывода
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
     * Получает информацию о ресурсе (файл или папка) по относительному пути.
     *
     * @param userId идентификатор пользователя
     * @param relativePath относительный путь к ресурсу
     * @return DTO с информацией о ресурсе
     * @throws ResourceNotFoundException если ресурс не найден
     * @throws Exception при ошибках MinIO
     */
    public FileResourceDto getResourceInfo(Long userId, String relativePath) throws Exception {
        checkUserAuthorization(relativePath);
        String userRoot = getUserRoot(userId);
        String fullPath = relativePath.startsWith(userRoot) ? relativePath : userRoot + relativePath;
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );
            String normalizedPath = relativePath.startsWith(userRoot) ?
                    relativePath.substring(userRoot.length()) : relativePath;
            int lastSlash = normalizedPath.lastIndexOf("/");
            String path = lastSlash >= 0 ? normalizedPath.substring(0, lastSlash + 1) : "";
            String name = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;

            return new FileResourceDto(path, name, stat.size(), ResourceType.FILE);
        } catch (ErrorResponseException e) {
            if (!e.errorResponse().code().equals("NoSuchKey") &&
                    !e.errorResponse().code().equals("NotFound")) {
                throw e;
            }
        }
        String dirPrefix = fullPath.endsWith("/") ? fullPath : fullPath + "/";
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(dirPrefix)
                        .recursive(false)
                        .build()
        );
        boolean hasObjects = results.iterator().hasNext();
        if (hasObjects) {
            String normalizedPath = relativePath.startsWith(userRoot) ?
                    relativePath.substring(userRoot.length()) : relativePath;
            if (normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            int lastSlash = normalizedPath.lastIndexOf("/");
            String path = lastSlash >= 0 ? normalizedPath.substring(0, lastSlash + 1) : "";
            String name = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;

            return new FileResourceDto(path, name, null, ResourceType.DIRECTORY);
        } else {
            throw new ResourceNotFoundException("Ресурс не найден: " + relativePath);
        }
    }


    /**
     * Удаляет ресурс (файл или папку) пользователя.
     *
     * @param userId идентификатор пользователя
     * @param relativePath относительный путь к ресурсу
     * @throws ResourceNotFoundException если ресурс не найден
     * @throws Exception при ошибках MinIO
     */
    public void deleteResource(Long userId, String relativePath) throws Exception {
        checkUserAuthorization(relativePath);
        String userRoot = getUserRoot(userId);
        String fullPath = relativePath.startsWith(userRoot) ? relativePath : userRoot + relativePath;
        try {
            getResourceInfo(userId, relativePath);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Ресурс для удаления не найден: " + relativePath);
        }
        if (relativePath.endsWith("/") || fullPath.endsWith("/")) {
            String prefix = fullPath.endsWith("/") ? fullPath : fullPath + "/";
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
        );
        for (Result<Item> result : results) {
            String objectName = result.get().objectName();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        }
    } else {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new ResourceNotFoundException("Файл не найден: " + relativePath);
            }
            throw e;
        }
    }
}

    /**
     * Перемещает или переименовывает ресурс пользователя.
     *
     * @param userId идентификатор пользователя
     * @param from исходный путь
     * @param to целевой путь
     * @return DTO с информацией о перемещённом ресурсе
     * @throws AccessDeniedException если путь не принадлежит пользователю
     * @throws Exception при ошибках MinIO
     */
    public FileResourceDto moveResource(Long userId, String from, String to) throws Exception {
        checkUserAuthorization(from);
        checkUserAuthorization(to);
        String userRoot = getUserRoot(userId);
        String sourceRelative = from.startsWith(userRoot) ? from.substring(userRoot.length()) : from;
        String targetRelative = to.startsWith(userRoot)   ? to.substring(userRoot.length())   : to;
        String sourceObject = userRoot + sourceRelative;
        String targetObject = userRoot + targetRelative;
        if (!sourceObject.startsWith(userRoot) || !targetObject.startsWith(userRoot)) {
            throw new AccessDeniedException("Пути должны находиться в корневой папке пользователя");
        }
        if (sourceRelative.endsWith("/")) {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(sourceObject)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> r : results) {
                Item item = r.get();
                if (item.isDir()) continue;
                String objectName = item.objectName();
                String innerPath = objectName.substring(sourceObject.length());
                String newObjectName = targetObject + innerPath;
                minioClient.copyObject(CopyObjectArgs.builder()
                        .bucket(bucket)
                        .object(newObjectName)
                        .source(CopySource.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .build())
                        .build());
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
            }
        } else {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetObject)
                    .source(CopySource.builder()
                            .bucket(bucket)
                            .object(sourceObject)
                            .build())
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(sourceObject)
                    .build());
        }
        return getResourceInfo(userId, targetObject);
    }

    /**
     * Скачивает файл пользователя.
     *
     * @param userId идентификатор пользователя
     * @param relativePath относительный путь к файлу
     * @return InputStream для чтения файла
     * @throws InvalidPathException если путь некорректен или указывает на папку
     * @throws ResourceNotFoundException если файл не найден
     * @throws Exception при ошибках MinIO
     */
    public InputStream downloadResource(Long userId, String relativePath) throws Exception {
        if (relativePath == null || relativePath.isBlank()) {
            throw new InvalidPathException("Путь не может быть пустым");
        }
        if (relativePath.endsWith("/")) {
            throw new InvalidPathException("Для скачивания папки используйте метод downloadFolderZip");
        }
        checkUserAuthorization(relativePath);
        String userRoot = getUserRoot(userId);
        if (!relativePath.startsWith(userRoot)) {
            throw new AccessDeniedException("Путь не принадлежит текущему пользователю");
        }
        String objectName = relativePath;
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
     * Скачивает папку пользователя в виде ZIP-архива.
     *
     * @param userId идентификатор пользователя
     * @param relativePath относительный путь к папке
     * @return InputStream с ZIP-архивом папки
     * @throws InvalidPathException если путь некорректен
     * @throws AccessDeniedException если путь не принадлежит пользователю
     * @throws Exception при ошибках MinIO
     */
    public InputStream downloadFolderZip(Long userId, String relativePath) throws Exception {
        if (relativePath == null) {
            throw new InvalidPathException("Путь не может быть пустым");
        }
        String normalized = relativePath.endsWith("/") ? relativePath : relativePath + "/";
        checkUserAuthorization(normalized);
        String userRoot = getUserRoot(userId);
        if (!normalized.startsWith(userRoot)) {
            throw new AccessDeniedException("Путь не принадлежит текущему пользователю");
        }
        String prefix = normalized;
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
     * Получает содержимое папки пользователя.
     *
     * @param userId идентификатор пользователя
     * @param folderPath относительный путь к папке
     * @param recursive если true — возвращает содержимое рекурсивно
     * @return список DTO с информацией о файлах и папках
     * @throws Exception при ошибках MinIO
     */
    public List<FileResourceDto> listDirectory(Long userId, String folderPath, boolean recursive) throws Exception {
        checkUserAuthorization(folderPath);
        String userRoot = getUserRoot(userId);

        String fullPrefix = userRoot + (folderPath != null ? folderPath : "");
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }
        List<FileResourceDto> result = new ArrayList<>();
        Iterable<Result<Item>> items = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(fullPrefix)
                        .recursive(recursive)
                        .build()
        );
        for (Result<Item> r : items) {
            Item item = r.get();
            String objectName = item.objectName();
            String relPath = objectName.substring(userRoot.length());
            boolean isDir = item.isDir() || relPath.endsWith("/");
            String normalized = isDir
                    ? relPath.substring(0, relPath.length() - 1)
                    : relPath;
            int idx = normalized.lastIndexOf("/");
            String path = idx >= 0
                    ? normalized.substring(0, idx + 1)
                    : "";
            String name = idx >= 0
                    ? normalized.substring(idx + 1)
                    : normalized;
            Long size = isDir ? null : item.size();
            ResourceType type = isDir ? ResourceType.DIRECTORY : ResourceType.FILE;

            result.add(new FileResourceDto(path, name, size, type));
        }

        return result;
    }

    /**
     * Ищет файлы и папки пользователя по запросу.
     *
     * @param userId идентификатор пользователя
     * @param query строка для поиска
     * @return список DTO с найденными файлами и папками
     * @throws Exception при ошибках MinIO
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

    /**
     * Создаёт новую пустую папку в облачном хранилище пользователя.
     *
     * @param userId идентификатор пользователя
     * @param path относительный путь к новой папке
     * @throws AccessDeniedException если путь не принадлежит пользователю
     * @throws InvalidPathException если путь некорректен
     * @throws Exception при ошибках MinIO
     */
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