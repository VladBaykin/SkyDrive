package com.baykin.cloud_storage.skydrive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для передачи информации о файле/папке.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileResourceDto {
    private String path;
    private String name;
    private Long size;
    private ResourceType type;
}
