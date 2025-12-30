package com.app.filecloud.controller;

import com.app.filecloud.dto.UserDto;
import com.app.filecloud.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new UserDto());
        return "register";
    }

    @PostMapping("/register")
    public String processRegister(@ModelAttribute("user") UserDto userDto) {
        try {
            userService.registerNewUser(userDto);
            return "redirect:/login?registered=true";
        } catch (RuntimeException e) {
            // Error
            return "redirect:/register?error=" + e.getMessage();
        }
    }
}