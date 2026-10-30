package tdtsp.network;

/**
 * A cleaned city node read from {@code data/processed/cities_*.csv}.
 */
public record City(
        int geonameId,
        String name,
        double latitude,
        double longitude,
        String featureClass,
        String featureCode,
        long population,
        String admin1) {

    public static City fromCsvRow(String[] row) {
        if (row.length != 8) {
            throw new IllegalArgumentException("expected 8 columns, got " + row.length);
        }
        return new City(
                Integer.parseInt(row[0].trim()),
                row[1].trim(),
                Double.parseDouble(row[2].trim()),
                Double.parseDouble(row[3].trim()),
                row[4].trim(),
                row[5].trim(),
                Long.parseLong(row[6].trim()),
                row[7].trim());
    }

    public String stopId() {
        return Integer.toString(geonameId);
    }
}
