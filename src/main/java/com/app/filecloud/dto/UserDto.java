package com.app.filecloud.dto;

import lombok.Data;

@Data
public class UserDto {
    private String username;
    private String email;
    private String password;
    // Có thể thêm confirmPassword nếu cần validation kỹ hơn
}