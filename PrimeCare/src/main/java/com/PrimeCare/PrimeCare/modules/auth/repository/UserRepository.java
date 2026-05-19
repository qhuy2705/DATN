package com.PrimeCare.PrimeCare.modules.auth.repository;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    boolean existsByDoctorProfile_Id(Long doctorProfileId);
    boolean existsByStaffProfile_Id(Long staffProfileId);
    boolean existsByPatient_Id(Long patientId);

    Optional<User> findByDoctorProfile_Id(Long doctorProfileId);
    List<User> findByDoctorProfile_IdIn(Collection<Long> doctorProfileIds);
    Optional<User> findByStaffProfile_Id(Long staffProfileId);
    Optional<User> findByPatient_Id(Long patientId);
    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

    @EntityGraph(attributePaths = {"staffProfile.branch", "doctorProfile.branch"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithBranchProfilesById(@Param("id") Long id);

    default Optional<User> findByEmailOrPhone(String identifier) {
        if (identifier == null) return Optional.empty();
        if (identifier.contains("@")) return findByEmail(identifier);
        return findByPhone(identifier);
    }
}
