package com.PrimeCare.PrimeCare.modules.publiccontact.service;

import com.PrimeCare.PrimeCare.modules.publiccontact.dto.request.PublicContactRequest;
import com.PrimeCare.PrimeCare.modules.publiccontact.dto.request.UpdatePublicContactStatusRequest;
import com.PrimeCare.PrimeCare.modules.publiccontact.dto.response.AdminPublicContactSubmissionResponse;
import com.PrimeCare.PrimeCare.modules.publiccontact.dto.response.PublicContactResponse;
import com.PrimeCare.PrimeCare.modules.publiccontact.entity.PublicContactSubmission;
import com.PrimeCare.PrimeCare.modules.publiccontact.repository.PublicContactSubmissionRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.email.MailSenderIdentity;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PublicContactService {

    private static final int MAX_ADMIN_PAGE_SIZE = 50;
    private static final Set<String> ADMIN_STATUSES = Set.of(
            "RECEIVED",
            "EMAILED",
            "EMAIL_FAILED",
            "IN_PROGRESS",
            "RESOLVED",
            "ARCHIVED"
    );

    private final PublicContactSubmissionRepository repository;
    private final JavaMailSender mailSender;
    private final MailSenderIdentity mailSenderIdentity;

    @Value("${app.support.contact-email:}")
    private String configuredSupportContactEmail;

    @Transactional
    public PublicContactResponse submit(PublicContactRequest req, HttpServletRequest request) {
        PublicContactSubmission submission = PublicContactSubmission.builder()
                .referenceCode(generateReferenceCode())
                .fullName(normalize(req.getFullName()))
                .email(normalize(req.getEmail()))
                .phone(normalize(req.getPhone()))
                .message(normalize(req.getMessage()))
                .sourcePage(normalize(req.getSourcePage()))
                .requesterIp(resolveClientIp(request))
                .userAgent(abbreviate(request != null ? request.getHeader("User-Agent") : null, 500))
                .status("RECEIVED")
                .build();

        submission = repository.save(submission);
        trySendSupportEmail(submission);
        return PublicContactResponse.builder()
                .id(submission.getId())
                .referenceCode(submission.getReferenceCode())
                .status(submission.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPublicContactSubmissionResponse> listSubmissions(String status, Pageable pageable) {
        String normalizedStatus = normalizeStatusFilter(status);
        Pageable boundedPageable = boundAdminPageable(pageable);
        Page<PublicContactSubmission> page = normalizedStatus == null
                ? repository.findAll(boundedPageable)
                : repository.findByStatus(normalizedStatus, boundedPageable);

        return PageResponse.<AdminPublicContactSubmissionResponse>builder()
                .items(page.getContent().stream().map(this::toAdminResponse).toList())
                .meta(PageResponse.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalItems(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .sort(boundedPageable.getSort().toString())
                        .build())
                .build();
    }

    @Transactional
    public AdminPublicContactSubmissionResponse updateStatus(Long id, UpdatePublicContactStatusRequest request) {
        PublicContactSubmission submission = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PUBLIC_CONTACT_SUBMISSION_NOT_FOUND));
        submission.setStatus(normalizeStatusValue(request == null ? null : request.getStatus()));
        return toAdminResponse(repository.save(submission));
    }

    private void trySendSupportEmail(PublicContactSubmission submission) {
        String supportContactEmail = resolveSupportContactEmail();
        if (supportContactEmail == null || supportContactEmail.isBlank()) {
            submission.setStatus("RECEIVED");
            submission.setEmailError("Support email is not configured");
            repository.save(submission);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailSenderIdentity.internetAddress());
            helper.setTo(supportContactEmail);
            if (submission.getEmail() != null && !submission.getEmail().isBlank()) {
                helper.setReplyTo(submission.getEmail());
            }
            helper.setSubject("[PrimeCare Contact] " + submission.getReferenceCode() + " - " + submission.getFullName());
            helper.setText(buildEmailBody(submission), false);
            mailSender.send(message);

            submission.setStatus("EMAILED");
            submission.setEmailedAt(LocalDateTime.now());
            submission.setEmailError(null);
        } catch (Exception ex) {
            submission.setStatus("EMAIL_FAILED");
            submission.setEmailError(abbreviate(ex.getMessage(), 500));
        }
        repository.save(submission);
    }

    private String buildEmailBody(PublicContactSubmission submission) {
        return String.join("\n",
                "PrimeCare public contact submission",
                "Reference: " + safe(submission.getReferenceCode()),
                "Submitted at: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(submission.getCreatedAt()),
                "Full name: " + safe(submission.getFullName()),
                "Phone: " + safe(submission.getPhone()),
                "Email: " + safe(submission.getEmail()),
                "Source page: " + safe(submission.getSourcePage()),
                "Requester IP: " + safe(submission.getRequesterIp()),
                "User-Agent: " + safe(submission.getUserAgent()),
                "",
                "Message:",
                safe(submission.getMessage())
        );
    }

    private AdminPublicContactSubmissionResponse toAdminResponse(PublicContactSubmission submission) {
        return AdminPublicContactSubmissionResponse.builder()
                .id(submission.getId())
                .referenceCode(submission.getReferenceCode())
                .fullName(submission.getFullName())
                .email(submission.getEmail())
                .phone(submission.getPhone())
                .message(submission.getMessage())
                .sourcePage(submission.getSourcePage())
                .requesterIp(submission.getRequesterIp())
                .userAgent(submission.getUserAgent())
                .status(submission.getStatus())
                .emailedAt(submission.getEmailedAt())
                .emailError(submission.getEmailError())
                .createdAt(submission.getCreatedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeStatusValue(status);
    }

    private String normalizeStatusValue(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || normalized.isBlank() || !ADMIN_STATUSES.contains(normalized)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Trạng thái yêu cầu liên hệ không hợp lệ");
        }
        return normalized;
    }

    private Pageable boundAdminPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_ADMIN_PAGE_SIZE));
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private String resolveSupportContactEmail() {
        if (configuredSupportContactEmail != null && !configuredSupportContactEmail.isBlank()) {
            return configuredSupportContactEmail;
        }
        try {
            return mailSenderIdentity.resolveFromAddress();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private String generateReferenceCode() {
        return "CT-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).format(LocalDateTime.now());
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        if (maxLength <= 3) return value.substring(0, maxLength);
        return value.substring(0, maxLength - 3) + "...";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
