package com.PrimeCare.PrimeCare.modules.service_result.service;

import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import com.PrimeCare.PrimeCare.shared.pdf.PdfLayout;
import com.PrimeCare.PrimeCare.shared.pdf.PdfTableBuilder;
import com.PrimeCare.PrimeCare.shared.pdf.PdfTheme;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceResultPdfService {

    private final ObjectMapper objectMapper;

    public byte[] generate(ServiceResult result) {
        try {
            ServiceOrderItem item = result.getServiceOrderItem();
            ServiceOrder order = item.getServiceOrder();
            Encounter encounter = order.getEncounter();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = PdfLayout.a4Document();
            PdfLayout.writer(document, out);
            document.open();

            PdfLayout.addHeader(document, buildHeader(result, encounter, item), null);
            PdfLayout.addTitle(document, titleFor(result, item));
            addResultAdministrativeBlock(document, result, encounter, order, item);
            renderResultContent(document, result);
            renderAttachments(document, result);
            addResultFooter(document, result, encounter);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Không tạo được PDF kết quả cận lâm sàng", ex);
        }
    }

    public byte[] generatePublicEncounterResult(Encounter encounter, List<ServiceResult> results) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = PdfLayout.a4Document();
            PdfLayout.writer(document, out);
            document.open();

            PdfLayout.addHeader(document, buildHeader(encounter), null);
            PdfLayout.addTitle(document, "Phiếu kết quả khám / cận lâm sàng");
            addEncounterAdministrativeBlock(document, encounter);
            addEncounterConclusion(document, encounter);

            if (results == null || results.isEmpty()) {
                PdfLayout.addSectionTitle(document, "Kết quả cận lâm sàng");
                PdfLayout.addNoteBox(document, List.of("Chưa có kết quả dịch vụ được công bố."));
            } else {
                int index = 1;
                for (ServiceResult result : results) {
                    ServiceOrderItem item = result.getServiceOrderItem();
                    PdfLayout.addSectionTitle(document, index++ + ". " + titleFor(result, item));
                    addResultAdministrativeBlock(document, result, encounter, item.getServiceOrder(), item);
                    renderResultContent(document, result);
                    renderAttachments(document, result);
                }
            }

            addPublicFooter(document, encounter);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Không tạo được PDF kết quả", ex);
        }
    }

    private PdfLayout.HeaderInfo buildHeader(ServiceResult result, Encounter encounter, ServiceOrderItem item) {
        String department = departmentName(item);
        String facilityName = encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getNameVn(), encounter.getBranch().getNameEn())
                : PdfTheme.DEFAULT_FACILITY_NAME;
        String address = encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getAddressVn(), encounter.getBranch().getAddressEn())
                : null;
        String hotline = encounter.getBranch() != null ? encounter.getBranch().getPhone() : null;
        return new PdfLayout.HeaderInfo(
                facilityName,
                department,
                address,
                hotline,
                "Mã phiếu kết quả",
                item.getServiceOrder() != null ? item.getServiceOrder().getCode() + "-" + item.getId() : String.valueOf(result.getId()),
                "Mã lượt khám",
                encounter.getCode(),
                "Ngày in",
                PdfFormatters.formatDateTime(LocalDateTime.now())
        );
    }

    private PdfLayout.HeaderInfo buildHeader(Encounter encounter) {
        String facilityName = encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getNameVn(), encounter.getBranch().getNameEn())
                : PdfTheme.DEFAULT_FACILITY_NAME;
        String address = encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getAddressVn(), encounter.getBranch().getAddressEn())
                : null;
        String hotline = encounter.getBranch() != null ? encounter.getBranch().getPhone() : null;
        return new PdfLayout.HeaderInfo(
                facilityName,
                "Phòng khám / Cận lâm sàng",
                address,
                hotline,
                "Mã lượt khám",
                encounter.getCode(),
                "Mã lịch hẹn",
                encounter.getAppointment() != null ? encounter.getAppointment().getCode() : null,
                "Ngày in",
                PdfFormatters.formatDateTime(LocalDateTime.now())
        );
    }

    private void addResultAdministrativeBlock(Document document,
                                              ServiceResult result,
                                              Encounter encounter,
                                              ServiceOrder order,
                                              ServiceOrderItem item) throws Exception {
        Patient patient = encounter.getPatient();
        PdfLayout.addSectionTitle(document, "Thông tin bệnh nhân và chỉ định");
        PdfPTable table = PdfLayout.keyValueTable(new float[]{1.25f, 2f, 1.25f, 2f});

        PdfTableBuilder.addKeyValue(table, "Họ và tên bệnh nhân", PdfFormatters.uppercaseVietnamese(encounter.getPatientFullNameSnapshot()));
        PdfTableBuilder.addKeyValue(table, "Tuổi / Năm sinh", PdfFormatters.ageOrBirthYear(encounter.getPatientDobSnapshot()));
        PdfTableBuilder.addKeyValue(table, "Giới tính", PdfFormatters.genderLabel(encounter.getPatientGenderSnapshot()));
        PdfTableBuilder.addKeyValue(table, "Mã bệnh nhân", patient != null ? patient.getCode() : "Đang cập nhật");
        PdfTableBuilder.addKeyValue(table, "Địa chỉ", patient != null ? patient.getAddress() : null);
        PdfTableBuilder.addKeyValue(table, "Khoa/phòng yêu cầu", encounter.getSpecialty() != null ? PdfFormatters.firstNonBlank(encounter.getSpecialty().getNameVn(), encounter.getSpecialty().getNameEn()) : null);
        PdfTableBuilder.addKeyValue(table, "Bác sĩ chỉ định", encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null);
        PdfTableBuilder.addKeyValue(table, "Trạng thái kết quả", PdfFormatters.serviceResultStatusLabel(result.getStatus()));
        PdfTableBuilder.addKeyValue(table, "Dịch vụ chỉ định", serviceName(item));
        PdfTableBuilder.addKeyValue(table, "Mã chỉ định", order != null ? order.getCode() : null);
        PdfTableBuilder.addKeyValue(table, "Ngày giờ chỉ định", order != null ? PdfFormatters.formatDateTime(order.getOrderedAt()) : null);
        PdfTableBuilder.addKeyValue(table, "Ngày giờ lấy mẫu", PdfFormatters.formatDateTime(item.getQueuedAt()));
        PdfTableBuilder.addKeyValue(table, "Ngày giờ thực hiện", PdfFormatters.formatDateTime(result.getPerformedAt()));
        PdfTableBuilder.addKeyValue(table, "Ngày giờ trả kết quả", PdfFormatters.formatDateTime(result.getVerifiedAt()));
        PdfTableBuilder.addKeyValue(table, "Mã lượt khám", encounter.getCode());
        PdfTableBuilder.addKeyValue(table, "Người thực hiện", result.getPerformedByUser() != null ? PdfFormatters.userDisplayName(result.getPerformedByUser()) : "Đang cập nhật");

        PdfPCell diagnosis = PdfTableBuilder.cell("Chẩn đoán sơ bộ / Lý do khám: "
                + PdfFormatters.safeText(PdfFormatters.firstNonBlank(encounter.getPreliminaryDiagnosis(), encounter.getChiefComplaint(), encounter.getIntakeReasonForVisit())), PdfTheme.BODY);
        diagnosis.setColspan(4);
        table.addCell(diagnosis);

        document.add(table);
    }

    private void addEncounterAdministrativeBlock(Document document, Encounter encounter) throws Exception {
        Patient patient = encounter.getPatient();
        PdfLayout.addSectionTitle(document, "Thông tin bệnh nhân");
        PdfPTable table = PdfLayout.keyValueTable(new float[]{1.25f, 2f, 1.25f, 2f});

        PdfTableBuilder.addKeyValue(table, "Họ và tên bệnh nhân", PdfFormatters.uppercaseVietnamese(encounter.getPatientFullNameSnapshot()));
        PdfTableBuilder.addKeyValue(table, "Tuổi / Năm sinh", PdfFormatters.ageOrBirthYear(encounter.getPatientDobSnapshot()));
        PdfTableBuilder.addKeyValue(table, "Giới tính", PdfFormatters.genderLabel(encounter.getPatientGenderSnapshot()));
        PdfTableBuilder.addKeyValue(table, "Mã bệnh nhân", patient != null ? patient.getCode() : "Đang cập nhật");
        PdfTableBuilder.addKeyValue(table, "Mã lượt khám", encounter.getCode());
        PdfTableBuilder.addKeyValue(table, "Mã lịch hẹn", encounter.getAppointment() != null ? encounter.getAppointment().getCode() : null);
        PdfTableBuilder.addKeyValue(table, "Bác sĩ phụ trách", encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null);
        PdfTableBuilder.addKeyValue(table, "Chuyên khoa", encounter.getSpecialty() != null ? PdfFormatters.firstNonBlank(encounter.getSpecialty().getNameVn(), encounter.getSpecialty().getNameEn()) : null);

        PdfPCell reason = PdfTableBuilder.cell("Chẩn đoán sơ bộ / Lý do khám: "
                + PdfFormatters.safeText(PdfFormatters.firstNonBlank(encounter.getPreliminaryDiagnosis(), encounter.getChiefComplaint(), encounter.getIntakeReasonForVisit())), PdfTheme.BODY);
        reason.setColspan(4);
        table.addCell(reason);

        document.add(table);
    }

    private void addEncounterConclusion(Document document, Encounter encounter) throws Exception {
        if (PdfFormatters.firstNonBlank(encounter.getFinalDiagnosis(), encounter.getConclusion(), encounter.getTreatmentPlan()) == null) {
            return;
        }
        PdfLayout.addSectionTitle(document, "Kết luận khám");
        addNarrativeSection(document, "Chẩn đoán cuối", encounter.getFinalDiagnosis(), false);
        addNarrativeSection(document, "Kết luận", encounter.getConclusion(), true);
        addNarrativeSection(document, "Kế hoạch điều trị", encounter.getTreatmentPlan(), false);
    }

    private void renderResultContent(Document document, ServiceResult result) throws Exception {
        ServiceResultTemplateCode templateCode = result.getTemplateCode() != null
                ? result.getTemplateCode()
                : ServiceResultTemplateCode.GENERIC_NARRATIVE;
        JsonNode fieldValues = parseJson(result.getFieldValuesJson());

        switch (templateCode) {
            case LAB_TABLE -> renderLabTable(document, result, fieldValues);
            case IMAGING_REPORT -> renderImagingReport(document, result, fieldValues);
            case PROCEDURE_REPORT -> renderProcedureReport(document, result, fieldValues);
            case GENERIC_NARRATIVE -> renderGenericReport(document, result, fieldValues);
        }
    }

    private void renderLabTable(Document document, ServiceResult result, JsonNode fieldValues) throws Exception {
        PdfLayout.addSectionTitle(document, "Kết quả xét nghiệm");
        addNarrativeSection(document, "Mẫu bệnh phẩm", text(fieldValues, "specimen"), false);
        addNarrativeSection(document, "Thông tin lâm sàng", text(fieldValues, "clinicalInfo"), false);
        addNarrativeSection(document, "Thiết bị", text(fieldValues, "deviceName"), false);

        JsonNode rows = fieldValues.path("rows");
        if (rows.isArray() && !rows.isEmpty()) {
            PdfPTable table = new PdfPTable(new float[]{0.55f, 3.1f, 1.5f, 2.0f, 1.1f});
            table.setWidthPercentage(100f);
            table.setSpacingAfter(7f);
            table.addCell(PdfTableBuilder.headerCell("STT"));
            table.addCell(PdfTableBuilder.headerCell("Tên xét nghiệm"));
            table.addCell(PdfTableBuilder.headerCell("Kết quả"));
            table.addCell(PdfTableBuilder.headerCell("Trị số tham chiếu (CSBT)"));
            table.addCell(PdfTableBuilder.headerCell("Đơn vị tính"));

            int index = 1;
            boolean hasAbnormal = false;
            for (JsonNode row : rows) {
                String flag = PdfFormatters.firstNonBlank(text(row, "flag"), text(row, "abnormalFlag"));
                boolean abnormal = isAbnormal(flag);
                hasAbnormal = hasAbnormal || abnormal;
                Font resultFont = abnormal ? PdfTheme.TABLE_BODY_BOLD : PdfTheme.TABLE_BODY;
                String resultText = PdfFormatters.safeText(text(row, "result"));
                if (abnormal) {
                    resultText += " (" + normalizeFlag(flag) + ") *";
                }

                table.addCell(PdfTableBuilder.cell(String.valueOf(index++), PdfTheme.TABLE_BODY, Element.ALIGN_CENTER));
                table.addCell(PdfTableBuilder.cell(PdfFormatters.firstNonBlank(text(row, "testName"), text(row, "parameter"), text(row, "name")), PdfTheme.TABLE_BODY));
                table.addCell(PdfTableBuilder.cell(resultText, resultFont, Element.ALIGN_CENTER));
                table.addCell(PdfTableBuilder.cell(PdfFormatters.firstNonBlank(text(row, "referenceRange"), text(row, "normalRange")), PdfTheme.TABLE_BODY));
                table.addCell(PdfTableBuilder.cell(PdfFormatters.firstNonBlank(text(row, "unit"), text(row, "unitName")), PdfTheme.TABLE_BODY, Element.ALIGN_CENTER));
            }
            document.add(table);
            if (hasAbnormal) {
                document.add(new Paragraph("(*) Chỉ số ngoài khoảng tham chiếu.", PdfTheme.NOTE));
            }
        } else {
            addNarrativeSection(document, "Nội dung kết quả", PdfFormatters.firstNonBlank(result.getResultTextVn(), result.getResultTextEn(), result.getResultDataJson()), false);
        }

        addNarrativeSection(document, "Nhận xét", PdfFormatters.firstNonBlank(result.getImpressionText(), text(fieldValues, "impression")), false);
        addNarrativeSection(document, "Kết luận", PdfFormatters.firstNonBlank(result.getConclusionText(), text(fieldValues, "conclusion"), result.getResultTextVn(), result.getResultTextEn()), true);
    }

    private void renderImagingReport(Document document, ServiceResult result, JsonNode fieldValues) throws Exception {
        PdfLayout.addSectionTitle(document, "Kết quả chẩn đoán hình ảnh");
        addNarrativeSection(document, "Thông tin lâm sàng", text(fieldValues, "clinicalInfo"), false);
        addNarrativeSection(document, "Kỹ thuật", text(fieldValues, "technique"), false);
        addNarrativeSection(document, "Mô tả", PdfFormatters.firstNonBlank(text(fieldValues, "findings"), result.getResultTextVn(), result.getResultTextEn()), false);
        addNarrativeSection(document, "Kết luận", PdfFormatters.firstNonBlank(result.getConclusionText(), result.getImpressionText(), text(fieldValues, "impression")), true);
        addNarrativeSection(document, "Đề nghị / Ghi chú", text(fieldValues, "recommendation"), false);
    }

    private void renderProcedureReport(Document document, ServiceResult result, JsonNode fieldValues) throws Exception {
        PdfLayout.addSectionTitle(document, "Kết quả thủ thuật / thăm dò chức năng");
        addNarrativeSection(document, "Thủ thuật", text(fieldValues, "procedureName"), false);
        addNarrativeSection(document, "Bệnh phẩm", text(fieldValues, "specimen"), false);
        addNarrativeSection(document, "Đại thể", text(fieldValues, "macroscopy"), false);
        addNarrativeSection(document, "Vi thể", text(fieldValues, "microscopy"), false);
        addNarrativeSection(document, "Mô tả bổ sung", PdfFormatters.firstNonBlank(result.getResultTextVn(), result.getResultTextEn()), false);
        addNarrativeSection(document, "Kết luận", PdfFormatters.firstNonBlank(result.getConclusionText(), text(fieldValues, "conclusion"), result.getImpressionText()), true);
    }

    private void renderGenericReport(Document document, ServiceResult result, JsonNode fieldValues) throws Exception {
        PdfLayout.addSectionTitle(document, "Nội dung kết quả");
        addNarrativeSection(document, "Nội dung kết quả", PdfFormatters.firstNonBlank(text(fieldValues, "summary"), result.getResultTextVn(), result.getResultTextEn()), false);
        addNarrativeSection(document, "Nhận xét", PdfFormatters.firstNonBlank(result.getImpressionText(), text(fieldValues, "impression")), false);
        addNarrativeSection(document, "Kết luận", PdfFormatters.firstNonBlank(result.getConclusionText(), text(fieldValues, "conclusion")), true);
    }

    private void renderAttachments(Document document, ServiceResult result) throws Exception {
        List<String> attachments = extractAttachmentLines(result.getAttachmentUrlsJson());
        if (result.getAttachmentUrl() != null && !result.getAttachmentUrl().isBlank()) {
            attachments = new ArrayList<>(attachments);
            attachments.add(PdfFormatters.safeText(result.getAttachmentUrl()));
        }
        if (attachments.isEmpty()) {
            return;
        }

        PdfLayout.addSectionTitle(document, "Hình ảnh / tài liệu đính kèm");
        PdfLayout.addNoteBox(document, attachments.stream().limit(6).map(value -> "Tệp đính kèm: " + value).toList());
    }

    private void addResultFooter(Document document, ServiceResult result, Encounter encounter) throws Exception {
        PdfLayout.addNoteBox(document, List.of(
                "Vui lòng mang theo phiếu này khi tái khám.",
                "Kết quả cần được bác sĩ điều trị giải thích trong bối cảnh lâm sàng."
        ));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(8f);
        table.setWidths(new float[]{1f, 1f});
        table.addCell(signatureCell("Kỹ thuật viên thực hiện",
                result.getPerformedByUser() != null ? PdfFormatters.userDisplayName(result.getPerformedByUser()) : null,
                result.getPerformedAt()));
        table.addCell(signatureCell("Bác sĩ chuyên khoa / Người xác nhận",
                result.getVerifiedByUser() != null ? PdfFormatters.userDisplayName(result.getVerifiedByUser())
                        : encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null,
                result.getVerifiedAt()));
        document.add(table);
    }

    private void addPublicFooter(Document document, Encounter encounter) throws Exception {
        PdfLayout.addNoteBox(document, List.of(
                "Để bảo vệ thông tin y tế cá nhân, chỉ chia sẻ phiếu này với nhân viên y tế hoặc người được bệnh nhân ủy quyền.",
                "Kết quả cần được bác sĩ điều trị giải thích trong bối cảnh lâm sàng."
        ));
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.addCell(signatureCell("Bác sĩ phụ trách",
                encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null,
                encounter.getCompletedAt()));
        document.add(table);
    }

    private PdfPCell signatureCell(String title, String name, LocalDateTime signedAt) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setMinimumHeight(86f);
        Paragraph date = new Paragraph(signedAt != null ? PdfFormatters.formatDateTime(signedAt) : "Ngày ___ tháng ___ năm ____", PdfTheme.NOTE);
        date.setAlignment(Element.ALIGN_CENTER);
        Paragraph titleParagraph = new Paragraph(title, PdfTheme.SECTION);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        Paragraph sign = new Paragraph("(Ký, ghi rõ họ tên)", PdfTheme.NOTE);
        sign.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(date);
        cell.addElement(titleParagraph);
        cell.addElement(sign);
        if (name != null && !name.isBlank()) {
            Paragraph nameParagraph = new Paragraph(name.trim(), PdfTheme.BODY);
            nameParagraph.setAlignment(Element.ALIGN_CENTER);
            nameParagraph.setSpacingBefore(34f);
            cell.addElement(nameParagraph);
        }
        return cell;
    }

    private void addNarrativeSection(Document document, String label, String value, boolean prominent) throws Exception {
        if (value == null || value.isBlank()) {
            return;
        }
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingAfter(5f);

        PdfPCell labelCell = PdfTableBuilder.cell(label, PdfTheme.SECTION);
        labelCell.setBackgroundColor(PdfTheme.SECTION_BG);
        table.addCell(labelCell);

        PdfPCell valueCell = PdfTableBuilder.cell(value, prominent ? PdfTheme.BODY_BOLD : PdfTheme.BODY);
        valueCell.setPadding(7f);
        table.addCell(valueCell);
        document.add(table);
    }

    private String titleFor(ServiceResult result, ServiceOrderItem item) {
        String explicitTitle = PdfFormatters.firstNonBlank(result.getReportTitle());
        if (explicitTitle != null) {
            return explicitTitle;
        }
        ServiceResultTemplateCode code = result.getTemplateCode();
        if (code == ServiceResultTemplateCode.LAB_TABLE) {
            return "Phiếu kết quả xét nghiệm";
        }
        String serviceName = serviceName(item).toLowerCase(PdfFormatters.VIETNAMESE);
        if (code == ServiceResultTemplateCode.IMAGING_REPORT) {
            if (serviceName.contains("siêu âm")) {
                return "Kết quả siêu âm";
            }
            if (serviceName.contains("x-quang") || serviceName.contains("x quang")) {
                return "Kết quả X-quang";
            }
            if (serviceName.contains("nội soi")) {
                return "Kết quả nội soi";
            }
            if (serviceName.contains("ct")) {
                return "Kết quả CT";
            }
            if (serviceName.contains("mri")) {
                return "Kết quả MRI";
            }
            return "Phiếu kết quả chẩn đoán hình ảnh";
        }
        return "Phiếu kết quả cận lâm sàng";
    }

    private String departmentName(ServiceOrderItem item) {
        String code = item != null ? item.getAssignedDepartmentCode() : null;
        if (code == null || code.isBlank()) {
            return "Phòng Cận lâm sàng";
        }
        return switch (code.trim().toUpperCase()) {
            case "LAB", "LABORATORY", "XN" -> "Khoa Xét nghiệm";
            case "IMAGING", "CDHA", "RADIOLOGY" -> "Khoa Chẩn đoán hình ảnh";
            case "ENDOSCOPY", "NS" -> "Phòng Nội soi";
            default -> "Phòng Cận lâm sàng";
        };
    }

    private String serviceName(ServiceOrderItem item) {
        return item == null ? "Dịch vụ cận lâm sàng"
                : PdfFormatters.safeText(PdfFormatters.firstNonBlank(
                item.getServiceNameVnSnapshot(),
                item.getServiceNameEnSnapshot(),
                item.getServiceCodeSnapshot()
        ));
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> extractAttachmentLines(String attachmentUrlsJson) {
        if (attachmentUrlsJson == null || attachmentUrlsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(attachmentUrlsJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            Iterator<JsonNode> iterator = root.elements();
            while (iterator.hasNext()) {
                JsonNode node = iterator.next();
                String label = PdfFormatters.firstNonBlank(text(node, "label"), text(node, "fileName"), text(node, "url"), text(node, "mimeType"));
                if (label != null) {
                    values.add(label);
                }
            }
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value != null && !value.isNull() ? PdfFormatters.optionalText(value.asText()) : null;
    }

    private boolean isAbnormal(String flag) {
        if (flag == null || flag.isBlank()) {
            return false;
        }
        String value = flag.trim().toUpperCase();
        return value.equals("HIGH") || value.equals("LOW") || value.equals("ABNORMAL") || value.equals("H") || value.equals("L") || value.equals("*");
    }

    private String normalizeFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            return "Bất thường";
        }
        return switch (flag.trim().toUpperCase()) {
            case "HIGH", "H" -> "H";
            case "LOW", "L" -> "L";
            case "ABNORMAL", "*" -> "Bất thường";
            default -> flag.trim();
        };
    }
}
