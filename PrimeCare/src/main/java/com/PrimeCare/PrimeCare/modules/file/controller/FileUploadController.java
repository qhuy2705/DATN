package com.PrimeCare.PrimeCare.modules.file.controller;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.file.dto.response.FileUploadResponse;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF','SERVICE_TECHNICIAN','DOCTOR','PATIENT','PHARMACIST')")
    @PostMapping(value = "/avatars", consumes = "multipart/form-data")
    public ApiResponse<FileUploadResponse> uploadAvatar(
            @RequestPart("file") MultipartFile file,
            @RequestParam FileOwnerType ownerType,
            @RequestParam Long ownerId,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Upload avatar thành công",
                fileStorageService.uploadAvatar(file, ownerType, ownerId, resolveCurrentUser(authentication))
        );
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF','SERVICE_TECHNICIAN','DOCTOR','PHARMACIST')")
    @PostMapping(value = "/attachments", consumes = "multipart/form-data")
    public ApiResponse<FileUploadResponse> uploadAttachment(
            @RequestPart("file") MultipartFile file,
            @RequestParam FileOwnerType ownerType,
            @RequestParam Long ownerId,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Upload file thành công",
                fileStorageService.uploadAttachment(file, ownerType, ownerId, resolveCurrentUser(authentication))
        );
    }

    @GetMapping("/public/content")
    public ResponseEntity<byte[]> getPublicFileContent(
            @RequestParam("path") String path,
            @RequestParam(name = "download", defaultValue = "false") boolean download
    ) {
        byte[] content = fileStorageService.downloadPublicMediaAsBytes(path);
        String fileName = fileStorageService.resolveFileName(path);
        String mimeType = fileStorageService.resolveMimeType(path);

        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content);
    }

    @GetMapping("/content")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF','SERVICE_TECHNICIAN','DOCTOR','CASHIER','PATIENT','PHARMACIST')")
    public ResponseEntity<byte[]> getFileContent(
            @RequestParam("path") String path,
            @RequestParam(name = "download", defaultValue = "false") boolean download,
            Authentication authentication
    ) {
        User currentUser = resolveCurrentUser(authentication);
        fileStorageService.assertCanAccessFile(currentUser, path);
        byte[] content = fileStorageService.downloadAsBytes(path);
        String fileName = fileStorageService.resolveFileName(path);
        String mimeType = fileStorageService.resolveMimeType(path);

        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content);
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = Long.valueOf(authentication.getName());
        return userRepository.findById(userId)
                             .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
