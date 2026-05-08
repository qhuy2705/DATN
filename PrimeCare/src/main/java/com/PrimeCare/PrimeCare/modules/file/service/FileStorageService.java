package com.PrimeCare.PrimeCare.modules.file.service;

import com.PrimeCare.PrimeCare.config.S3Properties;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.file.dto.response.FileUploadResponse;
import com.PrimeCare.PrimeCare.modules.file.entity.FileObject;
import com.PrimeCare.PrimeCare.modules.file.repository.FileObjectRepository;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import com.PrimeCare.PrimeCare.shared.enums.StorageProvider;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final FileObjectRepository fileObjectRepository;
    private final AuditLogService auditLogService;
    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ServiceResultRepository serviceResultRepository;

    @Value("${app.file.local-storage-root:${java.io.tmpdir}/primecare-storage}")
    private String localStorageRoot;

    private Path localRoot() {
        if (localStorageRoot == null || localStorageRoot.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Local storage root is not configured");
        }
        return Paths.get(localStorageRoot);
    }

    @Transactional
    public FileUploadResponse uploadAvatar(
            MultipartFile file,
            FileOwnerType ownerType,
            Long ownerId,
            User createdBy
    ) {
        validateNotEmpty(file);
        validateContentType(file, ALLOWED_AVATAR_TYPES);
        validateSize(file, 5L * 1024 * 1024);
        validateFileSignature(file, ALLOWED_AVATAR_TYPES);

        assertCanUploadFile(createdBy, ownerType, ownerId, true);

        String extension = extractExtension(file.getOriginalFilename());
        String key = buildKey("avatars", ownerType, ownerId, extension);

        return upload(file, ownerType, ownerId, createdBy, key);
    }

    @Transactional
    public FileUploadResponse uploadAttachment(
            MultipartFile file,
            FileOwnerType ownerType,
            Long ownerId,
            User createdBy
    ) {
        validateNotEmpty(file);
        validateContentType(file, ALLOWED_ATTACHMENT_TYPES);
        validateSize(file, 10L * 1024 * 1024);
        validateFileSignature(file, ALLOWED_ATTACHMENT_TYPES);

        assertCanUploadFile(createdBy, ownerType, ownerId, false);

        String extension = extractExtension(file.getOriginalFilename());
        String key = buildKey("attachments", ownerType, ownerId, extension);

        return upload(file, ownerType, ownerId, createdBy, key);
    }

    @Transactional
    public String uploadPdfBytes(byte[] content, FileOwnerType ownerType, Long ownerId, String fileName) {
        if (content == null || content.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Nội dung file không được để trống");
        }

        String extension = extractExtension(fileName);
        if (extension.isBlank()) {
            extension = ".pdf";
        }

        String key = buildKey("pdfs", ownerType, ownerId, extension);
        StorageProvider storageProvider = putObjectWithFallback(key, MediaType.APPLICATION_PDF_VALUE, content);
        fileObjectRepository.save(
                FileObject.builder()
                          .ownerType(ownerType)
                          .ownerId(ownerId)
                          .fileName(safeFileName(fileName == null || fileName.isBlank() ? "document.pdf" : fileName))
                          .mimeType(MediaType.APPLICATION_PDF_VALUE)
                          .fileSize((long) content.length)
                          .storageProvider(storageProvider)
                          .storagePath(key)
                          .createdBy(null)
                          .build()
        );
        return key;
    }

    @Transactional(readOnly = true)
    public void assertCanAccessFile(User requester, String key) {
        if (requester == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        String normalizedKey = normalizeAndValidateStorageKey(key);
        FileObject fileObject = fileObjectRepository.findTopByStoragePathOrderByIdDesc(normalizedKey)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy metadata của file"));

        if (hasElevatedInternalAccess(requester)) {
            return;
        }

        if (Boolean.TRUE.equals(isOwnerAccessibleToRequester(requester, fileObject))) {
            return;
        }

        throw new ApiException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập file này");
    }

    @Transactional(readOnly = true)
    public byte[] downloadAsBytes(String key) {
        return downloadAsBytes(key, true);
    }

    @Transactional(readOnly = true)
    public byte[] downloadAsBytes(String key, boolean requireKnownStorageObject) {
        String normalizedKey = normalizeAndValidateStorageKey(key);
        if (requireKnownStorageObject) {
            ensureKnownStorageObject(normalizedKey);
        }

        Path localPath = resolveLocalPath(normalizedKey);
        if (Files.exists(localPath)) {
            try {
                return Files.readAllBytes(localPath);
            } catch (IOException e) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể đọc file từ local storage");
            }
        }

        try {
            ResponseBytes<?> objectBytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                                    .bucket(s3Properties.getBucket())
                                    .key(normalizedKey)
                                    .build()
            );
            return objectBytes.asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy file trên storage");
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể tải file từ storage");
        }
    }

    @Transactional(readOnly = true)
    public String resolveFileName(String key) {
        return findByStoragePath(key)
                .map(FileObject::getFileName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(() -> deriveFileNameFromKey(key));
    }

    @Transactional(readOnly = true)
    public String resolveMimeType(String key) {
        Optional<String> mimeFromDb = findByStoragePath(key)
                .map(FileObject::getMimeType)
                .filter(value -> value != null && !value.isBlank());
        if (mimeFromDb.isPresent()) {
            return mimeFromDb.get();
        }

        String fileName = resolveFileName(key);
        try {
            Path localPath = resolveLocalPath(key);
            if (Files.exists(localPath)) {
                String detected = Files.probeContentType(localPath);
                if (detected != null && !detected.isBlank()) {
                    return detected;
                }
            }
        } catch (IOException ignored) {
            // fallback to filename based detection
        }

        String guessed = java.net.URLConnection.guessContentTypeFromName(fileName);
        return guessed != null && !guessed.isBlank() ? guessed : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    @Transactional(readOnly = true)
    public String buildPublicUrlByKey(String key) {
        String normalizedKey = normalizeAndValidateStorageKey(key);
        FileOwnerType ownerType = findByStoragePath(normalizedKey).map(FileObject::getOwnerType).orElse(null);
        return buildPublicUrl(normalizedKey, ownerType);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPublicMediaAsBytes(String key) {
        String normalizedKey = normalizeAndValidateStorageKey(key);
        if (!isPublicMediaKey(normalizedKey)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Không có quyền truy cập tệp công khai này");
        }
        return downloadAsBytes(normalizedKey, false);
    }

    private Optional<FileObject> findByStoragePath(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return fileObjectRepository.findTopByStoragePathOrderByIdDesc(normalizeAndValidateStorageKey(key));
    }

    private FileUploadResponse upload(
            MultipartFile file,
            FileOwnerType ownerType,
            Long ownerId,
            User createdBy,
            String key
    ) {
        try {
            StorageProvider storageProvider = putObjectWithFallback(key, file.getContentType(), file.getBytes());

            String url = buildPublicUrl(key, ownerType);

            FileObject saved = fileObjectRepository.save(
                    FileObject.builder()
                              .ownerType(ownerType)
                              .ownerId(ownerId)
                              .fileName(safeFileName(file.getOriginalFilename()))
                              .mimeType(file.getContentType())
                              .fileSize(file.getSize())
                              .storageProvider(storageProvider)
                              .storagePath(key)
                              .createdBy(createdBy)
                              .build()
            );

            auditLogService.log(createdBy, "UPLOAD", ownerType.name(), ownerId, null, snapshotFile(saved));

            return FileUploadResponse.builder()
                                     .fileId(saved.getId())
                                     .fileName(saved.getFileName())
                                     .mimeType(saved.getMimeType())
                                     .fileSize(saved.getFileSize())
                                     .url(url)
                                     .storagePath(key)
                                     .build();

        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không đọc được file upload");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Upload file that bai");
        }
    }

    private StorageProvider putObjectWithFallback(String key, String contentType, byte[] content) {
        try {
            putObjectToS3(key, contentType, content);
            return StorageProvider.S3;
        } catch (Exception ex) {
            try {
                log.warn(
                        "s3_upload_failed_falling_back_to_local_storage localRoot={} key={} cause={}: {}",
                        localRoot().toAbsolutePath().normalize(),
                        key,
                        ex.getClass().getSimpleName(),
                        ex.getMessage()
                );
                writeLocalFile(key, content);
                return StorageProvider.LOCAL;
            } catch (IOException ioException) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể lưu file lên storage");
            }
        }
    }

    private void putObjectToS3(String key, String contentType, byte[] content) {
        PutObjectRequest request = PutObjectRequest.builder()
                                                   .bucket(s3Properties.getBucket())
                                                   .key(key)
                                                   .contentType(contentType)
                                                   .contentLength((long) content.length)
                                                   .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    private void writeLocalFile(String key, byte[] content) throws IOException {
        Path target = resolveLocalPath(normalizeAndValidateStorageKey(key));
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private Path resolveLocalPath(String key) {
        try {
            Path root = localRoot().toAbsolutePath().normalize();
            Path resolved = root.resolve(normalizeAndValidateStorageKey(key)).normalize();
            if (!resolved.startsWith(root)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Đường dẫn file không hợp lệ");
            }
            return resolved;
        } catch (InvalidPathException ex) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Đường dẫn file không hợp lệ");
        }
    }

    private String normalizeAndValidateStorageKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Không có đường dẫn file để tải");
        }

        String normalized = key.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("..") || normalized.contains("//")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Đường dẫn file không hợp lệ");
        }
        return normalized;
    }

    private void ensureKnownStorageObject(String key) {
        if (findByStoragePath(key).isPresent()) {
            return;
        }
        if (isLegacyGeneratedPdfKey(key)) {
            return;
        }
        throw new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy file hợp lệ");
    }

    private boolean isLegacyGeneratedPdfKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = normalizeAndValidateStorageKey(key);
        return normalized.startsWith("pdfs/") && normalized.endsWith(".pdf");
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File không được để trống");
        }
    }

    private void validateContentType(MultipartFile file, Set<String> allowedTypes) {
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Định dạng file không hợp lệ");
        }
    }

    private void validateSize(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Kich thuoc file vuot gioi han");
        }
    }

    private void validateFileSignature(MultipartFile file, Set<String> allowedTypes) {
        try {
            byte[] bytes = file.getBytes();
            String contentType = file.getContentType();
            if (MediaType.APPLICATION_PDF_VALUE.equals(contentType) && !startsWith(bytes, 0x25, 0x50, 0x44, 0x46)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Nội dung file PDF không hợp lệ");
            }
            if (MediaType.IMAGE_PNG_VALUE.equals(contentType) && !startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Nội dung file PNG không hợp lệ");
            }
            if (MediaType.IMAGE_JPEG_VALUE.equals(contentType) && !startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Nội dung file JPEG không hợp lệ");
            }
            if ("image/webp".equals(contentType) && !(startsWith(bytes, 0x52, 0x49, 0x46, 0x46) && containsAscii(bytes, 8, "WEBP"))) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Nội dung file WEBP không hợp lệ");
            }
            if (contentType != null && !allowedTypes.contains(contentType)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Định dạng file không hợp lệ");
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể đọc file upload");
        }
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes == null || bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAscii(byte[] bytes, int offset, String expected) {
        if (bytes == null || expected == null || bytes.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private void assertCanUploadFile(User requester, FileOwnerType ownerType, Long ownerId, boolean avatarUpload) {
        if (requester == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        if (ownerType == null || ownerId == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Thiếu thông tin chủ sở hữu file");
        }
        if (hasElevatedInternalAccess(requester)) {
            return;
        }
        if (avatarUpload) {
            if (ownerType == FileOwnerType.USER && ownerId.equals(requester.getId())) {
                return;
            }
            if (ownerType == FileOwnerType.DOCTOR && requester.getDoctorProfile() != null && ownerId.equals(requester.getDoctorProfile().getId())) {
                return;
            }
            if (ownerType == FileOwnerType.PATIENT && requester.getPatient() != null && ownerId.equals(requester.getPatient().getId())) {
                return;
            }
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Bạn chỉ được cập nhật avatar của chính mình");
        }

        FileObject probe = FileObject.builder().ownerType(ownerType).ownerId(ownerId).build();
        if (Boolean.TRUE.equals(isOwnerAccessibleToRequester(requester, probe))) {
            return;
        }
        throw new ApiException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền đính kèm file cho đối tượng này");
    }

    private boolean hasElevatedInternalAccess(User requester) {
        return switch (requester.getRole()) {
            case SYSTEM_ADMIN, OPERATIONS_ADMIN, STAFF -> true;
            default -> false;
        };
    }

    private Boolean isOwnerAccessibleToRequester(User requester, FileObject fileObject) {
        Long ownerId = fileObject.getOwnerId();
        if (ownerId == null) {
            return false;
        }

        return switch (fileObject.getOwnerType()) {
            case USER -> ownerId.equals(requester.getId());
            case DOCTOR -> requester.getDoctorProfile() != null && ownerId.equals(requester.getDoctorProfile().getId());
            case PATIENT -> requester.getPatient() != null && ownerId.equals(requester.getPatient().getId());
            case APPOINTMENT -> canAccessAppointment(requester, ownerId);
            case INVOICE -> canAccessInvoice(requester, ownerId);
            case PRESCRIPTION -> canAccessPrescription(requester, ownerId);
            case SERVICE_RESULT -> canAccessServiceResult(requester, ownerId);
            case ARTICLE, SERVICE -> false;
        };
    }

    private boolean canAccessAppointment(User requester, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));

        if (requester.getPatient() != null && appointment.getPatient() != null
                && appointmentId != null && requester.getPatient().getId().equals(appointment.getPatient().getId())) {
            return true;
        }

        if (requester.getDoctorProfile() != null && appointment.getDoctor() != null
                && requester.getDoctorProfile().getId().equals(appointment.getDoctor().getId())) {
            return true;
        }

        return requester.getRole() == com.PrimeCare.PrimeCare.shared.enums.UserRole.SERVICE_TECHNICIAN
                || requester.getRole() == com.PrimeCare.PrimeCare.shared.enums.UserRole.CASHIER
                || requester.getRole() == com.PrimeCare.PrimeCare.shared.enums.UserRole.PHARMACIST;
    }

    private boolean canAccessInvoice(User requester, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        if (requester.getPatient() != null && invoice.getServiceOrder() != null
                && invoice.getServiceOrder().getEncounter() != null
                && invoice.getServiceOrder().getEncounter().getPatient() != null
                && requester.getPatient().getId().equals(invoice.getServiceOrder().getEncounter().getPatient().getId())) {
            return true;
        }

        return requester.getRole() == com.PrimeCare.PrimeCare.shared.enums.UserRole.CASHIER;
    }

    private boolean canAccessPrescription(User requester, Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRESCRIPTION_NOT_FOUND));

        if (requester.getPatient() != null && prescription.getEncounter() != null && prescription.getEncounter().getPatient() != null
                && requester.getPatient().getId().equals(prescription.getEncounter().getPatient().getId())) {
            return true;
        }

        if (requester.getDoctorProfile() != null && prescription.getEncounter() != null && prescription.getEncounter().getDoctor() != null
                && requester.getDoctorProfile().getId().equals(prescription.getEncounter().getDoctor().getId())) {
            return true;
        }

        return requester.getRole() == com.PrimeCare.PrimeCare.shared.enums.UserRole.PHARMACIST;
    }

    private boolean canAccessServiceResult(User requester, Long serviceResultId) {
        ServiceResult serviceResult = serviceResultRepository.findById(serviceResultId)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_RESULT_NOT_FOUND));

        Encounter encounter = serviceResult.getServiceOrderItem() != null
                && serviceResult.getServiceOrderItem().getServiceOrder() != null
                ? serviceResult.getServiceOrderItem().getServiceOrder().getEncounter()
                : null;

        if (encounter == null) {
            return false;
        }

        if (requester.getPatient() != null && encounter.getPatient() != null
                && requester.getPatient().getId().equals(encounter.getPatient().getId())) {
            return true;
        }

        if (requester.getDoctorProfile() != null && encounter.getDoctor() != null
                && requester.getDoctorProfile().getId().equals(encounter.getDoctor().getId())) {
            return true;
        }

        return requester.getRole() == com.PrimeCare.PrimeCare.shared.enums.UserRole.SERVICE_TECHNICIAN;
    }

    private String buildKey(String folder, FileOwnerType ownerType, Long ownerId, String extension) {
        return folder + "/"
                + ownerType.name().toLowerCase() + "/"
                + ownerId + "/"
                + LocalDate.now() + "/"
                + UUID.randomUUID() + extension;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        return originalFilename.strip();
    }

    private String deriveFileNameFromKey(String key) {
        if (key == null || key.isBlank()) {
            return "download";
        }
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < key.length() - 1 ? key.substring(lastSlash + 1) : key;
    }

    private String buildPublicUrl(String key, FileOwnerType ownerType) {
        Path localPath = resolveLocalPath(key);
        if (Files.exists(localPath)) {
            String basePath = isPublicMediaOwnerType(ownerType) ? "/api/files/public/content" : "/api/files/content";
            return basePath + "?path=" + URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
        }
        if (s3Properties.getPublicBaseUrl() != null && !s3Properties.getPublicBaseUrl().isBlank()) {
            return s3Properties.getPublicBaseUrl() + "/" + key;
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(
                s3Properties.getBucket(),
                s3Properties.getRegion(),
                URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20")
        );
    }

    private boolean isPublicMediaKey(String key) {
        return findByStoragePath(key)
                .map(FileObject::getOwnerType)
                .map(this::isPublicMediaOwnerType)
                .orElse(false);
    }

    private boolean isPublicMediaOwnerType(FileOwnerType ownerType) {
        return ownerType == FileOwnerType.DOCTOR || ownerType == FileOwnerType.SERVICE || ownerType == FileOwnerType.ARTICLE;
    }

    private Map<String, Object> snapshotFile(FileObject file) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", file.getId());
        data.put("ownerType", file.getOwnerType() != null ? file.getOwnerType().name() : null);
        data.put("ownerId", file.getOwnerId());
        data.put("fileName", file.getFileName());
        data.put("mimeType", file.getMimeType());
        data.put("fileSize", file.getFileSize());
        data.put("storageProvider", file.getStorageProvider() != null ? file.getStorageProvider().name() : null);
        data.put("storagePath", file.getStoragePath());
        return data;
    }
}
