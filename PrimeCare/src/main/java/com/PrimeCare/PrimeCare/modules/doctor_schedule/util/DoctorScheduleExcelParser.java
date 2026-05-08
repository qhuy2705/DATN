package com.PrimeCare.PrimeCare.modules.doctor_schedule.util;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DoctorScheduleExcelParser {

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu")
    };

    private DoctorScheduleExcelParser() {
    }

    public static List<ParsedScheduleRow> parse(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = readEntries(inputStream);
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        String sheetPath = entries.keySet()
                                  .stream()
                                  .filter(name -> name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
                                  .sorted(Comparator.naturalOrder())
                                  .findFirst()
                                  .orElseThrow(() -> new IOException("Không tìm thấy sheet dữ liệu trong file Excel"));

        return parseSheet(entries.get(sheetPath), sharedStrings);
    }

    private static Map<String, byte[]> readEntries(InputStream inputStream) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                zipInputStream.transferTo(outputStream);
                result.put(entry.getName(), outputStream.toByteArray());
                zipInputStream.closeEntry();
            }
        }
        return result;
    }

    private static List<String> parseSharedStrings(byte[] xmlBytes) throws IOException {
        if (xmlBytes == null || xmlBytes.length == 0) {
            return List.of();
        }

        Document document = parseXml(xmlBytes);
        NodeList nodes = document.getElementsByTagName("si");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add(extractNodeText(nodes.item(i)).trim());
        }
        return result;
    }

    private static List<ParsedScheduleRow> parseSheet(byte[] xmlBytes, List<String> sharedStrings) throws IOException {
        if (xmlBytes == null || xmlBytes.length == 0) {
            return List.of();
        }

        Document document = parseXml(xmlBytes);
        NodeList rows = document.getElementsByTagName("row");
        List<ParsedScheduleRow> result = new ArrayList<>();

        for (int i = 0; i < rows.getLength(); i++) {
            Element rowElement = (Element) rows.item(i);
            int rowNumber = parseInt(rowElement.getAttribute("r")).orElse(i + 1);
            if (rowNumber == 1) {
                continue;
            }

            Map<String, String> cells = parseRowCells(rowElement, sharedStrings);
            String workDateRaw = trimToNull(cells.get("A"));
            String sessionRaw = trimToNull(cells.get("B"));
            String columnCRaw = trimToNull(cells.get("C"));
            String columnDRaw = trimToNull(cells.get("D"));
            String columnERaw = trimToNull(cells.get("E"));

            if (workDateRaw == null && sessionRaw == null && columnCRaw == null && columnDRaw == null && columnERaw == null) {
                continue;
            }

            result.add(new ParsedScheduleRow(
                    rowNumber,
                    workDateRaw,
                    parseDate(workDateRaw),
                    sessionRaw,
                    parseSession(sessionRaw),
                    resolveNote(columnCRaw, columnDRaw, columnERaw)
            ));
        }

        return result;
    }

    private static String resolveNote(String columnCRaw, String columnDRaw, String columnERaw) {
        String noteFromE = trimToNull(columnERaw);
        if (noteFromE != null) {
            return noteFromE;
        }

        String noteFromC = trimToNull(columnCRaw);
        String noteFromD = trimToNull(columnDRaw);

        if (noteFromD == null) {
            return noteFromC;
        }
        if (noteFromC == null) {
            return parseInt(noteFromD).isPresent() ? null : noteFromD;
        }

        boolean cLooksNumeric = parseInt(noteFromC).isPresent();
        boolean dLooksNumeric = parseInt(noteFromD).isPresent();

        if (!cLooksNumeric) {
            return noteFromC;
        }
        if (!dLooksNumeric) {
            return noteFromD;
        }

        return null;
    }

    private static Map<String, String> parseRowCells(Element rowElement, List<String> sharedStrings) {
        NodeList cells = rowElement.getElementsByTagName("c");
        Map<String, String> result = new HashMap<>();

        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            String ref = cell.getAttribute("r");
            String column = ref == null ? "" : ref.replaceAll("\\d", "");
            if (column.isBlank()) {
                continue;
            }
            result.put(column, parseCellValue(cell, sharedStrings));
        }

        return result;
    }

    private static String parseCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList inlineNodes = cell.getElementsByTagName("t");
            if (inlineNodes.getLength() == 0) {
                return "";
            }
            return inlineNodes.item(0).getTextContent();
        }

        NodeList valueNodes = cell.getElementsByTagName("v");
        if (valueNodes.getLength() == 0) {
            return "";
        }

        String value = valueNodes.item(0).getTextContent();
        if ("s".equals(type)) {
            int index = parseInt(value).orElse(-1);
            if (index >= 0 && index < sharedStrings.size()) {
                return sharedStrings.get(index);
            }
        }
        return value;
    }

    private static LocalDate parseDate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String value = rawValue.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            double serial = Double.parseDouble(value);
            return EXCEL_EPOCH.plusDays((long) Math.floor(serial));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BranchSessionType parseSession(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(rawValue, Normalizer.Form.NFD)
                                      .replaceAll("\\p{M}", "")
                                      .trim()
                                      .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "MORNING", "SANG", "CA SANG", "AM", "1" -> BranchSessionType.AM;
            case "AFTERNOON", "CHIEU", "CA CHIEU", "PM", "2" -> BranchSessionType.PM;
            default -> null;
        };
    }

    private static Optional<Integer> parseInt(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of((int) Math.round(Double.parseDouble(rawValue.trim())));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Document parseXml(byte[] xmlBytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
        } catch (Exception ex) {
            throw new IOException("Không thể đọc dữ liệu XML từ file Excel", ex);
        }
    }

    private static String extractNodeText(Node node) {
        StringBuilder builder = new StringBuilder();
        collectText(node, builder);
        return builder.toString();
    }

    private static void collectText(Node node, StringBuilder builder) {
        if (node == null) {
            return;
        }

        if (node.getNodeType() == Node.TEXT_NODE) {
            builder.append(node.getNodeValue());
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectText(children.item(i), builder);
        }
    }

    public record ParsedScheduleRow(
            int rowNumber,
            String rawWorkDate,
            LocalDate workDate,
            String rawSession,
            BranchSessionType session,
            String note
    ) {
    }
}
