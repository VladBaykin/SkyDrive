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
     * Удаление ресурса (файл или папка).
     * Если ресурс – папка, удаляются все объекты с данным префиксом.
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
     * Перемещение (переименование) ресурса.
     * Реализуется через копирование и удаление исходного объекта.
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
     * Скачивание папки в виде ZIP-архива.
     * Метод находит все объекты с заданным префиксом и архивирует их.
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
     * Получение содержимого папки.
     * Если recursive = false — возвращает только объекты первого уровня,
     * иначе — все объекты с указанным префиксом.
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