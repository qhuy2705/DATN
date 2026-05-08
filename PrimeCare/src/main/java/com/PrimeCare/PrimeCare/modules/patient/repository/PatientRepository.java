package com.PrimeCare.PrimeCare.modules.patient.repository;

import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPhone(String phone);

    Optional<Patient> findByPhoneAndFullNameIgnoreCaseAndDob(String phone, String fullName, LocalDate dob);

    Optional<Patient> findByPhoneAndFullNameIgnoreCase(String phone, String fullName);

    Optional<Patient> findByCode(String code);

    Optional<Patient> findByEmailIgnoreCase(String email);

    Optional<Patient> findByPhoneAndDob(String phone, LocalDate dob);

    boolean existsByCode(String code);

    Page<Patient> findByStatus(PatientStatus status, Pageable pageable);

    @Query("""
            select p
            from Patient p
            where (:status is null or p.status = :status)
              and (
                    :q is null
                    or lower(p.code) like lower(concat('%', :q, '%'))
                    or lower(p.fullName) like lower(concat('%', :q, '%'))
                    or lower(p.phone) like lower(concat('%', :q, '%'))
                    or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%'))
                    or lower(coalesce(p.identityNumber, '')) like lower(concat('%', :q, '%'))
                    or lower(coalesce(p.insuranceNumber, '')) like lower(concat('%', :q, '%'))
              )
            """)
    Page<Patient> searchAdmin(@Param("q") String q,
                              @Param("status") PatientStatus status,
                              Pageable pageable);

    @Query("""
            select p
            from Patient p
            where p.status = :status
              and (
                    lower(p.code) like lower(concat('%', :q, '%'))
                    or lower(p.fullName) like lower(concat('%', :q, '%'))
                    or lower(p.phone) like lower(concat('%', :q, '%'))
                    or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%'))
              )
            """)
    Page<Patient> searchForReception(@Param("q") String q,
                                     @Param("status") PatientStatus status,
                                     Pageable pageable);
}
