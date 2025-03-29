package com.baykin.cloud_storage.skydrive.dto;

import com.baykin.cloud_storage.skydrive.model.Role;
import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String password;
    private Role role;
}
