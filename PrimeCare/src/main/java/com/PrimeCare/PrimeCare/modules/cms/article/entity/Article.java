package com.PrimeCare.PrimeCare.modules.cms.article.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.ArticleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name="articles",
        uniqueConstraints = {
                @UniqueConstraint(name="uq_article_slug_vn", columnNames = {"slug_vn"}),
                @UniqueConstraint(name="uq_article_slug_en", columnNames = {"slug_en"})
        },
        indexes = @Index(name="idx_art_pub", columnList="status,published_at")
)
public class Article extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // i18n fields
    @Column(name="title_vn", nullable=false, length=255)
    private String titleVn;

    @Column(name="title_en", length=255)
    private String titleEn;

    @Column(name="slug_vn", nullable=false, length=255)
    private String slugVn;

    @Column(name="slug_en", length=255)
    private String slugEn;

    @Lob
    @Column(name="summary_vn", columnDefinition = "longtext")
    private String summaryVn;

    @Lob
    @Column(name="summary_en", columnDefinition = "longtext")
    private String summaryEn;

    @Lob
    @Column(name="content_vn", nullable=false, columnDefinition = "longtext")
    private String contentVn;

    @Lob
    @Column(name="content_en", columnDefinition = "longtext")
    private String contentEn;

    // canonical
    @Column(name="thumbnail_url", length=500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length=16)
    private ArticleStatus status;

    @Column(name="published_at")
    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="created_by")
    private User createdBy;

    @PrePersist
    void prePersist() {
        if (status == null) status = ArticleStatus.PUBLISHED;
    }
}
