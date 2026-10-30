package tdtsp.network;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180 CSV reader/writer (UTF-8, LF output, no BOM).
 */
public final class Csv {

    private Csv() {
    }

    public static List<String[]> read(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                rows.add(row.toArray(String[]::new));
                row.clear();
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row.toArray(String[]::new));
        }
        return rows;
    }

    public static void write(Path path, List<String[]> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(escape(row[i]));
            }
            out.append('\n');
        }
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? '"' + escaped + '"' : escaped;
    }
}
