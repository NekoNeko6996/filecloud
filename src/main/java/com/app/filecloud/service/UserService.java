package com.app.filecloud.service;

import com.app.filecloud.dto.UserDto;
import com.app.filecloud.entity.User;

public interface UserService {
    User registerNewUser(UserDto userDto);
}