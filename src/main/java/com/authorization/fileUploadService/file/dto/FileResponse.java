package com.authorization.fileUploadService.file.dto;

import com.authorization.fileUploadService.file.FileMetadata;
import com.authorization.fileUploadService.file.UploadStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FileResponse {
    private UUID id;
    private String originalFilename;
    private String contentType;
    private Long fileSizeBytes;
    private UploadStatus uploadStatus;
    private LocalDateTime createdAt;

    // No user object, no s3Key, no password — only what client needs
    public static FileResponse from(FileMetadata file) {
        return FileResponse.builder()
                .id(file.getId())
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSizeBytes(file.getFileSizeBytes())
                .uploadStatus(file.getUploadStatus())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
