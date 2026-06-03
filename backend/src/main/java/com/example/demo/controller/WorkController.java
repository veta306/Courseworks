package com.example.demo.controller;

import com.example.demo.dto.FileInfoDTO;
import com.example.demo.entity.Department;
import com.example.demo.entity.Work;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.WorkRepository;
import com.example.demo.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkRepository workRepository;
    private final DepartmentRepository departmentRepository;

    @GetMapping("/{id}")
    public Work getWork(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Work work = workRepository.findById(id).orElseThrow();
        Department department = departmentRepository.findByResponsibleUserEmail(principal.getName()).orElse(null);
        if ((work.getStudent() != null && work.getStudent().getEmail().equals(principal.getName())) ||
                (work.getSupervisor() != null && work.getSupervisor().getEmail().equals(principal.getName())) ||
                department != null
        ) {
            return work;
        }
        return null;
    }

    @PostMapping("/{id}/refresh-pdf")
    public List<FileInfoDTO> refreshPdfs(
            @PathVariable Long id
    ) {
        System.out.println(id);
        System.out.println("refreshPdfs");
        Work work = workRepository.findById(id).orElseThrow();
        String submissionLink = work.getGoogleSubmissionLink();
        System.out.println(submissionLink);

        return List.of(
                new FileInfoDTO("Додаткові завдання «Низькорівневі».pdf", "https://drive.google.com/file/d/1a9UTIWhAg_SOcXAhGvRZEz31y36tR-Fm/view"),
                new FileInfoDTO("Лабораторна робота №4_ПтаАМ_2026.pdf", "https://drive.google.com/file/d/1yGvYgXhHV7fcneDh1coSyUAdWdZBP-_F/view")
        );


    }

    @PostMapping("/{id}/select-pdf")
    boolean selectPdf(
            @PathVariable Long id,
            @RequestBody FileInfoDTO file
    ) {
        System.out.println(id);
        System.out.println("selectPdf");
        Work work = workRepository.findById(id).orElseThrow();
        work.setFullTextLink(file.getFileUrl());
        workRepository.save(work);
        return true;
    }
}
