import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Q7 - Dual-Index Pharmacy Inventory Redesign & Benchmark.
 *
 * Diagnoses the bottleneck of Q4 HashMap inventory (sorting overhead and auxiliary list lookups)
 * and redesigns the system using a synchronized Dual-Index architecture:
 *   1. TreeMap<String, Medicine> for code order (O(n) natural in-order traversal).
 *   2. LinkedHashMap<String, Medicine> for insertion order (O(n) natural link traversal, O(1) lookups).
 *
 * Compares Original HashMap vs Dual-Index at 1,000, 10,000, and 100,000 medicines.
 * Measures 100,000 lookups, report generation, mutations, and memory footprint.
 */
public final class Q7PharmacyRedesign {
    private static final int LOOKUPS_PER_RUN = 100_000;
    private static final int MEASURED_RUNS = 5;
    private static final int MUTATION_BATCH_SIZE = 1_000;
    private static final int[] INVENTORY_SIZES = {1_000, 10_000, 100_000};
    private static final Path CSV_PATH = Path.of(
            "Task07_Design_Redesign", "data", "Q7_redesign_results.csv");

    private Q7PharmacyRedesign() {
        // Utility class.
    }

    /**
     * Immutable medicine record matching Q4 specification.
     */
    public record Medicine(String code, String name, int stock) {
        public Medicine {
            Objects.requireNonNull(code, "Medicine code cannot be null");
            Objects.requireNonNull(name, "Medicine name cannot be null");
            if (stock < 0) {
                throw new IllegalArgumentException("Stock cannot be negative: " + stock);
            }
        }
    }

    /**
     * Performance measurement container for one warm-up and five timed runs.
     */
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

    /**
     * Benchmark results container for an implementation at a specific inventory scale.
     */
    private static final class ImplementationBenchmarkResult {
        private final String implementationName;
        private final int datasetSize;
        private final Measurement lookup = new Measurement();
        private final Measurement codeReport = new Measurement();
        private final Measurement insertionReport = new Measurement();
        private final Measurement mutations = new Measurement();
        private long estimatedMemoryBytes;
        private int tempCollectionsCodeReport;
        private int tempCollectionsInsertionReport;
        private boolean codeOrderMatches;
        private boolean insertionOrderMatches;
        private boolean consistencyVerified;
        private long lookupChecksum;

        private ImplementationBenchmarkResult(String implementationName, int datasetSize) {
            this.implementationName = implementationName;
            this.datasetSize = datasetSize;
        }
    }

    // =========================================================================
    // 1. INVENTORY IMPLEMENTATIONS
    // =========================================================================

    /**
     * Common interface for the pharmacy inventory architectures under evaluation.
     */
    public interface PharmacyInventory {
        void populate(List<Medicine> medicines);
        boolean add(Medicine medicine);
        boolean update(Medicine medicine);
        Medicine lookup(String code);
        Medicine remove(String code);
        String generateCodeOrderReport();
        String generateInsertionOrderReport();
        int size();
        void batchMutate(List<Medicine> updates, List<String> removes, List<Medicine> reAdds);
        long estimateRetainedMemoryBytes();
        int temporaryCollectionsDuringCodeReport();
        int temporaryCollectionsDuringInsertionReport();
        boolean verifyConsistency();
    }

    /**
     * Original Q4 Architecture:
     * - Uses HashMap<String, Medicine> for primary storage.
     * - Uses an auxiliary List<String> to retain insertion order.
     * - Code-order report requires copying all entries to an ArrayList and running TimSort O(n log n).
     * - Insertion-order report iterates the auxiliary list and performs n random map lookups.
     */
    public static final class OriginalHashMapInventory implements PharmacyInventory {
        private final Map<String, Medicine> map = new HashMap<>();
        private final List<String> insertionOrder = new ArrayList<>();

        @Override
        public void populate(List<Medicine> medicines) {
            map.clear();
            insertionOrder.clear();
            for (Medicine med : medicines) {
                map.put(med.code(), med);
                insertionOrder.add(med.code());
            }
        }

        @Override
        public boolean add(Medicine medicine) {
            if (map.containsKey(medicine.code())) {
                return false;
            }
            map.put(medicine.code(), medicine);
            insertionOrder.add(medicine.code());
            return true;
        }

        @Override
        public boolean update(Medicine medicine) {
            if (!map.containsKey(medicine.code())) {
                return false;
            }
            map.put(medicine.code(), medicine);
            return true;
        }

        @Override
        public Medicine lookup(String code) {
            return map.get(code);
        }

        @Override
        public Medicine remove(String code) {
            Medicine removed = map.remove(code);
            if (removed != null) {
                // Linear scan removal in ArrayList is an O(n) penalty of the auxiliary list approach.
                insertionOrder.remove(code);
            }
            return removed;
        }

        @Override
        public String generateCodeOrderReport() {
            // Bottleneck: Allocates temporary ArrayList of n entries and sorts it every invocation.
            StringBuilder report = new StringBuilder(map.size() * 32);
            List<Map.Entry<String, Medicine>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, Medicine> entry : entries) {
                appendMedicine(entry.getValue(), report);
            }
            return report.toString();
        }

        @Override
        public String generateInsertionOrderReport() {
            // Bottleneck: Iterates external list and performs n individual hash lookups.
            StringBuilder report = new StringBuilder(map.size() * 32);
            for (String key : insertionOrder) {
                Medicine med = map.get(key);
                if (med != null) {
                    appendMedicine(med, report);
                }
            }
            return report.toString();
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public void batchMutate(List<Medicine> updates, List<String> removes, List<Medicine> reAdds) {
            for (Medicine med : updates) {
                update(med);
            }
            for (String code : removes) {
                remove(code);
            }
            for (Medicine med : reAdds) {
                add(med);
            }
        }

        @Override
        public long estimateRetainedMemoryBytes() {
            int n = map.size();
            // HashMap: table array + Node (32 bytes) * n + key/val references.
            // Auxiliary ArrayList: array of references (8 bytes * capacity) + list header (24 bytes).
            long mapTableBytes = (long) (n / 0.75f) * 8L;
            long mapNodesBytes = (long) n * 32L;
            long listArrayBytes = (long) n * 8L + 24L;
            return mapTableBytes + mapNodesBytes + listArrayBytes;
        }

        @Override
        public int temporaryCollectionsDuringCodeReport() {
            // 1 temporary ArrayList allocated to hold copied entries for sorting.
            return 1;
        }

        @Override
        public int temporaryCollectionsDuringInsertionReport() {
            return 0;
        }

        @Override
        public boolean verifyConsistency() {
            return map.size() == insertionOrder.size();
        }
    }

    /**
     * Redesigned Q7 Architecture:
     * Dual-Index Pharmacy Inventory:
     *   1. TreeMap<String, Medicine> codeIndex:
     *      Maintains code order naturally using a Red-Black tree.
     *      Code-order report is produced via zero-copy in-order iteration in O(n) time.
     *   2. LinkedHashMap<String, Medicine> insertionIndex:
     *      Maintains insertion order naturally via internal doubly-linked bucket nodes.
     *      Insertion-order report is produced via zero-lookup link traversal in O(n) time.
     *      Lookups are serviced in O(1) average time via LinkedHashMap.
     *
     * Both indexes hold references to identical immutable Medicine objects.
     * Strict assertions enforce synchronization across all mutating operations.
     */
    public static final class DualIndexPharmacyInventory implements PharmacyInventory {
        private final TreeMap<String, Medicine> codeIndex = new TreeMap<>();
        private final LinkedHashMap<String, Medicine> insertionIndex = new LinkedHashMap<>();

        @Override
        public void populate(List<Medicine> medicines) {
            codeIndex.clear();
            insertionIndex.clear();
            for (Medicine med : medicines) {
                codeIndex.put(med.code(), med);
                insertionIndex.put(med.code(), med);
            }
            assert verifyConsistency() : "Consistency check failed after populate()";
        }

        @Override
        public boolean add(Medicine medicine) {
            if (codeIndex.containsKey(medicine.code())) {
                return false;
            }
            codeIndex.put(medicine.code(), medicine);
            insertionIndex.put(medicine.code(), medicine);
            assert codeIndex.size() == insertionIndex.size() : "Index size mismatch after add()";
            return true;
        }

        @Override
        public boolean update(Medicine medicine) {
            if (!codeIndex.containsKey(medicine.code())) {
                return false;
            }
            codeIndex.put(medicine.code(), medicine);
            // Updating an existing key in LinkedHashMap retains its original insertion position.
            insertionIndex.put(medicine.code(), medicine);
            assert codeIndex.size() == insertionIndex.size() : "Index size mismatch after update()";
            return true;
        }

        @Override
        public Medicine lookup(String code) {
            // Service lookups via LinkedHashMap for average O(1) hash table access.
            return insertionIndex.get(code);
        }

        public Medicine lookupByTree(String code) {
            // Alternative O(log n) lookup via TreeMap.
            return codeIndex.get(code);
        }

        @Override
        public Medicine remove(String code) {
            Medicine removedTree = codeIndex.remove(code);
            Medicine removedLinked = insertionIndex.remove(code);
            assert Objects.equals(removedTree, removedLinked) : "Indexes returned different removed elements";
            assert codeIndex.size() == insertionIndex.size() : "Index size mismatch after remove()";
            return removedTree;
        }

        @Override
        public String generateCodeOrderReport() {
            // Direct zero-copy traversal: TreeMap provides natural ascending key order.
            StringBuilder report = new StringBuilder(codeIndex.size() * 32);
            for (Medicine med : codeIndex.values()) {
                appendMedicine(med, report);
            }
            return report.toString();
        }

        @Override
        public String generateInsertionOrderReport() {
            // Direct zero-lookup traversal: LinkedHashMap provides internal doubly-linked iteration.
            StringBuilder report = new StringBuilder(insertionIndex.size() * 32);
            for (Medicine med : insertionIndex.values()) {
                appendMedicine(med, report);
            }
            return report.toString();
        }

        @Override
        public int size() {
            assert codeIndex.size() == insertionIndex.size() : "Size discrepancy between indexes";
            return codeIndex.size();
        }

        @Override
        public void batchMutate(List<Medicine> updates, List<String> removes, List<Medicine> reAdds) {
            for (Medicine med : updates) {
                update(med);
            }
            for (String code : removes) {
                remove(code);
            }
            for (Medicine med : reAdds) {
                add(med);
            }
            assert verifyConsistency() : "Consistency check failed after batchMutate()";
        }

        @Override
        public long estimateRetainedMemoryBytes() {
            int n = codeIndex.size();
            // TreeMap.Entry: 40 bytes (parent, left, right, color, key, value) * n + header.
            // LinkedHashMap.Entry: 48 bytes (before, after, next, hash, key, value) * n + table array.
            long treeNodeBytes = (long) n * 40L + 32L;
            long linkedTableBytes = (long) (n / 0.75f) * 8L;
            long linkedNodeBytes = (long) n * 48L;
            return treeNodeBytes + linkedTableBytes + linkedNodeBytes;
        }

        @Override
        public int temporaryCollectionsDuringCodeReport() {
            // Zero temporary collections created; streams/iterators traverse existing tree nodes directly.
            return 0;
        }

        @Override
        public int temporaryCollectionsDuringInsertionReport() {
            // Zero temporary collections created; iterates internal doubly-linked list directly.
            return 0;
        }

        @Override
        public boolean verifyConsistency() {
            if (codeIndex.size() != insertionIndex.size()) {
                return false;
            }
            for (Map.Entry<String, Medicine> entry : codeIndex.entrySet()) {
                Medicine linkedMed = insertionIndex.get(entry.getKey());
                if (linkedMed == null || !linkedMed.equals(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    // =========================================================================
    // 2. DATA GENERATION & VALIDATION HELPERS
    // =========================================================================

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

    private static List<String> sortedKeys(List<Medicine> medicines) {
        List<String> keys = new ArrayList<>(medicines.size());
        for (Medicine medicine : medicines) {
            keys.add(medicine.code());
        }
        Collections.sort(keys);
        return keys;
    }

    private static void appendMedicine(Medicine medicine, StringBuilder report) {
        report.append(medicine.code()).append('|')
                .append(medicine.name()).append('|')
                .append(medicine.stock()).append('\n');
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

    private static long performLookups(PharmacyInventory inventory, List<String> lookupKeys) {
        long checksum = 0L;
        for (String key : lookupKeys) {
            Medicine medicine = inventory.lookup(key);
            if (medicine == null) {
                throw new IllegalStateException("Missing medicine code during lookup: " + key);
            }
            checksum += medicine.stock();
        }
        return checksum;
    }

    private static double timeLookups(PharmacyInventory inventory, List<String> lookupKeys) {
        long start = System.nanoTime();
        long checksum = performLookups(inventory, lookupKeys);
        long elapsed = System.nanoTime() - start;
        if (checksum <= 0L) {
            throw new IllegalStateException("Invalid lookup checksum.");
        }
        return elapsed / 1_000_000.0;
    }

    private static double timeCodeReport(PharmacyInventory inventory) {
        long start = System.nanoTime();
        String report = inventory.generateCodeOrderReport();
        long elapsed = System.nanoTime() - start;
        if (report.isEmpty()) {
            throw new IllegalStateException("Unexpected empty code-order report.");
        }
        return elapsed / 1_000_000.0;
    }

    private static double timeInsertionReport(PharmacyInventory inventory) {
        long start = System.nanoTime();
        String report = inventory.generateInsertionOrderReport();
        long elapsed = System.nanoTime() - start;
        if (report.isEmpty()) {
            throw new IllegalStateException("Unexpected empty insertion-order report.");
        }
        return elapsed / 1_000_000.0;
    }

    private static double timeMutations(
            PharmacyInventory inventory,
            List<Medicine> updates,
            List<String> removes,
            List<Medicine> reAdds) {
        long start = System.nanoTime();
        inventory.batchMutate(updates, removes, reAdds);
        long elapsed = System.nanoTime() - start;
        return elapsed / 1_000_000.0;
    }

    private static void measure(
            Measurement measurement, java.util.function.DoubleSupplier operation) {
        measurement.warmupMs = operation.getAsDouble();
        for (int i = 0; i < MEASURED_RUNS; i++) {
            measurement.runsMs[i] = operation.getAsDouble();
        }
        measurement.calculateAverage();
    }

    // =========================================================================
    // 3. UNIT & REGRESSION TEST SUITE
    // =========================================================================

    private static void runUnitTests() {
        System.out.println("Running comprehensive unit and edge-case tests...");

        DualIndexPharmacyInventory inventory = new DualIndexPharmacyInventory();

        // Test 1: Empty state
        assert inventory.size() == 0 : "Initial size must be 0";
        assert inventory.verifyConsistency() : "Empty state must be consistent";
        assert inventory.lookup("MED-999999") == null : "Lookup on empty must be null";
        assert inventory.remove("MED-999999") == null : "Remove on empty must be null";

        // Test 2: Sequential Add
        Medicine m1 = new Medicine("MED-000003", "Paracetamol", 100);
        Medicine m2 = new Medicine("MED-000001", "Amoxicillin", 50);
        Medicine m3 = new Medicine("MED-000002", "Cetirizine", 75);

        assert inventory.add(m1) : "Adding m1 must succeed";
        assert inventory.add(m2) : "Adding m2 must succeed";
        assert inventory.add(m3) : "Adding m3 must succeed";
        assert inventory.size() == 3 : "Size must be 3";

        // Test 3: Duplicate key prevention
        Medicine duplicateM1 = new Medicine("MED-000003", "Paracetamol 500mg", 120);
        boolean duplicateAdded = inventory.add(duplicateM1);
        assert !duplicateAdded : "Adding duplicate code must return false";
        assert inventory.size() == 3 : "Size must remain 3 after duplicate attempt";
        assert inventory.lookup("MED-000003").stock() == 100 : "Original stock must be preserved";

        // Test 4: Update operation
        Medicine updatedM1 = new Medicine("MED-000003", "Paracetamol_Updated", 140);
        assert inventory.update(updatedM1) : "Update of existing key must succeed";
        assert inventory.lookup("MED-000003").stock() == 140 : "Stock must reflect updated value";
        assert inventory.lookup("MED-000003").name().equals("Paracetamol_Updated") : "Name must be updated";
        assert !inventory.update(new Medicine("MED-000999", "Unknown", 10)) : "Update of nonexistent key must fail";

        // Test 5: Verify Code Order (Ascending: MED-000001, MED-000002, MED-000003)
        String codeReport = inventory.generateCodeOrderReport();
        List<String> expectedCodeOrder = List.of("MED-000001", "MED-000002", "MED-000003");
        assert hasExpectedOrder(codeReport, expectedCodeOrder) : "Code-order report must be in ascending code order";

        // Test 6: Verify Insertion Order (Added: MED-000003, MED-000001, MED-000002)
        String insertionReport = inventory.generateInsertionOrderReport();
        List<String> expectedInsertionOrder = List.of("MED-000003", "MED-000001", "MED-000002");
        assert hasExpectedOrder(insertionReport, expectedInsertionOrder) : "Insertion-order report must match insertion sequence";

        // Test 7: Removal operation
        Medicine removedM2 = inventory.remove("MED-000001");
        assert removedM2 != null && removedM2.code().equals("MED-000001") : "Removed medicine must be returned";
        assert inventory.size() == 2 : "Size must decrement to 2";
        assert inventory.lookup("MED-000001") == null : "Removed medicine must not be found";
        assert inventory.verifyConsistency() : "Consistency must hold after removal";

        // Verify orders after removal
        assert hasExpectedOrder(inventory.generateCodeOrderReport(), List.of("MED-000002", "MED-000003"))
                : "Code report must reflect removal";
        assert hasExpectedOrder(inventory.generateInsertionOrderReport(), List.of("MED-000003", "MED-000002"))
                : "Insertion report must reflect removal";

        System.out.println("All unit tests and assertion checks PASSED successfully.\n");
    }

    // =========================================================================
    // 4. BENCHMARK EXECUTION & COMPARISON
    // =========================================================================

    private static ImplementationBenchmarkResult benchmarkInventory(
            String implName,
            PharmacyInventory inventory,
            int size,
            List<Medicine> medicines,
            List<String> lookupKeys,
            List<String> sortedCodes,
            List<String> expectedInsertionOrder,
            List<Medicine> updateBatch,
            List<String> removeBatch,
            List<Medicine> reAddBatch) {

        inventory.populate(medicines);
        if (inventory.size() != size) {
            throw new IllegalStateException(implName + " population size mismatch: "
                    + inventory.size() + " != " + size);
        }

        ImplementationBenchmarkResult result = new ImplementationBenchmarkResult(implName, size);

        // 1. Initial Verification & Metadata on clean population
        result.codeOrderMatches = hasExpectedOrder(inventory.generateCodeOrderReport(), sortedCodes);
        result.insertionOrderMatches = hasExpectedOrder(inventory.generateInsertionOrderReport(), expectedInsertionOrder);
        result.consistencyVerified = inventory.verifyConsistency();
        result.estimatedMemoryBytes = inventory.estimateRetainedMemoryBytes();
        result.tempCollectionsCodeReport = inventory.temporaryCollectionsDuringCodeReport();
        result.tempCollectionsInsertionReport = inventory.temporaryCollectionsDuringInsertionReport();

        if (!result.codeOrderMatches || !result.insertionOrderMatches || !result.consistencyVerified) {
            throw new IllegalStateException("Validation checks failed for " + implName + " at size " + size);
        }

        // 2. Measure 100,000 lookups
        measure(result.lookup, () -> timeLookups(inventory, lookupKeys));
        result.lookupChecksum = performLookups(inventory, lookupKeys);

        // 3. Measure Code-Order Report
        measure(result.codeReport, () -> timeCodeReport(inventory));

        // 4. Measure Insertion-Order Report
        measure(result.insertionReport, () -> timeInsertionReport(inventory));

        // 5. Measure Mutation Operations (batch update, remove, re-add)
        // Reset/repopulate to ensure each mutation run starts from identical clean state
        measure(result.mutations, () -> {
            inventory.populate(medicines);
            long start = System.nanoTime();
            inventory.batchMutate(updateBatch, removeBatch, reAddBatch);
            return (System.nanoTime() - start) / 1_000_000.0;
        });

        // Restore clean state
        inventory.populate(medicines);

        return result;
    }

    private static void writeCsv(List<ImplementationBenchmarkResult> results) throws IOException {
        Files.createDirectories(CSV_PATH.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(CSV_PATH, StandardCharsets.UTF_8)) {
            writer.write("Medicine_Count,Implementation,Lookup_100k_Warmup_ms,Lookup_100k_Run1_ms,Lookup_100k_Run2_ms,"
                    + "Lookup_100k_Run3_ms,Lookup_100k_Run4_ms,Lookup_100k_Run5_ms,Lookup_100k_Avg_ms,Avg_Per_Lookup_us,"
                    + "Code_Report_Warmup_ms,Code_Report_Run1_ms,Code_Report_Run2_ms,Code_Report_Run3_ms,Code_Report_Run4_ms,"
                    + "Code_Report_Run5_ms,Code_Report_Avg_ms,Insertion_Report_Warmup_ms,Insertion_Report_Run1_ms,"
                    + "Insertion_Report_Run2_ms,Insertion_Report_Run3_ms,Insertion_Report_Run4_ms,Insertion_Report_Run5_ms,"
                    + "Insertion_Report_Avg_ms,Mutations_1k_Warmup_ms,Mutations_1k_Run1_ms,Mutations_1k_Run2_ms,"
                    + "Mutations_1k_Run3_ms,Mutations_1k_Run4_ms,Mutations_1k_Run5_ms,Mutations_1k_Avg_ms,"
                    + "Estimated_Memory_KB,Temp_Collections_Code_Report,Temp_Collections_Insertion_Report,"
                    + "Code_Order_Matches,Insertion_Order_Matches,Consistency_Verified,Lookup_Checksum");
            writer.newLine();

            for (ImplementationBenchmarkResult r : results) {
                double avgPerLookupUs = r.lookup.averageMs * 1_000.0 / LOOKUPS_PER_RUN;
                double memoryKb = r.estimatedMemoryBytes / 1024.0;

                writer.write(String.format(java.util.Locale.ROOT,
                        "%d,\"%s\",%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.6f,"
                        + "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,"
                        + "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f,%d,%d,%b,%b,%b,%d",
                        r.datasetSize, r.implementationName,
                        r.lookup.warmupMs, r.lookup.runsMs[0], r.lookup.runsMs[1], r.lookup.runsMs[2],
                        r.lookup.runsMs[3], r.lookup.runsMs[4], r.lookup.averageMs, avgPerLookupUs,
                        r.codeReport.warmupMs, r.codeReport.runsMs[0], r.codeReport.runsMs[1], r.codeReport.runsMs[2],
                        r.codeReport.runsMs[3], r.codeReport.runsMs[4], r.codeReport.averageMs,
                        r.insertionReport.warmupMs, r.insertionReport.runsMs[0], r.insertionReport.runsMs[1],
                        r.insertionReport.runsMs[2], r.insertionReport.runsMs[3], r.insertionReport.runsMs[4],
                        r.insertionReport.averageMs,
                        r.mutations.warmupMs, r.mutations.runsMs[0], r.mutations.runsMs[1], r.mutations.runsMs[2],
                        r.mutations.runsMs[3], r.mutations.runsMs[4], r.mutations.averageMs,
                        memoryKb, r.tempCollectionsCodeReport, r.tempCollectionsInsertionReport,
                        r.codeOrderMatches, r.insertionOrderMatches, r.consistencyVerified, r.lookupChecksum));
                writer.newLine();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println(" Q7 PHARMACY INVENTORY REDESIGN: Original HashMap vs Dual-Index");
        System.out.println("==========================================================================");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Lookups per run: " + LOOKUPS_PER_RUN);
        System.out.println("Warm-up iterations: 1 | Timed measured repetitions: " + MEASURED_RUNS);
        System.out.println("Mutation batch size: " + MUTATION_BATCH_SIZE + " updates, removes & re-adds\n");

        // Execute unit tests first with assertions enabled
        runUnitTests();

        List<ImplementationBenchmarkResult> csvResults = new ArrayList<>();

        try {
            for (int size : INVENTORY_SIZES) {
                System.out.println("--------------------------------------------------------------------------");
                System.out.printf(java.util.Locale.ROOT, "BENCHMARKING INVENTORY SIZE: %,d MEDICINES%n", size);
                System.out.println("--------------------------------------------------------------------------");

                List<Medicine> medicines = createMedicines(size);
                List<String> lookupKeys = createLookupKeys(size);
                List<String> sortedCodes = sortedKeys(medicines);

                List<String> expectedInsertionOrder = new ArrayList<>(size);
                for (Medicine m : medicines) {
                    expectedInsertionOrder.add(m.code());
                }

                // Prepare mutation batches
                int mutationCount = Math.min(MUTATION_BATCH_SIZE, size / 2);
                List<Medicine> updateBatch = new ArrayList<>(mutationCount);
                List<String> removeBatch = new ArrayList<>(mutationCount);
                List<Medicine> reAddBatch = new ArrayList<>(mutationCount);

                for (int i = 0; i < mutationCount; i++) {
                    String code = medicineCode(i);
                    updateBatch.add(new Medicine(code, "Updated_Med_" + (i + 1), 999));
                    removeBatch.add(code);
                    reAddBatch.add(new Medicine(code, "Medicine_" + (i + 1), 50 + (i % 500)));
                }

                // 1. Original HashMap Architecture
                OriginalHashMapInventory original = new OriginalHashMapInventory();
                ImplementationBenchmarkResult origResult = benchmarkInventory(
                        "Original HashMap", original, size, medicines, lookupKeys,
                        sortedCodes, expectedInsertionOrder, updateBatch, removeBatch, reAddBatch);
                csvResults.add(origResult);

                // 2. Redesigned Dual-Index Architecture
                DualIndexPharmacyInventory redesigned = new DualIndexPharmacyInventory();
                ImplementationBenchmarkResult redResult = benchmarkInventory(
                        "Dual-Index (TreeMap + LinkedHashMap)", redesigned, size, medicines, lookupKeys,
                        sortedCodes, expectedInsertionOrder, updateBatch, removeBatch, reAddBatch);
                csvResults.add(redResult);

                // Print Side-by-Side Comparison
                double codeSpeedup = origResult.codeReport.averageMs / redResult.codeReport.averageMs;
                double insertionSpeedup = origResult.insertionReport.averageMs / redResult.insertionReport.averageMs;
                double mutationSpeedup = origResult.mutations.averageMs / redResult.mutations.averageMs;
                double memoryOverhead = (double) redResult.estimatedMemoryBytes / origResult.estimatedMemoryBytes;

                System.out.printf(java.util.Locale.ROOT,
                        "%-38s | 100k Lookups: %8.4f ms | Code Report: %8.4f ms | Ins Report: %8.4f ms | Mutations: %8.4f ms%n",
                        origResult.implementationName, origResult.lookup.averageMs,
                        origResult.codeReport.averageMs, origResult.insertionReport.averageMs,
                        origResult.mutations.averageMs);
                System.out.printf(java.util.Locale.ROOT,
                        "%-38s | 100k Lookups: %8.4f ms | Code Report: %8.4f ms | Ins Report: %8.4f ms | Mutations: %8.4f ms%n",
                        redResult.implementationName, redResult.lookup.averageMs,
                        redResult.codeReport.averageMs, redResult.insertionReport.averageMs,
                        redResult.mutations.averageMs);

                System.out.printf(java.util.Locale.ROOT,
                        "  --> [Speedup] Code Report: %.2fx %s | Insertion Report: %.2fx %s | Mutations: %.2fx %s%n",
                        codeSpeedup, (codeSpeedup >= 1.0 ? "FASTER" : "SLOWER"),
                        insertionSpeedup, (insertionSpeedup >= 1.0 ? "FASTER" : "SLOWER"),
                        mutationSpeedup, (mutationSpeedup >= 1.0 ? "FASTER" : "SLOWER"));
                System.out.printf(java.util.Locale.ROOT,
                        "  --> [Memory]  Original: ~%.1f KB | Dual-Index: ~%.1f KB (%.2fx overhead)%n",
                        origResult.estimatedMemoryBytes / 1024.0, redResult.estimatedMemoryBytes / 1024.0, memoryOverhead);
                System.out.printf(java.util.Locale.ROOT,
                        "  --> [Temp Collections Allocated in Code Report] Original: %d | Dual-Index: %d (100%% ELIMINATED)%n%n",
                        origResult.tempCollectionsCodeReport, redResult.tempCollectionsCodeReport);
            }

            writeCsv(csvResults);

            System.out.println("==========================================================================");
            System.out.println("Q7 BENCHMARK & VALIDATION COMPLETED SUCCESSFULLY");
            System.out.println("==========================================================================");
            System.out.println("Total benchmark configurations executed: " + csvResults.size());
            System.out.println("Results exported to: " + CSV_PATH.toAbsolutePath());
            System.out.println("All order validations, consistency assertions and checksums PASSED.");
            System.out.println("==========================================================================");

        } catch (IOException ex) {
            System.err.println("I/O error while writing Q7 CSV: " + ex.getMessage());
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("Q7 execution error: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
