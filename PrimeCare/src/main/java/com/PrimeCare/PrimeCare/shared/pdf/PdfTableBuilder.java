package com.PrimeCare.PrimeCare.shared.pdf;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

public final class PdfTableBuilder {

    private PdfTableBuilder() {
    }

    public static PdfPCell cell(String value, Font font) {
        return cell(value, font, Element.ALIGN_LEFT);
    }

    public static PdfPCell cell(String value, Font font, int horizontalAlignment) {
        PdfPCell cell = new PdfPCell(new Phrase(PdfFormatters.safeText(value), font));
        cell.setPadding(5f);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(PdfTheme.BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(horizontalAlignment);
        return cell;
    }

    public static PdfPCell noBorderCell(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(PdfFormatters.safeText(value, ""), font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2f);
        return cell;
    }

    public static PdfPCell headerCell(String value) {
        PdfPCell cell = cell(value, PdfTheme.TABLE_HEADER, Element.ALIGN_CENTER);
        cell.setBackgroundColor(PdfTheme.HEADER_BG);
        cell.setPadding(5f);
        return cell;
    }

    public static PdfPCell sectionHeaderCell(String value, int colspan) {
        PdfPCell cell = cell(value, PdfTheme.SECTION);
        cell.setColspan(colspan);
        cell.setBackgroundColor(PdfTheme.SECTION_BG);
        cell.setPadding(6f);
        return cell;
    }

    public static void addKeyValue(PdfPTable table, String label, String value) {
        PdfPCell labelCell = cell(label, PdfTheme.BODY_BOLD);
        labelCell.setBackgroundColor(PdfTheme.SECTION_BG);
        table.addCell(labelCell);
        table.addCell(cell(value, PdfTheme.BODY));
    }

    public static void addMergedNote(PdfPTable table, String value, int colspan) {
        PdfPCell cell = cell(value, PdfTheme.NOTE);
        cell.setColspan(colspan);
        table.addCell(cell);
    }
}
