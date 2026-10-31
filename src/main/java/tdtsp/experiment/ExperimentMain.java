package tdtsp.experiment;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs the experiments of plan §6 from the repository root.
 *
 * <pre>
 * mvn -q compile
 * java -cp target/classes tdtsp.experiment.ExperimentMain            # all four
 * java -cp target/classes tdtsp.experiment.ExperimentMain exp1_heuristics exp4_classic_baseline
 * </pre>
 *
 * Each experiment reads {@code experiments/<id>/config.properties} and writes
 * {@code results.csv}, {@code summary.csv}, {@code plots/} and {@code README.md} there.
 */
public final class ExperimentMain {

    private static final List<String> ALL = List.of(
            Exp1Heuristics.ID, Exp2Representations.ID, Exp3Scalability.ID, Exp4ClassicBaseline.ID);

    private ExperimentMain() {
    }

    public static void main(String[] args) throws Exception {
        List<String> ids = args.length == 0 || (args.length == 1 && "all".equals(args[0])) ? ALL : List.of(args);
        for (String id : ids) {
            Path dir = Path.of("experiments", id);
            long start = System.nanoTime();
            System.out.println("Running " + id);
            Instance.SUBSTITUTIONS.clear();
            switch (id) {
                case Exp1Heuristics.ID -> Exp1Heuristics.run(dir);
                case Exp2Representations.ID -> Exp2Representations.run(dir);
                case Exp3Scalability.ID -> Exp3Scalability.run(dir);
                case Exp4ClassicBaseline.ID -> Exp4ClassicBaseline.run(dir);
                default -> throw new IllegalArgumentException("unknown experiment " + id + "; expected one of " + ALL);
            }
            System.out.printf("Finished %s in %.1f s%n", id, (System.nanoTime() - start) / 1e9);
        }
    }
}
