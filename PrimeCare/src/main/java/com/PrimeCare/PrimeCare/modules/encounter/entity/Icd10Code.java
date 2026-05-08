package com.PrimeCare.PrimeCare.modules.encounter.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "icd10_codes",
        indexes = {
                @Index(name = "idx_icd10_code", columnList = "code"),
                @Index(name = "idx_icd10_name_vn", columnList = "name_vn"),
                @Index(name = "idx_icd10_category", columnList = "category")
        }
)
public class Icd10Code extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "name_vn", nullable = false, length = 500)
    private String nameVn;

    @Column(name = "name_en", length = 500)
    private String nameEn;

    @Column(name = "category", length = 64)
    private String category;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
