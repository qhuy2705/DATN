package com.PrimeCare.PrimeCare.modules.file.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import com.PrimeCare.PrimeCare.shared.enums.StorageProvider;
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
        name="files",
        indexes = @Index(name="idx_files_owner", columnList="owner_type,owner_id")
)
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name="owner_type", nullable=false, length=32)
    private FileOwnerType ownerType;

    @Column(name="owner_id", nullable=false)
    private Long ownerId;

    @Column(name="file_name", nullable=false, length=255)
    private String fileName;

    @Column(name="mime_type", nullable=false, length=100)
    private String mimeType;

    @Column(name="file_size", nullable=false)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name="storage_provider", nullable=false, length=16)
    private StorageProvider storageProvider;

    @Column(name="storage_path", nullable=false, length=500)
    private String storagePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="created_by")
    private User createdBy;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (storageProvider == null) storageProvider = StorageProvider.S3;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
