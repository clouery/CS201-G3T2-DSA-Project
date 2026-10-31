package tdtsp.experiment;

import java.util.Arrays;

/** Descriptive statistics over seeds (plan §6: mean, median, standard deviation). */
final class Stats {

    private Stats() {
    }

    static double mean(double[] xs) {
        return xs.length == 0 ? Double.NaN : Arrays.stream(xs).sum() / xs.length;
    }

    static double median(double[] xs) {
        if (xs.length == 0) {
            return Double.NaN;
        }
        double[] s = xs.clone();
        Arrays.sort(s);
        int m = s.length / 2;
        return s.length % 2 == 1 ? s[m] : (s[m - 1] + s[m]) / 2;
    }

    /** Sample standard deviation; 0 for a single value. */
    static double std(double[] xs) {
        if (xs.length < 2) {
            return 0;
        }
        double mu = mean(xs);
        double ss = 0;
        for (double x : xs) {
            ss += (x - mu) * (x - mu);
        }
        return Math.sqrt(ss / (xs.length - 1));
    }
}
