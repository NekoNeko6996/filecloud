package com.app.filecloud.service.impl;

import com.app.filecloud.dto.UserDto;
import com.app.filecloud.entity.Role;
import com.app.filecloud.entity.User;
import com.app.filecloud.repository.RoleRepository;
import com.app.filecloud.repository.UserRepository;
import com.app.filecloud.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // Hàm kiểm tra xem hệ thống đã có chủ sở hữu chưa
    public boolean hasOwner() {
        return userRepository.count() > 0;
    }

    @Override
    @Transactional
    public User registerNewUser(UserDto userDto) {
        // 1. KIỂM TRA QUAN TRỌNG: Chỉ cho phép 1 user duy nhất
        if (hasOwner()) {
            throw new RuntimeException("Hệ thống đã được thiết lập chủ sở hữu. Không thể đăng ký thêm!");
        }

        // 2. Logic tạo user như cũ
        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setActive(true);

        // 3. Mặc định user này là ADMIN (Full quyền)
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName("ROLE_ADMIN");
                    return roleRepository.save(newRole);
                });
        
        user.setRoles(new HashSet<>(Collections.singletonList(adminRole)));

        return userRepository.save(user);
    }
}