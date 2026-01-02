package com.app.filecloud.controller;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.repository.ContentSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final ContentSubjectRepository subjectRepository;

    @GetMapping
    public String subjectsPage(Model model) {
        List<ContentSubject> subjects = subjectRepository.findAll();
        model.addAttribute("subjects", subjects);
        return "subjects";
    }
    
    // --- MỚI: Trang Profile (Static Demo) ---
    @GetMapping("/profile")
    public String subjectProfilePage() {
        return "subject-profile";
    }

    // API tạo nhanh Subject
    @PostMapping("/create")
    public String createSubject(@RequestParam("name") String name) {
        ContentSubject subject = ContentSubject.builder().mainName(name).build();
        subjectRepository.save(subject);
        return "redirect:/subjects";
    }
}