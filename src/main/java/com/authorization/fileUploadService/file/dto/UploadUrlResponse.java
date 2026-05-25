package com.authorization.fileUploadService.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UploadUrlResponse {
    private String fileId;
    private String uploadUrl;
    private String expiresIn;
}
