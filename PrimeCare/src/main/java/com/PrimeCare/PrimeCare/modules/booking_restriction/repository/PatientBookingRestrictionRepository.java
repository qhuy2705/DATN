package com.PrimeCare.PrimeCare.modules.booking_restriction.repository;

import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientBookingRestriction;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface PatientBookingRestrictionRepository extends
        JpaRepository<PatientBookingRestriction, Long>,
        JpaSpecificationExecutor<PatientBookingRestriction> {

    List<PatientBookingRestriction> findByIdentityKeyHashAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
            String identityKeyHash,
            BookingRestrictionStatus status,
            LocalDateTime now
    );

    List<PatientBookingRestriction> findByIdentityKeyHashAndStatusOrderByExpiresAtDesc(
            String identityKeyHash,
            BookingRestrictionStatus status
    );
}
