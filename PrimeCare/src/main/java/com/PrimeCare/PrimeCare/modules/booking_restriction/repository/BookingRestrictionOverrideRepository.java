package com.PrimeCare.PrimeCare.modules.booking_restriction.repository;

import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.BookingRestrictionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRestrictionOverrideRepository extends JpaRepository<BookingRestrictionOverride, Long> {

    List<BookingRestrictionOverride> findByRestriction_IdOrderByCreatedAtDesc(Long restrictionId);
}
