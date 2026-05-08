package com.PrimeCare.PrimeCare.modules.patient_portal.service;

import com.PrimeCare.PrimeCare.modules.auth.dto.request.LoginRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.AuthIssueResult;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AuthService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.request.RegisterPatientAccountRequest;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PatientAccountService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthIssueResult register(RegisterPatientAccountRequest req, String userAgent, String ip) {
        String phone = req.getPhone().trim();
        String email = normalize(req.getEmail());
        String fullName = req.getFullName().trim();

        ensureIdentityAvailable(phone, email);

        Patient patient = matchOrCreatePatient(fullName, phone, email, req.getDob(), req.getGender(), req.getAddress(), req.getInsuranceNumber());
        if (userRepository.existsByPatient_Id(patient.getId())) {
            throw new ApiException(ErrorCode.PATIENT_ACCOUNT_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(UserRole.PATIENT)
                .status(UserStatus.ACTIVE)
                .patient(patient)
                .build();
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier(email != null ? email : phone);
        loginRequest.setPassword(req.getPassword());
        return authService.login(loginRequest, userAgent, ip);
    }

    private void ensureIdentityAvailable(String phone, String email) {
        if (userRepository.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.AUTH_PHONE_EXISTS);
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_EXISTS);
        }
    }

    private Patient matchOrCreatePatient(String fullName,
                                         String phone,
                                         String email,
                                         LocalDate dob,
                                         Gender gender,
                                         String address,
                                         String insuranceNumber) {
        Patient existing = null;
        if (dob != null) {
            existing = patientRepository.findByPhoneAndDob(phone, dob).orElse(null);
        }
        if (existing == null && email != null) {
            existing = patientRepository.findByEmailIgnoreCase(email).orElse(null);
        }
        if (existing == null) {
            existing = patientRepository.findByPhone(phone).orElse(null);
        }

        if (existing != null) {
            validateExistingPatientMatch(existing, fullName, dob);
            mergePatient(existing, fullName, email, dob, gender, address, insuranceNumber);
            return patientRepository.save(existing);
        }

        return patientRepository.save(Patient.builder()
                .code(generateCode())
                .fullName(fullName)
                .phone(phone)
                .email(email)
                .dob(dob)
                .gender(gender)
                .address(normalize(address))
                .insuranceNumber(normalize(insuranceNumber))
                .status(PatientStatus.ACTIVE)
                .build());
    }

    private void validateExistingPatientMatch(Patient patient, String fullName, LocalDate dob) {
        if (patient.getFullName() != null && !patient.getFullName().equalsIgnoreCase(fullName)) {
            throw new ApiException(ErrorCode.PATIENT_REGISTRATION_MISMATCH);
        }
        if (patient.getDob() != null && dob != null && !patient.getDob().equals(dob)) {
            throw new ApiException(ErrorCode.PATIENT_REGISTRATION_MISMATCH);
        }
    }

    private void mergePatient(Patient patient,
                              String fullName,
                              String email,
                              LocalDate dob,
                              Gender gender,
                              String address,
                              String insuranceNumber) {
        if (patient.getFullName() == null || patient.getFullName().isBlank()) patient.setFullName(fullName);
        if ((patient.getEmail() == null || patient.getEmail().isBlank()) && email != null) patient.setEmail(email);
        if (patient.getDob() == null) patient.setDob(dob);
        if (patient.getGender() == null) patient.setGender(gender);
        if ((patient.getAddress() == null || patient.getAddress().isBlank()) && address != null && !address.isBlank()) patient.setAddress(address.trim());
        if ((patient.getInsuranceNumber() == null || patient.getInsuranceNumber().isBlank()) && insuranceNumber != null && !insuranceNumber.isBlank()) patient.setInsuranceNumber(insuranceNumber.trim());
        if (patient.getStatus() == null) patient.setStatus(PatientStatus.ACTIVE);
    }

    private String generateCode() {
        String datePart = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now());
        for (int i = 0; i < 20; i++) {
            String suffix = String.format("%04d", secureRandom.nextInt(10_000));
            String code = "PT" + datePart + suffix;
            if (!patientRepository.existsByCode(code)) return code;
        }
        return "PT" + Instant.now().toEpochMilli();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
