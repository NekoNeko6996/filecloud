package com.app.filecloud.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class AdminPageController {
    @GetMapping("/admin/scan-test")
    public String scanPage() {
        return "admin/admin_scan_test";
    }
}