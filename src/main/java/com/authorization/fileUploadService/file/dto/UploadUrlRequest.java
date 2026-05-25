package com.authorization.fileUploadService.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UploadUrlRequest {

    @NotBlank(message = "Filename is required")
    private String filename;

    @Pattern(
            regexp = "image/jpeg|image/png|image/gif|application/pdf",
            message = "Only JPEG, PNG, GIF and PDF files are allowed"
    )
    private String contentType;

    private Long fileSizeBytes;
}
