package com.PrimeCare.PrimeCare.modules.file.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {
    private Long fileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String url;
    private String storagePath;
}