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

    @Override
    @Transactional
    public User registerNewUser(UserDto userDto) {
        // 1. Check Email Exists
        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // 2. Create User Entity from DTO
        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());

        // 3. Encrypt Password
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setActive(true);

        // 4. Grant Role, Default ROLE_USER
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    // If DB Role Not found (Self-healing)
                    Role newRole = new Role();
                    newRole.setName("ROLE_USER");
                    return roleRepository.save(newRole);
                });

        user.setRoles(new HashSet<>(Collections.singletonList(userRole)));

        // 5. Save to DB
        return userRepository.save(user);
    }
}