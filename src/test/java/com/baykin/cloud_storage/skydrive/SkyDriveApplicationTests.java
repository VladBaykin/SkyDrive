package com.baykin.cloud_storage.skydrive;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SkyDriveApplicationTests {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.12")
            .withDatabaseName("cloud_storage")
            .withUsername("postgres")
            .withPassword("password");

    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.2")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());


    static GenericContainer<?> minio = new GenericContainer<>("minio/minio")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static {
        postgres.start();
        redis.start();
        minio.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getFirstMappedPort());

        registry.add("minio.url", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket-name", () -> "user-files");
    }

    @Test
    void contextLoads() {
    }
}
