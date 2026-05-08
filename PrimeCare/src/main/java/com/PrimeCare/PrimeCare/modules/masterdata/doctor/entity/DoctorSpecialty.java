package com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity;

import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="doctor_specialties")
public class DoctorSpecialty {

    @EmbeddedId
    private DoctorSpecialtyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("doctorId")
    @JoinColumn(name="doctor_id", nullable=false)
    private DoctorProfile doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("specialtyId")
    @JoinColumn(name="specialty_id", nullable=false)
    private Specialty specialty;
}
