package com.baykin.cloud_storage.skydrive;

import org.springframework.boot.SpringApplication;

public class TestSkyDriveApplication {

    public static void main(String[] args) {
        SpringApplication.from(SkyDriveApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
