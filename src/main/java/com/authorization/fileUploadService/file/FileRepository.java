package com.authorization.fileUploadService.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileMetadata, UUID> {

    List<FileMetadata> findByUserIdAndUploadStatus(UUID userId, UploadStatus status);
    Optional<FileMetadata> findByIdAndUserId(UUID id, UUID userId);
}
