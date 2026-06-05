package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Department;
import com.example.demo.entity.Discipline;
import com.example.demo.entity.Work;
import com.example.demo.entity.enums.PlagiarismCheckStatus;
import com.example.demo.entity.enums.WorkState;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.DisciplineRepository;
import com.example.demo.repository.WorkRepository;
import com.example.demo.service.*;
import com.google.api.services.classroom.model.Course;
import com.google.api.services.classroom.model.CourseWork;
import com.google.api.services.classroom.model.CourseWorkMaterial;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController()
@RequiredArgsConstructor
@RequestMapping("/api/manager")
public class ManagerController {

    private final ManagerService managerService;
    private final GoogleClassroomService googleClassroomService;
    private final DisciplineService disciplineService;
    private final DepartmentService departmentService;
    private final BackgroundService backgroundService;
    private final DisciplineUpdateNotifier notifier;
    private final DisciplineRepository disciplineRepository;
    private final GoogleDriveService googleDriveService;
    private final WorkRepository workRepository;
    private final NotificationService notificationService;
    private final DepartmentRepository departmentRepository;
    private final ApplicationContext applicationContext;

    @GetMapping("/")
    public Department getDepartment(@AuthenticationPrincipal OAuth2User principal) {
        return managerService.getDepartment(principal.getAttribute("email"));
    }

    @Transactional
    @PostMapping("/disciplines")
    public Discipline createDiscipline(@RequestBody DisciplineRequest request, @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) throws GeneralSecurityException, IOException {
        Department department = managerService.getDepartment(authorizedClient.getPrincipalName());
        department.getDisciplines().forEach(discipline -> {
            if (discipline.getName().equals(request.getName()) && discipline.getYear().equals(request.getYear())) {
                throw new RuntimeException("Discipline already exists");
            }
        });
        Discipline discipline = disciplineService.createDiscipline(authorizedClient.getAccessToken().getTokenValue(), request);
        Set<Work> works = new HashSet<>(managerService.createWorks(authorizedClient.getAccessToken().getTokenValue(), discipline));
        workRepository.saveAll(works);
        discipline.setWorks(works);
        disciplineRepository.save(discipline);
        department.getDisciplines().add(discipline);
        departmentService.saveDepartment(department);
        backgroundService.verifyWorks(authorizedClient.getAccessToken().getTokenValue(), discipline, department, true);
        return discipline;
    }

    @Transactional
    @PutMapping("/disciplines/{id}/update")
    public Discipline updateWorks(
            @PathVariable Long id,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @RequestBody(required = false) UpdateWorkRequest request
    ) throws GeneralSecurityException, IOException {

        Long workId = request != null ? request.getWorkId() : null;

        Discipline discipline = disciplineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Discipline not found"));
        Department department = managerService.getDepartment(authorizedClient.getPrincipalName());
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // If workId is null, it means that this update is triggered by discipline update, 
        // so we need to update discipline users
        if (workId == null) {
            disciplineRepository.save(disciplineService.updateDisciplineUsers(accessToken, discipline));
        }
        Set<Work> newWorks = new HashSet<>(managerService.createWorks(accessToken, discipline));

        Map<String, Work> existingWorksByStudent = discipline.getWorks().stream()
                .filter(w -> w.getStudent() != null)
                .collect(Collectors.toMap(
                        w -> w.getStudent().getEmail(),
                        Function.identity()
                ));

        for (Work newWork : newWorks) {
            Work existing = existingWorksByStudent.get(newWork.getStudent().getEmail());
            // If workId is not null, it means that this update is triggered by work update,
            // so we need to check that we are updating the only one work
            if (workId != null && !Objects.equals(existing.getId(), workId)) {
                continue;
            }
            if (existing == null) {
                workRepository.save(newWork);
                discipline.getWorks().add(newWork);
            } else {
                if (!Objects.equals(existing.getSupervisor(), newWork.getSupervisor()) ||
                        !Objects.equals(existing.getReviewer(), newWork.getReviewer()) ||
                        !Objects.equals(existing.getTheme(), newWork.getTheme()) ||
                        !Objects.equals(existing.getStudentGroup(), newWork.getStudentGroup()) ||
                        !Objects.equals(existing.getRawStudentName(), newWork.getRawStudentName()) ||
                        !Objects.equals(existing.getRawSupervisorName(), newWork.getRawSupervisorName())) {
                    existing.setState(WorkState.ONLY_DATA_UPDATE);
                    existing.setSupervisor(newWork.getSupervisor());
                    existing.setReviewer(newWork.getReviewer());
                    existing.setTheme(newWork.getTheme());
                    existing.setStudentGroup(newWork.getStudentGroup());
                    existing.setRawStudentName(newWork.getRawStudentName());
                    existing.setRawSupervisorName(newWork.getRawSupervisorName());
                    existing.setExternalIdCode(newWork.getExternalIdCode());
                }
                if (!Objects.equals(existing.getTurnInDate(), newWork.getTurnInDate())) {
                    if (existing.getPlagiarismCheckStatus() == PlagiarismCheckStatus.IN_PROGRESS ||
                            existing.getPlagiarismCheckStatus() == PlagiarismCheckStatus.CHECKED) {
                        notificationService.createWorkAbortedNotification(existing, discipline, department);
                    } else {
                        existing.setState(WorkState.UPDATE);
                        existing.setClassroomLink(newWork.getClassroomLink());
                        existing.setTurnInDate(newWork.getTurnInDate());
                    }
                }
                workRepository.save(existing);
            }
        }
        disciplineRepository.save(discipline);
        if (workId != null) {
            backgroundService.verifyWorksSync(accessToken, discipline, department, false);
        }
        else
        {
            backgroundService.verifyWorks(accessToken, discipline, department, workId == null);
        }
        return discipline;
    }

    @PatchMapping("/disciplines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDiscipline(@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient
                                         authorizedClient, @PathVariable Long id, @RequestBody UpdateDisciplineRequest request) throws
            GeneralSecurityException, IOException {
        disciplineService.updateDiscipline(authorizedClient.getAccessToken().getTokenValue(), id, request);
    }

    @DeleteMapping("/disciplines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDiscipline(@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient
                                         authorizedClient, @PathVariable Long id) throws GeneralSecurityException, IOException {
        Discipline discipline = disciplineRepository.findById(id).orElseThrow(() -> new RuntimeException("Discipline not found"));
        if (discipline.getGoogleDriveFolderLink() != null) {
            googleDriveService.deleteFile(authorizedClient.getAccessToken().getTokenValue(), GoogleDriveService.extractFolderIdFromLink(discipline.getGoogleDriveFolderLink()));
        }
        disciplineRepository.delete(discipline);
    }

    @GetMapping("/disciplines/{id}/wait-update")
    public DeferredResult<Boolean> waitUntilUpdated(@PathVariable Long id) {
        return notifier.registerListener(id);
    }

    @GetMapping("/classrooms")
    public List<Course> getClassrooms(@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient
                                              authorizedClient)
            throws GeneralSecurityException, IOException {
        List<Course> allCourses = googleClassroomService.getCourses(authorizedClient.getAccessToken().getTokenValue());
        Pattern pattern = Pattern.compile("курсов|квал|дисертац", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return allCourses.stream()
                .filter(course -> pattern.matcher(course.getName()).find())
                .collect(Collectors.toList());
    }

    @PostMapping("/courseworks")
    public List<CourseWork> getCourseWorksByLink(
            @RequestBody Map<String, String> body,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient
    ) throws GeneralSecurityException, IOException {
        return googleClassroomService.getCourseWorks(authorizedClient.getAccessToken().getTokenValue(), body.get("courseLink"));
    }

    @PostMapping("/materials")
    public List<CourseWorkMaterial> getMaterialsByLink(
            @RequestBody Map<String, String> body,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient
    ) throws GeneralSecurityException, IOException {
        return googleClassroomService.getCourseMaterials(authorizedClient.getAccessToken().getTokenValue(), body.get("courseLink"));
    }

    @PostMapping("/works/export")
    public void exportWorks(@RequestBody ExportWorksRequest request, HttpServletResponse response,
                            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) throws IOException, GeneralSecurityException {
        byte[] zipBytes = managerService.exportWorksAsZip(
                request,
                authorizedClient.getAccessToken().getTokenValue()
        );

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        String filename = "works-export-" + timestamp + ".zip";

        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        response.getOutputStream().write(zipBytes);
        response.flushBuffer();
    }

    @PostMapping("/works/review")
    public void takeWorksForReview(
            @RequestBody ReviewWorksRequest request,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            HttpServletResponse response
    ) throws Exception {
        List<Work> works = workRepository.findAllById(request.getIds());
        Discipline discipline = disciplineRepository.findByWorkId(works.getFirst().getId());
        Department department = departmentRepository.findByResponsibleUserEmail(authorizedClient.getPrincipalName())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        for (Work work : works) {
            work.setPlagiarismCheckStatus(PlagiarismCheckStatus.IN_PROGRESS);
            notificationService.createUnderReviewNotification(work, discipline, department);
        }
        workRepository.saveAll(works);

        byte[] zipBytes = managerService.exportWorksAsZip(
                new ExportWorksRequest(request.getIds(), false, true, false, false),
                authorizedClient.getAccessToken().getTokenValue()
        );

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        String filename = "check-export-" + timestamp + ".zip";

        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        response.getOutputStream().write(zipBytes);
        response.flushBuffer();
    }

    @PostMapping(
            value = "/disciplines/{id}/plagiarism-reports",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Discipline uploadReports(
            @PathVariable Long id, @RequestPart("files") List<MultipartFile> files,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient
    ) throws GeneralSecurityException, IOException {
        Department department = managerService.getDepartment(authorizedClient.getPrincipalName());
        Discipline discipline = disciplineRepository.findById(id).orElseThrow(() -> new RuntimeException("Discipline not found"));
        return managerService.processReports(authorizedClient.getAccessToken().getTokenValue(), department, discipline, files);
    }

    @PutMapping("/works/{id}/update")
    public RedirectView updateWork(
            @PathVariable Long id,
            @RequestBody UpdateWorkRequest request,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient
    ) throws GeneralSecurityException, IOException {
        Discipline discipline = disciplineRepository.findByWorkId(id);

        // Call the controller's PUT handler through the Spring proxy so
        // transactional/proxy behavior is preserved and all parameters are passed.
        ManagerController proxy = applicationContext.getBean(ManagerController.class);
        proxy.updateWorks(discipline.getId(), authorizedClient, request);

        return new RedirectView("/api/manager/disciplines/" + discipline.getId() + "/update");
    }
}
