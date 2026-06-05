package com.example.demo.controller;

import com.example.demo.dto.WorkDTO;
import com.example.demo.entity.Department;
import com.example.demo.entity.Discipline;
import com.example.demo.entity.Work;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.DisciplineRepository;
import com.example.demo.repository.WorkRepository;
import com.example.demo.service.GoogleClassroomService;
import com.example.demo.service.ManagerService;
import com.google.api.services.classroom.model.StudentSubmission;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkRepository workRepository;
    private final DepartmentRepository departmentRepository;
    private final GoogleClassroomService googleClassroomService;
    private final DisciplineRepository disciplineRepository;

    @GetMapping("/{id}")
    public WorkDTO getWork(@PathVariable Long id,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient)
            throws GeneralSecurityException, IOException {
        Work work = workRepository.findById(id).orElseThrow();
        Department department = departmentRepository.findByResponsibleUserEmail(authorizedClient.getPrincipalName())
                .orElse(null);
        if ((work.getStudent() != null && work.getStudent().getEmail().equals(authorizedClient.getPrincipalName())) ||
                (work.getSupervisor() != null
                        && work.getSupervisor().getEmail().equals(authorizedClient.getPrincipalName()))
                ||
                department != null) {
            if (work.getGoogleSubmissionId() == null) {
                return new WorkDTO(work, null);
            }
            Discipline discipline = disciplineRepository.findByWorkId(work.getId());
            StudentSubmission studentSubmission = googleClassroomService.getSubmission(
                    authorizedClient.getAccessToken().getTokenValue(),
                    discipline.getGoogleClassId(), discipline.getGoogleAssignmentId(), work.getGoogleSubmissionId());

            LocalDateTime latestActionDate = OffsetDateTime
                    .parse(studentSubmission.getSubmissionHistory().reversed().stream()
                            .filter(el -> el.getStateHistory() != null &&
                                    el.getStateHistory().getState().equals("TURNED_IN"))
                            .findFirst().get().getStateHistory().getStateTimestamp())
                    .atZoneSameInstant(ZoneId.of("Europe/Kyiv")).toLocalDateTime();
            return new WorkDTO(work, latestActionDate);
        }
        return null;
    }
}
