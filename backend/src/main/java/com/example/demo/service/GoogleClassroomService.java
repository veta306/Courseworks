package com.example.demo.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.classroom.Classroom;
import com.google.api.services.classroom.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleClassroomService {

    private static final String APPLICATION_NAME = "coursework-management";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    private void applyRequestTimeouts(HttpRequest request) {
        request.setConnectTimeout(CONNECT_TIMEOUT_MS);
        request.setReadTimeout(READ_TIMEOUT_MS);
    }

    private Classroom getClassroomService(String accessToken) throws GeneralSecurityException, IOException {
        return new Classroom.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                request -> {
                    request.getHeaders().setAuthorization("Bearer " + accessToken);
                    applyRequestTimeouts(request);
                })
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public List<Course> getCourses(String accessToken) throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        ListCoursesResponse response = classroomService.courses().list().execute();
        return response.getCourses();
    }

    public Course getCourse(String accessToken, String courseId) throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        return classroomService.courses().get(courseId).execute();
    }

    public List<CourseWork> getCourseWorks(String accessToken, String courseLink)
            throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        return classroomService.courses().courseWork().list(getCourseId(accessToken, courseLink)).execute().getCourseWork();
    }

    public List<CourseWorkMaterial> getCourseMaterials(String accessToken, String courseLink)
            throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        List<CourseWorkMaterial> courseWorkMaterials = classroomService.courses().courseWorkMaterials().list(getCourseId(accessToken, courseLink)).execute().getCourseWorkMaterial();
        return courseWorkMaterials.stream().filter(material -> material.getMaterials() != null &&
                material.getMaterials().getFirst().getDriveFile() != null &&
                material.getMaterials().getFirst().getDriveFile().getDriveFile() != null &&
                material.getMaterials().getFirst().getDriveFile().getDriveFile().getAlternateLink() != null &&
                material.getMaterials().getFirst().getDriveFile().getDriveFile().getAlternateLink().contains("spreadsheets"))
                .toList();
    }

    public String getCourseId(String accessToken, String courseLink) throws GeneralSecurityException, IOException {
        List<Course> courses = getCourses(accessToken);
        return courses.stream().filter(course -> course.getCourseState().equals("ACTIVE"))
                .filter(course -> course.getAlternateLink().equals(courseLink))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Course not found")).getId();
    }

    public String getCourseWorkId(String accessToken, String courseId, String courseWorkLink)
            throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        List<CourseWork> courseWorks = classroomService.courses().courseWork().list(courseId).execute().getCourseWork();
        return courseWorks.stream()
                .filter(courseWork -> courseWork.getAlternateLink().equals(courseWorkLink))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Course work not found")).getId();
    }

    public CourseWork getCourseWork(String accessToken, String courseId, String cwId)
            throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        return classroomService.courses().courseWork().get(courseId, cwId).execute();
    }

    public List<Student> getStudents(String accessToken, String courseId) throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        List<Student> allStudents = new ArrayList<>();
        String pageToken = null;

        do {
            Classroom.Courses.Students.List request = classroomService.courses().students().list(courseId)
                    .setPageSize(100);
            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            ListStudentsResponse response = request.execute();
            if (response.getStudents() != null) {
                allStudents.addAll(response.getStudents());
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        return allStudents;
    }

    public List<Teacher> getTeachers(String accessToken, String courseId) throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        List<Teacher> allTeachers = new ArrayList<>();
        String pageToken = null;

        do {
            Classroom.Courses.Teachers.List request = classroomService.courses().teachers().list(courseId)
                    .setPageSize(100);
            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            ListTeachersResponse response = request.execute();
            if (response.getTeachers() != null) {
                allTeachers.addAll(response.getTeachers());
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        return allTeachers;
    }

    public List<StudentSubmission> getSubmissions(String accessToken, String courseId, String cwId) throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
//        return classroomService.courses().courseWork().studentSubmissions().list(courseId, cwId).setStates(List.of("TURNED_IN", "RETURNED")).execute().getStudentSubmissions();
        return classroomService.courses().courseWork().studentSubmissions().list(courseId, cwId)
                .setStates(List.of("TURNED_IN", "RETURNED")).execute().getStudentSubmissions();
    }

    public StudentSubmission getSubmission(String accessToken, String courseId, String cwId, String submissionId)
            throws GeneralSecurityException, IOException {
        Classroom classroomService = getClassroomService(accessToken);
        return classroomService.courses().courseWork().studentSubmissions().get(courseId, cwId, submissionId).execute();
        // TODO: Fix fails provoked by backend logout when frontend is still logged in
    }
}
