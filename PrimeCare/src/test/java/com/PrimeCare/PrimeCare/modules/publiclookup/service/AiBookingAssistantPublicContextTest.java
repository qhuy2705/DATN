package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.BranchSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.BranchSpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.modules.medical_service.repository.MedicalServiceRepository;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantActionResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiBookingAssistantPublicContextTest {

    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private BranchSpecialtyRepository branchSpecialtyRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private AppointmentAvailabilityService appointmentAvailabilityService;
    @Mock
    private MedicalServiceRepository medicalServiceRepository;

    @InjectMocks
    private AiBookingAssistantService service;

    @BeforeEach
    void setUp() {
        when(specialtyRepository.findAllByStatus(eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(branchSpecialtyRepository.existsByBranch_IdAndSpecialty_IdAndStatus(any(), any(), eq(BranchSpecialtyStatus.ACTIVE)))
                .thenReturn(true);
    }

    @Test
    void branchByLocationReturnsActiveBranchesOnly() {
        Branch hanoi = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Branch inactive = branch(2L, "HN2", "PrimeCare Hà Nội 2", "99 Giảng Võ, Hà Nội", BranchStatus.INACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        stubActiveBranches(hanoi, inactive);
        when(branchSpecialtyRepository.findActiveByBranchId(1L)).thenReturn(List.of(mapping(hanoi, cardio)));

        PublicAssistantResponse response = service.chat(request("Có chi nhánh nào ở Hà Nội không?"));

        assertThat(response.getIntent()).isEqualTo("BRANCH_SEARCH");
        assertThat(response.getMessage()).contains("PrimeCare Hà Nội", "12 Trần Duy Hưng");
        assertThat(response.getMessage()).doesNotContain("PrimeCare Hà Nội 2");
        assertThat(response.getActions()).extracting(PublicAssistantActionResponse::getType)
                .contains("VIEW_BRANCH");
    }

    @Test
    void branchSpecialtyByLocationReturnsOnlyValidActiveMapping() {
        Branch hanoiCardio = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Branch hanoiNoCardio = branch(2L, "HN-TAYHO", "PrimeCare Tây Hồ", "1 Lạc Long Quân, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(100L, "BS. Hoàng Minh Sơn", hanoiCardio, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(hanoiCardio, hanoiNoCardio);
        stubSpecialties(cardio);
        when(branchSpecialtyRepository.findActiveBySpecialtyId(cardio.getId()))
                .thenReturn(List.of(mapping(hanoiCardio, cardio)));
        when(doctorProfileRepository.findActiveByBranchAndSpecialty(hanoiCardio.getId(), cardio.getId()))
                .thenReturn(List.of(doctor));

        PublicAssistantResponse response = service.chat(request("Có chi nhánh nào khám tim mạch ở Hà Nội không?"));

        assertThat(response.getIntent()).isEqualTo("BRANCH_SPECIALTY_SEARCH");
        assertThat(response.getMessage()).contains("PrimeCare Hà Nội", "Tim mạch", "BS. Hoàng Minh Sơn");
        assertThat(response.getMessage()).doesNotContain("PrimeCare Tây Hồ");
    }

    @Test
    void doctorByNameReturnsPublicBookableDoctor() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(100L, "Bác sĩ Nguyễn Văn A", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Bác sĩ Nguyễn Văn A khám ở đâu?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_RECOMMENDATION");
        assertThat(response.getMessage()).contains("Bác sĩ Nguyễn Văn A", "PrimeCare Hà Nội");
        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(doctor.getId());
    }

    @Test
    void doctorWithoutPublicAccountIsNotSuggested() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(100L, "Bác sĩ Nguyễn Văn A", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(doctorProfileRepository.findAll()).thenReturn(List.of(doctor));

        PublicAssistantResponse response = service.chat(request("Bác sĩ Nguyễn Văn A khám ở đâu?"));

        assertThat(response.getSuggestedDoctor()).isNull();
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getMessage()).contains("chưa tìm thấy bác sĩ khả dụng");
    }

    @Test
    void doctorInInactiveBranchIsNotSuggested() {
        Branch inactiveBranch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.INACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(100L, "Bác sĩ Nguyễn Văn A", inactiveBranch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(inactiveBranch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));
        when(doctorProfileRepository.findAll()).thenReturn(List.of(doctor));

        PublicAssistantResponse response = service.chat(request("Bác sĩ Nguyễn Văn A khám ở đâu?"));

        assertThat(response.getSuggestedDoctor()).isNull();
        assertThat(response.getMessage()).contains("chưa tìm thấy bác sĩ khả dụng");
    }

    @Test
    void accentInsensitiveBranchSpecialtySearchWorks() {
        Branch hanoi = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty derma = specialty(20L, "DERMA", "Da liễu");
        stubActiveBranches(hanoi);
        stubSpecialties(derma);
        when(branchSpecialtyRepository.findActiveBySpecialtyId(derma.getId()))
                .thenReturn(List.of(mapping(hanoi, derma)));
        when(doctorProfileRepository.findActiveByBranchAndSpecialty(hanoi.getId(), derma.getId()))
                .thenReturn(List.of());

        PublicAssistantResponse response = service.chat(request("Co chi nhanh nao kham da lieu o Ha Noi khong?"));

        assertThat(response.getIntent()).isEqualTo("BRANCH_SPECIALTY_SEARCH");
        assertThat(response.getMessage()).contains("PrimeCare Hà Nội", "Da liễu");
    }

    @Test
    void branchServiceQuestionUsesActivePublicServiceCatalogWithoutClaimingBranchMapping() {
        Branch hanoi = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        MedicalService cbc = medicalService(30L, "LAB-CBC", "Công thức máu toàn phần", MedicalServiceStatus.ACTIVE, true);
        stubActiveBranches(hanoi);
        when(medicalServiceRepository.findByPublicVisibleTrueAndStatusOrderByDisplayOrderAscNameVnAsc(MedicalServiceStatus.ACTIVE))
                .thenReturn(List.of(cbc));

        PublicAssistantResponse response = service.chat(request("Chi nhánh nào có xét nghiệm máu?"));

        assertThat(response.getIntent()).isEqualTo("SERVICE_SEARCH");
        assertThat(response.getMessage()).contains("Công thức máu toàn phần");
        assertThat(response.getMessage()).contains("chưa xác nhận phân bổ dịch vụ theo từng cơ sở");
        assertThat(response.getActions()).extracting(PublicAssistantActionResponse::getType)
                .contains("NAVIGATE");
    }

    @Test
    void inactiveServiceIsNotReturned() {
        MedicalService inactive = medicalService(31L, "LAB-THYROID", "Xét nghiệm tuyến giáp", MedicalServiceStatus.INACTIVE, true);
        when(medicalServiceRepository.findByPublicVisibleTrueAndStatusOrderByDisplayOrderAscNameVnAsc(MedicalServiceStatus.ACTIVE))
                .thenReturn(List.of(inactive));

        PublicAssistantResponse response = service.chat(request("PrimeCare có xét nghiệm tuyến giáp không?"));

        assertThat(response.getIntent()).isEqualTo("SERVICE_SEARCH");
        assertThat(response.getMessage()).contains("chưa tìm thấy dịch vụ y tế công khai");
        assertThat(response.getMessage()).doesNotContain("Xét nghiệm tuyến giáp");
    }

    @Test
    void incompleteDoctorBookingContextAsksFollowUpAndDoesNotCreateDraft() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(100L, "Bác sĩ Nguyễn Văn A", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Tôi muốn đặt lịch bác sĩ Nguyễn Văn A tại Hà Nội"));

        assertThat(response.getIntent()).isEqualTo("CLARIFICATION");
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getMessage()).contains("ngày nào", "buổi nào");
    }

    @Test
    void doctorNameWithoutDoctorPrefixCanBeMatched() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(100L, "Nguyễn Văn A", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Nguyễn Văn A khám ở đâu?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_RECOMMENDATION");
        assertThat(response.getMessage()).contains("Nguyễn Văn A", "PrimeCare Hà Nội");
    }

    @Test
    void doctorNameLookupQuestionReturnsPublicBookableDoctors() {
        Branch cauGiay = branch(1L, "CG", "PrimeCare Cầu Giấy", "1 Xuân Thủy, Cầu Giấy, Hà Nội", BranchStatus.ACTIVE);
        Specialty derma = specialty(20L, "DERMA", "Da liễu");
        DoctorProfile doctor = doctor(101L, "BS. Nguyễn Hương Giang", cauGiay, derma, DoctorStatus.ACTIVE);
        stubActiveBranches(cauGiay);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Có bác sĩ nào tên Giang không?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_SEARCH");
        assertThat(response.getMessage()).contains("Giang", "Nguyễn Hương Giang", "Da liễu", "PrimeCare Cầu Giấy");
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getActions()).extracting(PublicAssistantActionResponse::getType)
                .contains("VIEW_DOCTOR")
                .doesNotContain("BOOK_APPOINTMENT_PREFILL");
    }

    @Test
    void findDoctorByNameQuestionUsesPartialAccentInsensitiveMatching() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(102L, "BS. Nguyễn Hương Giang", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Tim bac si Huong Giang"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_SEARCH");
        assertThat(response.getMessage()).contains("Nguyễn Hương Giang", "Tim mạch");
    }

    @Test
    void doctorNameLocationQuestionFiltersByBranchLocation() {
        Branch hanoi = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Branch hcm = branch(2L, "HCM", "PrimeCare Gò Vấp", "5 Quang Trung, Gò Vấp, TP.HCM", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile hanoiDoctor = doctor(103L, "BS. Nguyễn Hương Giang", hanoi, cardio, DoctorStatus.ACTIVE);
        DoctorProfile hcmDoctor = doctor(104L, "BS. Trần Minh Giang", hcm, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(hanoi, hcm);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(hanoiDoctor, hcmDoctor)));

        PublicAssistantResponse response = service.chat(request("Có bác sĩ Giang ở Hà Nội không?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_SEARCH");
        assertThat(response.getMessage()).contains("Nguyễn Hương Giang", "PrimeCare Hà Nội");
        assertThat(response.getMessage()).doesNotContain("Trần Minh Giang");
    }

    @Test
    void doctorNameSpecialtyQuestionFiltersBySpecialty() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        Specialty derma = specialty(20L, "DERMA", "Da liễu");
        DoctorProfile cardioDoctor = doctor(105L, "BS. Nguyễn Hương Giang", branch, cardio, DoctorStatus.ACTIVE);
        DoctorProfile dermaDoctor = doctor(106L, "BS. Trần Minh Giang", branch, derma, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        stubSpecialties(cardio, derma);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cardioDoctor, dermaDoctor)));

        PublicAssistantResponse response = service.chat(request("Bác sĩ Giang khám tim mạch không?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_SEARCH");
        assertThat(response.getMessage()).contains("Nguyễn Hương Giang", "Tim mạch");
        assertThat(response.getMessage()).doesNotContain("Trần Minh Giang");
    }

    @Test
    void doctorNameAvailabilityQuestionChecksRealAvailabilityWithoutFakeDraft() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(107L, "BS. Nguyễn Hương Giang", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Bác sĩ Giang có lịch ngày mai không?"));

        assertThat(response.getIntent()).isEqualTo("SHOW_AVAILABLE_SLOTS");
        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(doctor.getId());
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getMessage()).contains("chưa có lịch trống");
    }

    @Test
    void inactiveDoctorIsNotReturnedForNameLookup() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile inactive = doctor(108L, "BS. Nguyễn Hương Giang", branch, cardio, DoctorStatus.INACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inactive)));

        PublicAssistantResponse response = service.chat(request("Có bác sĩ nào tên Giang không?"));

        assertThat(response.getSuggestedDoctor()).isNull();
        assertThat(response.getMessage()).contains("chưa tìm thấy bác sĩ khả dụng");
    }

    @Test
    void doctorWithoutPublicValidSpecialtyIsNotReturnedForNameLookup() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(109L, "BS. Nguyễn Hương Giang", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(branchSpecialtyRepository.existsByBranch_IdAndSpecialty_IdAndStatus(eq(1L), eq(10L), eq(BranchSpecialtyStatus.ACTIVE)))
                .thenReturn(false);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Có bác sĩ nào tên Giang không?"));

        assertThat(response.getSuggestedDoctor()).isNull();
        assertThat(response.getMessage()).contains("chưa tìm thấy bác sĩ khả dụng");
    }

    @Test
    void branchDoctorListQuestionReturnsDoctorsAtLocation() {
        Branch cauGiay = branch(1L, "CG", "PrimeCare Cầu Giấy", "1 Xuân Thủy, Cầu Giấy, Hà Nội", BranchStatus.ACTIVE);
        Branch haDong = branch(2L, "HD", "PrimeCare Hà Đông", "2 Quang Trung, Hà Đông, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile cauGiayDoctor = doctor(110L, "BS. Nguyễn Hương Giang", cauGiay, cardio, DoctorStatus.ACTIVE);
        DoctorProfile haDongDoctor = doctor(111L, "BS. Trần Minh An", haDong, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(cauGiay, haDong);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cauGiayDoctor, haDongDoctor)));

        PublicAssistantResponse response = service.chat(request("Chi nhánh Cầu Giấy có bác sĩ nào?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_SEARCH");
        assertThat(response.getMessage()).contains("Nguyễn Hương Giang", "PrimeCare Cầu Giấy");
        assertThat(response.getMessage()).doesNotContain("Trần Minh An");
    }

    @Test
    void generalDoctorListQuestionReturnsPublicDoctors() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(112L, "BS. Nguyễn Hương Giang", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Có những bác sĩ nào?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_SEARCH");
        assertThat(response.getMessage()).contains("Nguyễn Hương Giang", "Tim mạch");
        assertThat(response.getBookingDraft()).isNull();
    }

    @Test
    void doctorGenderQuestionDoesNotPretendGenderFilterExists() {
        PublicAssistantResponse response = service.chat(request("Có bác sĩ nữ không?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_SEARCH");
        assertThat(response.getMessage()).contains("chưa hỗ trợ lọc bác sĩ theo giới tính");
        assertThat(response.getBookingDraft()).isNull();
    }

    @Test
    void singleLetterDoctorNameWithDatePhraseIsCleanedBeforeMatching() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile doctor = doctor(100L, "Bác sĩ Nguyễn Văn A", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("Tôi muốn đặt lịch bác sĩ A sáng mai"));

        assertThat(response.getIntent()).isEqualTo("SHOW_AVAILABLE_SLOTS");
        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(doctor.getId());
        assertThat(response.getBookingDraft()).isNull();
    }

    @Test
    void branchCountByLocationAnswersNumberFirst() {
        Branch cauGiay = branch(1L, "CG", "PrimeCare Cầu Giấy", "1 Xuân Thủy, Cầu Giấy, Hà Nội", BranchStatus.ACTIVE);
        Branch haDong = branch(2L, "HD", "PrimeCare Hà Đông", "2 Quang Trung, Hà Đông, Hà Nội", BranchStatus.ACTIVE);
        Branch goVap = branch(3L, "GV", "PrimeCare Gò Vấp", "5 Quang Trung, Gò Vấp, TP.HCM", BranchStatus.ACTIVE);
        stubActiveBranches(cauGiay, haDong, goVap);

        PublicAssistantResponse response = service.chat(request("Có bao nhiêu chi nhánh ở Hà Nội?"));

        assertThat(response.getIntent()).isEqualTo("BRANCH_COUNT");
        assertThat(response.getMessage()).contains("2 cơ sở", "PrimeCare Cầu Giấy", "PrimeCare Hà Đông");
        assertThat(response.getMessage()).doesNotContain("PrimeCare Gò Vấp");
    }

    @Test
    void singleBranchResultSupportsFacilityAddressFollowUp() {
        Branch q1 = branch(1L, "Q1", "PrimeCare Quận 1", "88 Nguyễn Du, Quận 1, TP.HCM", BranchStatus.ACTIVE);
        q1.setPhone("028-1234-5678");
        stubActiveBranches(q1);
        when(branchRepository.findById(q1.getId())).thenReturn(java.util.Optional.of(q1));

        PublicAssistantResponse first = service.chat(request("Có chi nhánh nào ở Quận 1 không?"));
        PublicAssistantResponse followUp = service.chat(request("Cơ sở này ở đâu?", first.getContext()));

        assertThat(followUp.getIntent()).isEqualTo("FACILITY_INFO");
        assertThat(followUp.getMessage()).contains("PrimeCare Quận 1", "88 Nguyễn Du", "028-1234-5678");
    }

    @Test
    void multipleBranchResultAsksClarificationForFacilityFollowUp() {
        Branch cauGiay = branch(1L, "CG", "PrimeCare Cầu Giấy", "1 Xuân Thủy, Cầu Giấy, Hà Nội", BranchStatus.ACTIVE);
        Branch haDong = branch(2L, "HD", "PrimeCare Hà Đông", "2 Quang Trung, Hà Đông, Hà Nội", BranchStatus.ACTIVE);
        stubActiveBranches(cauGiay, haDong);

        PublicAssistantResponse first = service.chat(request("Có chi nhánh nào ở Hà Nội không?"));
        PublicAssistantResponse followUp = service.chat(request("Cơ sở này ở đâu?", first.getContext()));

        assertThat(followUp.getIntent()).isEqualTo("FACILITY_INFO");
        assertThat(followUp.getMessage()).contains("cơ sở nào");
    }

    @Test
    void doctorCountByBranchAnswersCountDirectly() {
        Branch q1 = branch(1L, "Q1", "PrimeCare Quận 1", "88 Nguyễn Du, Quận 1, TP.HCM", BranchStatus.ACTIVE);
        Specialty general = specialty(10L, "GENERAL", "Nội tổng quát");
        DoctorProfile doctor = doctor(120L, "BS.CKI. Phạm Thanh Hải", q1, general, DoctorStatus.ACTIVE);
        stubActiveBranches(q1);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));

        PublicAssistantResponse response = service.chat(request("PrimeCare quận 1 có bao nhiêu bác sĩ?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_COUNT");
        assertThat(response.getMessage()).contains("PrimeCare Quận 1 hiện có 1 bác sĩ", "Phạm Thanh Hải", "Nội tổng quát");
        assertThat(response.getBookingDraft()).isNull();
    }

    @Test
    void doctorNameCountAnswersCountDirectly() {
        Branch branch = branch(1L, "HN", "PrimeCare Hà Nội", "12 Trần Duy Hưng, Hà Nội", BranchStatus.ACTIVE);
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        DoctorProfile first = doctor(121L, "BS. Nguyễn Hương Giang", branch, cardio, DoctorStatus.ACTIVE);
        DoctorProfile second = doctor(122L, "BS. Trần Minh Giang", branch, cardio, DoctorStatus.ACTIVE);
        stubActiveBranches(branch);
        when(doctorProfileRepository.search(isNull(), isNull(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        PublicAssistantResponse response = service.chat(request("Có bao nhiêu bác sĩ tên Giang?"));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_COUNT");
        assertThat(response.getMessage()).contains("2 bác sĩ", "Nguyễn Hương Giang", "Trần Minh Giang");
    }

    @Test
    void branchSpecialtyInfoDoesNotTriggerSlotSearch() {
        Branch q1 = branch(1L, "Q1", "PrimeCare Quận 1", "88 Nguyễn Du, Quận 1, TP.HCM", BranchStatus.ACTIVE);
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");
        stubActiveBranches(q1);
        stubSpecialties(pediatrics);
        when(branchSpecialtyRepository.findActiveBySpecialtyId(pediatrics.getId()))
                .thenReturn(List.of(mapping(q1, pediatrics)));
        when(doctorProfileRepository.findActiveByBranchAndSpecialty(q1.getId(), pediatrics.getId()))
                .thenReturn(List.of());

        PublicAssistantResponse response = service.chat(request("PrimeCare quận 1 có chuyên khoa Nhi không?"));

        assertThat(response.getIntent()).isEqualTo("BRANCH_SPECIALTY_SEARCH");
        assertThat(response.getMessage()).contains("PrimeCare Quận 1", "có chuyên khoa Nhi khoa", "chưa tìm thấy bác sĩ khả dụng");
        assertThat(response.getMessage()).doesNotContain("lịch trống");
        verifyNoInteractions(appointmentAvailabilityService);
    }

    @Test
    void branchSpecialtyCountUsesActiveMappings() {
        Branch q1 = branch(1L, "Q1", "PrimeCare Quận 1", "88 Nguyễn Du, Quận 1, TP.HCM", BranchStatus.ACTIVE);
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");
        Specialty cardio = specialty(10L, "CARDIO", "Tim mạch");
        stubActiveBranches(q1);
        when(branchSpecialtyRepository.findActiveByBranchId(q1.getId()))
                .thenReturn(List.of(mapping(q1, pediatrics), mapping(q1, cardio)));

        PublicAssistantResponse response = service.chat(request("Cơ sở Quận 1 có bao nhiêu chuyên khoa?"));

        assertThat(response.getIntent()).isEqualTo("SPECIALTY_COUNT");
        assertThat(response.getMessage()).contains("PrimeCare Quận 1 hiện có 2 chuyên khoa", "Nhi khoa", "Tim mạch");
    }

    @Test
    void serviceListFollowUpUsesCurrentFacilityContextWithoutClaimingMapping() {
        Branch q1 = branch(1L, "Q1", "PrimeCare Quận 1", "88 Nguyễn Du, Quận 1, TP.HCM", BranchStatus.ACTIVE);
        MedicalService cbc = medicalService(30L, "LAB-CBC", "Công thức máu toàn phần", MedicalServiceStatus.ACTIVE, true);
        MedicalService ultrasound = medicalService(31L, "ULTRASOUND", "Siêu âm tổng quát", MedicalServiceStatus.ACTIVE, true);
        stubActiveBranches(q1);
        when(branchRepository.findById(q1.getId())).thenReturn(java.util.Optional.of(q1));
        when(medicalServiceRepository.findByPublicVisibleTrueAndStatusOrderByDisplayOrderAscNameVnAsc(MedicalServiceStatus.ACTIVE))
                .thenReturn(List.of(cbc, ultrasound));

        PublicAssistantResponse first = service.chat(request("Có chi nhánh nào ở Quận 1 không?"));
        PublicAssistantResponse followUp = service.chat(request("Cơ sở này có dịch vụ gì?", first.getContext()));

        assertThat(followUp.getIntent()).isEqualTo("SERVICE_SEARCH");
        assertThat(followUp.getMessage()).contains("Công thức máu toàn phần", "Siêu âm tổng quát");
        assertThat(followUp.getMessage()).contains("chưa xác nhận phân bổ dịch vụ theo từng cơ sở");
    }

    private void stubSpecialties(Specialty... specialties) {
        when(specialtyRepository.findAllByStatus(eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(specialties)));
        for (Specialty specialty : specialties) {
            when(specialtyRepository.findById(specialty.getId())).thenReturn(java.util.Optional.of(specialty));
        }
    }

    private void stubActiveBranches(Branch... branches) {
        when(branchRepository.findAllByStatus(eq(BranchStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(branches)));
    }

    private PublicAssistantRequest request(String question) {
        PublicAssistantRequest request = new PublicAssistantRequest();
        request.setQuestion(question);
        return request;
    }

    private PublicAssistantRequest request(String question, com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiConversationContext context) {
        PublicAssistantRequest request = request(question);
        request.setContext(context);
        request.setConversationId(context != null ? context.getConversationId() : null);
        return request;
    }

    private BranchSpecialty mapping(Branch branch, Specialty specialty) {
        return BranchSpecialty.builder()
                .branch(branch)
                .specialty(specialty)
                .status(BranchSpecialtyStatus.ACTIVE)
                .displayOrder(1)
                .build();
    }

    private DoctorProfile doctor(Long id, String name, Branch branch, Specialty specialty, DoctorStatus status) {
        DoctorProfile doctor = DoctorProfile.builder()
                .id(id)
                .fullName(name)
                .branch(branch)
                .status(status)
                .build();
        doctor.getDoctorSpecialties().add(DoctorSpecialty.builder()
                .doctor(doctor)
                .specialty(specialty)
                .build());
        return doctor;
    }

    private Specialty specialty(Long id, String code, String name) {
        return Specialty.builder()
                .id(id)
                .code(code)
                .nameVn(name)
                .status("ACTIVE")
                .defaultSlotMinutes(30)
                .bufferEveryN(0)
                .bufferMinutes(0)
                .build();
    }

    private Branch branch(Long id, String code, String name, String address, BranchStatus status) {
        return Branch.builder()
                .id(id)
                .code(code)
                .nameVn(name)
                .addressVn(address)
                .status(status)
                .build();
    }

    private MedicalService medicalService(Long id, String code, String name, MedicalServiceStatus status, boolean publicVisible) {
        return MedicalService.builder()
                .id(id)
                .code(code)
                .nameVn(name)
                .descriptionVn("Xét nghiệm huyết học cơ bản.")
                .serviceType(MedicalServiceType.LAB)
                .status(status)
                .publicVisible(publicVisible)
                .displayOrder(1)
                .build();
    }
}
