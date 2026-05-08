package com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "doctor_profiles")
public class DoctorProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="full_name", nullable=false, length=255)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="branch_id", nullable=false)
    private Branch branch;

    @Column(name="display_title_vn", length=150)
    private String displayTitleVn;

    @Column(name="display_title_en", length=150)
    private String displayTitleEn;

    @Lob
    @Column(name="bio_vn", columnDefinition = "longtext")
    private String bioVn;

    @Lob
    @Column(name="bio_en", columnDefinition = "longtext")
    private String bioEn;

    @Lob
    @Column(name="expertise_vn", columnDefinition = "longtext")
    private String expertiseVn;

    @Lob
    @Column(name="expertise_en", columnDefinition = "longtext")
    private String expertiseEn;

    @Lob
    @Column(name="education_vn", columnDefinition = "longtext")
    private String educationVn;

    @Lob
    @Column(name="education_en", columnDefinition = "longtext")
    private String educationEn;

    @Lob
    @Column(name="achievements_vn", columnDefinition = "longtext")
    private String achievementsVn;

    @Lob
    @Column(name="achievements_en", columnDefinition = "longtext")
    private String achievementsEn;

    @Column(name="years_exp")
    private Integer yearsExp;

    @Column(name="avatar_url", length=500)
    private String avatarUrl;

    @Column(name="slot_minutes_override")
    private Integer slotMinutesOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DoctorStatus status;

    @Builder.Default
    @JsonIgnore
    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DoctorSpecialty> doctorSpecialties = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (status == null) status = DoctorStatus.ACTIVE;
    }
}