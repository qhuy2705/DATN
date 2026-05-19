package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceItem;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import com.PrimeCare.PrimeCare.shared.pdf.PdfLayout;
import com.PrimeCare.PrimeCare.shared.pdf.PdfTableBuilder;
import com.PrimeCare.PrimeCare.shared.pdf.PdfTheme;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class InvoicePdfService {

    public byte[] generate(
            Invoice invoice,
            byte[] qrPng,
            String bankCode,
            String accountNo,
            String accountName,
            String transferContent
    ) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = PdfLayout.a4Document();
            PdfLayout.writer(document, out);
            document.open();

            ServiceOrder serviceOrder = invoice.getServiceOrder();
            Prescription prescription = invoice.getPrescription();
            Encounter encounter = serviceOrder != null
                    ? serviceOrder.getEncounter()
                    : (prescription != null ? prescription.getEncounter() : null);
            Patient patient = encounter != null ? encounter.getPatient() : null;

            PdfLayout.addHeader(document, buildHeader(invoice, serviceOrder, encounter), shouldShowHeaderQr(invoice, qrPng) ? qrPng : null);
            PdfLayout.addTitle(document, resolveTitle(invoice));
            addPatientAdministrativeBlock(document, invoice, encounter, patient);
            addLineItems(document, invoice, serviceOrder);
            addTotals(document, invoice);
            addPaymentGuideIfNeeded(document, invoice, qrPng, bankCode, accountNo, accountName, transferContent);
            addNotes(document);
            addSignatureSection(document, invoice);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Không tạo được PDF phiếu thanh toán", ex);
        }
    }

    private PdfLayout.HeaderInfo buildHeader(Invoice invoice, ServiceOrder serviceOrder, Encounter encounter) {
        String facilityName = serviceOrder != null && serviceOrder.getBranch() != null
                ? PdfFormatters.firstNonBlank(serviceOrder.getBranch().getNameVn(), serviceOrder.getBranch().getNameEn())
                : encounter != null && encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getNameVn(), encounter.getBranch().getNameEn())
                : PdfTheme.DEFAULT_FACILITY_NAME;
        String address = serviceOrder != null && serviceOrder.getBranch() != null
                ? PdfFormatters.firstNonBlank(serviceOrder.getBranch().getAddressVn(), serviceOrder.getBranch().getAddressEn())
                : encounter != null && encounter.getBranch() != null
                ? PdfFormatters.firstNonBlank(encounter.getBranch().getAddressVn(), encounter.getBranch().getAddressEn())
                : null;
        String hotline = serviceOrder != null && serviceOrder.getBranch() != null
                ? serviceOrder.getBranch().getPhone()
                : encounter != null && encounter.getBranch() != null ? encounter.getBranch().getPhone() : null;

        return new PdfLayout.HeaderInfo(
                facilityName,
                "Bộ phận thu ngân",
                address,
                hotline,
                "Số phiếu",
                invoice.getCode(),
                "Mã giao dịch",
                PdfFormatters.firstNonBlank(invoice.getPaymentReference(), invoice.getVnpTxnRef()),
                "Ngày lập phiếu",
                PdfFormatters.formatDateTime(LocalDateTime.now())
        );
    }

    private String resolveTitle(Invoice invoice) {
        if (isFinanciallyPaid(invoice)) {
            return "Biên lai thu tiền";
        }
        return "Phiếu thanh toán viện phí";
    }

    private void addPatientAdministrativeBlock(Document document, Invoice invoice, Encounter encounter, Patient patient) throws Exception {
        PdfLayout.addSectionTitle(document, "Thông tin hành chính");

        PdfPTable table = PdfLayout.keyValueTable(new float[]{1.2f, 2f, 1.2f, 2f});
        PdfTableBuilder.addKeyValue(table, "Họ và tên bệnh nhân", PdfFormatters.uppercaseVietnamese(patientName(encounter, patient)));
        PdfTableBuilder.addKeyValue(table, "Năm sinh / Tuổi", encounter != null ? PdfFormatters.ageOrBirthYear(encounter.getPatientDobSnapshot()) : patient != null ? PdfFormatters.ageOrBirthYear(patient.getDob()) : null);
        PdfTableBuilder.addKeyValue(table, "Giới tính", encounter != null ? PdfFormatters.genderLabel(encounter.getPatientGenderSnapshot()) : patient != null ? PdfFormatters.genderLabel(patient.getGender()) : null);
        PdfTableBuilder.addKeyValue(table, "Mã bệnh nhân", patient != null ? patient.getCode() : "Đang cập nhật");
        PdfTableBuilder.addKeyValue(table, "Số điện thoại", PdfFormatters.firstNonBlank(encounter != null ? encounter.getPatientPhoneSnapshot() : null, patient != null ? patient.getPhone() : null));
        PdfTableBuilder.addKeyValue(table, "Địa chỉ", patient != null ? patient.getAddress() : null);
        PdfTableBuilder.addKeyValue(table, "Số thẻ BHYT", patient != null ? patient.getInsuranceNumber() : "Không áp dụng");
        PdfTableBuilder.addKeyValue(table, "Tuyến khám", "Không áp dụng");
        PdfTableBuilder.addKeyValue(table, "Mã lượt khám", encounter != null ? encounter.getCode() : null);
        PdfTableBuilder.addKeyValue(table, "Mã chỉ định", invoice.getServiceOrder() != null ? invoice.getServiceOrder().getCode() : null);
        PdfTableBuilder.addKeyValue(table, "Mã đơn thuốc", invoice.getPrescription() != null ? invoice.getPrescription().getCode() : null);

        PdfPCell diagnosis = PdfTableBuilder.cell("Chẩn đoán lâm sàng: "
                + PdfFormatters.safeText(resolveDiagnosis(encounter)), PdfTheme.BODY);
        diagnosis.setColspan(4);
        table.addCell(diagnosis);

        document.add(table);
    }

    private void addLineItems(Document document, Invoice invoice, ServiceOrder serviceOrder) throws Exception {
        PdfLayout.addSectionTitle(document, "Chi tiết dịch vụ / thuốc");

        PdfPTable table = new PdfPTable(new float[]{0.55f, 3.2f, 0.75f, 1.25f, 1.35f, 1.35f, 1.35f});
        table.setWidthPercentage(100f);
        table.setSpacingAfter(8f);
        table.addCell(PdfTableBuilder.headerCell("STT"));
        table.addCell(PdfTableBuilder.headerCell("Tên dịch vụ / Thuốc"));
        table.addCell(PdfTableBuilder.headerCell("Số lượng"));
        table.addCell(PdfTableBuilder.headerCell("Đơn giá"));
        table.addCell(PdfTableBuilder.headerCell("Thành tiền"));
        table.addCell(PdfTableBuilder.headerCell("BHYT chi trả"));
        table.addCell(PdfTableBuilder.headerCell("Bệnh nhân chi trả"));

        List<LineItem> items = resolveLineItems(invoice, serviceOrder);
        if (items.isEmpty()) {
            PdfTableBuilder.addMergedNote(table, "Chưa có dòng chi phí nào được ghi nhận trên phiếu thanh toán.", 7);
        } else {
            int index = 1;
            for (LineItem item : items) {
                long insuranceAmount = 0L;
                long patientAmount = Math.max(item.totalAmount() - insuranceAmount, 0L);
                table.addCell(PdfTableBuilder.cell(String.valueOf(index++), PdfTheme.TABLE_BODY, Element.ALIGN_CENTER));
                table.addCell(PdfTableBuilder.cell(item.name(), PdfTheme.TABLE_BODY));
                table.addCell(PdfTableBuilder.cell(String.valueOf(item.quantity()), PdfTheme.TABLE_BODY, Element.ALIGN_CENTER));
                table.addCell(PdfTableBuilder.cell(PdfFormatters.formatMoneyVnd(item.unitPrice()), PdfTheme.TABLE_BODY, Element.ALIGN_RIGHT));
                table.addCell(PdfTableBuilder.cell(PdfFormatters.formatMoneyVnd(item.totalAmount()), PdfTheme.TABLE_BODY, Element.ALIGN_RIGHT));
                table.addCell(PdfTableBuilder.cell(PdfFormatters.formatMoneyVnd(insuranceAmount), PdfTheme.TABLE_BODY, Element.ALIGN_RIGHT));
                table.addCell(PdfTableBuilder.cell(PdfFormatters.formatMoneyVnd(patientAmount), PdfTheme.TABLE_BODY_BOLD, Element.ALIGN_RIGHT));
            }
        }

        document.add(table);
    }

    private void addTotals(Document document, Invoice invoice) throws Exception {
        long subtotal = value(invoice.getSubtotalAmount());
        long discount = value(invoice.getDiscountAmount());
        long total = value(invoice.getTotalAmount());
        long insurancePaid = 0L;
        long patientPaid = Math.max(total - insurancePaid, 0L);

        PdfPTable table = new PdfPTable(new float[]{1.6f, 1f});
        table.setWidthPercentage(52f);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingAfter(8f);

        PdfTableBuilder.addKeyValue(table, "Tổng chi phí", PdfFormatters.formatMoneyVnd(subtotal));
        PdfTableBuilder.addKeyValue(table, "Tổng quỹ BHYT thanh toán", PdfFormatters.formatMoneyVnd(insurancePaid));
        PdfTableBuilder.addKeyValue(table, "Tổng tiền bệnh nhân cùng chi trả", PdfFormatters.formatMoneyVnd(patientPaid));
        PdfTableBuilder.addKeyValue(table, "Giảm trừ", PdfFormatters.formatMoneyVnd(discount));

        PdfPCell label = PdfTableBuilder.cell("Số tiền thực thu", PdfTheme.BODY_BOLD);
        label.setBackgroundColor(PdfTheme.SECTION_BG);
        PdfPCell value = PdfTableBuilder.cell(PdfFormatters.formatMoneyVnd(total), PdfTheme.BODY_BOLD, Element.ALIGN_RIGHT);
        table.addCell(label);
        table.addCell(value);

        PdfPCell words = PdfTableBuilder.cell("Bằng chữ: " + PdfFormatters.numberToVietnameseWords(total), PdfTheme.BODY);
        words.setColspan(2);
        table.addCell(words);

        PdfPCell status = PdfTableBuilder.cell("Trạng thái: " + PdfFormatters.paymentStatusLabel(invoice.getPaymentStatus())
                + " · Phương thức thanh toán: " + PdfFormatters.paymentMethodLabel(invoice.getPaymentMethod()), PdfTheme.BODY);
        status.setColspan(2);
        table.addCell(status);

        document.add(table);
    }

    private void addPaymentGuideIfNeeded(Document document,
                                         Invoice invoice,
                                         byte[] qrPng,
                                         String bankCode,
                                         String accountNo,
                                         String accountName,
                                         String transferContent) throws Exception {
        boolean showGuide = !isFinanciallyPaid(invoice)
                && qrPng != null
                && qrPng.length > 0;
        boolean showReconciliation = invoice.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                || invoice.getPaymentMethod() == PaymentMethod.VNPAY;

        if (!showGuide && !showReconciliation) {
            return;
        }

        PdfLayout.addSectionTitle(document, "Thông tin thanh toán / đối soát");
        PdfPTable table = new PdfPTable(new float[]{1.3f, 2f, 1f});
        table.setWidthPercentage(100f);
        table.setSpacingAfter(8f);

        PdfPCell info = new PdfPCell();
        info.setColspan(qrPng != null && qrPng.length > 0 ? 2 : 3);
        info.setBorder(Rectangle.BOX);
        info.setBorderColor(PdfTheme.BORDER);
        info.setPadding(7f);
        info.addElement(new Paragraph("Ngân hàng: " + PdfFormatters.safeText(bankCode, "Đang cập nhật"), PdfTheme.BODY));
        info.addElement(new Paragraph("Số tài khoản: " + PdfFormatters.safeText(accountNo, "Đang cập nhật"), PdfTheme.BODY));
        info.addElement(new Paragraph("Chủ tài khoản: " + PdfFormatters.safeText(accountName, "Đang cập nhật"), PdfTheme.BODY));
        info.addElement(new Paragraph("Nội dung chuyển khoản: " + PdfFormatters.safeText(transferContent, "Đang cập nhật"), PdfTheme.BODY_BOLD));
        table.addCell(info);

        if (qrPng != null && qrPng.length > 0) {
            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.BOX);
            qrCell.setBorderColor(PdfTheme.BORDER);
            qrCell.setPadding(7f);
            qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            Image qr = Image.getInstance(qrPng);
            qr.scaleToFit(96, 96);
            qr.setAlignment(Image.ALIGN_CENTER);
            qrCell.addElement(qr);
            qrCell.addElement(new Paragraph("Quét QR để thanh toán", PdfTheme.NOTE));
            table.addCell(qrCell);
        }
        document.add(table);
    }

    private void addNotes(Document document) throws Exception {
        PdfLayout.addNoteBox(document, List.of(
                "Phiếu này có giá trị xác nhận thanh toán tại PrimeCare.",
                "Vui lòng kiểm tra kỹ thông tin trước khi rời quầy.",
                "Các khoản bảo hiểm y tế được hiển thị theo dữ liệu được xác nhận tại thời điểm lập phiếu."
        ));
    }

    private void addSignatureSection(Document document, Invoice invoice) throws Exception {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{1f, 1f, 1f});
        table.setSpacingBefore(6f);

        table.addCell(signatureCell("Người nộp tiền", null));
        table.addCell(signatureCell("Thu ngân / Người lập phiếu", PdfFormatters.userDisplayName(invoice.getCashier())));
        table.addCell(signatureCell("Đại diện cơ sở", null));

        document.add(table);
    }

    private PdfPCell signatureCell(String title, String name) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4f);
        cell.setMinimumHeight(92f);
        Paragraph titleParagraph = new Paragraph(title, PdfTheme.SECTION);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        Paragraph sign = new Paragraph("(Ký, ghi rõ họ tên)", PdfTheme.NOTE);
        sign.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(titleParagraph);
        cell.addElement(sign);
        if (name != null && !name.isBlank()) {
            Paragraph nameParagraph = new Paragraph(name.trim(), PdfTheme.BODY);
            nameParagraph.setAlignment(Element.ALIGN_CENTER);
            nameParagraph.setSpacingBefore(42f);
            cell.addElement(nameParagraph);
        }
        return cell;
    }

    private boolean shouldShowHeaderQr(Invoice invoice, byte[] qrPng) {
        return qrPng != null && qrPng.length > 0
                && (!isFinanciallyPaid(invoice)
                || invoice.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                || invoice.getPaymentMethod() == PaymentMethod.VNPAY);
    }

    private boolean isFinanciallyPaid(Invoice invoice) {
        return invoice.getPaymentStatus() == PaymentStatus.PAID
                || invoice.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private List<LineItem> resolveLineItems(Invoice invoice, ServiceOrder serviceOrder) {
        List<LineItem> items = new ArrayList<>();
        if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
            for (InvoiceItem item : invoice.getItems()) {
                items.add(new LineItem(
                        PdfFormatters.safeText(item.getNameSnapshot()),
                        item.getQuantity() == null ? 1 : item.getQuantity(),
                        value(item.getUnitPrice()),
                        value(item.getTotalAmount())
                ));
            }
            return items;
        }

        if (serviceOrder != null && serviceOrder.getItems() != null) {
            for (ServiceOrderItem item : serviceOrder.getItems()) {
                items.add(new LineItem(
                        PdfFormatters.safeText(PdfFormatters.firstNonBlank(item.getServiceNameVnSnapshot(), item.getServiceNameEnSnapshot(), item.getServiceCodeSnapshot())),
                        item.getQuantity() == null ? 1 : item.getQuantity(),
                        value(item.getPriceSnapshot()),
                        value(item.getLineTotalAmount())
                ));
            }
        }
        return items;
    }

    private String patientName(Encounter encounter, Patient patient) {
        return PdfFormatters.firstNonBlank(
                encounter != null ? encounter.getPatientFullNameSnapshot() : null,
                patient != null ? patient.getFullName() : null
        );
    }

    private String resolveDiagnosis(Encounter encounter) {
        return PdfFormatters.firstNonBlank(
                encounter != null ? encounter.getFinalDiagnosis() : null,
                encounter != null ? encounter.getPreliminaryDiagnosis() : null,
                encounter != null ? encounter.getConclusion() : null
        );
    }

    private long value(Long amount) {
        return amount == null ? 0L : amount;
    }

    private record LineItem(String name, int quantity, long unitPrice, long totalAmount) {
    }
}
