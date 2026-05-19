package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateMyDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorSelfProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DoctorSelfProfileService service;

    @Test
    void updateMyProfileIgnoresAdminOwnedFieldsAndUpdatesAllowedPublicFields() throws Exception {
        DoctorProfile doctor = DoctorProfile.builder()
                .id(3L)
                .fullName("Dr Official")
                .branch(Branch.builder()
                        .id(1L)
                        .nameVn("PrimeCare")
                        .status(BranchStatus.ACTIVE)
                        .build())
                .displayTitleVn("Bac si")
                .yearsExp(12)
                .status(DoctorStatus.ACTIVE)
                .build();
        User user = User.builder()
                .id(10L)
                .email("doctor@primecare.test")
                .role(UserRole.DOCTOR)
                .status(UserStatus.ACTIVE)
                .doctorProfile(doctor)
                .build();
        UpdateMyDoctorProfileRequest request = new ObjectMapper().readValue("""
                {
                  "fullName": "Dr Changed By Self API",
                  "yearsExp": 99,
                  "displayTitleVn": "  Thac si, Bac si  ",
                  "displayTitleEn": "  MD  ",
                  "bioVn": "  Tieu su moi  ",
                  "bioEn": "  New bio  ",
                  "expertiseVn": "  Chuyen mon moi  ",
                  "expertiseEn": "  New expertise  ",
                  "educationVn": "  Dao tao moi  ",
                  "educationEn": "  New education  ",
                  "achievementsVn": "  Thanh tuu moi  ",
                  "achievementsEn": "  New achievements  ",
                  "avatarUrl": "  https://cdn.primecare.test/avatar.png  "
                }
                """, UpdateMyDoctorProfileRequest.class);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        DoctorProfileResponse response = service.updateMyProfile(10L, request);

        assertThat(doctor.getFullName()).isEqualTo("Dr Official");
        assertThat(doctor.getYearsExp()).isEqualTo(12);
        assertThat(response.getFullName()).isEqualTo("Dr Official");
        assertThat(response.getYearsExp()).isEqualTo(12);
        assertThat(doctor.getDisplayTitleVn()).isEqualTo("Thac si, Bac si");
        assertThat(doctor.getDisplayTitleEn()).isEqualTo("MD");
        assertThat(doctor.getBioVn()).isEqualTo("Tieu su moi");
        assertThat(doctor.getBioEn()).isEqualTo("New bio");
        assertThat(doctor.getExpertiseVn()).isEqualTo("Chuyen mon moi");
        assertThat(doctor.getExpertiseEn()).isEqualTo("New expertise");
        assertThat(doctor.getEducationVn()).isEqualTo("Dao tao moi");
        assertThat(doctor.getEducationEn()).isEqualTo("New education");
        assertThat(doctor.getAchievementsVn()).isEqualTo("Thanh tuu moi");
        assertThat(doctor.getAchievementsEn()).isEqualTo("New achievements");
        assertThat(doctor.getAvatarUrl()).isEqualTo("https://cdn.primecare.test/avatar.png");
    }
}
