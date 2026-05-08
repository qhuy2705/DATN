package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.cms.faq.entity.Faq;
import com.PrimeCare.PrimeCare.modules.cms.faq.repository.FaqRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.modules.medical_service.repository.MedicalServiceRepository;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.FaqStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.blankToNull;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.compactText;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.safeLower;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.searchable;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.tagList;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.truncate;

@Service
@RequiredArgsConstructor
public class PublicAssistantKnowledgeProvider {

    private final MedicalServiceRepository medicalServiceRepository;
    private final SpecialtyRepository specialtyRepository;
    private final BranchRepository branchRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final FaqRepository faqRepository;
    private final PublicAssistantIntentDetector intentDetector;

    @Transactional(readOnly = true)
    public AssistantKnowledge loadKnowledge(boolean english) {
        List<MedicalService> publicServices = medicalServiceRepository
                .findByPublicVisibleTrueAndStatusOrderByDisplayOrderAscNameVnAsc(MedicalServiceStatus.ACTIVE);

        List<String> featuredServices = publicServices.stream()
                .limit(8)
                .map(service -> localizedServiceName(service, english))
                .filter(Objects::nonNull)
                .toList();

        List<Specialty> activeSpecialties = specialtyRepository.findAll().stream()
                .filter(item -> item.getStatus() == null || "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .sorted(Comparator.comparing(item -> safeLower(localizedSpecialtyName(item, english))))
                .limit(12)
                .toList();

        List<String> specialties = activeSpecialties.stream()
                .map(item -> localizedSpecialtyName(item, english))
                .filter(Objects::nonNull)
                .toList();

        List<Branch> activeBranches = branchRepository.findAll().stream()
                .filter(item -> item.getStatus() == null || item.getStatus() == BranchStatus.ACTIVE)
                .sorted(Comparator.comparing(item -> safeLower(localizedBranchName(item, english))))
                .limit(8)
                .toList();

        List<String> branches = activeBranches.stream()
                .map(item -> localizedBranchName(item, english))
                .filter(Objects::nonNull)
                .toList();

        List<DoctorProfile> activeDoctors = doctorProfileRepository
                .search(null, null, null, DoctorStatus.ACTIVE, PageRequest.of(0, 8))
                .getContent();

        List<KnowledgeSnippet> snippets = new ArrayList<>();
        snippets.addAll(buildStaticWorkflowSnippets(english));
        snippets.addAll(buildStaticFaqSnippets(english));
        snippets.addAll(loadFaqSnippets(english));
        snippets.addAll(loadServiceSnippets(publicServices, english));
        snippets.addAll(loadSpecialtySnippets(activeSpecialties, english));
        snippets.addAll(loadDoctorSnippets(activeDoctors, english));
        snippets.addAll(loadBranchSnippets(activeBranches, english));

        return new AssistantKnowledge(featuredServices, specialties, branches, deduplicateSnippets(snippets));
    }

    private List<KnowledgeSnippet> buildStaticWorkflowSnippets(boolean english) {
        List<KnowledgeSnippet> snippets = new ArrayList<>();
        snippets.add(new KnowledgeSnippet(
                "workflow-booking",
                "WORKFLOW_BOOKING",
                english ? "PrimeCare website booking flow" : "Flow đặt lịch trên website PrimeCare",
                english
                        ? "Booking on the website follows 4 steps: (1) choose branch, specialty and doctor; (2) choose visit date, AM or PM session, then choose an available slot in real time; (3) fill in patient information including full name, phone, optional email, date of birth, gender and optional note; (4) confirm the request and keep the appointment code for later lookup."
                        : "Đặt lịch trên website gồm 4 bước: (1) chọn cơ sở, chuyên khoa và bác sĩ; (2) chọn ngày khám, buổi sáng hoặc chiều, rồi chọn khung giờ còn trống theo thời gian thực; (3) điền thông tin bệnh nhân gồm họ tên, số điện thoại, email nếu có, ngày sinh, giới tính và ghi chú nếu cần; (4) xác nhận gửi yêu cầu và lưu mã lịch hẹn để tra cứu lại.",
                AssistantIntent.BOOKING,
                "/booking",
                List.of("dat lich", "booking", "4 buoc", "appointment", "slot", "visit date", "session")
        ));
        snippets.add(new KnowledgeSnippet(
                "workflow-availability",
                "WORKFLOW_BOOKING",
                english ? "How availability works on PrimeCare" : "Cách xem lịch trống trên PrimeCare",
                english
                        ? "The availability page only shows open slots after the user chooses branch, specialty, doctor, visit date and session. Each card shows doctor, specialty, branch, date and slot range, and the user can move directly into booking from that slot."
                        : "Trang lịch trống chỉ hiển thị các khung giờ còn mở sau khi người dùng chọn cơ sở, chuyên khoa, bác sĩ, ngày khám và buổi khám. Mỗi thẻ hiển thị bác sĩ, chuyên khoa, cơ sở, ngày và khoảng giờ, sau đó người dùng có thể chuyển thẳng sang đặt lịch từ khung đó.",
                AssistantIntent.BOOKING,
                "/availability",
                List.of("lich trong", "availability", "slot", "khung gio", "ngay kham", "buoi kham")
        ));
        snippets.add(new KnowledgeSnippet(
                "workflow-lookup-appointment",
                "WORKFLOW_LOOKUP",
                english ? "How appointment lookup works" : "Cách tra cứu phiếu hẹn",
                english
                        ? "On the lookup page, choose appointment lookup, enter the appointment code, request OTP, verify the 6-digit OTP, then preview the appointment information and open the appointment PDF if available."
                        : "Ở trang tra cứu, chọn tra cứu phiếu hẹn, nhập mã lịch hẹn, gửi OTP, xác thực OTP 6 số, rồi xem nhanh thông tin phiếu hẹn và mở PDF phiếu hẹn nếu có.",
                AssistantIntent.RESULT_LOOKUP,
                "/appointments/lookup",
                List.of("tra cuu phieu hen", "appointment lookup", "otp", "pdf", "ma lich hen")
        ));
        snippets.add(new KnowledgeSnippet(
                "workflow-lookup-result",
                "WORKFLOW_LOOKUP",
                english ? "How result lookup works" : "Cách tra cứu kết quả",
                english
                        ? "On the lookup page, choose result lookup, enter the appointment or encounter code, request OTP to the registered email, verify the 6-digit OTP, then review the result summary and open the PDF report. Public result lookup only works after the doctor has completed the encounter and the released results have been verified."
                        : "Ở trang tra cứu, chọn tra cứu kết quả, nhập mã lịch hẹn hoặc mã hồ sơ, gửi OTP về email đã đăng ký, xác thực OTP 6 số, rồi xem tóm tắt kết quả và mở PDF báo cáo. Tra cứu công khai chỉ hoạt động sau khi bác sĩ đã hoàn tất lần khám.",
                AssistantIntent.RESULT_LOOKUP,
                "/appointments/lookup",
                List.of("tra cuu ket qua", "result lookup", "otp", "pdf", "ma ho so", "encounter")
        ));
        snippets.add(new KnowledgeSnippet(
                "workflow-preparation",
                "WORKFLOW_PREPARATION",
                english ? "General preparation guidance" : "Hướng dẫn chuẩn bị chung",
                english
                        ? "Preparation depends on the service. Public guidance currently highlights examples such as blood tests that may require fasting, abdominal ultrasound that may require drinking water, and endoscopy that usually needs a dedicated checklist from PrimeCare before the appointment."
                        : "Chuẩn bị trước khám phụ thuộc từng dịch vụ. Hướng dẫn công khai hiện nêu các ví dụ như xét nghiệm máu có thể cần nhịn ăn, siêu âm bụng có thể cần uống nước, còn nội soi thường cần checklist riêng từ PrimeCare trước buổi hẹn.",
                AssistantIntent.PREPARATION,
                "/medical-services",
                List.of("chuan bi", "nhin an", "sieu am", "noi soi", "xet nghiem")
        ));
        snippets.add(new KnowledgeSnippet(
                "workflow-contact",
                "CONTACT",
                english ? "PrimeCare contact information" : "Thông tin liên hệ PrimeCare",
                english
                        ? "The public contact page shows hotline 1900 1234, email info@primecare.vn, head office at 88 Nguyen Du, District 1, Ho Chi Minh City, and working hours Monday to Saturday 07:00-20:00, Sunday 08:00-17:00."
                        : "Trang liên hệ công khai đang hiển thị hotline 1900 1234, email info@primecare.vn, trụ sở chính tại 88 Nguyễn Du, Quận 1, TP.HCM và giờ làm việc Thứ 2 - Thứ 7 từ 07:00 - 20:00, Chủ nhật từ 08:00 - 17:00.",
                AssistantIntent.FAQ,
                "/contact",
                List.of("hotline", "lien he", "contact", "email", "gio lam viec", "dia chi")
        ));
        return snippets;
    }

    private List<KnowledgeSnippet> buildStaticFaqSnippets(boolean english) {
        List<KnowledgeSnippet> snippets = new ArrayList<>();
        snippets.add(new KnowledgeSnippet(
                "faq-booking-channel",
                "FAQ",
                english ? "How to book an appointment" : "Làm thế nào để đặt lịch khám",
                english
                        ? "You can book directly on the website, call the hotline 1900 1234, or come to the reception desk at a PrimeCare branch."
                        : "Bạn có thể đặt lịch trực tuyến qua website, gọi hotline 1900 1234, hoặc đến trực tiếp quầy lễ tân tại các cơ sở PrimeCare.",
                AssistantIntent.BOOKING,
                "/faq",
                List.of("dat lich", "website", "hotline", "le tan")
        ));
        snippets.add(new KnowledgeSnippet(
                "faq-book-before",
                "FAQ",
                english ? "Do I need to book in advance" : "Tôi có cần đặt lịch trước không",
                english
                        ? "PrimeCare recommends booking in advance to keep a convenient time slot, but walk-in visits are still possible."
                        : "PrimeCare khuyến khích đặt lịch trước để giữ khung giờ thuận tiện nhất, tuy nhiên bạn vẫn có thể đến khám trực tiếp theo hình thức walk-in.",
                AssistantIntent.FAQ,
                "/faq",
                List.of("dat lich truoc", "walk in", "truoc")
        ));
        snippets.add(new KnowledgeSnippet(
                "faq-price",
                "FAQ",
                english ? "How much does the visit cost" : "Chi phí khám bệnh là bao nhiêu",
                english
                        ? "Cost depends on the specialty and service. Users can review the public medical services page or contact PrimeCare for detailed advice."
                        : "Chi phí phụ thuộc vào chuyên khoa và loại dịch vụ. Người dùng có thể xem trang Dịch vụ y tế công khai hoặc liên hệ PrimeCare để được tư vấn chi tiết hơn.",
                AssistantIntent.FAQ,
                "/medical-services",
                List.of("gia", "chi phi", "price", "service cost")
        ));
        snippets.add(new KnowledgeSnippet(
                "faq-insurance",
                "FAQ",
                english ? "Does PrimeCare accept insurance" : "PrimeCare có nhận bảo hiểm không",
                english
                        ? "PrimeCare states that it works with multiple insurance companies and users should bring their insurance card when coming to the visit."
                        : "PrimeCare hiện nêu rằng có liên kết với nhiều công ty bảo hiểm và người dùng nên mang theo thẻ bảo hiểm khi đến khám.",
                AssistantIntent.FAQ,
                "/faq",
                List.of("bao hiem", "insurance", "the bao hiem")
        ));
        snippets.add(new KnowledgeSnippet(
                "faq-reschedule",
                "FAQ",
                english ? "Can I cancel or reschedule" : "Tôi có thể hủy hoặc đổi lịch không",
                english
                        ? "PrimeCare's public FAQ says users can cancel or reschedule at least 4 hours before the visit via hotline or reception support."
                        : "FAQ công khai của PrimeCare nêu rằng bạn có thể hủy hoặc đổi lịch trước giờ khám ít nhất 4 tiếng qua hotline hoặc quầy lễ tân hỗ trợ.",
                AssistantIntent.FAQ,
                "/faq",
                List.of("huy lich", "doi lich", "reschedule", "cancel")
        ));
        snippets.add(new KnowledgeSnippet(
                "faq-result-turnaround",
                "FAQ",
                english ? "How long do results take" : "Kết quả có trong bao lâu",
                english
                        ? "Turnaround depends on the service. Public FAQ examples say many results are available in 1-3 working days, while some rapid tests can be returned the same day."
                        : "Thời gian có kết quả phụ thuộc loại dịch vụ. Ví dụ trong FAQ công khai cho biết nhiều kết quả thường có trong 1-3 ngày làm việc, còn một số xét nghiệm nhanh có thể có trong ngày.",
                AssistantIntent.RESULT_LOOKUP,
                "/faq",
                List.of("bao lau", "turnaround", "ket qua", "1-3 ngay")
        ));
        return snippets;
    }

    private List<KnowledgeSnippet> loadFaqSnippets(boolean english) {
        return faqRepository.findTop12ByStatusOrderByCreatedAtDesc(FaqStatus.PUBLISHED).stream()
                .map(faq -> new KnowledgeSnippet(
                        "faq-db-" + faq.getId(),
                        "FAQ",
                        localizedFaqQuestion(faq, english),
                        localizedFaqAnswer(faq, english),
                        classifyFaqIntent(localizedFaqQuestion(faq, english), AssistantIntent.FAQ),
                        "/faq",
                        tagList(blankToNull(faq.getCategory()), localizedFaqQuestion(faq, english))
                ))
                .filter(snippet -> blankToNull(snippet.title()) != null && blankToNull(snippet.content()) != null)
                .toList();
    }

    private List<KnowledgeSnippet> loadServiceSnippets(List<MedicalService> publicServices, boolean english) {
        return publicServices.stream()
                .limit(16)
                .map(service -> {
                    String name = localizedServiceName(service, english);
                    String description = localizedServiceDescription(service, english);
                    List<String> details = new ArrayList<>();
                    if (description != null) {
                        details.add(description);
                    }
                    if (service.getBasePrice() != null && service.getBasePrice() > 0) {
                        details.add(english
                                ? "Base public price: " + formatCurrency(service.getBasePrice(), true)
                                : "Giá công khai tham khảo: " + formatCurrency(service.getBasePrice(), false));
                    }
                    if (service.getDefaultTurnaroundMinutes() != null && service.getDefaultTurnaroundMinutes() > 0) {
                        details.add(english
                                ? "Default turnaround: around " + service.getDefaultTurnaroundMinutes() + " minutes"
                                : "Thời gian trả kết quả mặc định khoảng " + service.getDefaultTurnaroundMinutes() + " phút");
                    }
                    if (service.getResultReportTitle() != null && !service.getResultReportTitle().isBlank()) {
                        details.add(english
                                ? "Report title: " + service.getResultReportTitle().trim()
                                : "Tiêu đề phiếu kết quả: " + service.getResultReportTitle().trim());
                    }
                    return new KnowledgeSnippet(
                            "service-" + service.getId(),
                            "SERVICE",
                            name,
                            String.join(english ? ". " : ". ", details),
                            service.getRequiresFileResult() != null && service.getRequiresFileResult() ? AssistantIntent.RESULT_LOOKUP : AssistantIntent.FAQ,
                            "/medical-services",
                            tagList(service.getCode(), name, String.valueOf(service.getServiceType()), service.getDepartmentCode())
                    );
                })
                .filter(snippet -> blankToNull(snippet.title()) != null && blankToNull(snippet.content()) != null)
                .toList();
    }

    private List<KnowledgeSnippet> loadSpecialtySnippets(List<Specialty> specialties, boolean english) {
        return specialties.stream()
                .limit(12)
                .map(specialty -> {
                    String name = localizedSpecialtyName(specialty, english);
                    String description = compactText(english ? blankToNull(specialty.getDescriptionEn()) : blankToNull(specialty.getDescriptionVn()), 420);
                    List<String> details = new ArrayList<>();
                    if (description != null) {
                        details.add(description);
                    }
                    if (specialty.getDefaultSlotMinutes() != null && specialty.getDefaultSlotMinutes() > 0) {
                        details.add(english
                                ? "Default slot length: " + specialty.getDefaultSlotMinutes() + " minutes"
                                : "Thời lượng slot mặc định: " + specialty.getDefaultSlotMinutes() + " phút");
                    }
                    return new KnowledgeSnippet(
                            "specialty-" + specialty.getId(),
                            "SPECIALTY",
                            name,
                            String.join(english ? ". " : ". ", details),
                            AssistantIntent.SPECIALTY_GUIDANCE,
                            "/specialties",
                            tagList(name, specialty.getCode())
                    );
                })
                .filter(snippet -> blankToNull(snippet.title()) != null && blankToNull(snippet.content()) != null)
                .toList();
    }

    private List<KnowledgeSnippet> loadDoctorSnippets(List<DoctorProfile> doctors, boolean english) {
        return doctors.stream()
                .map(doctor -> {
                    String title = doctor.getFullName();
                    String branchName = localizedBranchName(doctor.getBranch(), english);
                    String displayTitle = english ? blankToNull(doctor.getDisplayTitleEn()) : blankToNull(doctor.getDisplayTitleVn());
                    if (displayTitle == null) {
                        displayTitle = blankToNull(doctor.getDisplayTitleVn());
                    }
                    String expertise = compactText(english ? blankToNull(doctor.getExpertiseEn()) : blankToNull(doctor.getExpertiseVn()), 260);
                    String specialtyNames = doctor.getDoctorSpecialties() == null ? null : doctor.getDoctorSpecialties().stream()
                            .map(item -> localizedSpecialtyName(item.getSpecialty(), english))
                            .filter(Objects::nonNull)
                            .distinct()
                            .limit(4)
                            .collect(Collectors.joining(english ? ", " : ", "));

                    List<String> details = new ArrayList<>();
                    if (displayTitle != null) {
                        details.add(displayTitle);
                    }
                    if (specialtyNames != null && !specialtyNames.isBlank()) {
                        details.add(english ? "Specialties: " + specialtyNames : "Chuyên khoa: " + specialtyNames);
                    }
                    if (branchName != null) {
                        details.add(english ? "Branch: " + branchName : "Cơ sở: " + branchName);
                    }
                    if (doctor.getYearsExp() != null && doctor.getYearsExp() > 0) {
                        details.add(english ? "Experience: " + doctor.getYearsExp() + " years" : "Kinh nghiệm: " + doctor.getYearsExp() + " năm");
                    }
                    if (expertise != null) {
                        details.add(expertise);
                    }
                    return new KnowledgeSnippet(
                            "doctor-" + doctor.getId(),
                            "DOCTOR",
                            title,
                            String.join(english ? ". " : ". ", details),
                            AssistantIntent.SPECIALTY_GUIDANCE,
                            "/doctors",
                            tagList(title, displayTitle, specialtyNames, branchName)
                    );
                })
                .filter(snippet -> blankToNull(snippet.title()) != null && blankToNull(snippet.content()) != null)
                .toList();
    }

    private List<KnowledgeSnippet> loadBranchSnippets(List<Branch> branches, boolean english) {
        return branches.stream()
                .map(branch -> {
                    String name = localizedBranchName(branch, english);
                    List<String> details = new ArrayList<>();
                    String address = english ? blankToNull(branch.getAddressEn()) : blankToNull(branch.getAddressVn());
                    if (address == null) {
                        address = blankToNull(branch.getAddressVn());
                    }
                    if (address != null) {
                        details.add(english ? "Address: " + address : "Địa chỉ: " + address);
                    }
                    if (blankToNull(branch.getPhone()) != null) {
                        details.add(english ? "Phone: " + branch.getPhone().trim() : "Điện thoại: " + branch.getPhone().trim());
                    }
                    if (blankToNull(branch.getEmail()) != null) {
                        details.add("Email: " + branch.getEmail().trim());
                    }
                    String description = compactText(english ? blankToNull(branch.getDescriptionEn()) : blankToNull(branch.getDescriptionVn()), 260);
                    if (description != null) {
                        details.add(description);
                    }
                    return new KnowledgeSnippet(
                            "branch-" + branch.getId(),
                            "BRANCH",
                            name,
                            String.join(english ? ". " : ". ", details),
                            AssistantIntent.FAQ,
                            "/branches",
                            tagList(name, address, branch.getPhone(), branch.getEmail())
                    );
                })
                .filter(snippet -> blankToNull(snippet.title()) != null && blankToNull(snippet.content()) != null)
                .toList();
    }

    private List<KnowledgeSnippet> deduplicateSnippets(List<KnowledgeSnippet> snippets) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<KnowledgeSnippet> deduped = new ArrayList<>();
        for (KnowledgeSnippet snippet : snippets) {
            if (snippet == null || blankToNull(snippet.title()) == null || blankToNull(snippet.content()) == null) {
                continue;
            }
            String key = searchable(snippet.title() + " " + truncate(snippet.content(), 160));
            if (seen.add(key)) {
                deduped.add(snippet);
            }
        }
        return deduped;
    }

    private String localizedFaqQuestion(Faq faq, boolean english) {
        if (faq == null) {
            return null;
        }
        String localized = english ? blankToNull(faq.getQuestionEn()) : blankToNull(faq.getQuestionVn());
        return localized != null ? localized : blankToNull(faq.getQuestionVn());
    }

    private String localizedFaqAnswer(Faq faq, boolean english) {
        if (faq == null) {
            return null;
        }
        String localized = english ? blankToNull(faq.getAnswerEn()) : blankToNull(faq.getAnswerVn());
        return compactText(localized != null ? localized : blankToNull(faq.getAnswerVn()), 500);
    }

    private AssistantIntent classifyFaqIntent(String question, AssistantIntent defaultIntent) {
        if (question == null || question.isBlank()) {
            return defaultIntent;
        }
        return intentDetector.detect(question, "/faq");
    }

    private String localizedServiceName(MedicalService service, boolean english) {
        if (service == null) {
            return null;
        }
        String localized = english ? blankToNull(service.getNameEn()) : blankToNull(service.getNameVn());
        return localized != null ? localized : blankToNull(service.getNameVn()) != null ? service.getNameVn() : service.getCode();
    }

    private String localizedServiceDescription(MedicalService service, boolean english) {
        if (service == null) {
            return null;
        }
        String localized = english ? blankToNull(service.getDescriptionEn()) : blankToNull(service.getDescriptionVn());
        return compactText(localized != null ? localized : blankToNull(service.getDescriptionVn()), 320);
    }

    private String localizedSpecialtyName(Specialty specialty, boolean english) {
        if (specialty == null) {
            return null;
        }
        String localized = english ? blankToNull(specialty.getNameEn()) : blankToNull(specialty.getNameVn());
        return localized != null ? localized : blankToNull(specialty.getNameVn());
    }

    private String localizedBranchName(Branch branch, boolean english) {
        if (branch == null) {
            return null;
        }
        String localized = english ? blankToNull(branch.getNameEn()) : blankToNull(branch.getNameVn());
        return localized != null ? localized : blankToNull(branch.getNameVn());
    }

    private String formatCurrency(Long amount, boolean english) {
        if (amount == null) {
            return english ? "contact PrimeCare" : "liên hệ PrimeCare";
        }
        NumberFormat format = NumberFormat.getInstance(new Locale("vi", "VN"));
        return format.format(amount) + (english ? " VND" : " VNĐ");
    }
}
