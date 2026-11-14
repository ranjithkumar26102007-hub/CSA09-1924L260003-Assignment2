import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Q3 — HashSet and the hashCode-equals Contract
 * Scenario : College Fest Event Attendance De-Duplication
 * Student  : Ranjith Kumar.R  |  Reg: 1924L260003
 *
 * <p>Demonstrates three test suites:</p>
 * <ol>
 *   <li>BrokenStudent (equals only, no hashCode) — accidental-correctness path</li>
 *   <li>BrokenStudent — deliberate duplicate exposure</li>
 *   <li>FixedStudent (both equals + hashCode) — duplicates fully eliminated</li>
 * </ol>
 *
 * <p>Also demonstrates hash-bucket mechanics and the edge case of mutating a
 * key's field after insertion (the object becomes "lost" in the wrong bucket).</p>
 */
public class Q3HashSetAttendance {

    // ─────────────────────────────────────────────
    // 1. BROKEN Student — equals only, no hashCode
    // ─────────────────────────────────────────────
    @SuppressWarnings("overrides") // intentional: demonstrates missing hashCode contract
    static class BrokenStudent {
        final String regNo;
        final String name;

        BrokenStudent(String regNo, String name) {
            this.regNo = regNo;
            this.name  = name;
        }

        /** Equality based on regNo only — intentionally correct. */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BrokenStudent)) return false;
            return regNo.equals(((BrokenStudent) o).regNo);
        }

        // hashCode() NOT overridden → inherits Object.hashCode() (identity hash)
        // This violates the contract: two objects that are equal MUST have the
        // same hashCode. Because they don't, HashSet may place them in different
        // buckets and treat them as distinct entries.

        @Override
        public String toString() {
            return "BrokenStudent{regNo='" + regNo + "', name='" + name + "'}";
        }
    }

    // ─────────────────────────────────────────────
    // 2. FIXED Student — both equals + hashCode
    // ─────────────────────────────────────────────
    static class FixedStudent {
        String regNo;   // non-final to demonstrate mutable-key edge case
        final String name;

        FixedStudent(String regNo, String name) {
            this.regNo = regNo;
            this.name  = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FixedStudent)) return false;
            return regNo.equals(((FixedStudent) o).regNo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(regNo);
        }

        @Override
        public String toString() {
            return "FixedStudent{regNo='" + regNo + "', name='" + name + "'}";
        }
    }

    // ─────────────────────────────────────────────
    // CSV helper
    // ─────────────────────────────────────────────
    private static PrintWriter csvWriter;

    private static void writeCsvRow(String suite, String scenario,
                                    int inputSize, int setSize,
                                    boolean duplicatesBlocked, String note) {
        csvWriter.printf("%s,%s,%d,%d,%b,\"%s\"%n",
                suite, scenario, inputSize, setSize, duplicatesBlocked, note);
    }

    // ─────────────────────────────────────────────
    // SUITE A: BrokenStudent — accidental correctness
    // ─────────────────────────────────────────────
    static void suiteA_AccidentalCorrectness() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" SUITE A: BrokenStudent — Accidental Correctness Path");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  hashCode() NOT overridden. Relies on identity hash.");
        System.out.println();

        /*
         * When we use the SAME object reference twice, equals() AND hashCode()
         * both agree (same object → same identity hash → same bucket → equals
         * returns true → duplicate blocked). This is the accidental-correctness
         * scenario: it looks like it works, but only because we reused the
         * exact same reference.
         */
        HashSet<BrokenStudent> attended = new HashSet<>();

        BrokenStudent alice = new BrokenStudent("2024CS001", "Alice");
        attended.add(alice);
        boolean addedAgain = attended.add(alice);   // same reference

        System.out.printf("  Added Alice (same reference twice) → set size = %d%n", attended.size());
        System.out.printf("  Second add() returned: %b (false = duplicate correctly blocked)%n", addedAgain);
        System.out.println("  ⚠  This works ONLY because the same object reference is reused.");
        System.out.println("  ⚠  Real-world scan entries come as separate objects with new regNos.");
        System.out.println();

        writeCsvRow("A-BrokenStudent", "Same reference reused",
                2, 1, !addedAgain,
                "Accidental correctness — same identity hash");
    }

    // ─────────────────────────────────────────────
    // SUITE B: BrokenStudent — duplicate exposure
    // ─────────────────────────────────────────────
    static void suiteB_DuplicateExposure() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" SUITE B: BrokenStudent — Deliberate Duplicate Exposure");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Two distinct objects with the same regNo → DIFFERENT identity hashes");
        System.out.println("  → different buckets → HashSet stores BOTH → duplicate leaks through!");
        System.out.println();

        // Simulate scans from two separate QR readers — both create new objects
        List<BrokenStudent> scanLog = new ArrayList<>();
        scanLog.add(new BrokenStudent("2024CS001", "Alice"));
        scanLog.add(new BrokenStudent("2024CS002", "Bob"));
        scanLog.add(new BrokenStudent("2024CS001", "Alice")); // duplicate scan
        scanLog.add(new BrokenStudent("2024CS003", "Carol"));
        scanLog.add(new BrokenStudent("2024CS002", "Bob"));   // duplicate scan

        HashSet<BrokenStudent> attended = new HashSet<>();
        for (BrokenStudent s : scanLog) {
            attended.add(s);
        }

        System.out.printf("  Input scans : %d%n", scanLog.size());
        System.out.printf("  Unique expected : 3  (Alice, Bob, Carol)%n");
        System.out.printf("  HashSet size    : %d  ← BUG: duplicates leaked!%n", attended.size());
        System.out.println();
        System.out.println("  Bucket-level evidence (identity hashCodes differ per object):");
        for (BrokenStudent s : scanLog) {
            System.out.printf("    regNo=%-12s  identityHash=%10d%n",
                    s.regNo, System.identityHashCode(s));
        }
        System.out.println();

        boolean duplicatesBlocked = (attended.size() == 3);
        System.out.println("  ✗ Duplicates WERE NOT blocked (BrokenStudent missing hashCode override)");
        System.out.println();

        writeCsvRow("B-BrokenStudent", "Distinct objects same regNo",
                5, attended.size(), duplicatesBlocked,
                "BUG: identity hash places duplicates in different buckets");
    }

    // ─────────────────────────────────────────────
    // SUITE C: FixedStudent — correct contract
    // ─────────────────────────────────────────────
    static void suiteC_FixedStudent() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" SUITE C: FixedStudent — hashCode + equals both on regNo");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Equal objects produce equal hashCodes → same bucket → equals()");
        System.out.println("  called → duplicate correctly blocked.");
        System.out.println();

        List<FixedStudent> scanLog = new ArrayList<>();
        scanLog.add(new FixedStudent("2024CS001", "Alice"));
        scanLog.add(new FixedStudent("2024CS002", "Bob"));
        scanLog.add(new FixedStudent("2024CS001", "Alice")); // duplicate
        scanLog.add(new FixedStudent("2024CS003", "Carol"));
        scanLog.add(new FixedStudent("2024CS002", "Bob"));   // duplicate
        scanLog.add(new FixedStudent("2024CS004", "Dave"));

        HashSet<FixedStudent> attended = new HashSet<>();
        for (FixedStudent s : scanLog) {
            attended.add(s);
        }

        System.out.printf("  Input scans     : %d%n", scanLog.size());
        System.out.printf("  Unique expected : 4  (Alice, Bob, Carol, Dave)%n");
        System.out.printf("  HashSet size    : %d%n", attended.size());

        boolean correct = (attended.size() == 4);
        if (correct) {
            System.out.println("  ✓ Duplicates correctly blocked — set size is exactly 4.");
        } else {
            System.out.println("  ✗ UNEXPECTED: set size does not match expected unique count.");
        }
        System.out.println();

        System.out.println("  Computed hashCodes (consistent across objects with same regNo):");
        for (FixedStudent s : scanLog) {
            System.out.printf("    regNo=%-12s  hashCode=%10d%n", s.regNo, s.hashCode());
        }
        System.out.println();

        writeCsvRow("C-FixedStudent", "Distinct objects same regNo",
                6, attended.size(), correct,
                "CORRECT: Objects.hash(regNo) produces consistent bucket placement");
    }

    // ─────────────────────────────────────────────
    // SUITE D: Mutable key edge case
    // ─────────────────────────────────────────────
    static void suiteD_MutableKeyEdgeCase() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" SUITE D: Edge Case — Mutating a HashSet Key After Insertion");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Inserting a FixedStudent, then changing its regNo field.");
        System.out.println("  The object moves to a different logical bucket but stays in");
        System.out.println("  the old physical bucket → 'lost' inside the set.");
        System.out.println();

        HashSet<FixedStudent> attended = new HashSet<>();
        FixedStudent eve = new FixedStudent("2024CS005", "Eve");
        attended.add(eve);

        System.out.printf("  After add: set size = %d, contains(eve) = %b%n",
                attended.size(), attended.contains(eve));

        // Mutate the key field AFTER insertion
        int hashBefore = eve.hashCode();
        eve.regNo = "2024CS999";   // ← mutable field mutated
        int hashAfter = eve.hashCode();

        System.out.printf("  hashCode before mutation : %d%n", hashBefore);
        System.out.printf("  hashCode after  mutation : %d%n", hashAfter);
        System.out.printf("  After mutation : set size = %d, contains(eve) = %b%n",
                attended.size(), attended.contains(eve));
        System.out.println("  ⚠  contains() now returns FALSE — eve is 'lost' in the wrong bucket!");
        System.out.println("  ⚠  Lesson: always use immutable or effectively-immutable keys in HashSet.");
        System.out.println();

        boolean correctContains = !attended.contains(eve); // should be lost → true
        writeCsvRow("D-MutableKey", "regNo mutated after insertion",
                1, attended.size(), correctContains,
                "Object lost in wrong bucket; contains() returns false after mutation");
    }

    // ─────────────────────────────────────────────
    // SUITE E: Scale benchmark (1k / 10k / 100k)
    // ─────────────────────────────────────────────
    static void suiteE_ScaleBenchmark() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" SUITE E: Scale Benchmark — FixedStudent HashSet");
        System.out.println("   Operations: add, contains-hit, contains-miss, remove");
        System.out.println("   Warm-up: 1  |  Measured runs: 5  |  Timing: nanoTime");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();

        int[] sizes = {1_000, 10_000, 100_000};
        int WARMUP = 1;
        int RUNS   = 5;

        System.out.printf("  %-10s  %-12s  %-12s  %-12s  %-12s  %-8s%n",
                "Size", "Add(ms)", "Contains+", "Contains-", "Remove(ms)", "FinalSz");
        System.out.println("  " + "-".repeat(74));

        for (int size : sizes) {
            // Build dataset once — not counted in timing
            List<String> regNos = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                regNos.add(String.format("REG%07d", i));
            }
            // Extra entries that do NOT exist in the set (for miss benchmark)
            List<String> misses = new ArrayList<>(size);
            for (int i = size; i < 2 * size; i++) {
                misses.add(String.format("REG%07d", i));
            }

            double[] addTimes      = new double[RUNS];
            double[] hitTimes      = new double[RUNS];
            double[] missTimes     = new double[RUNS];
            double[] removeTimes   = new double[RUNS];

            for (int run = -WARMUP; run < RUNS; run++) {
                // ── ADD ──
                HashSet<FixedStudent> set = new HashSet<>(size * 2, 0.75f);
                long t0 = System.nanoTime();
                for (String r : regNos) {
                    set.add(new FixedStudent(r, "Student_" + r));
                }
                long addNs = System.nanoTime() - t0;

                // correctness
                if (set.size() != size) {
                    throw new RuntimeException("Add correctness failed at size=" + size);
                }

                // ── CONTAINS HIT ──
                t0 = System.nanoTime();
                for (String r : regNos) {
                    if (!set.contains(new FixedStudent(r, "ignored"))) {
                        throw new RuntimeException("Contains-hit failed for " + r);
                    }
                }
                long hitNs = System.nanoTime() - t0;

                // ── CONTAINS MISS ──
                t0 = System.nanoTime();
                for (String r : misses) {
                    if (set.contains(new FixedStudent(r, "ignored"))) {
                        throw new RuntimeException("Contains-miss false-positive for " + r);
                    }
                }
                long missNs = System.nanoTime() - t0;

                // ── REMOVE ──
                t0 = System.nanoTime();
                for (String r : regNos) {
                    set.remove(new FixedStudent(r, "ignored"));
                }
                long removeNs = System.nanoTime() - t0;
                if (!set.isEmpty()) {
                    throw new RuntimeException("Remove correctness failed at size=" + size);
                }

                if (run >= 0) {
                    addTimes[run]    = addNs    / 1_000_000.0;
                    hitTimes[run]    = hitNs    / 1_000_000.0;
                    missTimes[run]   = missNs   / 1_000_000.0;
                    removeTimes[run] = removeNs / 1_000_000.0;
                }
            }

            double avgAdd    = average(addTimes);
            double avgHit    = average(hitTimes);
            double avgMiss   = average(missTimes);
            double avgRemove = average(removeTimes);

            System.out.printf("  %-10s  %-12.4f  %-12.4f  %-12.4f  %-12.4f  %-8d%n",
                    String.format("%,d", size), avgAdd, avgHit, avgMiss, avgRemove, size);

            writeCsvRow("E-Scale", "FixedStudent-add",    size, size, true,
                    String.format("avg=%.4fms", avgAdd));
            writeCsvRow("E-Scale", "FixedStudent-containsHit",  size, size, true,
                    String.format("avg=%.4fms", avgHit));
            writeCsvRow("E-Scale", "FixedStudent-containsMiss", size, size, true,
                    String.format("avg=%.4fms", avgMiss));
            writeCsvRow("E-Scale", "FixedStudent-remove", size, size, true,
                    String.format("avg=%.4fms", avgRemove));
        }
        System.out.println();
    }

    private static double average(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }

    // ─────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  Q3 — HashSet and the hashCode-equals Contract            ║");
        System.out.println("║  College Fest Event Attendance De-Duplication             ║");
        System.out.println("║  Student: Ranjith Kumar.R  |  Reg: 1924L260003            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();

        // Resolve CSV path
        Path csvPath = resolveCsvPath();
        Files.createDirectories(csvPath.getParent());
        csvWriter = new PrintWriter(new FileWriter(csvPath.toFile()));
        csvWriter.println("Suite,Scenario,InputSize,SetSize,DuplicatesBlocked,Notes");

        suiteA_AccidentalCorrectness();
        suiteB_DuplicateExposure();
        suiteC_FixedStudent();
        suiteD_MutableKeyEdgeCase();
        suiteE_ScaleBenchmark();

        csvWriter.flush();
        csvWriter.close();

        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println(" CSV exported to: " + csvPath.toAbsolutePath());
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
    }

    private static Path resolveCsvPath() {
        // Prefer sibling data/ folder so the class works from both IDEs and CLI
        Path candidate = Paths.get("Task03_HashSet_Student", "data",
                "Q3_benchmark_results.csv");
        if (candidate.getParent().toFile().isDirectory()) {
            return candidate;
        }
        return Paths.get("data", "Q3_benchmark_results.csv");
    }
}
