package com.PrimeCare.PrimeCare.modules.masterdata.branch.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "branches")
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="code", nullable=false, length=32, unique = true)
    private String code;

    @Column(name="name_vn", nullable=false, length=255)
    private String nameVn;

    @Column(name="name_en", length=255)
    private String nameEn;

    @Column(name="address_vn", nullable=false, length=500)
    private String addressVn;

    @Column(name="address_en", length=500)
    private String addressEn;

    @Lob
    @Column(name="description_vn", columnDefinition = "longtext")
    private String descriptionVn;

    @Lob
    @Column(name="description_en", columnDefinition = "longtext")
    private String descriptionEn;

    @Column(name="phone", length=32)
    private String phone;

    @Column(name="email", length=255)
    private String email;

    @Column(name="lat", precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name="lng", precision = 10, scale = 7)
    private BigDecimal lng;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length=16)
    private BranchStatus status;

    @Builder.Default
    @JsonIgnore
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BranchSession> sessions = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (status == null) status = BranchStatus.ACTIVE;
    }
}
