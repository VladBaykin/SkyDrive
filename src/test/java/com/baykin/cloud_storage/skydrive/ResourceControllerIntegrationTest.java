package com.baykin.cloud_storage.skydrive;

import com.baykin.cloud_storage.skydrive.dto.AuthRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
// Обеспечиваем чистоту контекста между тестами
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ResourceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueUsername;

    @BeforeEach
    void setUp() throws Exception {
        uniqueUsername = "fileuser" + System.currentTimeMillis();
        AuthRequest registerRequest = new AuthRequest();
        registerRequest.setUsername(uniqueUsername);
        registerRequest.setPassword("password");

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    void testFileUploadAndConflict() throws Exception {
        // Создаем multipart файл
        MockMultipartFile file = new MockMultipartFile("file", "test.txt",
                "text/plain", "Hello, world!".getBytes());

        // Первый вызов – ожидаем статус 201 Created
        mockMvc.perform(multipart("/api/resource")
                        .file(file)
                        .param("path", "documents/")
                        .with(user(uniqueUsername).password("password").roles("USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("test.txt")))
                .andExpect(jsonPath("$.type", is("FILE")));

        // Повторная загрузка того же файла должна вернуть статус 409 Conflict
        mockMvc.perform(multipart("/api/resource")
                        .file(file)
                        .param("path", "documents/")
                        .with(user(uniqueUsername).password("password").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("уже существует")));
    }

    @Test
    void testListDirectoryAndDownloadZip() throws Exception {
        // Загружаем два файла в папку "archive/"
        MockMultipartFile file1 = new MockMultipartFile("file", "file1.txt",
                "text/plain", "Content of file1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "file2.txt",
                "text/plain", "Content of file2".getBytes());

        mockMvc.perform(multipart("/api/resource")
                        .file(file1)
                        .param("path", "archive/")
                        .with(user(uniqueUsername).password("password").roles("USER")))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/resource")
                        .file(file2)
                        .param("path", "archive/")
                        .with(user(uniqueUsername).password("password").roles("USER")))
                .andExpect(status().isCreated());

        // Получаем содержимое папки (нерекурсивно)
        mockMvc.perform(get("/api/directory")
                        .param("path", "archive/")
                        .param("recursive", "false")
                        .with(user(uniqueUsername).password("password").roles("USER")))
                .andExpect(status().isOk());

        // Формируем полный путь к папке.
        // Корневой путь формируется как "user-{username}-files/"
        String folderPath = "user-" + uniqueUsername + "-files/archive/";
        // Скачиваем папку в виде ZIP-архива
        mockMvc.perform(get("/api/resource/download")
                        .param("path", folderPath)
                        .param("zip", "true")
                        .with(user(uniqueUsername).password("password").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"archive.zip\"")))
                .andExpect(content().contentType("application/zip"));
    }
}