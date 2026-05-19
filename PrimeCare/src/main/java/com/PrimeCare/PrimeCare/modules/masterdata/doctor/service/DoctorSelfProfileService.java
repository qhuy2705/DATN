package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateMyDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.mapper.DoctorProfileMapper;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DoctorSelfProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DoctorProfileResponse getMyProfile(Long userId) {
        User user = getRequiredDoctorUser(userId);
        return DoctorProfileMapper.toResponse(user.getDoctorProfile(), user);
    }

    @Transactional
    public DoctorProfileResponse updateMyProfile(Long userId, UpdateMyDoctorProfileRequest req) {
        User user = getRequiredDoctorUser(userId);
        DoctorProfile doctor = user.getDoctorProfile();

        doctor.setDisplayTitleVn(StringUtil.trimToNull(req.getDisplayTitleVn()));
        doctor.setDisplayTitleEn(StringUtil.trimToNull(req.getDisplayTitleEn()));
        doctor.setBioVn(StringUtil.trimToNull(req.getBioVn()));
        doctor.setBioEn(StringUtil.trimToNull(req.getBioEn()));
        doctor.setExpertiseVn(StringUtil.trimToNull(req.getExpertiseVn()));
        doctor.setExpertiseEn(StringUtil.trimToNull(req.getExpertiseEn()));
        doctor.setEducationVn(StringUtil.trimToNull(req.getEducationVn()));
        doctor.setEducationEn(StringUtil.trimToNull(req.getEducationEn()));
        doctor.setAchievementsVn(StringUtil.trimToNull(req.getAchievementsVn()));
        doctor.setAchievementsEn(StringUtil.trimToNull(req.getAchievementsEn()));
        doctor.setAvatarUrl(StringUtil.trimToNull(req.getAvatarUrl()));

        user = userRepository.save(user);
        return DoctorProfileMapper.toResponse(user.getDoctorProfile(), user);
    }

    private User getRequiredDoctorUser(Long userId) {
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (user.getDoctorProfile() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return user;
    }
}
