import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Q2PlaylistBenchmark - Music Playlist Editor Collection Comparison
 * 
 * Compares ArrayList<Song> and LinkedList<Song> across 7 operations:
 *   1. Adding at the end (populate 0 to N)
 *   2. Inserting at the front (batch of K inserts at index 0)
 *   3. Removing from the front (batch of K removals at index 0)
 *   4. Inserting in the middle (batch of K inserts at size/2)
 *   5. Removing from the middle (batch of K removals at size/2)
 *   6. Accessing the k-th element (batch of K random-index gets)
 *   7. Iterating through the complete list (traversing all N elements)
 * 
 * Evaluated across 3 playlist sizes: 1,000, 10,000, and 100,000 songs.
 * Also evaluates an "Up Next" queue using ArrayDeque.
 */
public class Q2PlaylistBenchmark {

    // Song domain entity
    public static class Song {
        private final String id;
        private final String title;
        private final String artist;
        private final int durationSec;

        public Song(String id, String title, String artist, int durationSec) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.durationSec = durationSec;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public int getDurationSec() { return durationSec; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Song)) return false;
            Song song = (Song) o;
            return id.equals(song.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return String.format("%s - %s [%s, %ds]", id, title, artist, durationSec);
        }
    }

    // Benchmark record container
    public static class OpResult {
        final int size;
        final String operation;
        final String collection;
        double warmupMs;
        final double[] runs = new double[5];
        double avgMs;

        public OpResult(int size, String operation, String collection) {
            this.size = size;
            this.operation = operation;
            this.collection = collection;
        }

        public void computeAverage() {
            double sum = 0;
            for (double r : runs) sum += r;
            this.avgMs = sum / runs.length;
        }
    }

    // Number of batch operations for insert/remove/access
    private static final int BATCH_OPS = 1000;

    /**
     * Generates a deterministic list of test songs.
     */
    private static List<Song> generateDataset(int count, String prefix) {
        List<Song> dataset = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            dataset.add(new Song(
                String.format("%s_%06d", prefix, i),
                "Track_" + i,
                "Artist_" + (i % 50),
                180 + (i % 120)
            ));
        }
        return dataset;
    }

    // =========================================================================
    // Benchmark Execution Core
    // =========================================================================

    @FunctionalInterface
    interface BenchmarkAction {
        void execute(List<Song> list);
    }

    @FunctionalInterface
    interface ListSupplier {
        List<Song> create();
    }

    /**
     * Runs 1 warm-up run + 5 measured repetitions for an operation.
     * Separates test setup time from timed operation execution.
     */
    private static OpResult benchmarkOperation(int size, String opName, String collName,
                                              ListSupplier supplier,
                                              List<Song> baseData,
                                              BenchmarkAction action) {
        OpResult result = new OpResult(size, opName, collName);

        // Warm-up run (excluded from average)
        {
            List<Song> warmList = supplier.create();
            if (baseData != null) warmList.addAll(baseData);
            long t0 = System.nanoTime();
            action.execute(warmList);
            long t1 = System.nanoTime();
            result.warmupMs = (t1 - t0) / 1_000_000.0;
        }

        // 5 Measured runs
        for (int r = 0; r < 5; r++) {
            List<Song> testList = supplier.create();
            if (baseData != null) testList.addAll(baseData);

            long t0 = System.nanoTime();
            action.execute(testList);
            long t1 = System.nanoTime();
            result.runs[r] = (t1 - t0) / 1_000_000.0;
        }

        result.computeAverage();
        return result;
    }

    // =========================================================================
    // Changed Condition: Up Next Queue via ArrayDeque
    // =========================================================================

    public static void testUpNextQueue() {
        System.out.println("=====================================================================");
        System.out.println("          CHANGED CONDITION: 'Up Next' Queue via ArrayDeque          ");
        System.out.println("=====================================================================\n");

        Deque<Song> upNext = new ArrayDeque<>();

        Song s1 = new Song("S01", "Midnight Melody", "Aria", 210);
        Song s2 = new Song("S02", "Sunrise Beat", "Echo", 195);
        Song s3 = new Song("S03", "Acoustic Breeze", "Nova", 240);
        Song sPriority1 = new Song("P01", "Breaking News / Live Anthem", "Broadcaster", 60);
        Song sPriority2 = new Song("P02", "Listener Request Priority", "DJ", 180);

        // 1. Add normal tracks at the back
        upNext.addLast(s1);
        upNext.addLast(s2);
        upNext.addLast(s3);
        System.out.println("Added 3 songs at the end (addLast):");
        upNext.forEach(s -> System.out.println("  - " + s));

        // 2. Add high-priority songs at the front
        upNext.addFirst(sPriority2);
        upNext.addFirst(sPriority1);
        System.out.println("\nAdded 2 priority songs at the front (addFirst):");
        upNext.forEach(s -> System.out.println("  - " + s));

        // 3. Play and remove songs from the front
        System.out.println("\nPlayback sequence (pollFirst):");
        List<Song> playedOrder = new ArrayList<>();
        while (!upNext.isEmpty()) {
            Song nowPlaying = upNext.pollFirst();
            playedOrder.add(nowPlaying);
            System.out.println("  Now Playing: " + nowPlaying);
        }

        // Correctness verification
        boolean correct = playedOrder.size() == 5
                && playedOrder.get(0).equals(sPriority1)
                && playedOrder.get(1).equals(sPriority2)
                && playedOrder.get(2).equals(s1)
                && playedOrder.get(3).equals(s2)
                && playedOrder.get(4).equals(s3);

        if (correct) {
            System.out.println("\nSUCCESS: ArrayDeque preserved expected double-ended playback order.");
        } else {
            System.err.println("\nFAILURE: Playback order violation in ArrayDeque!");
            System.exit(1);
        }
        System.out.println();
    }

    // =========================================================================
    // CSV Export
    // =========================================================================

    private static void exportCsv(List<OpResult> results, String filePath) {
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("Playlist_Size,Operation,Collection,Warmup_ms,Run1_ms,Run2_ms,Run3_ms,Run4_ms,Run5_ms,Avg_ms\n");
            for (OpResult r : results) {
                bw.write(String.format("%d,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n",
                        r.size,
                        r.operation,
                        r.collection,
                        r.warmupMs,
                        r.runs[0],
                        r.runs[1],
                        r.runs[2],
                        r.runs[3],
                        r.runs[4],
                        r.avgMs));
            }
            System.out.println("CSV export completed successfully: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error writing CSV: " + e.getMessage());
        }
    }

    private static String resolveCsvPath() {
        File candidate1 = new File("Task02_CollectionsComparison/data");
        if (candidate1.exists() || candidate1.mkdirs()) {
            return "Task02_CollectionsComparison/data/Q2_benchmark_results.csv";
        }
        File candidate2 = new File("data");
        if (candidate2.exists() || candidate2.mkdirs()) {
            return "data/Q2_benchmark_results.csv";
        }
        return "Q2_benchmark_results.csv";
    }

    // =========================================================================
    // Main Benchmark Suite
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=====================================================================");
        System.out.println("   Q2 PLAYLIST BENCHMARK: ArrayList<Song> vs LinkedList<Song>        ");
        System.out.println("=====================================================================\n");

        int[] playlistSizes = new int[] { 1_000, 10_000, 100_000 };
        List<OpResult> allResults = new ArrayList<>();

        for (int size : playlistSizes) {
            System.out.println("=====================================================================");
            System.out.printf("           RUNNING BENCHMARK SUITE FOR PLAYLIST SIZE: %,d           %n", size);
            System.out.println("=====================================================================");

            // Pre-generate equivalent datasets
            List<Song> baseData = generateDataset(size, "BASE");
            List<Song> insertData = generateDataset(BATCH_OPS, "INS");
            Random rng = new Random(42);
            int[] randomIndices = new int[BATCH_OPS];
            for (int i = 0; i < BATCH_OPS; i++) {
                randomIndices[i] = rng.nextInt(size);
            }

            // -------------------------------------------------------------
            // Op 1: Adding at the end (populate 0 to N)
            // -------------------------------------------------------------
            {
                OpResult rAl = benchmarkOperation(size, "Add_At_End", "ArrayList",
                        ArrayList::new, null,
                        list -> {
                            for (Song s : baseData) list.add(s);
                            if (list.size() != size) throw new AssertionError("Size mismatch");
                        });
                OpResult rLl = benchmarkOperation(size, "Add_At_End", "LinkedList",
                        LinkedList::new, null,
                        list -> {
                            for (Song s : baseData) list.add(s);
                            if (list.size() != size) throw new AssertionError("Size mismatch");
                        });
                allResults.add(rAl);
                allResults.add(rLl);
                printComparison(rAl, rLl);
            }

            // -------------------------------------------------------------
            // Op 2: Inserting at the front (batch of K inserts at index 0)
            // -------------------------------------------------------------
            {
                OpResult rAl = benchmarkOperation(size, "Insert_At_Front", "ArrayList",
                        ArrayList::new, baseData,
                        list -> {
                            for (int i = 0; i < BATCH_OPS; i++) list.add(0, insertData.get(i));
                            if (list.size() != size + BATCH_OPS) throw new AssertionError("Size mismatch");
                        });
                OpResult rLl = benchmarkOperation(size, "Insert_At_Front", "LinkedList",
                        LinkedList::new, baseData,
                        list -> {
                            for (int i = 0; i < BATCH_OPS; i++) list.add(0, insertData.get(i));
                            if (list.size() != size + BATCH_OPS) throw new AssertionError("Size mismatch");
                        });
                allResults.add(rAl);
                allResults.add(rLl);
                printComparison(rAl, rLl);
            }

            // -------------------------------------------------------------
            // Op 3: Removing from the front (batch of K removals at index 0)
            // -------------------------------------------------------------
            {
                int removeCount = Math.min(BATCH_OPS, size);
                OpResult rAl = benchmarkOperation(size, "Remove_From_Front", "ArrayList",
                        ArrayList::new, baseData,
                        list -> {
                            for (int i = 0; i < removeCount; i++) list.remove(0);
                            if (list.size() != size - removeCount) throw new AssertionError("Size mismatch");
                        });
                OpResult rLl = benchmarkOperation(size, "Remove_From_Front", "LinkedList",
                        LinkedList::new, baseData,
                        list -> {
                            for (int i = 0; i < removeCount; i++) list.remove(0);
                            if (list.size() != size - removeCount) throw new AssertionError("Size mismatch");
                        });
                allResults.add(rAl);
                allResults.add(rLl);
                printComparison(rAl, rLl);
            }

            // -------------------------------------------------------------
            // Op 4: Inserting in the middle (batch of K inserts at size/2)
            // -------------------------------------------------------------
            {
                OpResult rAl = benchmarkOperation(size, "Insert_In_Middle", "ArrayList",
                        ArrayList::new, baseData,
                        list -> {
                            for (int i = 0; i < BATCH_OPS; i++) {
                                list.add(list.size() / 2, insertData.get(i));
                            }
                            if (list.size() != size + BATCH_OPS) throw new AssertionError("Size mismatch");
                        });
                OpResult rLl = benchmarkOperation(size, "Insert_In_Middle", "LinkedList",
                        LinkedList::new, baseData,
                        list -> {
                            for (int i = 0; i < BATCH_OPS; i++) {
                                list.add(list.size() / 2, insertData.get(i));
                            }
                            if (list.size() != size + BATCH_OPS) throw new AssertionError("Size mismatch");
                        });
                allResults.add(rAl);
                allResults.add(rLl);
                printComparison(rAl, rLl);
            }

            // -------------------------------------------------------------
            // Op 5: Removing from the middle (batch of K removals at size/2)
            // -------------------------------------------------------------
            {
                int removeCount = Math.min(BATCH_OPS, size);
                OpResult rAl = benchmarkOperation(size, "Remove_From_Middle", "ArrayList",
                        ArrayList::new, baseData,
                        list -> {
                            for (int i = 0; i < removeCount; i++) {
                                list.remove(list.size() / 2);
                            }
                            if (list.size() != size - removeCount) throw new AssertionError("Size mismatch");
                        });
                OpResult rLl = benchmarkOperation(size, "Remove_From_Middle", "LinkedList",
                        LinkedList::new, baseData,
                        list -> {
                            for (int i = 0; i < removeCount; i++) {
                                list.remove(list.size() / 2);
                            }
                            if (list.size() != size - removeCount) throw new AssertionError("Size mismatch");
                        });
                allResults.add(rAl);
                allResults.add(rLl);
                printComparison(rAl, rLl);
            }

            // -------------------------------------------------------------
            // Op 6: Accessing the k-th element (batch of K random-index gets)
            // -------------------------------------------------------------
            {
                OpResult rAl = benchmarkOperation(size, "Get_Kth_Element", "ArrayList",
                        ArrayList::new, baseData,
                        list -> {
                            int checksum = 0;
                            for (int idx : randomIndices) {
                                Song s = list.get(idx);
                                if (s != null) checksum += s.getDurationSec();
                            }
                            if (checksum == 0 && size > 0) throw new AssertionError("Verification failed");
                        });
                OpResult rLl = benchmarkOperation(size, "Get_Kth_Element", "LinkedList",
                        LinkedList::new, baseData,
                        list -> {
                            int checksum = 0;
                            for (int idx : randomIndices) {
                                Song s = list.get(idx);
                                if (s != null) checksum += s.getDurationSec();
                            }
                            if (checksum == 0 && size > 0) throw new AssertionError("Verification failed");
                        });
                allResults.add(rAl);
                allResults.add(rLl);
                printComparison(rAl, rLl);
            }

            // -------------------------------------------------------------
            // Op 7: Iterating through the complete list (all N elements)
            // -------------------------------------------------------------
            {
                OpResult rAl = benchmarkOperation(size, "Iterate_Complete_List", "ArrayList",
                        ArrayList::new, baseData,
                        list -> {
                            int count = 0;
                            long totalDuration = 0;
                            for (Song s : list) {
                                count++;
                                totalDuration += s.getDurationSec();
                            }
                            if (count != size) throw new AssertionError("Iterator count mismatch");
                        });
                OpResult rLl = benchmarkOperation(size, "Iterate_Complete_List", "LinkedList",
                        LinkedList::new, baseData,
                        list -> {
                            int count = 0;
                            long totalDuration = 0;
                            for (Song s : list) {
                                count++;
                                totalDuration += s.getDurationSec();
                            }
                            if (count != size) throw new AssertionError("Iterator count mismatch");
                        });
                allResults.add(rAl);
                allResults.add(rLl);
                printComparison(rAl, rLl);
            }
            System.out.println();
        }

        // Run Changed Condition: ArrayDeque Up Next Queue
        testUpNextQueue();

        // Export raw measurements to CSV
        String csvPath = resolveCsvPath();
        exportCsv(allResults, csvPath);

        System.out.println("=====================================================================");
        System.out.println("                 Q2 BENCHMARK COMPLETED SUCCESSFULLY                 ");
        System.out.println("=====================================================================");
    }

    private static void printComparison(OpResult rAl, OpResult rLl) {
        double ratio = (rLl.avgMs > 0 && rAl.avgMs > 0) ? (rLl.avgMs / rAl.avgMs) : 1.0;
        String verdict;
        if (ratio > 1.2) {
            verdict = String.format("ArrayList is %.2fx FASTER", ratio);
        } else if (ratio < 0.8) {
            verdict = String.format("LinkedList is %.2fx FASTER", 1.0 / ratio);
        } else {
            verdict = "Comparable performance";
        }
        System.out.printf("%-22s | AL: %9.4f ms | LL: %9.4f ms | %s%n",
                rAl.operation, rAl.avgMs, rLl.avgMs, verdict);
    }
}
