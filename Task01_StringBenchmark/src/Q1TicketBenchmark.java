import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q1TicketBenchmark - Railway E-Ticket String Construction Benchmark
 * 
 * Compares three string building approaches under three distinct workloads:
 *  1. String concatenation (+)
 *  2. StringBuilder (unsynchronized, fast)
 *  3. StringBuffer (synchronized, thread-safe)
 * 
 * Also demonstrates a concurrent scenario with thread-local StringBuilder
 * formatting and a shared StringBuffer booking log.
 */
public class Q1TicketBenchmark {

    // Workload definitions
    static class Workload {
        final String name;
        final int ticketCount;
        final int fieldCount;
        final List<String[]> tickets;

        Workload(String name, int ticketCount, int fieldCount) {
            this.name = name;
            this.ticketCount = ticketCount;
            this.fieldCount = fieldCount;
            this.tickets = generateDataset(ticketCount, fieldCount);
        }

        private static List<String[]> generateDataset(int tickets, int fields) {
            List<String[]> list = new ArrayList<>(tickets);
            for (int t = 0; t < tickets; t++) {
                String[] f = new String[fields];
                for (int i = 0; i < fields; i++) {
                    switch (i) {
                        case 0 -> f[i] = "PNR:" + (8421000000L + t);
                        case 1 -> f[i] = "TRAIN:12675-KOVAI_EXP";
                        case 2 -> f[i] = "PASSENGER:Passenger_" + t;
                        case 3 -> f[i] = "COACH:B" + ((t % 12) + 1);
                        case 4 -> f[i] = "BERTH:" + ((t % 72) + 1);
                        case 5 -> f[i] = "FARE:INR" + (450 + (t % 500));
                        default -> f[i] = "F" + i + ":VAL_" + t + "_" + i;
                    }
                }
                list.add(f);
            }
            return list;
        }
    }

    // Benchmark result container
    static class BenchmarkResult {
        String workload;
        String method;
        double warmupMs;
        double[] runMs = new double[5];
        double avgMs;
        long estimatedIntermediateObjects;
        long approxMemoryDeltaBytes;

        BenchmarkResult(String workload, String method, long estObjects) {
            this.workload = workload;
            this.method = method;
            this.estimatedIntermediateObjects = estObjects;
        }

        void computeAverage() {
            double sum = 0;
            for (double ms : runMs) {
                sum += ms;
            }
            this.avgMs = sum / runMs.length;
        }
    }

    // Approach 1: String concatenation with +
    public static String buildWithPlus(String[] fields) {
        String ticket = "";
        for (int i = 0; i < fields.length; i++) {
            ticket = ticket + fields[i] + (i < fields.length - 1 ? " | " : "");
        }
        return ticket;
    }

    // Approach 2: StringBuilder
    public static String buildWithStringBuilder(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            sb.append(fields[i]);
            if (i < fields.length - 1) {
                sb.append(" | ");
            }
        }
        return sb.toString();
    }

    // Approach 3: StringBuffer
    public static String buildWithStringBuffer(String[] fields) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < fields.length; i++) {
            sb.append(fields[i]);
            if (i < fields.length - 1) {
                sb.append(" | ");
            }
        }
        return sb.toString();
    }

    // Functional interface for ticket generation
    @FunctionalInterface
    interface TicketBuilder {
        String build(String[] fields);
    }

    /**
     * Executes benchmark for a given method across 1 warmup + 5 measured runs.
     */
    private static BenchmarkResult runBenchmark(Workload workload, String methodName, 
                                                TicketBuilder builder, long estObjectsPerTicket) {
        long totalEstObjects = estObjectsPerTicket * workload.ticketCount;
        BenchmarkResult res = new BenchmarkResult(workload.name, methodName, totalEstObjects);

        // Pre-run garbage collection to stabilize memory reading
        suggestGc();
        long memBefore = getUsedMemory();

        // Warm-up run (not included in measured average)
        long tStartWarm = System.nanoTime();
        for (String[] ticketFields : workload.tickets) {
            builder.build(ticketFields);
        }
        long tEndWarm = System.nanoTime();
        res.warmupMs = (tEndWarm - tStartWarm) / 1_000_000.0;

        // 5 Measured runs
        for (int r = 0; r < 5; r++) {
            long tStart = System.nanoTime();
            for (String[] ticketFields : workload.tickets) {
                builder.build(ticketFields);
            }
            long tEnd = System.nanoTime();
            res.runMs[r] = (tEnd - tStart) / 1_000_000.0;
        }

        long memAfter = getUsedMemory();
        res.approxMemoryDeltaBytes = Math.max(0, memAfter - memBefore);
        res.computeAverage();

        return res;
    }

    /**
     * Approximate memory usage via Runtime.
     * Note: This is an estimation subject to GC activity and JIT compilation.
     */
    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void suggestGc() {
        System.gc();
        try {
            Thread.sleep(80);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Verifies that all 3 methods produce identical character sequences.
     */
    public static void verifyContentEquivalence(Workload workload) {
        System.out.println("=== Verifying Content Equivalence ===");
        String[] sample = workload.tickets.get(0);
        String sPlus = buildWithPlus(sample);
        String sBuilder = buildWithStringBuilder(sample);
        String sBuffer = buildWithStringBuffer(sample);

        boolean match1 = sPlus.equals(sBuilder);
        boolean match2 = sBuilder.equals(sBuffer);

        if (match1 && match2) {
            System.out.println("SUCCESS: All three methods generate 100% IDENTICAL ticket output.");
            System.out.println("Sample output (first 100 chars): " 
                + sBuilder.substring(0, Math.min(100, sBuilder.length())) + "...");
        } else {
            System.err.println("FAILURE: Content discrepancy detected!");
            System.exit(1);
        }
        System.out.println();
    }

    /**
     * Demonstrates multithreaded ticket generation:
     * - Each thread formats tickets using its own local StringBuilder (fast, unshared)
     * - Each thread safely appends complete records to a shared StringBuffer log (synchronized)
     * - Verifies exactly 1,000 records are written without data corruption.
     */
    public static void demonstrateMultithreadedLog() {
        System.out.println("=== Multithreaded Booking Log Demonstration ===");
        final int TOTAL_RECORDS = 1000;
        final int NUM_THREADS = 10;
        final int RECORDS_PER_THREAD = TOTAL_RECORDS / NUM_THREADS;

        StringBuffer sharedLog = new StringBuffer();
        AtomicInteger recordsProcessed = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        long start = System.nanoTime();
        for (int t = 0; t < NUM_THREADS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < RECORDS_PER_THREAD; i++) {
                    int pnrSuffix = threadId * RECORDS_PER_THREAD + i;
                    // Thread-local StringBuilder for building ticket text without lock contention
                    StringBuilder localTicket = new StringBuilder();
                    localTicket.append("LOG_ENTRY[")
                               .append("PNR:").append(9000000000L + pnrSuffix)
                               .append("|TID:").append(threadId)
                               .append("|STATUS:CONFIRMED")
                               .append("]\n");

                    // Append completed atomic record to shared StringBuffer (thread-safe synchronized method)
                    sharedLog.append(localTicket);
                    recordsProcessed.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        try {
            boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("WARNING: Multithreaded execution timed out!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrupted: " + e.getMessage());
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        // Verify count
        String logContent = sharedLog.toString();
        String[] lines = logContent.split("\n");
        int count = 0;
        for (String line : lines) {
            if (line.startsWith("LOG_ENTRY[") && line.endsWith("]")) {
                count++;
            }
        }

        System.out.println("Threads: " + NUM_THREADS);
        System.out.println("Target records: " + TOTAL_RECORDS);
        System.out.println("Atomic counter: " + recordsProcessed.get());
        System.out.println("Parsed records in shared StringBuffer: " + count);
        System.out.println("Duration: " + durationMs + " ms");

        if (count == TOTAL_RECORDS) {
            System.out.println("SUCCESS: Exactly 1,000 complete, uncorrupted records written to shared log.");
        } else {
            System.err.println("FAILURE: Corrupted or missing records in shared log! Found: " + count);
        }
        System.out.println();
    }

    /**
     * Saves all benchmark results to CSV format.
     */
    public static void saveResultsToCsv(List<BenchmarkResult> results, String filePath) {
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Workload,Method,Warmup_ms,Run1_ms,Run2_ms,Run3_ms,Run4_ms,Run5_ms,Avg_ms,Estimated_Intermediate_Objects,Approx_Memory_Delta_KB\n");
            for (BenchmarkResult r : results) {
                double kb = r.approxMemoryDeltaBytes / 1024.0;
                writer.write(String.format("%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%.2f\n",
                        r.workload,
                        r.method,
                        r.warmupMs,
                        r.runMs[0],
                        r.runMs[1],
                        r.runMs[2],
                        r.runMs[3],
                        r.runMs[4],
                        r.avgMs,
                        r.estimatedIntermediateObjects,
                        kb));
            }
            System.out.println("CSV export completed successfully: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("=====================================================================");
        System.out.println("     Q1 TICKET BENCHMARK: String (+) vs StringBuilder vs StringBuffer ");
        System.out.println("=====================================================================\n");

        // Define three deliberately contrasting workloads
        System.out.println("Initializing workloads...");
        Workload workloadA = new Workload("Workload_A_1_Ticket_6_Fields", 1, 6);
        Workload workloadB = new Workload("Workload_B_10000_Tickets_50_Fields", 10_000, 50);
        Workload workloadC = new Workload("Workload_C_1000_Tickets_100_Fields", 1_000, 100);
        System.out.println("Workloads initialized.\n");

        // Verify content equivalence
        verifyContentEquivalence(workloadA);

        List<BenchmarkResult> allResults = new ArrayList<>();

        // Array of workloads
        Workload[] workloads = new Workload[] { workloadA, workloadB, workloadC };

        for (Workload w : workloads) {
            System.out.println("---------------------------------------------------------------------");
            System.out.printf("Running Benchmark for %s (%d tickets, %d fields each)%n", 
                              w.name, w.ticketCount, w.fieldCount);
            System.out.println("---------------------------------------------------------------------");

            // Object estimation:
            // For String (+): each ticket of n fields performs n concatenations.
            // The first (n - 1) concatenations generate temporary intermediate String objects.
            // The n-th concatenation produces the 1 final output String.
            // Intermediate Strings = tickets * (fields - 1). Final Strings = tickets.
            //
            // For StringBuilder & StringBuffer:
            // The internal character buffer mutates in place -> 0 intermediate String objects.
            // Only 1 final output String is produced upon calling toString().
            long estPlusPerTicket = w.fieldCount - 1;
            long estSbPerTicket = 0;

            BenchmarkResult rPlus = runBenchmark(w, "String_Plus", 
                    Q1TicketBenchmark::buildWithPlus, estPlusPerTicket);
            BenchmarkResult rBuilder = runBenchmark(w, "StringBuilder", 
                    Q1TicketBenchmark::buildWithStringBuilder, estSbPerTicket);
            BenchmarkResult rBuffer = runBenchmark(w, "StringBuffer", 
                    Q1TicketBenchmark::buildWithStringBuffer, estSbPerTicket);

            allResults.add(rPlus);
            allResults.add(rBuilder);
            allResults.add(rBuffer);

            System.out.printf("%-15s | Avg: %10.4f ms | Warmup: %8.4f ms | Est Objects: %10d | Approx Mem Delta: %8.2f KB%n",
                    rPlus.method, rPlus.avgMs, rPlus.warmupMs, rPlus.estimatedIntermediateObjects, rPlus.approxMemoryDeltaBytes / 1024.0);
            System.out.printf("%-15s | Avg: %10.4f ms | Warmup: %8.4f ms | Est Objects: %10d | Approx Mem Delta: %8.2f KB%n",
                    rBuilder.method, rBuilder.avgMs, rBuilder.warmupMs, rBuilder.estimatedIntermediateObjects, rBuilder.approxMemoryDeltaBytes / 1024.0);
            System.out.printf("%-15s | Avg: %10.4f ms | Warmup: %8.4f ms | Est Objects: %10d | Approx Mem Delta: %8.2f KB%n",
                    rBuffer.method, rBuffer.avgMs, rBuffer.warmupMs, rBuffer.estimatedIntermediateObjects, rBuffer.approxMemoryDeltaBytes / 1024.0);
            System.out.println();
        }

        System.out.println("* NOTE: Memory measurements (Runtime totalMemory - freeMemory) represent approximate");
        System.out.println("  process-wide JVM heap occupancy, not exact isolated per-method allocations.");
        System.out.println("  Readings reflect global heap state, GC cycles, JIT compilation, and background runtime threads.\n");

        // Run Multithreaded demonstration
        demonstrateMultithreadedLog();

        // Save CSV
        String csvPath = resolveCsvPath();
        saveResultsToCsv(allResults, csvPath);

        System.out.println("=====================================================================");
        System.out.println("                    BENCHMARK RUN COMPLETED                          ");
        System.out.println("=====================================================================");
    }

    private static String resolveCsvPath() {
        File candidate1 = new File("Task01_StringBenchmark/data");
        if (candidate1.exists()) {
            return "Task01_StringBenchmark/data/benchmark_results.csv";
        }
        File candidate2 = new File("data");
        if (candidate2.exists()) {
            return "data/benchmark_results.csv";
        }
        candidate1.mkdirs();
        return "Task01_StringBenchmark/data/benchmark_results.csv";
    }
}
