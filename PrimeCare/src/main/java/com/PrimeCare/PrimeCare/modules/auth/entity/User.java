package com.PrimeCare.PrimeCare.modules.auth.entity;

import com.PrimeCare.PrimeCare.modules.doctor_leave.entity.AdminProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
        name="users",
        uniqueConstraints = {
                @UniqueConstraint(name="uq_users_email", columnNames = {"email"}),
                @UniqueConstraint(name="uq_users_phone", columnNames = {"phone"}),
                @UniqueConstraint(name="uq_users_doctor_profile", columnNames = {"doctor_profile_id"}),
                @UniqueConstraint(name="uq_users_staff_profile", columnNames = {"staff_profile_id"}),
                @UniqueConstraint(name="uq_users_patient", columnNames = {"patient_id"})
        }
)
public class User extends BaseEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name="email", length=255)
        private String email;

        @Column(name="email_verified_at")
        private LocalDateTime emailVerifiedAt;

        @Column(name="phone", length=32)
        private String phone;

        @JsonIgnore
        @Column(name="password_hash", nullable=false, length=255)
        private String passwordHash;

        @Enumerated(EnumType.STRING)
        @Column(name="role", nullable=false, length=32)
        private UserRole role;

        @Enumerated(EnumType.STRING)
        @Column(name="status", nullable=false, length=16)
        private UserStatus status;

        @Column(name="last_login_at")
        private LocalDateTime lastLoginAt;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name="doctor_profile_id")
        private DoctorProfile doctorProfile;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name="staff_profile_id")
        private StaffProfile staffProfile;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name="admin_profile_id")
        private AdminProfile adminProfile;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name="patient_id")
        private Patient patient;

        @PrePersist
        void prePersist() {
                if (status == null) status = UserStatus.ACTIVE;
        }

        public String getFullName() {
                if (doctorProfile != null) return doctorProfile.getFullName();
                if (staffProfile != null) return staffProfile.getFullName();
                if (adminProfile != null) return adminProfile.getFullName();
                if (patient != null) return patient.getFullName();
                return "Unknown User";
        }
}
