package com.PrimeCare.PrimeCare.modules.service_result.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.PdfGenerationStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "service_results")
public class ServiceResult extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_item_id", nullable = false, unique = true)
    private ServiceOrderItem serviceOrderItem;
    @Column(name = "result_text_vn", columnDefinition = "TEXT")
    private String resultTextVn;
    @Column(name = "result_text_en", columnDefinition = "TEXT")
    private String resultTextEn;
    @Column(name = "result_data_json", columnDefinition = "TEXT")
    private String resultDataJson;
    @Column(name = "attachment_url", length = 1000)
    private String attachmentUrl;
    @Column(name = "attachment_mime_type", length = 128)
    private String attachmentMimeType;
    @Enumerated(EnumType.STRING)
    @Column(name = "template_code", length = 32)
    private ServiceResultTemplateCode templateCode;
    @Column(name = "template_schema_json", columnDefinition = "TEXT")
    private String templateSchemaJson;
    @Column(name = "field_values_json", columnDefinition = "TEXT")
    private String fieldValuesJson;
    @Column(name = "conclusion_text", columnDefinition = "TEXT")
    private String conclusionText;
    @Column(name = "impression_text", columnDefinition = "TEXT")
    private String impressionText;
    @Column(name = "attachment_urls_json", columnDefinition = "TEXT")
    private String attachmentUrlsJson;
    @Column(name = "report_title", length = 255)
    private String reportTitle;
    @Column(name = "report_pdf_path", length = 1000)
    private String reportPdfPath;
    @Enumerated(EnumType.STRING)
    @Column(name = "report_pdf_status", length = 16)
    private PdfGenerationStatus reportPdfStatus;
    @Column(name = "report_pdf_error_message", columnDefinition = "TEXT")
    private String reportPdfErrorMessage;
    @Column(name = "report_pdf_generated_at")
    private LocalDateTime reportPdfGeneratedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "performed_by_user_id")
    private User performedByUser;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "verified_by_user_id")
    private User verifiedByUser;
    @Column(name = "performed_at")
    private LocalDateTime performedAt;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    private ServiceResultStatus status;
    @PrePersist
    void prePersist() {
        if (status == null) status = ServiceResultStatus.DRAFT;
        if (reportPdfStatus == null) reportPdfStatus = PdfGenerationStatus.PENDING;
    }
}
