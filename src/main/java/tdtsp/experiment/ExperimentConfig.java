package tdtsp.experiment;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** An experiment's {@code config.properties} (plan §6, per-experiment folders). */
final class ExperimentConfig {

    private final Path path;
    private final Properties properties = new Properties();

    ExperimentConfig(Path path) throws IOException {
        this.path = path;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }

    String string(String key) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            throw new IllegalArgumentException("missing key " + key + " in " + path);
        }
        return raw.trim();
    }

    int integer(String key) {
        return Integer.parseInt(string(key));
    }

    List<Integer> integers(String key) {
        return Arrays.stream(string(key).split(",")).map(String::trim).map(Integer::parseInt).toList();
    }

    String text() throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).strip();
    }
}
