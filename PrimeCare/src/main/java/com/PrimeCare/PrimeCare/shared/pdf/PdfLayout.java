package com.PrimeCare.PrimeCare.shared.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.ExceptionConverter;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

public final class PdfLayout {

    private static final String[] LOGO_CANDIDATES = {
            "static/logo.png",
            "static/images/logo.png",
            "logo.png"
    };

    private PdfLayout() {
    }

    public static Document a4Document() {
        return new Document(PageSize.A4, 32, 32, 34, 46);
    }

    public static PdfWriter writer(Document document, ByteArrayOutputStream out) throws Exception {
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new FooterEvent());
        return writer;
    }

    public static void addHeader(Document document, HeaderInfo info, byte[] qrPng) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{3.2f, 1.4f});
        table.setSpacingAfter(8f);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        Image logo = loadLogo();
        if (logo != null) {
            logo.scaleToFit(42, 42);
            logo.setAlignment(Image.ALIGN_LEFT);
            left.addElement(logo);
        }
        left.addElement(new Paragraph(PdfFormatters.safeText(info.facilityName(), PdfTheme.DEFAULT_FACILITY_NAME), PdfTheme.HOSPITAL));
        if (info.department() != null && !info.department().isBlank()) {
            left.addElement(new Paragraph(info.department().trim(), PdfTheme.HOSPITAL_META));
        }
        left.addElement(new Paragraph("Địa chỉ: " + PdfFormatters.safeText(info.address(), "Đang cập nhật"), PdfTheme.HOSPITAL_META));
        left.addElement(new Paragraph("Hotline: " + PdfFormatters.safeText(info.hotline(), PdfTheme.DEFAULT_HOTLINE), PdfTheme.HOSPITAL_META));

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addRightMeta(right, info.documentCodeLabel(), info.documentCode());
        addRightMeta(right, info.secondaryCodeLabel(), info.secondaryCode());
        addRightMeta(right, info.printedAtLabel(), info.printedAt());
        if (qrPng != null && qrPng.length > 0) {
            Image qr = Image.getInstance(qrPng);
            qr.scaleToFit(72, 72);
            qr.setAlignment(Image.ALIGN_RIGHT);
            right.addElement(qr);
        }

        table.addCell(left);
        table.addCell(right);
        document.add(table);
    }

    public static void addTitle(Document document, String title) throws Exception {
        Paragraph paragraph = new Paragraph(PdfFormatters.uppercaseVietnamese(title), PdfTheme.TITLE);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingBefore(2f);
        paragraph.setSpacingAfter(10f);
        document.add(paragraph);
    }

    public static void addSectionTitle(Document document, String title) throws Exception {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(5f);
        table.setSpacingAfter(5f);
        table.addCell(PdfTableBuilder.sectionHeaderCell(PdfFormatters.safeText(title), 1));
        document.add(table);
    }

    public static void addNoteBox(Document document, List<String> lines) throws Exception {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);
        table.setSpacingAfter(8f);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(PdfTheme.BORDER);
        cell.setPadding(8f);
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                cell.addElement(new Paragraph(line.trim(), PdfTheme.NOTE));
            }
        }
        table.addCell(cell);
        document.add(table);
    }

    public static PdfPTable keyValueTable(float[] widths) throws Exception {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100f);
        table.setWidths(widths);
        table.setSpacingAfter(8f);
        return table;
    }

    private static void addRightMeta(PdfPCell cell, String label, String value) {
        if (label == null || label.isBlank()) {
            return;
        }
        Paragraph p = new Paragraph(label.trim() + ": " + PdfFormatters.safeText(value, "Đang cập nhật"), PdfTheme.HOSPITAL_META);
        p.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(p);
    }

    private static Image loadLogo() {
        for (String candidate : LOGO_CANDIDATES) {
            try {
                ClassPathResource resource = new ClassPathResource(candidate);
                if (resource.exists()) {
                    return Image.getInstance(resource.getURL());
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public record HeaderInfo(
            String facilityName,
            String department,
            String address,
            String hotline,
            String documentCodeLabel,
            String documentCode,
            String secondaryCodeLabel,
            String secondaryCode,
            String printedAtLabel,
            String printedAt
    ) {
        public static HeaderInfo of(String facilityName, String department, String address, String hotline,
                                    String documentCodeLabel, String documentCode) {
            return new HeaderInfo(
                    facilityName,
                    department,
                    address,
                    hotline,
                    documentCodeLabel,
                    documentCode,
                    null,
                    null,
                    "Ngày in",
                    PdfFormatters.formatDateTime(LocalDateTime.now())
            );
        }
    }

    private static final class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                String footer = "PrimeCare · Thông tin y tế cá nhân, chỉ sử dụng theo đúng mục đích khám chữa bệnh · Trang "
                        + writer.getPageNumber();
                ColumnText.showTextAligned(
                        writer.getDirectContent(),
                        Element.ALIGN_CENTER,
                        new Phrase(footer, PdfTheme.NOTE),
                        (document.right() + document.left()) / 2,
                        document.bottom() - 22,
                        0
                );
            } catch (Exception ex) {
                throw new ExceptionConverter(ex);
            }
        }
    }
}
