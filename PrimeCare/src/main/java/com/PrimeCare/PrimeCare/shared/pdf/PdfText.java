package com.PrimeCare.PrimeCare.shared.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;

public final class PdfText {

    private PdfText() {
    }

    public static Paragraph paragraph(String text, Font font) {
        return new Paragraph(PdfFormatters.safeText(text), font);
    }

    public static Paragraph labelValue(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(PdfFormatters.safeText(label, "") + ": ", PdfTheme.BODY_BOLD));
        paragraph.add(new Chunk(PdfFormatters.safeText(value), PdfTheme.BODY));
        return paragraph;
    }
}
