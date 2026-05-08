package com.PrimeCare.PrimeCare.shared.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PdfFontSupport {

    private static final Logger log = LoggerFactory.getLogger(PdfFontSupport.class);

    private static final String[] CLASSPATH_FONT_CANDIDATES = {
            "fonts/NotoSans-Regular.ttf",
            "fonts/NotoSans.ttf",
            "fonts/DejaVuSans.ttf",
            "fonts/LiberationSans-Regular.ttf"
    };

    private static final List<String> SYSTEM_FONT_CANDIDATES = List.of(
            "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
            "/Library/Fonts/Arial Unicode.ttf",
            "C:/Windows/Fonts/arialuni.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
            "C:/Windows/Fonts/tahoma.ttf"
    );

    private static volatile BaseFont unicodeBaseFont;

    private PdfFontSupport() {}

    public static Font font(float size, int style) {
        BaseFont baseFont = resolveUnicodeBaseFont();
        if (baseFont != null) {
            return new Font(baseFont, size, style);
        }
        throw new IllegalStateException("Không tìm thấy font Unicode cho PDF. Hãy thêm NotoSans/DejaVuSans/LiberationSans vào src/main/resources/fonts hoặc cấu hình font hệ thống.");
    }

    private static BaseFont resolveUnicodeBaseFont() {
        BaseFont cached = unicodeBaseFont;
        if (cached != null) {
            return cached;
        }
        synchronized (PdfFontSupport.class) {
            if (unicodeBaseFont != null) {
                return unicodeBaseFont;
            }
            unicodeBaseFont = loadClasspathFont();
            if (unicodeBaseFont != null) {
                return unicodeBaseFont;
            }
            unicodeBaseFont = loadSystemFont();
            return unicodeBaseFont;
        }
    }

    private static BaseFont loadClasspathFont() {
        for (String candidate : CLASSPATH_FONT_CANDIDATES) {
            try {
                ClassPathResource resource = new ClassPathResource(candidate);
                if (resource.exists()) {
                    log.info("Using embedded PDF Unicode font: {}", candidate);
                    return BaseFont.createFont(resource.getURL().toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception ignored) {
                log.warn("Không load được font PDF từ classpath: {}", candidate, ignored);
            }
        }
        return null;
    }

    private static BaseFont loadSystemFont() {
        for (String candidate : SYSTEM_FONT_CANDIDATES) {
            try {
                Path path = Path.of(candidate);
                if (Files.exists(path)) {
                    log.warn("Không tìm thấy font PDF Unicode trong resources/fonts; đang dùng font hệ thống: {}", path.toAbsolutePath());
                    return BaseFont.createFont(path.toAbsolutePath().toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception ignored) {
                log.warn("Không load được font PDF hệ thống: {}", candidate, ignored);
            }
        }
        log.error("Không tìm thấy font Unicode để render PDF tiếng Việt. Các phiếu PDF sẽ không được tạo để tránh mất dấu.");
        return null;
    }
}
