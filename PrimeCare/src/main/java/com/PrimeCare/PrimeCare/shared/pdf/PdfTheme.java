package com.PrimeCare.PrimeCare.shared.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.GrayColor;

public final class PdfTheme {

    public static final String BRAND_NAME = "PrimeCare";
    public static final String DEFAULT_FACILITY_NAME = "Cơ sở y tế PrimeCare";
    public static final String DEFAULT_HOTLINE = "1900 1234";

    public static final GrayColor BORDER = new GrayColor(0.82f);
    public static final GrayColor LIGHT_BORDER = new GrayColor(0.90f);
    public static final GrayColor HEADER_BG = new GrayColor(0.92f);
    public static final GrayColor SECTION_BG = new GrayColor(0.96f);

    public static final Font HOSPITAL = PdfFontSupport.font(10.5f, Font.BOLD);
    public static final Font HOSPITAL_META = PdfFontSupport.font(8.5f, Font.NORMAL);
    public static final Font TITLE = PdfFontSupport.font(15f, Font.BOLD);
    public static final Font SECTION = PdfFontSupport.font(10.5f, Font.BOLD);
    public static final Font BODY = PdfFontSupport.font(9.5f, Font.NORMAL);
    public static final Font BODY_BOLD = PdfFontSupport.font(9.5f, Font.BOLD);
    public static final Font TABLE_HEADER = PdfFontSupport.font(8.8f, Font.BOLD);
    public static final Font TABLE_BODY = PdfFontSupport.font(8.8f, Font.NORMAL);
    public static final Font TABLE_BODY_BOLD = PdfFontSupport.font(8.8f, Font.BOLD);
    public static final Font NOTE = PdfFontSupport.font(8.2f, Font.NORMAL);

    private PdfTheme() {
    }
}
