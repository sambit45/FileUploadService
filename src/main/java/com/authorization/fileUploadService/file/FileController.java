package com.authorization.fileUploadService.file;

import com.authorization.fileUploadService.file.dto.FileResponse;
import com.authorization.fileUploadService.file.dto.UploadUrlRequest;
import com.authorization.fileUploadService.file.dto.UploadUrlResponse;
import com.authorization.fileUploadService.user.User;
import com.authorization.fileUploadService.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> generateUploadUrl(
            Authentication authentication,
            @Valid @RequestBody UploadUrlRequest request) {

        String userEmail = authentication.getName();
        return ResponseEntity.ok(
                fileService.generateUploadUrl(userEmail, request));
    }

    @PatchMapping("/{fileId}/confirm")
    public ResponseEntity<String> confirmUpload(
            Authentication authentication,
            @PathVariable UUID fileId) {

        fileService.confirmUpload(authentication.getName(), fileId);
        return ResponseEntity.ok("Upload confirmed successfully");
    }

    @GetMapping
    public ResponseEntity<List<FileResponse>> listFiles(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<FileResponse> files = fileRepository
                .findByUserIdAndUploadStatus(user.getId(), UploadStatus.COMPLETED)
                .stream()
                .map(FileResponse::from)
                .toList();
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<String> getDownloadUrl(
            Authentication authentication,
            @PathVariable UUID fileId) {

        String url = fileService.getDownloadUrl(authentication.getName(), fileId);
        return ResponseEntity.ok(url);
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            Authentication authentication,
            @PathVariable UUID fileId) {

        fileService.deleteFile(authentication.getName(), fileId);
        return ResponseEntity.noContent().build();
    }

}
