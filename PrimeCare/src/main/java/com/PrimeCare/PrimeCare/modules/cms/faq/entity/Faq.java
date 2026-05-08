package com.PrimeCare.PrimeCare.modules.cms.faq.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.FaqStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name="faqs",
        indexes = @Index(name="idx_faq_cat", columnList="category,status")
)
public class Faq extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="category", length=100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length=16)
    private FaqStatus status;

    @Column(name="question_vn", nullable=false, length=500)
    private String questionVn;

    @Column(name="question_en", length=500)
    private String questionEn;

    @Lob
    @Column(name="answer_vn", nullable=false, columnDefinition = "longtext")
    private String answerVn;

    @Lob
    @Column(name="answer_en", columnDefinition = "longtext")
    private String answerEn;

    @PrePersist
    void prePersist() {
        if (status == null) status = FaqStatus.PUBLISHED;
    }
}
