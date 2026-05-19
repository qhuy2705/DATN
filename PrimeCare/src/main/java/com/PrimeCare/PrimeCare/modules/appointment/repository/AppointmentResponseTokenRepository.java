package com.PrimeCare.PrimeCare.modules.appointment.repository;

import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentResponseToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentResponseTokenRepository extends JpaRepository<AppointmentResponseToken, Long> {

    Optional<AppointmentResponseToken> findByTokenHash(String tokenHash);
}
