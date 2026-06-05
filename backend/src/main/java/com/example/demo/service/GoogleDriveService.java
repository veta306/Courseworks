package com.example.demo.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleDriveService {

    private static final String APPLICATION_NAME = "coursework-management";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    private void applyRequestTimeouts(HttpRequest request) {
        request.setConnectTimeout(CONNECT_TIMEOUT_MS);
        request.setReadTimeout(READ_TIMEOUT_MS);
    }

    public Drive getGoogleDriveService(String accessToken) throws GeneralSecurityException, IOException {
        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                request -> {
                    request.getHeaders().setAuthorization("Bearer " + accessToken);
                    applyRequestTimeouts(request);
                })
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public InputStream getFileContent(String accessToken, String link) throws GeneralSecurityException, IOException {
        Drive driveService = getGoogleDriveService(accessToken);
        return driveService.files().get(extractFileIdFromLink(link)).executeMediaAsInputStream();
    }

    public static String extractFileIdFromLink(String url) {
        String[] parts = url.split("/d/")[1].split("/");
        return parts[0];
    }

    public static String extractFolderIdFromLink(String url) {
        String[] parts = url.split("/folders/")[1].split("/");
        return parts[0];
    }

    public String createFolderIfNotExists(String accessToken, String folderName, String parentId) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);

        String query = "mimeType='application/vnd.google-apps.folder' and name='" + folderName + "' and trashed=false";
        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }

        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.getFirst().getId();
        }

        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        if (parentId != null) {
            fileMetadata.setParents(Collections.singletonList(parentId));
        }

        File folder = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();

        return folder.getId();
    }

    public void deleteFile(String accessToken, String id) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);
        File fileMetadata = new File();
        fileMetadata.setTrashed(true);
        driveService.files().update(id, fileMetadata).execute();
    }


    public String renameFile(String accessToken, String id, String newName) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);

        File fileMetadata = new File();
        fileMetadata.setName(newName);

        File updatedFile = driveService.files().update(id, fileMetadata)
                .setFields("id")
                .execute();

        return updatedFile.getId();
    }

    public String copyFile(String accessToken, String fileLink, String newName, String parentId) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);

        File copiedFile = new File();
        copiedFile.setName(newName);
        copiedFile.setParents(Collections.singletonList(parentId));

        File result = driveService.files().copy(extractFileIdFromLink(fileLink), copiedFile)
                .setFields("id")
                .execute();

        return result.getId();
    }


    public String uploadFile(String accessToken, String fileName, String mimeType, byte[] content, String parentId) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(parentId));

        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
        InputStreamContent mediaContent = new InputStreamContent(mimeType, inputStream);

        File file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        return file.getId();
    }

    public String updateFileContent(String accessToken, String link, byte[] newContent) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);
        ByteArrayContent mediaContent = new ByteArrayContent("application/pdf", newContent);

        File file = driveService.files().update(extractFileIdFromLink(link), null, mediaContent)
                .setFields("id")
                .execute();

        return file.getId();
    }


    public void addViewerPermissions(String accessToken, String fileId, String email) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);

        Permission permission = new Permission()
                .setType("user")
                .setRole("reader")
                .setEmailAddress(email);

        driveService.permissions().create(fileId, permission)
                .setFields("id")
                .execute();
    }

    public void addViewerPermissionsToMultipleUsers(String accessToken, String fileId, List<String> emails) throws GeneralSecurityException, IOException {
        if (fileId == null || emails == null || emails.isEmpty()) {
            System.out.println("File ID is null or email list is empty. Skipping permission assignment.");
            return;
        }
        for (String email : emails) {
            if (email != null && !email.trim().isEmpty()) {
                System.out.println("Going to grant permission for file " + fileId + " to user " + email);
                addViewerPermissions(accessToken, fileId, email.trim());
            }
        }
    }

    public void makeFilePublic(String accessToken, String fileId) throws IOException, GeneralSecurityException {
        Drive driveService = getGoogleDriveService(accessToken);

        Permission permission = new Permission()
                .setType("anyone")
                .setRole("reader");

        driveService.permissions().create(fileId, permission)
                .setFields("id")
                .execute();
    }

    public File getFileMetadata(String accessToken, String fileId) throws GeneralSecurityException, IOException {
        Drive driveService = getGoogleDriveService(accessToken);
        return driveService.files().get(fileId).setFields("name,size").execute();
    }
}
