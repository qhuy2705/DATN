package com.PrimeCare.PrimeCare.shared.email;

import java.util.List;

public record EmailTemplate(
        String title,
        String greeting,
        String intro,
        List<Row> rows,
        List<String> notes,
        Cta cta,
        String footerNote
) {
    public record Row(String label, String value) {
    }

    public record Cta(String label, String url) {
    }
}
