package com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity;

import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
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
@Table(name="specialties")
public class Specialty extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="code", nullable=false, length=32, unique=true)
    private String code;

    @Column(name="name_vn", nullable=false, length=255)
    private String nameVn;

    @Column(name="name_en", length=255)
    private String nameEn;

    @Lob
    @Column(name="description_vn", columnDefinition = "longtext")
    private String descriptionVn;

    @Lob
    @Column(name="description_en", columnDefinition = "longtext")
    private String descriptionEn;

    @Column(name="icon_url", length=500)
    private String iconUrl;

    @Column(name="status", nullable=false, length=16)
    private String status;

    @Column(name="default_slot_minutes", nullable=false)
    private Integer defaultSlotMinutes;

    @Column(name="buffer_every_n", nullable=false)
    private Integer bufferEveryN;

    @Column(name="buffer_minutes", nullable=false)
    private Integer bufferMinutes;

    @Column(name="max_per_session")
    private Integer maxPerSession;

    @Builder.Default
    @JsonIgnore
    @OneToMany(mappedBy = "specialty", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DoctorSpecialty> doctorSpecialties = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (status == null) status = "ACTIVE";
        if (defaultSlotMinutes == null) defaultSlotMinutes = 30;
        if (bufferEveryN == null) bufferEveryN = 0;
        if (bufferMinutes == null) bufferMinutes = 0;
    }
}