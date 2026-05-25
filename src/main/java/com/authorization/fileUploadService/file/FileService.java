package com.authorization.fileUploadService.file;

import com.authorization.fileUploadService.file.dto.UploadUrlRequest;
import com.authorization.fileUploadService.file.dto.UploadUrlResponse;
import com.authorization.fileUploadService.user.User;
import com.authorization.fileUploadService.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(60);

    public UploadUrlResponse generateUploadUrl(String userEmail,
                                               UploadUrlRequest request) {
        // Validate file size
        if (request.getFileSizeBytes() != null
                && request.getFileSizeBytes() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB limit");
        }

        // Load user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Build a unique S3 key — userId/uuid-filename
        String s3Key = user.getId() + "/"
                + UUID.randomUUID() + "-"
                + request.getFilename();

        // Generate pre-signed upload URL
        String uploadUrl = s3Service.generateUploadUrl(
                s3Key, request.getContentType(), UPLOAD_URL_EXPIRY);

        // Save PENDING metadata to DB
        FileMetadata fileMetadata = FileMetadata.builder()
                .user(user)
                .originalFilename(request.getFilename())
                .s3Key(s3Key)
                .contentType(request.getContentType())
                .fileSizeBytes(request.getFileSizeBytes())
//                .uploadStatus(UploadStatus.PENDING)
                .build();

        FileMetadata saved = fileRepository.save(fileMetadata);

        log.info("Upload URL generated user={} fileId={} key={}",
                userEmail, saved.getId(), s3Key);

        return UploadUrlResponse.builder()
                .fileId(saved.getId().toString())
                .uploadUrl(uploadUrl)
                .expiresIn("15 minutes")
                .build();
    }

    public void confirmUpload(String userEmail, UUID fileId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FileMetadata file = fileRepository
                .findByIdAndUserId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        // Verify file actually exists in S3
        if (!s3Service.objectExists(file.getS3Key())) {
            file.setUploadStatus(UploadStatus.FAILED);
            fileRepository.save(file);
            throw new RuntimeException("File not found in S3 — upload may have failed");
        }

        // Get actual size from S3
        Long actualSize = s3Service.getObjectSize(file.getS3Key());

        file.setUploadStatus(UploadStatus.COMPLETED);
        file.setFileSizeBytes(actualSize);
        fileRepository.save(file);

        log.info("Upload confirmed user={} fileId={} size={}bytes",
                userEmail, fileId, actualSize);
    }

    public String getDownloadUrl(String userEmail, UUID fileId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FileMetadata file = fileRepository
                .findByIdAndUserId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (file.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new RuntimeException("File upload not completed");
        }

        return s3Service.generateDownloadUrl(file.getS3Key(), DOWNLOAD_URL_EXPIRY);
    }

    public void deleteFile(String userEmail, UUID fileId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FileMetadata file = fileRepository
                .findByIdAndUserId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        s3Service.deleteObject(file.getS3Key());
        fileRepository.delete(file);

        log.info("File deleted user={} fileId={}", userEmail, fileId);
    }
}
