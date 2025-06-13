package com.baykin.cloud_storage.skydrive;

import com.baykin.cloud_storage.skydrive.dto.AuthRequest;
import com.baykin.cloud_storage.skydrive.repository.UserRepository;
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

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ResourceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private final String username = "user";
    private final String password = "password";

    @BeforeEach
    void setup() throws Exception {
        userRepository.deleteAll();

        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadListDownloadDeleteFlow() throws Exception {
        // 1) Загрузка файла
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/resource")
                        .file(file)
                        .param("path", "docs/")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].name").value("a.txt"));

        // 2) Повторная загрузка → 400 или 409 (в зависимости от вашего кода)
        mockMvc.perform(multipart("/api/resource")
                        .file(file)
                        .param("path", "docs/")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isConflict());

        // 3) Листинг директории
        mockMvc.perform(get("/api/directory")
                        .param("path", "docs/")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("a.txt"));

        // 4) Получение информации о ресурсе
        String fullPath = String.format("user-%d-files/docs/a.txt",
                userRepository.findByUsername(username).get().getId());
        mockMvc.perform(get("/api/resource")
                        .param("path", fullPath)
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FILE"));

        // 5) Скачивание файла
        mockMvc.perform(get("/api/resource/download")
                        .param("path", fullPath)
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));

        // 6) Удаление файла
        mockMvc.perform(delete("/api/resource")
                        .param("path", fullPath)
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isNoContent());

        // 7) Повторное получение → 404
        mockMvc.perform(get("/api/resource")
                        .param("path", fullPath)
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAndDownloadFolderAsZip() throws Exception {
        // Загружаем два файла в архив
        MockMultipartFile f1 = new MockMultipartFile("file", "f1.txt",
                "text/plain", "1".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("file", "f2.txt",
                "text/plain", "2".getBytes());

        mockMvc.perform(multipart("/api/resource").file(f1).param("path","ziptest/")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/resource").file(f2).param("path","ziptest/")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isCreated());

        // Скачиваем папку как zip
        String folderPath = String.format("user-%d-files/ziptest/",
                userRepository.findByUsername(username).get().getId());
        mockMvc.perform(get("/api/resource/download")
                        .param("path", folderPath)
                        .param("zip", "true")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("archive.zip")))
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    void moveAndSearch() throws Exception {
        // Загрузка файла
        MockMultipartFile file = new MockMultipartFile("file","m.txt",
                "text/plain","m".getBytes());
        mockMvc.perform(multipart("/api/resource").file(file).param("path","mv/")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isCreated());

        // Переименование
        String from = String.format("user-%d-files/mv/m.txt",
                userRepository.findByUsername(username).get().getId());
        String to   = String.format("user-%d-files/mv/new.txt",
                userRepository.findByUsername(username).get().getId());
        mockMvc.perform(post("/api/resource/move")
                        .param("from", from)
                        .param("to", to)
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new.txt"));

        // Поиск
        mockMvc.perform(get("/api/resource/search")
                        .param("query","new")
                        .with(user(username).password(password).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("new.txt"));
    }
}