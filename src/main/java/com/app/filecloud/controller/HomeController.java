package com.app.filecloud.controller;

import com.app.filecloud.entity.ContentSubject;
import com.app.filecloud.entity.FileNode;
import com.app.filecloud.repository.ContentSubjectRepository;
import com.app.filecloud.repository.FileNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ContentSubjectRepository subjectRepository;
    private final FileNodeRepository fileNodeRepository;

    @GetMapping("/")
    public String dashboard(Model model) {
        List<ContentSubject> recentSubjects = subjectRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))
        ).getContent();

        List<FileNode> recentMedia = fileNodeRepository.findByType(
                FileNode.Type.FILE,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        model.addAttribute("recentSubjects", recentSubjects);
        model.addAttribute("recentFiles", recentMedia);

        return "dashboard";
    }
}
