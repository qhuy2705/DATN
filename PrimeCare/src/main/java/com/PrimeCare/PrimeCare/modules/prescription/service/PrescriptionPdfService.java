package com.PrimeCare.PrimeCare.modules.prescription.service;

import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionItem;
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
public class PrescriptionPdfService {

    public byte[] generate(Prescription prescription) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = PdfLayout.a4Document();
            PdfLayout.writer(document, out);
            document.open();

            Encounter encounter = prescription.getEncounter();
            Patient patient = encounter != null ? encounter.getPatient() : null;

            PdfLayout.addHeader(document, buildHeader(prescription, encounter), null);
            PdfLayout.addTitle(document, "Đơn thuốc ngoại trú");
            addPatientBlock(document, prescription, encounter, patient);
            addDiagnosisSection(document, encounter);
            addMedicationTable(document, prescription);
            addClinicalAdvice(document, prescription, encounter, patient);
            addSignatureSection(document, prescription);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Không thể tạo PDF đơn thuốc", ex);
        }
    }

    private PdfLayout.HeaderInfo buildHeader(Prescription prescription, Encounter encounter) {
        String facilityName = encounter != null && encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getNameVn(), encounter.getBranch().getNameEn())
                : PdfTheme.DEFAULT_FACILITY_NAME;
        String address = encounter != null && encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getAddressVn(), encounter.getBranch().getAddressEn())
                : null;
        String hotline = encounter != null && encounter.getBranch() != null ? encounter.getBranch().getPhone() : null;
        String department = encounter != null && encounter.getSpecialty() != null
                ? PdfFormatters.firstNonBlank(encounter.getSpecialty().getNameVn(), encounter.getSpecialty().getNameEn())
                : "Khoa khám bệnh";
        return new PdfLayout.HeaderInfo(
                facilityName,
                department,
                address,
                hotline,
                "Mã đơn thuốc",
                prescription.getCode(),
                "Mã lượt khám",
                encounter != null ? encounter.getCode() : null,
                "Ngày kê",
                PdfFormatters.formatDate(prescription.getIssuedDate())
        );
    }

    private void addPatientBlock(Document document, Prescription prescription, Encounter encounter, Patient patient) throws Exception {
        PdfLayout.addSectionTitle(document, "Thông tin bệnh nhân");
        PdfPTable table = PdfLayout.keyValueTable(new float[]{1.2f, 2f, 1.2f, 2f});

        PdfTableBuilder.addKeyValue(table, "Họ và tên", PdfFormatters.uppercaseVietnamese(encounter != null ? encounter.getPatientFullNameSnapshot() : patient != null ? patient.getFullName() : null));
        PdfTableBuilder.addKeyValue(table, "Trạng thái đơn", PdfFormatters.prescriptionStatusLabel(prescription.getStatus()));
        PdfTableBuilder.addKeyValue(table, "Năm sinh / Tuổi", encounter != null ? PdfFormatters.ageOrBirthYear(encounter.getPatientDobSnapshot()) : patient != null ? PdfFormatters.ageOrBirthYear(patient.getDob()) : null);
        PdfTableBuilder.addKeyValue(table, "Giới tính", encounter != null ? PdfFormatters.genderLabel(encounter.getPatientGenderSnapshot()) : patient != null ? PdfFormatters.genderLabel(patient.getGender()) : null);
        PdfTableBuilder.addKeyValue(table, "Số điện thoại", PdfFormatters.firstNonBlank(encounter != null ? encounter.getPatientPhoneSnapshot() : null, patient != null ? patient.getPhone() : null));
        PdfTableBuilder.addKeyValue(table, "Mã bệnh nhân", patient != null ? patient.getCode() : "Đang cập nhật");
        PdfTableBuilder.addKeyValue(table, "Số BHYT", patient != null ? patient.getInsuranceNumber() : "Không áp dụng");
        PdfTableBuilder.addKeyValue(table, "Cân nặng", encounter != null && encounter.getWeightKg() != null ? encounter.getWeightKg() + " kg" : "Chưa ghi nhận");

        PdfPCell address = PdfTableBuilder.cell("Địa chỉ: " + PdfFormatters.safeText(patient != null ? patient.getAddress() : null, "Chưa ghi nhận"), PdfTheme.BODY);
        address.setColspan(4);
        table.addCell(address);
        document.add(table);
    }

    private void addDiagnosisSection(Document document, Encounter encounter) throws Exception {
        PdfLayout.addSectionTitle(document, "Chẩn đoán và thông tin điều trị");
        PdfLayout.addNoteBox(document, List.of(
                "Chẩn đoán sơ bộ: " + PdfFormatters.safeText(encounter != null ? encounter.getPreliminaryDiagnosis() : null),
                "Chẩn đoán cuối: " + PdfFormatters.safeText(encounter != null ? encounter.getFinalDiagnosis() : null),
                "Kết luận: " + PdfFormatters.safeText(encounter != null ? encounter.getConclusion() : null)
        ));
    }

    private void addMedicationTable(Document document, Prescription prescription) throws Exception {
        PdfLayout.addSectionTitle(document, "Danh sách thuốc");
        PdfPTable table = new PdfPTable(new float[]{0.55f, 2.7f, 0.75f, 1.1f, 1.2f, 0.9f, 1.0f, 2.2f});
        table.setWidthPercentage(100f);
        table.setSpacingAfter(8f);
        table.addCell(PdfTableBuilder.headerCell("STT"));
        table.addCell(PdfTableBuilder.headerCell("Thuốc"));
        table.addCell(PdfTableBuilder.headerCell("Số lượng"));
        table.addCell(PdfTableBuilder.headerCell("Liều dùng"));
        table.addCell(PdfTableBuilder.headerCell("Tần suất"));
        table.addCell(PdfTableBuilder.headerCell("Số ngày"));
        table.addCell(PdfTableBuilder.headerCell("Đường dùng"));
        table.addCell(PdfTableBuilder.headerCell("Hướng dẫn"));

        List<PrescriptionItem> items = prescription.getItems() != null ? prescription.getItems() : List.of();
        if (items.isEmpty()) {
            PdfTableBuilder.addMergedNote(table, "Không có thuốc trong đơn.", 8);
        } else {
            int index = 1;
            for (PrescriptionItem item : items) {
                table.addCell(PdfTableBuilder.cell(String.valueOf(index++), PdfTheme.TABLE_BODY, Element.ALIGN_CENTER));
                table.addCell(PdfTableBuilder.cell(buildMedicationDisplay(item), PdfTheme.TABLE_BODY));
                table.addCell(PdfTableBuilder.cell(item.getQuantity() != null ? item.getQuantity().toString() : "1", PdfTheme.TABLE_BODY, Element.ALIGN_CENTER));
                table.addCell(PdfTableBuilder.cell(item.getDose(), PdfTheme.TABLE_BODY));
                table.addCell(PdfTableBuilder.cell(item.getFrequency(), PdfTheme.TABLE_BODY));
                table.addCell(PdfTableBuilder.cell(item.getDurationDays() != null ? item.getDurationDays().toString() : "Đang cập nhật", PdfTheme.TABLE_BODY, Element.ALIGN_CENTER));
                table.addCell(PdfTableBuilder.cell(item.getRoute(), PdfTheme.TABLE_BODY));
                table.addCell(PdfTableBuilder.cell(buildInstructionDisplay(item), PdfTheme.TABLE_BODY));
            }
        }
        document.add(table);
    }

    private void addClinicalAdvice(Document document, Prescription prescription, Encounter encounter, Patient patient) throws Exception {
        PdfLayout.addSectionTitle(document, "Lưu ý sử dụng thuốc");
        PdfLayout.addNoteBox(document, List.of(
                "Dị ứng / tiền sử: " + PdfFormatters.safeText(PdfFormatters.firstNonBlank(encounter != null ? encounter.getAllergySnapshot() : null, patient != null ? patient.getAllergyNote() : null)),
                "Bệnh nền: " + PdfFormatters.safeText(PdfFormatters.firstNonBlank(encounter != null ? encounter.getChronicDiseaseSnapshot() : null, patient != null ? patient.getChronicDiseaseNote() : null)),
                "Kế hoạch điều trị: " + PdfFormatters.safeText(encounter != null ? encounter.getTreatmentPlan() : null),
                "Ghi chú chung: " + PdfFormatters.safeText(prescription.getGeneralNote())
        ));

        if (encounter != null && encounter.getFollowUpDate() != null) {
            PdfLayout.addNoteBox(document, List.of("Hẹn tái khám: " + PdfFormatters.formatDate(encounter.getFollowUpDate())
                    + (encounter.getFollowUpNote() != null && !encounter.getFollowUpNote().isBlank() ? " - " + encounter.getFollowUpNote().trim() : "")));
        }
    }

    private void addSignatureSection(Document document, Prescription prescription) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{1f, 1f});
        table.setSpacingBefore(8f);
        table.addCell(signatureCell("Bệnh nhân / Người nhận thuốc", null));
        table.addCell(signatureCell("Bác sĩ kê đơn",
                prescription.getEncounter() != null && prescription.getEncounter().getDoctor() != null
                        ? prescription.getEncounter().getDoctor().getFullName()
                        : null));
        document.add(table);
    }

    private PdfPCell signatureCell(String title, String name) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setMinimumHeight(88f);
        Paragraph titleParagraph = new Paragraph(title, PdfTheme.SECTION);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        Paragraph sign = new Paragraph("(Ký, ghi rõ họ tên)", PdfTheme.NOTE);
        sign.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(titleParagraph);
        cell.addElement(sign);
        if (name != null && !name.isBlank()) {
            Paragraph nameParagraph = new Paragraph(name.trim(), PdfTheme.BODY);
            nameParagraph.setAlignment(Element.ALIGN_CENTER);
            nameParagraph.setSpacingBefore(40f);
            cell.addElement(nameParagraph);
        }
        return cell;
    }

    private String buildMedicationDisplay(PrescriptionItem item) {
        String value = PdfFormatters.safeText(item.getMedicationNameSnapshot());
        String strength = PdfFormatters.optionalText(item.getStrengthSnapshot());
        String form = PdfFormatters.optionalText(item.getDosageFormSnapshot());
        String code = PdfFormatters.optionalText(item.getMedicationCodeSnapshot());
        if (strength != null) {
            value += " - " + strength;
        }
        if (form != null) {
            value += " (" + form + ")";
        }
        if (code != null) {
            value += " [" + code + "]";
        }
        return value;
    }

    private String buildInstructionDisplay(PrescriptionItem item) {
        String instruction = PdfFormatters.optionalText(item.getInstruction());
        String unit = PdfFormatters.optionalText(item.getUnitSnapshot());
        if (unit != null && item.getQuantity() != null) {
            String quantityNote = "Cấp " + item.getQuantity() + " " + unit;
            return instruction == null ? quantityNote : instruction + ". " + quantityNote;
        }
        return instruction == null ? "Đang cập nhật" : instruction;
    }
}
