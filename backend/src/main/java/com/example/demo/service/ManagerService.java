package com.example.demo.service;

import com.example.demo.dto.ExportWorksRequest;
import com.example.demo.entity.*;
import com.example.demo.entity.enums.DisciplineType;
import com.example.demo.entity.enums.PlagiarismCheckStatus;
import com.example.demo.entity.enums.WorkState;
import com.example.demo.repository.*;
import com.example.demo.utils.PDFTools;
import com.example.demo.utils.StrDist;
import com.google.api.services.classroom.model.*;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ManagerService {
    private final DepartmentRepository departmentRepository;
    private final GoogleClassroomService googleClassroomService;
    private final UserRepository userRepository;
    private final WorkRepository workRepository;
    private final GoogleSheetsService googleSheetsService;
    private final GoogleDriveService googleDriveService;
    private final PlagiarismReportRepository plagiarismReportRepository;
    private final DisciplineRepository disciplineRepository;
    private final NotificationService notificationService;

    public Department getDepartment(String email) {
        return departmentRepository.findByResponsibleUserEmail(email).orElse(null);
    }

    public List<Work> createWorks(String accessToken, Discipline discipline) throws GeneralSecurityException, IOException {
        List<StudentSubmission> submissions = googleClassroomService.getSubmissions(accessToken, discipline.getGoogleClassId(), discipline.getGoogleAssignmentId());
        List<Student> students = googleClassroomService.getStudents(accessToken, discipline.getGoogleClassId());
        List<Work> workList = new ArrayList<>();
        List<GoogleSheetsService.AssignmentRecord> assignments = googleSheetsService.extractAssignments(accessToken, discipline.getTopicDistributionLink());
        Set<User> supervisors = new HashSet<>(discipline.getSupervisors());
        Pattern PHOTO_DATETIME = Pattern.compile(".*\\(.{0,7}\\d{1,2}.{0,7}\\d{4}.{0,7}\\d{1,2}([:\\s]{0,3})\\d{2}(\\s*(AM|PM))?\\)\\.pdf", Pattern.CASE_INSENSITIVE);

        if (submissions != null && !(submissions.isEmpty())) {
            for (StudentSubmission submission : submissions) {
                String studentEmail = students.stream()
                        .filter(student -> submission.getUserId().equals(student.getUserId()))
                        .map(student -> student.getProfile().getEmailAddress())
                        .findFirst()
                        .orElse("Unknown");
                System.out.println("userId = " + submission.getUserId() + " , email = " + studentEmail + " , grade = " + submission.getAssignedGrade() + " , updateTime = " + submission.getUpdateTime());
                Optional<User> student = userRepository.findByEmail(studentEmail);
                if (student.isPresent()) {
                    Optional<GoogleSheetsService.AssignmentRecord> assignmentRecord = findMatchingAssignment(student.get(), assignments);
                    List<Attachment> attachments = submission.getAssignmentSubmission().getAttachments();
                    if (attachments != null) {
                        long maxSize = Integer.MIN_VALUE / 2;
                        Attachment attachmentChosen = null;
                        // System.out.println(studentEmail + " //// " + student.get().getName() + " has " + attachments.size() + " attachments");
                        for (Attachment attachment : attachments) {
                            if (attachment.getDriveFile() != null && attachment.getDriveFile().getTitle() != null && attachment.getDriveFile().getTitle().endsWith(".pdf")) {
                                try {
                                    File fileMetaData = googleDriveService.getFileMetadata(accessToken, attachment.getDriveFile().getId());
                                    String title = attachment.getDriveFile().getTitle();
                                    // System.out.println(title + " /// " + attachment.getDriveFile().getId());
                                    long size = fileMetaData.getSize();
                                    if (title.contains("повна"))
                                        size *= 2;
                                    if (PHOTO_DATETIME.matcher(title).find()) {
                                        System.out.println("``" + title + "'' is probably a photo");
                                        size /= 5;
                                    }
                                    // System.out.println("size (changed) is " + size);public
                                    if (size > maxSize) {
                                        if (maxSize > 0) {
                                            System.out.println("For student " + student.get().getName() + ", file was changed from " +
                                                    (attachmentChosen == null ? "null" : attachmentChosen.getDriveFile().getTitle()) +
                                                    " (" + maxSize + " byte(s)) to " + attachment.getDriveFile().getTitle() + " (" + size + " byte(s))");
                                        }
                                        maxSize = size;
                                        attachmentChosen = attachment;
                                    }
                                } catch (IOException e) {
                                    System.out.println(e.getMessage());
                                    continue;
                                }
                            }
                        }
                        if (attachmentChosen != null) {
                            Work work = new Work();
                            work.setState(WorkState.NEW);
                            work.setPlagiarismCheckStatus(PlagiarismCheckStatus.NOT_CHECKED);
                            work.setStudent(student.orElse(null));

                            work.setClassroomLink(attachmentChosen.getDriveFile().getAlternateLink());
                            work.setGoogleSubmissionLink(submission.getAlternateLink());
                            work.setTopicDistributionLink(discipline.getTopicDistributionLink());
                            work.setType(discipline.getType());
                            work.setTurnInDate(OffsetDateTime.parse(submission.getSubmissionHistory().reversed().stream()
                                            .filter(el -> el.getStateHistory() != null && el.getStateHistory().getState().equals("TURNED_IN"))
                                            .findFirst().get().getStateHistory().getStateTimestamp())
                                    .atZoneSameInstant(ZoneId.of("Europe/Kyiv")).toLocalDateTime());
                            assignmentRecord.ifPresent(record -> {
                                work.setTheme(record.topic());
                                work.setRawSupervisorName(record.supervisor());
                                work.setRawStudentName(record.student());
                                Optional<User> supervisor = supervisors.stream()
                                        .filter(user -> PDFTools.isNameMentioned(user.getName(), record.supervisor()))
                                        .findFirst();
                                work.setSupervisor(supervisor.orElse(null));
                                if (work.getType() == DisciplineType.QUALIFICATION_WORK) {
                                    Optional<User> reviewer = supervisors.stream()
                                            .filter(user -> PDFTools.isNameMentioned(user.getName(), record.reviewer()))
                                            .findFirst();
                                    work.setReviewer(reviewer.orElse(null));
                                    work.setRawReviewerName(record.reviewer());
                                    work.setExternalIdCode(record.externalIdCode());
                                }
                                work.setStudentGroup(record.group());
                            });
                            workList.add(work);
                        }
                    }
                } else {
                    System.out.println("Student email " + studentEmail + " not found");
                }
            }
        } else {
            System.out.println("submissions not found");
        }
        return workList;
    }

    private Optional<GoogleSheetsService.AssignmentRecord> findMatchingAssignment(User student, List<GoogleSheetsService.AssignmentRecord> assignments) {
        // TODO: move to new util class, together with some actually string-only methods from PDFTools
        String ns = student.getName();
        String apostrophes = StrDist.APOSTROPHES.replace("'", "");
        for(int i=ns.length()-1; i>=0; i--) {
            if (apostrophes.indexOf(ns.charAt(i)) != -1)
                ns = ns.replace(ns.charAt(i), '\'');
        }
        final String nameSimplifiedApostrophes = ns;
        return assignments.stream()
                .filter(record -> PDFTools.isNameMentioned(nameSimplifiedApostrophes, record.student()))
                .findFirst();
    }

    public byte[] exportWorksAsZip(ExportWorksRequest request, String accessToken) throws IOException, GeneralSecurityException {
        List<Work> works = workRepository.findAllById(request.getIds());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(baos);

        for (Work work : works) {
            if (request.isIncludeFull() && work.getFullTextLink() != null) {
                String fileId = GoogleDriveService.extractFileIdFromLink(work.getFullTextLink());
                File metadata = googleDriveService.getFileMetadata(accessToken, fileId);
                InputStream input = googleDriveService.getFileContent(accessToken, work.getFullTextLink());
                zipOut.putNextEntry(new ZipEntry(metadata.getName()));
                input.transferTo(zipOut);
                zipOut.closeEntry();
            }

            if (request.isIncludeShort() && work.getShortTextLink() != null) {
                String fileId = GoogleDriveService.extractFileIdFromLink(work.getShortTextLink());
                File metadata = googleDriveService.getFileMetadata(accessToken, fileId);
                InputStream input = googleDriveService.getFileContent(accessToken, work.getShortTextLink());
                zipOut.putNextEntry(new ZipEntry(metadata.getName()));
                input.transferTo(zipOut);
                zipOut.closeEntry();
            }

            if (request.isIncludeFullReport() &&
                    work.getPlagiarismReport() != null &&
                    work.getPlagiarismReport().getFullReportLink() != null) {
                String fileId = GoogleDriveService.extractFileIdFromLink(work.getPlagiarismReport().getFullReportLink());
                File metadata = googleDriveService.getFileMetadata(accessToken, fileId);
                InputStream input = googleDriveService.getFileContent(accessToken, work.getPlagiarismReport().getFullReportLink());
                zipOut.putNextEntry(new ZipEntry(metadata.getName()));
                input.transferTo(zipOut);
                zipOut.closeEntry();
            }

            if (request.isIncludeShortReport() &&
                    work.getPlagiarismReport() != null &&
                    work.getPlagiarismReport().getShortReportLink() != null) {
                String fileId = GoogleDriveService.extractFileIdFromLink(work.getPlagiarismReport().getShortReportLink());
                File metadata = googleDriveService.getFileMetadata(accessToken, fileId);
                InputStream input = googleDriveService.getFileContent(accessToken, work.getPlagiarismReport().getShortReportLink());
                zipOut.putNextEntry(new ZipEntry(metadata.getName()));
                input.transferTo(zipOut);
                zipOut.closeEntry();
            }
        }

        zipOut.close();
        return baos.toByteArray();
    }

    public Discipline processReports(String accessToken, Department department, Discipline discipline, List<MultipartFile> files) throws IOException, GeneralSecurityException {
        for (MultipartFile file : files) {
            String firstPage = PDFTools.extractFirstPageText(file.getInputStream());
            for (Work work : discipline.getWorks()) {
                if (work.getStudent() != null && StrDist.calcStrDist(work.getStudent().getName(), firstPage,
                        StrDist.SearchBorder.ANYWHERE, StrDist.SearchBorder.ANYWHERE, false, true).matchLevel.betterOrEqual(StrDist.MatchLevel.MEDIUM))
                {
                    List<String> relatedUserEmails = ManagerService.getRelatedUsers(department, work);
                    PlagiarismReport report;
                    if (work.getPlagiarismReport() != null) {
                        report = work.getPlagiarismReport();
                    } else {
                        report = new PlagiarismReport();
                    }
                    String workName = PDFTools.getFileName(discipline, work)
                            .replace("{0}_", "")
                            .replace("_{0}", "");

                    if (PDFTools.getNumberOfPages(file.getInputStream()) > 5) {
                        String fileId;
                        if (report.getFullReportLink() == null) {
                            fileId = googleDriveService.uploadFile(accessToken,
                                    "ЗВІТ_ПОВНИЙ_" + workName,
                                    "application/pdf",
                                    file.getInputStream().readAllBytes(),
                                    GoogleDriveService.extractFolderIdFromLink(discipline.getGoogleDriveFolderLink()));
                        } else {
                            fileId = googleDriveService.updateFileContent(accessToken,
                                    work.getPlagiarismReport().getFullReportLink(),
                                    file.getInputStream().readAllBytes());
                        }
                        googleDriveService.addViewerPermissionsToMultipleUsers(
                                accessToken,
                                fileId,
                                relatedUserEmails
                        );
                        report.setFullReportLink("https://drive.google.com/file/d/" + fileId);
                    } else {
                        String fileId;
                        if (report.getShortReportLink() == null) {
                            fileId = googleDriveService.uploadFile(accessToken,
                                    "ЗВІТ_КОРОТКИЙ_" + workName,
                                    "application/pdf",
                                    file.getInputStream().readAllBytes(),
                                    GoogleDriveService.extractFolderIdFromLink(discipline.getGoogleDriveFolderLink()));
                        } else {
                            fileId = googleDriveService.updateFileContent(accessToken,
                                    work.getPlagiarismReport().getFullReportLink(),
                                    file.getInputStream().readAllBytes());
                        }
                        googleDriveService.addViewerPermissionsToMultipleUsers(
                                accessToken,
                                fileId,
                                relatedUserEmails
                        );
                        report.setShortReportLink("https://drive.google.com/file/d/" + fileId);
                    }
                    work.setPlagiarismCheckStatus(PlagiarismCheckStatus.CHECKED);
                    work.setPlagiarismReport(plagiarismReportRepository.save(report));
                    if ((work.getPlagiarismReport().getFullReportLink() != null
                            && work.getPlagiarismReport().getShortReportLink() == null) ||
                            (work.getPlagiarismReport().getFullReportLink() == null
                                    && work.getPlagiarismReport().getShortReportLink() != null)) {
                        notificationService.createReportAddedNotification(work, discipline, department);
                    }
                    workRepository.save(work);
                }
            }
        }
        return disciplineRepository.save(discipline);
    }

    public static List<String> getRelatedUsers(Department department, Work work) {
        List<String> relatedUserEmails = new ArrayList<>();
        if (work.getStudent() != null) {
            relatedUserEmails.add(work.getStudent().getEmail());
        }
        if (work.getSupervisor() != null) {
            relatedUserEmails.add(work.getSupervisor().getEmail());
        }
        if (work.getReviewer() != null) {
            relatedUserEmails.add(work.getReviewer().getEmail());
        }

        relatedUserEmails.addAll(department.getHeadUsers().stream().map(user -> user.getEmail()).toList());

        relatedUserEmails = relatedUserEmails.stream()
//                .filter(email -> !email.equals(department.getResponsibleUser().getEmail())) // TODO: make this option available in UI
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>())).stream().toList();

        return relatedUserEmails;
    }
}
