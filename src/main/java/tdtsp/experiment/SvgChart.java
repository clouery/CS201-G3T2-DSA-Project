package tdtsp.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal dependency-free SVG charts: line charts and grouped bar charts over
 * categorical x values. NaN values are skipped.
 */
final class SvgChart {

    private static final int W = 720;
    private static final int H = 420;
    private static final int LEFT = 78;
    private static final int RIGHT = 24;
    private static final int TOP = 70;
    private static final int BOTTOM = 62;
    private static final String[] COLORS = {"#2563eb", "#dc2626", "#16a34a", "#9333ea", "#ea580c"};

    private SvgChart() {
    }

    static void line(Path out, String title, String xLabel, String yLabel,
                     List<String> categories, Map<String, double[]> series) throws IOException {
        StringBuilder svg = new StringBuilder();
        double yMax = frame(svg, title, xLabel, yLabel, categories, series);
        int s = 0;
        for (Map.Entry<String, double[]> e : series.entrySet()) {
            String color = COLORS[s++ % COLORS.length];
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < categories.size(); i++) {
                double v = e.getValue()[i];
                if (Double.isNaN(v)) {
                    continue;
                }
                path.append(path.length() == 0 ? "M" : " L").append(fmt(x(i, categories.size())))
                        .append(',').append(fmt(y(v, yMax)));
            }
            svg.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(color)
                    .append("\" stroke-width=\"2.5\"/>\n");
            for (int i = 0; i < categories.size(); i++) {
                double v = e.getValue()[i];
                if (!Double.isNaN(v)) {
                    svg.append("<circle cx=\"").append(fmt(x(i, categories.size()))).append("\" cy=\"")
                            .append(fmt(y(v, yMax))).append("\" r=\"4\" fill=\"").append(color).append("\"/>\n");
                }
            }
        }
        write(out, svg);
    }

    static void bars(Path out, String title, String xLabel, String yLabel,
                     List<String> categories, Map<String, double[]> series) throws IOException {
        StringBuilder svg = new StringBuilder();
        double yMax = frame(svg, title, xLabel, yLabel, categories, series);
        double slot = (double) (W - LEFT - RIGHT) / categories.size();
        double barW = slot * 0.7 / series.size();
        int s = 0;
        for (Map.Entry<String, double[]> e : series.entrySet()) {
            String color = COLORS[s % COLORS.length];
            for (int i = 0; i < categories.size(); i++) {
                double v = e.getValue()[i];
                if (Double.isNaN(v)) {
                    continue;
                }
                double bx = LEFT + slot * i + slot * 0.15 + barW * s;
                double by = y(v, yMax);
                svg.append("<rect x=\"").append(fmt(bx)).append("\" y=\"").append(fmt(by))
                        .append("\" width=\"").append(fmt(barW - 2)).append("\" height=\"")
                        .append(fmt(H - BOTTOM - by)).append("\" fill=\"").append(color).append("\"/>\n");
                svg.append("<text x=\"").append(fmt(bx + (barW - 2) / 2)).append("\" y=\"").append(fmt(by - 5))
                        .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#374151\">")
                        .append(tick(v, v)).append("</text>\n");
            }
            s++;
        }
        write(out, svg);
    }

    /** Draws title, legend, axes, grid and category labels; returns the y-axis maximum. */
    private static double frame(StringBuilder svg, String title, String xLabel, String yLabel,
                                List<String> categories, Map<String, double[]> series) {
        double max = 0;
        for (double[] values : series.values()) {
            for (double v : values) {
                if (!Double.isNaN(v)) {
                    max = Math.max(max, v);
                }
            }
        }
        double step = niceStep(max <= 0 ? 1 : max / 5);
        double yMax = Math.ceil((max <= 0 ? 1 : max) / step) * step;

        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(W).append("\" height=\"").append(H)
                .append("\" viewBox=\"0 0 ").append(W).append(' ').append(H)
                .append("\" font-family=\"Helvetica, Arial, sans-serif\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        svg.append("<text x=\"").append(LEFT).append("\" y=\"26\" font-size=\"16\" font-weight=\"bold\" fill=\"#111827\">")
                .append(esc(title)).append("</text>\n");

        double lx = LEFT;
        int s = 0;
        for (String name : series.keySet()) {
            String color = COLORS[s++ % COLORS.length];
            svg.append("<rect x=\"").append(fmt(lx)).append("\" y=\"38\" width=\"12\" height=\"12\" fill=\"")
                    .append(color).append("\"/>\n");
            svg.append("<text x=\"").append(fmt(lx + 17)).append("\" y=\"48\" font-size=\"12\" fill=\"#374151\">")
                    .append(esc(name)).append("</text>\n");
            lx += 30 + 7 * name.length();
        }

        for (double v = 0; v <= yMax + step / 2; v += step) {
            double yy = y(v, yMax);
            svg.append("<line x1=\"").append(LEFT).append("\" x2=\"").append(W - RIGHT).append("\" y1=\"")
                    .append(fmt(yy)).append("\" y2=\"").append(fmt(yy)).append("\" stroke=\"#e5e7eb\"/>\n");
            svg.append("<text x=\"").append(LEFT - 8).append("\" y=\"").append(fmt(yy + 4))
                    .append("\" text-anchor=\"end\" font-size=\"11\" fill=\"#6b7280\">").append(tick(v, step))
                    .append("</text>\n");
        }
        svg.append("<line x1=\"").append(LEFT).append("\" x2=\"").append(LEFT).append("\" y1=\"").append(TOP)
                .append("\" y2=\"").append(H - BOTTOM).append("\" stroke=\"#9ca3af\"/>\n");
        svg.append("<line x1=\"").append(LEFT).append("\" x2=\"").append(W - RIGHT).append("\" y1=\"")
                .append(H - BOTTOM).append("\" y2=\"").append(H - BOTTOM).append("\" stroke=\"#9ca3af\"/>\n");

        for (int i = 0; i < categories.size(); i++) {
            svg.append("<text x=\"").append(fmt(x(i, categories.size()))).append("\" y=\"").append(H - BOTTOM + 18)
                    .append("\" text-anchor=\"middle\" font-size=\"12\" fill=\"#374151\">")
                    .append(esc(categories.get(i))).append("</text>\n");
        }
        svg.append("<text x=\"").append((LEFT + W - RIGHT) / 2).append("\" y=\"").append(H - 16)
                .append("\" text-anchor=\"middle\" font-size=\"13\" fill=\"#111827\">").append(esc(xLabel))
                .append("</text>\n");
        svg.append("<text transform=\"translate(18,").append((TOP + H - BOTTOM) / 2)
                .append(") rotate(-90)\" text-anchor=\"middle\" font-size=\"13\" fill=\"#111827\">")
                .append(esc(yLabel)).append("</text>\n");
        return yMax;
    }

    private static double x(int i, int count) {
        double slot = (double) (W - LEFT - RIGHT) / count;
        return LEFT + slot * (i + 0.5);
    }

    private static double y(double v, double yMax) {
        return H - BOTTOM - (H - BOTTOM - TOP) * v / yMax;
    }

    private static double niceStep(double raw) {
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / mag;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return nice * mag;
    }

    private static String tick(double v, double step) {
        if (Math.abs(step) >= 1 && Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        return String.format(Locale.ROOT, Math.abs(step) >= 0.1 ? "%.1f" : "%.2f", v);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void write(Path out, StringBuilder svg) throws IOException {
        svg.append("</svg>\n");
        Files.createDirectories(out.getParent());
        Files.writeString(out, svg.toString(), StandardCharsets.UTF_8);
    }
}
