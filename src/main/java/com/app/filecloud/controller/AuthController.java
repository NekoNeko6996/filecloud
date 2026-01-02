package com.app.filecloud.controller;

import com.app.filecloud.dto.UserDto;
import com.app.filecloud.service.impl.UserServiceImpl; // Import Impl để dùng hàm hasOwner hoặc cast từ interface
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserServiceImpl userService; // Inject trực tiếp Impl hoặc thêm method vào Interface

    @GetMapping("/login")
    public String loginPage(Model model) {
        // Nếu chưa có user nào (Lần chạy đầu tiên) -> Gợi ý sang trang register
        if (!userService.hasOwner()) {
            return "redirect:/register";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        // Nếu ĐÃ có chủ sở hữu -> Không cho vào trang đăng ký nữa
        if (userService.hasOwner()) {
            return "redirect:/login";
        }
        
        model.addAttribute("user", new UserDto());
        return "register";
    }

    @PostMapping("/register")
    public String processRegister(@ModelAttribute("user") UserDto userDto) {
        try {
            userService.registerNewUser(userDto);
            return "redirect:/login?registered=true";
        } catch (RuntimeException e) {
            return "redirect:/register?error=" + e.getMessage();
        }
    }
}