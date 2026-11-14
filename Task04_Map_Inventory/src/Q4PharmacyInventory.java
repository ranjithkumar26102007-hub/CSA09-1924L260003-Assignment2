import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Q4 - Pharmacy inventory map comparison.
 * Compares HashMap, TreeMap and LinkedHashMap using identical medicine data.
 * Lookup and report timings use one warm-up and five measured repetitions.
 */
public final class Q4PharmacyInventory {
    private static final int LOOKUPS_PER_RUN = 100_000;
    private static final int MEASURED_RUNS = 5;
    private static final double ACCEPTABLE_LOOKUP_MS = 1.0;
    private static final int[] INVENTORY_SIZES = {1_000, 10_000, 100_000};
    private static final Path CSV_PATH = Path.of(
            "Task04_Map_Inventory", "data", "Q4_benchmark_results.csv");

    private Q4PharmacyInventory() {
        // Utility class.
    }

    private record Medicine(String code, String name, int stock) { }

    private static final class Measurement {
        private double warmupMs;
        private final double[] runsMs = new double[MEASURED_RUNS];
        private double averageMs;

        private void calculateAverage() {
            double sum = 0.0;
            for (double value : runsMs) {
                sum += value;
            }
            averageMs = sum / runsMs.length;
        }
    }

    private static final class MapMeasurements {
        private final String mapName;
        private final Measurement lookup = new Measurement();
        private final Measurement codeReport = new Measurement();
        private final Measurement insertionReport = new Measurement();
        private boolean codeOrderMatches;
        private boolean insertionOrderMatches;
        private long lookupChecksum;

        private MapMeasurements(String mapName) {
            this.mapName = mapName;
        }
    }

    private static String medicineCode(int index) {
        return String.format("MED-%06d", index + 1);
    }

    private static List<Medicine> createMedicines(int size) {
        List<Medicine> medicines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            medicines.add(new Medicine(
                    medicineCode(i),
                    "Medicine_" + (i + 1),
                    50 + (i % 500)));
        }
        return medicines;
    }

    private static List<String> createLookupKeys(int size) {
        List<String> keys = new ArrayList<>(LOOKUPS_PER_RUN);
        for (int i = 0; i < LOOKUPS_PER_RUN; i++) {
            int index = (i * 31 + 7) % size;
            keys.add(medicineCode(index));
        }
        return keys;
    }

    private static <M extends Map<String, Medicine>> M populate(
            M map, List<Medicine> medicines, List<String> insertionOrder) {
        for (Medicine medicine : medicines) {
            map.put(medicine.code(), medicine);
            insertionOrder.add(medicine.code());
        }
        return map;
    }

    private static long performLookups(
            Map<String, Medicine> map, List<String> lookupKeys) {
        long checksum = 0L;
        for (String key : lookupKeys) {
            Medicine medicine = map.get(key);
            if (medicine == null) {
                throw new IllegalStateException("Missing medicine code: " + key);
            }
            checksum += medicine.stock();
        }
        return checksum;
    }

    private static double timeLookups(
            Map<String, Medicine> map, List<String> lookupKeys) {
        long start = System.nanoTime();
        long checksum = performLookups(map, lookupKeys);
        long elapsed = System.nanoTime() - start;
        // Keep the result observable and validate that every lookup succeeded.
        if (checksum <= 0L) {
            throw new IllegalStateException("Unexpected lookup checksum.");
        }
        return elapsed / 1_000_000.0;
    }

    private static String appendMedicine(Medicine medicine, StringBuilder report) {
        report.append(medicine.code()).append('|')
                .append(medicine.name()).append('|')
                .append(medicine.stock()).append('\n');
        return medicine.code();
    }

    /** Builds a medicine report in ascending medicine-code order. */
    private static String buildCodeOrderReport(Map<String, Medicine> map) {
        StringBuilder report = new StringBuilder(map.size() * 32);
        if (map instanceof TreeMap<?, ?>) {
            for (Map.Entry<String, Medicine> entry : map.entrySet()) {
                appendMedicine(entry.getValue(), report);
            }
        } else {
            List<Map.Entry<String, Medicine>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, Medicine> entry : entries) {
                appendMedicine(entry.getValue(), report);
            }
        }
        return report.toString();
    }

    /**
     * Builds an insertion-order report. LinkedHashMap supplies that order itself;
     * HashMap and TreeMap require the auxiliary list of keys recorded at insertion.
     */
    private static String buildInsertionOrderReport(
            Map<String, Medicine> map, List<String> insertionOrder) {
        StringBuilder report = new StringBuilder(map.size() * 32);
        if (map instanceof LinkedHashMap<?, ?>) {
            for (Map.Entry<String, Medicine> entry : map.entrySet()) {
                appendMedicine(entry.getValue(), report);
            }
        } else {
            for (String key : insertionOrder) {
                Medicine medicine = map.get(key);
                if (medicine == null) {
                    throw new IllegalStateException("Missing medicine during insertion report: " + key);
                }
                appendMedicine(medicine, report);
            }
        }
        return report.toString();
    }

    private static double timeCodeReport(Map<String, Medicine> map) {
        long start = System.nanoTime();
        String report = buildCodeOrderReport(map);
        long elapsed = System.nanoTime() - start;
        if (report.isEmpty()) {
            throw new IllegalStateException("Unexpected empty code-order report.");
        }
        return elapsed / 1_000_000.0;
    }

    private static double timeInsertionReport(
            Map<String, Medicine> map, List<String> insertionOrder) {
        long start = System.nanoTime();
        String report = buildInsertionOrderReport(map, insertionOrder);
        long elapsed = System.nanoTime() - start;
        if (report.isEmpty()) {
            throw new IllegalStateException("Unexpected empty insertion-order report.");
        }
        return elapsed / 1_000_000.0;
    }

    private static boolean hasExpectedOrder(String report, List<String> expectedKeys) {
        String[] lines = report.split("\\n");
        if (lines.length != expectedKeys.size()) {
            return false;
        }
        for (int i = 0; i < lines.length; i++) {
            int delimiter = lines[i].indexOf('|');
            if (delimiter <= 0 || !lines[i].substring(0, delimiter).equals(expectedKeys.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> sortedKeys(List<Medicine> medicines) {
        List<String> keys = new ArrayList<>(medicines.size());
        for (Medicine medicine : medicines) {
            keys.add(medicine.code());
        }
        Collections.sort(keys);
        return keys;
    }

    private static void measure(
            Measurement measurement, java.util.function.DoubleSupplier operation) {
        measurement.warmupMs = operation.getAsDouble();
        for (int i = 0; i < MEASURED_RUNS; i++) {
            measurement.runsMs[i] = operation.getAsDouble();
        }
        measurement.calculateAverage();
    }

    private static String csvEscape(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String csvMeasurementColumns(Measurement measurement) {
        StringBuilder result = new StringBuilder();
        result.append(String.format(java.util.Locale.ROOT, "%.4f", measurement.warmupMs));
        for (double run : measurement.runsMs) {
            result.append(',').append(String.format(java.util.Locale.ROOT, "%.4f", run));
        }
        result.append(',').append(String.format(java.util.Locale.ROOT, "%.4f", measurement.averageMs));
        return result.toString();
    }

    private static void writeCsv(List<String> rows) throws IOException {
        Files.createDirectories(CSV_PATH.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
                CSV_PATH, StandardCharsets.UTF_8)) {
            writer.write("Medicine_Count,Map_Type,Lookup_Count,Lookup_Warmup_ms,"
                    + "Lookup_Run1_ms,Lookup_Run2_ms,Lookup_Run3_ms,Lookup_Run4_ms,"
                    + "Lookup_Run5_ms,Lookup_Average_ms,Average_Per_Lookup_us,"
                    + "Code_Report_Warmup_ms,Code_Report_Run1_ms,Code_Report_Run2_ms,"
                    + "Code_Report_Run3_ms,Code_Report_Run4_ms,Code_Report_Run5_ms,"
                    + "Code_Report_Average_ms,Insertion_Report_Warmup_ms,"
                    + "Insertion_Report_Run1_ms,Insertion_Report_Run2_ms,"
                    + "Insertion_Report_Run3_ms,Insertion_Report_Run4_ms,"
                    + "Insertion_Report_Run5_ms,Insertion_Report_Average_ms,"
                    + "Code_Order_Matches,Insertion_Order_Matches,Lookup_Checksum");
            writer.newLine();
            for (String row : rows) {
                writer.write(row);
                writer.newLine();
            }
        }
    }

    private static Map<String, Medicine> newMap(String mapName) {
        return switch (mapName) {
            case "HashMap" -> new HashMap<>();
            case "TreeMap" -> new TreeMap<>();
            case "LinkedHashMap" -> new LinkedHashMap<>();
            default -> throw new IllegalArgumentException("Unknown map: " + mapName);
        };
    }

    private static MapMeasurements runForMap(
            String mapName,
            int size,
            List<Medicine> medicines,
            List<String> lookupKeys,
            List<String> insertionOrder,
            List<String> sortedMedicineCodes) {
        Map<String, Medicine> map = newMap(mapName);
        populate(map, medicines, insertionOrder);
        if (map.size() != size) {
            throw new IllegalStateException(mapName + " contains " + map.size()
                    + " records instead of " + size);
        }

        MapMeasurements result = new MapMeasurements(mapName);
        measure(result.lookup, () -> timeLookups(map, lookupKeys));
        measure(result.codeReport, () -> timeCodeReport(map));
        measure(result.insertionReport, () -> timeInsertionReport(map, insertionOrder));

        result.lookupChecksum = performLookups(map, lookupKeys);
        result.codeOrderMatches = hasExpectedOrder(
                buildCodeOrderReport(map), sortedMedicineCodes);
        result.insertionOrderMatches = hasExpectedOrder(
                buildInsertionOrderReport(map, insertionOrder), insertionOrder);

        if (!result.codeOrderMatches || !result.insertionOrderMatches) {
            throw new IllegalStateException("Report order validation failed for " + mapName
                    + " with " + size + " medicines.");
        }
        return result;
    }

    private static String toCsvRow(int size, MapMeasurements m) {
        double averagePerLookupUs = m.lookup.averageMs * 1_000.0 / LOOKUPS_PER_RUN;
        return size + "," + csvEscape(m.mapName) + "," + LOOKUPS_PER_RUN + ","
                + csvMeasurementColumns(m.lookup) + ","
                + String.format(java.util.Locale.ROOT, "%.6f", averagePerLookupUs) + ","
                + csvMeasurementColumns(m.codeReport) + ","
                + csvMeasurementColumns(m.insertionReport) + ","
                + m.codeOrderMatches + "," + m.insertionOrderMatches + ","
                + m.lookupChecksum;
    }

    private static void printMeasurement(String label, Measurement m) {
        System.out.printf(java.util.Locale.ROOT,
                "  %-22s warm-up %9.4f ms | average %9.4f ms | runs %s%n",
                label, m.warmupMs, m.averageMs, formatRuns(m.runsMs));
    }

    private static String formatRuns(double[] values) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(String.format(java.util.Locale.ROOT, "%.4f", values[i]));
        }
        return text.append(']').toString();
    }

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println(" Q4 PHARMACY INVENTORY: HashMap vs TreeMap vs LinkedHashMap");
        System.out.println("===============================================================");
        System.out.println("Lookups per measured run: " + LOOKUPS_PER_RUN);
        System.out.println("Warm-up: 1 | Measured repetitions: " + MEASURED_RUNS);
        System.out.printf(java.util.Locale.ROOT,
                "Acceptable average response time criterion: < %.2f ms per lookup%n",
                ACCEPTABLE_LOOKUP_MS);

        List<String> csvRows = new ArrayList<>();
        String[] mapNames = {"HashMap", "TreeMap", "LinkedHashMap"};

        try {
            for (int size : INVENTORY_SIZES) {
                System.out.println("\n---------------------------------------------------------------");
                System.out.println("INVENTORY SIZE: " + String.format(java.util.Locale.ROOT, "%,d", size));
                System.out.println("---------------------------------------------------------------");

                List<Medicine> medicines = createMedicines(size);
                List<String> lookupKeys = createLookupKeys(size);
                List<String> sortedCodes = sortedKeys(medicines);

                for (String mapName : mapNames) {
                    // Each map records insertion order during its own population step.
                    List<String> mapInsertionOrder = new ArrayList<>(size);
                    MapMeasurements result = runForMap(mapName, size, medicines,
                            lookupKeys, mapInsertionOrder, sortedCodes);

                    double averagePerLookupMs = result.lookup.averageMs / LOOKUPS_PER_RUN;
                    System.out.printf(java.util.Locale.ROOT,
                            "\n%s | 100,000 lookups avg: %.4f ms | %.4f us/lookup | %s%n",
                            mapName, result.lookup.averageMs,
                            averagePerLookupMs * 1_000.0,
                            averagePerLookupMs < ACCEPTABLE_LOOKUP_MS
                                    ? "WITHIN 1 ms criterion" : "ABOVE 1 ms criterion");
                    printMeasurement("Code-order report", result.codeReport);
                    printMeasurement("Insertion-order report", result.insertionReport);
                    System.out.println("  Code-order report correct: " + result.codeOrderMatches);
                    System.out.println("  Insertion-order report correct: " + result.insertionOrderMatches);
                    csvRows.add(toCsvRow(size, result));
                }
            }

            writeCsv(csvRows);
            System.out.println("\n===============================================================");
            System.out.println("Q4 VALIDATION SUMMARY");
            System.out.println("===============================================================");
            System.out.println("Map/size combinations measured: " + csvRows.size());
            System.out.println("Expected combinations: 9 (3 map types x 3 inventory sizes)");
            System.out.println("Each combination: 100,000 lookups, 1 warm-up + 5 measured runs");
            System.out.println("All code-order and insertion-order report checks passed.");
            System.out.println("CSV exported: " + CSV_PATH.toAbsolutePath());
            System.out.println("Q4 PHARMACY INVENTORY BENCHMARK COMPLETED SUCCESSFULLY");
            System.out.println("===============================================================");
        } catch (IOException ex) {
            System.err.println("Could not write Q4 CSV: " + ex.getMessage());
            System.exit(1);
        } catch (RuntimeException ex) {
            System.err.println("Q4 validation/execution failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
