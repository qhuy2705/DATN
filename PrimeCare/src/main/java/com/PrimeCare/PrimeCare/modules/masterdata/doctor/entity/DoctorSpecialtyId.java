package com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class DoctorSpecialtyId implements Serializable {

    @Column(name="doctor_id")
    private Long doctorId;

    @Column(name="specialty_id")
    private Long specialtyId;
}
