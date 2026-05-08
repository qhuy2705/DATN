package com.PrimeCare.PrimeCare.modules.notification.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import com.PrimeCare.PrimeCare.shared.pdf.PdfLayout;
import com.PrimeCare.PrimeCare.shared.pdf.PdfTableBuilder;
import com.PrimeCare.PrimeCare.shared.pdf.PdfTheme;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class AppointmentPdfService {

    public byte[] generateAppointmentPdf(Appointment appointment, byte[] qrPng) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = PdfLayout.a4Document();
            PdfLayout.writer(document, out);
            document.open();

            PdfLayout.addHeader(document, buildHeader(appointment), qrPng);
            PdfLayout.addTitle(document, "Phiếu hẹn khám");
            addAppointmentSection(document, appointment);
            addPatientSection(document, appointment);
            addVisitContentSection(document, appointment);
            addGuidanceSection(document);
            addConfirmationSection(document);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Không tạo được PDF phiếu hẹn", ex);
        }
    }

    private PdfLayout.HeaderInfo buildHeader(Appointment appointment) {
        String branchName = appointment.getBranch() != null
                ? PdfFormatters.firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn())
                : PdfTheme.DEFAULT_FACILITY_NAME;
        String address = appointment.getBranch() != null
                ? PdfFormatters.firstNonBlank(appointment.getBranch().getAddressVn(), appointment.getBranch().getAddressEn())
                : null;
        String hotline = appointment.getBranch() != null ? appointment.getBranch().getPhone() : null;

        return PdfLayout.HeaderInfo.of(
                branchName,
                "Bộ phận tiếp nhận",
                address,
                hotline,
                "Mã lịch hẹn",
                appointment.getCode()
        );
    }

    private void addAppointmentSection(Document document, Appointment appointment) throws Exception {
        PdfLayout.addSectionTitle(document, "Thông tin lịch hẹn");
        PdfPTable table = PdfLayout.keyValueTable(new float[]{1.2f, 2f, 1.2f, 2f});

        PdfTableBuilder.addKeyValue(table, "Mã lịch hẹn", appointment.getCode());
        PdfTableBuilder.addKeyValue(table, "Trạng thái", PdfFormatters.appointmentStatusLabel(appointment.getStatus()));
        PdfTableBuilder.addKeyValue(table, "Ngày khám", PdfFormatters.formatDate(appointment.getVisitDate()));
        PdfTableBuilder.addKeyValue(table, "Giờ dự kiến", buildTimeWindow(appointment));
        PdfTableBuilder.addKeyValue(table, "Buổi khám", PdfFormatters.sessionLabel(appointment.getSession()));
        PdfTableBuilder.addKeyValue(table, "Số thứ tự dự kiến", appointment.getQueueNo() != null ? String.valueOf(appointment.getQueueNo()) : "Đang cập nhật");
        PdfTableBuilder.addKeyValue(table, "Chuyên khoa", appointment.getSpecialty() != null ? PdfFormatters.firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn()) : null);
        PdfTableBuilder.addKeyValue(table, "Bác sĩ", appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null);
        PdfTableBuilder.addKeyValue(table, "Cơ sở khám", appointment.getBranch() != null ? PdfFormatters.firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn()) : null);
        PdfTableBuilder.addKeyValue(table, "Phòng khám", "Đang cập nhật");

        PdfPCell address = PdfTableBuilder.cell("Địa chỉ cơ sở: " + PdfFormatters.safeText(
                appointment.getBranch() != null
                        ? PdfFormatters.firstNonBlank(appointment.getBranch().getAddressVn(), appointment.getBranch().getAddressEn())
                        : null
        ), PdfTheme.BODY);
        address.setColspan(4);
        table.addCell(address);

        document.add(table);
    }

    private void addPatientSection(Document document, Appointment appointment) throws Exception {
        PdfLayout.addSectionTitle(document, "Thông tin bệnh nhân");
        PdfPTable table = PdfLayout.keyValueTable(new float[]{1.2f, 2f, 1.2f, 2f});
        Patient patient = appointment.getPatient();

        PdfTableBuilder.addKeyValue(table, "Họ và tên", PdfFormatters.uppercaseVietnamese(appointment.getPatientFullName()));
        PdfTableBuilder.addKeyValue(table, "Mã bệnh nhân", patient != null ? patient.getCode() : "Đang cập nhật");
        PdfTableBuilder.addKeyValue(table, "Năm sinh / Tuổi", PdfFormatters.ageOrBirthYear(appointment.getPatientDob()));
        PdfTableBuilder.addKeyValue(table, "Giới tính", PdfFormatters.genderLabel(appointment.getPatientGender()));
        PdfTableBuilder.addKeyValue(table, "Số điện thoại", appointment.getPatientPhone());
        PdfTableBuilder.addKeyValue(table, "Email", PdfFormatters.safeText(appointment.getPatientEmail(), "Không ghi nhận"));

        PdfPCell address = PdfTableBuilder.cell("Địa chỉ: " + PdfFormatters.safeText(patient != null ? patient.getAddress() : null, "Chưa ghi nhận"), PdfTheme.BODY);
        address.setColspan(4);
        table.addCell(address);

        document.add(table);
    }

    private void addVisitContentSection(Document document, Appointment appointment) throws Exception {
        PdfLayout.addSectionTitle(document, "Nội dung khám");
        PdfPTable table = PdfLayout.keyValueTable(new float[]{1.2f, 2f, 1.2f, 2f});

        PdfTableBuilder.addKeyValue(table, "Loại khám", visitTypeLabel(appointment.getVisitType()));
        PdfTableBuilder.addKeyValue(table, "Mã check-in", appointment.getCode());

        PdfPCell reason = PdfTableBuilder.cell("Lý do khám: " + PdfFormatters.safeText(appointment.getReasonForVisit(), "Chưa ghi nhận"), PdfTheme.BODY);
        reason.setColspan(4);
        table.addCell(reason);

        if (appointment.getPatientNote() != null && !appointment.getPatientNote().isBlank()) {
            PdfPCell note = PdfTableBuilder.cell("Ghi chú bệnh nhân: " + PdfFormatters.safeText(appointment.getPatientNote()), PdfTheme.BODY);
            note.setColspan(4);
            table.addCell(note);
        }

        if (appointment.getInsuranceNote() != null && !appointment.getInsuranceNote().isBlank()) {
            PdfPCell insurance = PdfTableBuilder.cell("Ghi chú bảo hiểm: " + PdfFormatters.safeText(appointment.getInsuranceNote()), PdfTheme.BODY);
            insurance.setColspan(4);
            table.addCell(insurance);
        }

        document.add(table);
    }

    private void addGuidanceSection(Document document) throws Exception {
        PdfLayout.addSectionTitle(document, "Hướng dẫn đến khám");
        PdfLayout.addNoteBox(document, List.of(
                "Vui lòng đến trước giờ hẹn 10-15 phút để xác nhận thông tin và hoàn tất thủ tục tiếp nhận.",
                "Mang theo CCCD/thẻ BHYT, giấy chuyển tuyến hoặc hồ sơ y tế liên quan nếu có.",
                "Trường hợp cần đổi hoặc hủy lịch, vui lòng liên hệ hotline của cơ sở trước giờ khám.",
                "Vui lòng giữ mã lịch hẹn hoặc phiếu hẹn điện tử để nhân viên tiếp nhận đối chiếu nhanh."
        ));
    }

    private void addConfirmationSection(Document document) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(8f);
        table.setWidths(new float[]{1f, 1f});
        table.addCell(signatureCell("Bệnh nhân / Người đăng ký"));
        table.addCell(signatureCell("Bộ phận tiếp nhận PrimeCare"));
        document.add(table);
    }

    private PdfPCell signatureCell(String title) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setMinimumHeight(78f);
        Paragraph p = new Paragraph(title, PdfTheme.SECTION);
        p.setAlignment(Element.ALIGN_CENTER);
        Paragraph sign = new Paragraph("(Ký, ghi rõ họ tên nếu in giấy)", PdfTheme.NOTE);
        sign.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
        cell.addElement(sign);
        return cell;
    }

    private String buildTimeWindow(Appointment appointment) {
        String start = PdfFormatters.formatTime(appointment.getEtaStart());
        String end = PdfFormatters.formatTime(appointment.getEtaEnd());
        if (start == null && end == null) {
            return "Đang cập nhật";
        }
        if (start == null) {
            return end;
        }
        if (end == null) {
            return start;
        }
        return start + " - " + end;
    }

    private String visitTypeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "Khám bệnh";
        }
        return switch (value.trim().toUpperCase()) {
            case "NEW_PATIENT" -> "Khám mới";
            case "FOLLOW_UP" -> "Tái khám";
            case "CONSULTATION" -> "Tư vấn";
            default -> value.trim();
        };
    }
}
