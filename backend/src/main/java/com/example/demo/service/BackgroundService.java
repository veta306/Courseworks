package com.example.demo.service;

import com.example.demo.entity.Department;
import com.example.demo.entity.Discipline;
import com.example.demo.entity.enums.DisciplineType;
import com.example.demo.entity.enums.FileNameTemplate;
import com.example.demo.entity.enums.MatchLevel;
import com.example.demo.entity.Work;
import com.example.demo.entity.enums.WorkState;
import com.example.demo.repository.DisciplineRepository;
import com.example.demo.repository.WorkRepository;
import com.example.demo.utils.PDFTools;
import com.example.demo.utils.StrDist;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BackgroundService {
    private final GoogleDriveService googleDriveService;
    private final WorkRepository workRepository;
    private final DisciplineRepository disciplineRepository;
    private final DisciplineUpdateNotifier notifier;
    private final NotificationService notificationService;

    private MatchLevel castMatchLevel(StrDist.MatchLevel matchLevel) {
        switch (matchLevel) {
            case HIGH -> { return MatchLevel.HIGH; }
            case MEDIUM -> { return MatchLevel.MEDIUM; }
            case LOW -> { return MatchLevel.LOW; }
            case NOT_MATCHED -> { return MatchLevel.NOT_MATCHED; }
        }
        System.err.printf("Unknown match level: %s\n", matchLevel);
        return MatchLevel.NOT_MATCHED;
    }

    @Async("asyncExecutor")
    public void verifyWorks(String accessToken, Discipline discipline, Department department) throws GeneralSecurityException, IOException {
        Set<Work> works = discipline.getWorks();

        String programFolderId = googleDriveService.createFolderIfNotExists(accessToken, "CourseworkManagement", null);
        String disciplineFolderId = googleDriveService.createFolderIfNotExists(accessToken, discipline.getName() + "-" + discipline.getYear(), programFolderId);
        discipline.setGoogleDriveFolderLink(
                "https://drive.google.com/drive/folders/" + disciplineFolderId
        );
        disciplineRepository.save(discipline);

        for (Work work : works) {

            if (work.getState() == WorkState.DEFAULT) {
                continue;
            }

            byte[] originalFileContent;
            try {
                originalFileContent = googleDriveService.getFileContent(accessToken, work.getClassroomLink()).readAllBytes();
            } catch (IOException e) {
                workRepository.save(work);
                continue;
            }
            String firstPage = PDFTools.extractFirstPageText(googleDriveService.getFileContent(accessToken, work.getClassroomLink()));

            try {
                Files.writeString(Path.of(work.getExternalIdCode() + "_" + work.getRawStudentName().replace("\\s", "\\x20") + ".txt"), firstPage);
                System.out.println("File ``" + work.getExternalIdCode() + "_" + work.getRawStudentName().replace("\\s", "\\x20") + ".txt'' probably created successfully");
            } catch (IOException | IllegalArgumentException | NullPointerException e) {
                System.out.println("Failed to create local file ``" + work.getExternalIdCode() + "_" + work.getRawStudentName() + ".txt''");
                System.out.println(e.getMessage());
            }

            if (work.getStudent() != null) {
                List<String> fullNameVariants = PDFTools.getVariants(work.getStudent().getName());
                List<String> searchVariants = new ArrayList<>();
                if (fullNameVariants.size() == 4) {
                    switch (discipline.getNameFormat()) {
                        case SURNAME_NAME -> searchVariants.add(fullNameVariants.getFirst());
                        case SURNAME_I -> searchVariants.add(fullNameVariants.get(1));
                        case SURNAME_IB -> searchVariants.add(fullNameVariants.get(2));
                        case SURNAME_NAME_PATRONYMIC -> searchVariants.add(fullNameVariants.get(3));
                        default -> searchVariants.addAll(fullNameVariants);
                    }
                }

                double minDist = Integer.MAX_VALUE;
                for (String fullName : searchVariants) {
                    StrDist.DistResInfo distInfo = StrDist.getBestMatchWordRow(fullName, firstPage, true);
                    double thisDist = distInfo.dist / Math.sqrt(fullName.length());
                    System.out.println(fullName + " -> " + thisDist);
                    if (thisDist < minDist) {
                        minDist = thisDist;
                        work.setIsCorrectStudent(castMatchLevel(distInfo.matchLevel));
                        work.setStudentDifference(distInfo.diffAsHtml);
                    }
                }
            }
            if (work.getSupervisor() != null) {
                List<String> fullNameVariants =
                        work.getType() == DisciplineType.QUALIFICATION_WORK ?
                                PDFTools.getPositionVariants(work.getRawSupervisorName()) :
                                PDFTools.getVariants(work.getSupervisor().getName());
                double minDist = Integer.MAX_VALUE;
                for (String fullName : fullNameVariants) {
                    StrDist.DistResInfo distInfo = StrDist.getBestMatchWordRow(fullName, firstPage, true);
                    double thisDist = distInfo.dist / Math.sqrt(fullName.length());
                    if (thisDist < minDist) {
                        System.out.println(fullName + " -> " + thisDist);
                        minDist = thisDist;
                        work.setIsCorrectSupervisor(castMatchLevel(distInfo.matchLevel));
                        work.setSupervisorDifference(distInfo.diffAsHtml);
                    }
                }
            }
            if (work.getTheme() != null) {
                String theme = work.getTheme();
                if (work.getType() == DisciplineType.COURSEWORK && (discipline.getName().contains("ООП") || discipline.getName().contains("БД")))
                    theme = "на тему «" + theme + "»";
                else // if (work.getType() == DisciplineType.QUALIFICATION_WORK)
                    theme = theme.toUpperCase(Locale.ROOT);
//                else
                StrDist.DistResInfo distInfo = StrDist.getBestMatchWordRow(theme, firstPage, true);
                work.setIsCorrectTheme(castMatchLevel(distInfo.matchLevel));
                work.setThemeDifference(distInfo.diffAsHtml);
            }
            if (work.getStudentGroup() != null){
                work.setGroupDifference(StrDist.getBestMatchWord("група " + work.getStudentGroup(), firstPage, true).diffAsHtml);
            }

            work.setMinistryDifference(StrDist.getBestMatchRow(department.getMinistry(), firstPage, true).diffAsHtml);

            work.setHEIDifference(StrDist.getBestMatchRow(department.getHEI(), firstPage, true).diffAsHtml);

            work.setDepartmentDifference(StrDist.getBestMatchRow(department.getName(), firstPage, true).diffAsHtml);

            work.setNameAtTitlePageDifference(StrDist.getBestMatchRow(discipline.getNameAtTitlePage(), firstPage, true).diffAsHtml);

            work.setCityYearDifference(StrDist.getBestMatchWord(department.getCityYear() + " – " + discipline.getYear(), firstPage, true).diffAsHtml);

            String filename = PDFTools.getFileName(discipline, work);
            System.out.println(filename + " -> " + filename.replace("{0}.pdf", "*додатків*.pdf"));
            work.setFileNameToCopy(filename.replace("{0}.pdf", "*додатків*.pdf"));

            if (work.getState() == WorkState.UPDATE) {
                String fullTextFileId = googleDriveService.updateFileContent(
                        accessToken,
                        work.getFullTextLink(),
                        originalFileContent
                );
                work.setFullTextLink("https://drive.google.com/file/d/" + fullTextFileId + "/view");

                byte[] trimmedPdfContent = PDFTools.trimAppendicesAndGetContent(originalFileContent);
                if (trimmedPdfContent != null && trimmedPdfContent.length > 0) {
                    String trimmedTextFileId = googleDriveService.updateFileContent(
                            accessToken,
                            work.getShortTextLink(),
                            trimmedPdfContent
                    );
                    work.setShortTextLink("https://drive.google.com/file/d/" + trimmedTextFileId + "/view");
                }
                notificationService.createWorkUpdatedNotification(work, discipline, department);

            } else if (work.getState() != WorkState.ONLY_DATA_UPDATE) {
                boolean appendicesFound = false;
                byte[] trimmedPdfContent = PDFTools.trimAppendicesAndGetContent(originalFileContent);
                if (trimmedPdfContent != null && trimmedPdfContent.length > 0) {
                    appendicesFound = true;
                    String trimmedTextFileId = googleDriveService.uploadFile(
                            accessToken,
                            (trimmedPdfContent.length == originalFileContent.length ?
                                    MessageFormat.format(filename, "(повнаНеМаєДодатків)") :
                                    MessageFormat.format(filename, "(безДодатків)")),
                            "application/pdf",
                            trimmedPdfContent,
                            disciplineFolderId
                    );
//                googleDriveService.addViewerPermissionsToMultipleUsers(
//                        accessToken,
//                        trimmedTextFileId,
//                        relatedUserEmails
//                );
                    work.setShortTextLink("https://drive.google.com/file/d/" + trimmedTextFileId + "/view");
                } else {
                    if (work.getType() != DisciplineType.COURSEWORK) {
                        System.err.println("Робота є не курсовою, а " + work.getType() + ", але не знайдено додатків. Це підозріло.");
                        // TODO: виразити також і через notification
                    }
                }

                String fullTextFileId = googleDriveService.copyFile(
                        accessToken,
                        work.getClassroomLink(),
                        MessageFormat.format(filename, appendicesFound ? "(повна)" : "(повнаНеМаєДодатків)"),
                        disciplineFolderId
                );

                List<String> relatedUserEmails = ManagerService.getRelatedUsers(department, work);

//                googleDriveService.addViewerPermissionsToMultipleUsers(
//                        accessToken,
//                        fullTextFileId,
//                        relatedUserEmails
//                );
                work.setFullTextLink("https://drive.google.com/file/d/" + fullTextFileId + "/view");

                notificationService.createWorkCreatedNotification(work, discipline, department);
            }
            work.setState(WorkState.DEFAULT);
            workRepository.save(work);
            notificationService.createCheckResultNotification(work, discipline);
        }
        discipline.setUpdating(false);
        discipline.setUpdateDate(LocalDateTime.now());
        disciplineRepository.save(discipline);
        notifier.notifyListeners(discipline.getId());
    }


}
