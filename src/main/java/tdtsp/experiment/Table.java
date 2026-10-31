package tdtsp.experiment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import tdtsp.network.Csv;

/** Rows of experiment results, with CSV/Markdown output and mean/median/std summaries. */
final class Table {

    private final List<String> columns;
    private final List<Object[]> rows = new ArrayList<>();

    Table(String... columns) {
        this.columns = List.of(columns);
    }

    void add(Object... values) {
        if (values.length != columns.size()) {
            throw new IllegalArgumentException("expected " + columns.size() + " values, got " + values.length);
        }
        rows.add(values);
    }

    void write(Path path) throws IOException {
        List<String[]> out = new ArrayList<>();
        out.add(columns.toArray(String[]::new));
        for (Object[] row : rows) {
            out.add(Arrays.stream(row).map(Table::format).toArray(String[]::new));
        }
        Csv.write(path, out);
    }

    /**
     * Groups by {@code keys} (in first-seen order) and reports mean, median and sample
     * standard deviation of each metric over the group's rows (the seeds).
     */
    Table summary(List<String> keys, List<String> metrics) {
        List<String> header = new ArrayList<>(keys);
        header.add("runs");
        for (String m : metrics) {
            header.add(m + "_mean");
            header.add(m + "_median");
            header.add(m + "_std");
        }
        Map<List<Object>, List<Object[]>> groups = new LinkedHashMap<>();
        for (Object[] row : rows) {
            List<Object> key = keys.stream().map(k -> row[col(k)]).toList();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        Table summary = new Table(header.toArray(String[]::new));
        for (Map.Entry<List<Object>, List<Object[]>> group : groups.entrySet()) {
            List<Object> values = new ArrayList<>(group.getKey());
            values.add(group.getValue().size());
            for (String m : metrics) {
                double[] xs = group.getValue().stream().mapToDouble(r -> ((Number) r[col(m)]).doubleValue()).toArray();
                values.add(Stats.mean(xs));
                values.add(Stats.median(xs));
                values.add(Stats.std(xs));
            }
            summary.add(values.toArray());
        }
        return summary;
    }

    /** Mean of {@code metric} over rows matching {@code key=value} pairs. */
    double mean(String metric, Object... filter) {
        return Stats.mean(rows.stream()
                .filter(r -> matches(r, filter))
                .mapToDouble(r -> ((Number) r[col(metric)]).doubleValue())
                .toArray());
    }

    /** Markdown table of the given columns. */
    String markdown(String... cols) {
        StringBuilder sb = new StringBuilder("| ").append(String.join(" | ", cols)).append(" |\n|");
        sb.append(" --- |".repeat(cols.length)).append('\n');
        for (Object[] row : rows) {
            sb.append("| ");
            for (String c : cols) {
                sb.append(format(row[col(c)])).append(" | ");
            }
            sb.setLength(sb.length() - 1);
            sb.append('\n');
        }
        return sb.toString();
    }

    private boolean matches(Object[] row, Object[] filter) {
        for (int i = 0; i < filter.length; i += 2) {
            if (!Objects.equals(row[col((String) filter[i])], filter[i + 1])) {
                return false;
            }
        }
        return true;
    }

    private int col(String name) {
        int i = columns.indexOf(name);
        if (i < 0) {
            throw new IllegalArgumentException("no column " + name);
        }
        return i;
    }

    private static String format(Object value) {
        if (value instanceof Double d) {
            return Double.isNaN(d) ? "" : String.format(Locale.ROOT, "%.3f", d);
        }
        return String.valueOf(value);
    }
}
