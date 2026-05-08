package com.PrimeCare.PrimeCare.shared.pdf;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceItem;
import com.PrimeCare.PrimeCare.modules.billing.service.InvoicePdfService;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.notification.service.AppointmentPdfService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.service.ServiceResultPdfService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentGenerationTest {

    @Test
    void moneyInWordsUsesVietnameseDiacritics() {
        assertThat(PdfFormatters.numberToVietnameseWords(350_000L)).isEqualTo("Ba trăm năm mươi nghìn đồng");
        assertThat(PdfFormatters.numberToVietnameseWords(1_005_000L)).isEqualTo("Một triệu không trăm lẻ năm nghìn đồng");
    }

    @Test
    void appointmentPdfSupportsVietnameseInput() {
        Appointment appointment = Appointment.builder()
                .id(1L)
                .code("APT-001")
                .status(AppointmentStatus.CONFIRMED)
                .branch(branch())
                .specialty(specialty())
                .doctor(doctor())
                .visitDate(LocalDate.of(2026, 5, 3))
                .session(BranchSessionType.AM)
                .sourceType(AppointmentSourceType.PUBLIC_BOOKING)
                .etaStart(LocalTime.of(8, 30))
                .etaEnd(LocalTime.of(9, 0))
                .patientFullName("Nguyễn Thị Ánh Tuyết")
                .patientPhone("0909123456")
                .patientEmail("anh.tuyet@example.test")
                .patientDob(LocalDate.of(1992, 8, 12))
                .patientGender(Gender.FEMALE)
                .reasonForVisit("Viêm họng cấp không xác định")
                .patientNote("Bệnh nhân có tiền sử dị ứng kháng sinh nhóm Penicillin")
                .patient(patient())
                .build();

        byte[] pdf = new AppointmentPdfService().generateAppointmentPdf(appointment, null);

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(1_000);
    }

    @Test
    void invoicePdfSupportsVietnameseAndHospitalLayout() {
        Patient patient = patient();
        Encounter encounter = encounter(patient);
        ServiceOrder order = serviceOrder(encounter);
        Invoice invoice = Invoice.builder()
                .id(10L)
                .code("INV-001")
                .serviceOrder(order)
                .subtotalAmount(350_000L)
                .discountAmount(0L)
                .taxAmount(0L)
                .totalAmount(350_000L)
                .paymentStatus(PaymentStatus.UNPAID)
                .items(List.of(InvoiceItem.builder()
                        .nameSnapshot("Siêu âm ổ bụng tổng quát")
                        .quantity(1)
                        .unitPrice(350_000L)
                        .subtotalAmount(350_000L)
                        .taxAmount(0L)
                        .totalAmount(350_000L)
                        .build()))
                .build();

        byte[] pdf = new InvoicePdfService().generate(invoice, null, null, null, null, null);

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(1_000);
    }

    @Test
    void serviceResultPdfSupportsVietnameseLabRowsAndAbnormalFlags() {
        Patient patient = patient();
        Encounter encounter = encounter(patient);
        ServiceOrder order = serviceOrder(encounter);
        ServiceOrderItem item = serviceOrderItem(order);
        ServiceResult result = ServiceResult.builder()
                .id(20L)
                .serviceOrderItem(item)
                .templateCode(ServiceResultTemplateCode.LAB_TABLE)
                .status(ServiceResultStatus.VERIFIED)
                .reportTitle("Phiếu kết quả xét nghiệm")
                .fieldValuesJson("""
                        {"rows":[
                          {"parameter":"Bạch cầu","result":"12.4","referenceRange":"4.0 - 10.0","unit":"G/L","flag":"HIGH"},
                          {"parameter":"CRP","result":"8","referenceRange":"< 5","unit":"mg/L","flag":"ABNORMAL"}
                        ]}
                        """)
                .conclusionText("Phù hợp tình trạng viêm họng cấp không xác định.")
                .verifiedAt(LocalDateTime.of(2026, 5, 3, 10, 15))
                .build();

        byte[] pdf = new ServiceResultPdfService(new ObjectMapper()).generate(result);

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(1_000);
    }

    private Branch branch() {
        return Branch.builder()
                .id(1L)
                .nameVn("Phòng khám Đa khoa PrimeCare")
                .addressVn("123 Trần Hưng Đạo, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .phone("1900 1234")
                .build();
    }

    private Specialty specialty() {
        return Specialty.builder()
                .id(2L)
                .nameVn("Tai Mũi Họng")
                .build();
    }

    private DoctorProfile doctor() {
        return DoctorProfile.builder()
                .id(3L)
                .fullName("BS. Trần Minh Quân")
                .branch(branch())
                .build();
    }

    private Patient patient() {
        return Patient.builder()
                .id(4L)
                .code("BN-001")
                .fullName("Nguyễn Thị Ánh Tuyết")
                .phone("0909123456")
                .dob(LocalDate.of(1992, 8, 12))
                .gender(Gender.FEMALE)
                .address("123 Trần Hưng Đạo, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .insuranceNumber("HS4010123456789")
                .allergyNote("Bệnh nhân có tiền sử dị ứng kháng sinh nhóm Penicillin")
                .build();
    }

    private Encounter encounter(Patient patient) {
        return Encounter.builder()
                .id(5L)
                .code("ENC-001")
                .branch(branch())
                .specialty(specialty())
                .doctor(doctor())
                .patient(patient)
                .patientFullNameSnapshot(patient.getFullName())
                .patientPhoneSnapshot(patient.getPhone())
                .patientDobSnapshot(patient.getDob())
                .patientGenderSnapshot(patient.getGender())
                .preliminaryDiagnosis("Viêm họng cấp không xác định")
                .finalDiagnosis("Viêm họng cấp không xác định")
                .build();
    }

    private ServiceOrder serviceOrder(Encounter encounter) {
        ServiceOrder order = ServiceOrder.builder()
                .id(6L)
                .code("SO-001")
                .encounter(encounter)
                .branch(branch())
                .status(ServiceOrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .orderedAt(LocalDateTime.of(2026, 5, 3, 8, 45))
                .build();
        ServiceOrderItem item = serviceOrderItem(order);
        order.setItems(List.of(item));
        return order;
    }

    private ServiceOrderItem serviceOrderItem(ServiceOrder order) {
        return ServiceOrderItem.builder()
                .id(7L)
                .serviceOrder(order)
                .serviceCodeSnapshot("SA-OBUNG")
                .serviceNameVnSnapshot("Siêu âm ổ bụng tổng quát")
                .priceSnapshot(350_000L)
                .quantity(1)
                .lineTotalAmount(350_000L)
                .status(ServiceOrderItemStatus.PENDING_PAYMENT)
                .resultStatus(ServiceResultStatus.VERIFIED)
                .assignedDepartmentCode("LAB")
                .queuedAt(LocalDateTime.of(2026, 5, 3, 9, 0))
                .build();
    }
}
