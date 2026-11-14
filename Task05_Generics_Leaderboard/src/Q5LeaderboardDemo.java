import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Q5 — Derive a Generic Ranking Component from a Principle
 * Scenario: Inter-College Sports Meet / Team Leaderboard
 * Student : Ranjith Kumar.R | Register: 1924L260003
 *
 * <p>Demonstrates:</p>
 * <ul>
 *   <li>Generic {@code Leaderboard<T>} component decoupling entities from ranking policies.</li>
 *   <li>Usage of bounded wildcards: {@code Comparator<? super T>} and {@code List<? extends T>}.</li>
 *   <li>Composite comparator chaining using {@code comparing()}, {@code thenComparing()}, and {@code reversed()}.</li>
 *   <li>Subtle bug in integer subtraction comparator failing under arithmetic overflow.</li>
 *   <li>Faulty comparator omitting tie-breaking criteria.</li>
 *   <li>Empirical performance comparison: Single Composite Comparator vs. Three Sequential Stable Passes.</li>
 *   <li>Mathematical verification of Comparator Transitivity across all triples.</li>
 * </ul>
 */
public class Q5LeaderboardDemo {

    // =========================================================================
    // 1. DOMAIN MODEL: Base Entity & Team Subclass
    // =========================================================================

    /**
     * Common base entity representing any ranked participant.
     * Used to demonstrate PECS (Producer Extends, Consumer Super).
     */
    public static class Participant {
        private final String registrationId;

        public Participant(String registrationId) {
            this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        }

        public String getRegistrationId() {
            return registrationId;
        }

        @Override
        public String toString() {
            return "Participant[ID=" + registrationId + "]";
        }
    }

    /**
     * Domain model representing a sports team.
     * <p>Design Decision: Does NOT implement {@code Comparable<Team>}.
     * A Team possesses multiple valid contextual orderings (points, goal difference,
     * fair-play index, alphabetical by name). Enforcing an arbitrary "natural"
     * order violates the Single Responsibility Principle and hardcodes domain policy.
     * External {@link Comparator} objects provide clean separation of concerns.</p>
     */
    public static class Team extends Participant {
        private final String name;
        private final int points;
        private final int goalDiff;

        public Team(String registrationId, String name, int points, int goalDiff) {
            super(registrationId);
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.points = points;
            this.goalDiff = goalDiff;
        }

        // Convenience constructor
        public Team(String name, int points, int goalDiff) {
            this("REG-" + Math.abs(name.hashCode() % 10000), name, points, goalDiff);
        }

        public String getName() {
            return name;
        }

        public int getPoints() {
            return points;
        }

        public int getGoalDiff() {
            return goalDiff;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Team other)) return false;
            return points == other.points &&
                   goalDiff == other.goalDiff &&
                   name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, points, goalDiff);
        }

        @Override
        public String toString() {
            return String.format("%-18s | Points: %3d | GoalDiff: %+3d | ID: %s",
                    name, points, goalDiff, getRegistrationId());
        }
    }

    /**
     * Additional domain model demonstrating generic reusability for individual players.
     */
    public static class Player {
        private final String name;
        private final int goals;
        private final int assists;

        public Player(String name, int goals, int assists) {
            this.name = Objects.requireNonNull(name);
            this.goals = goals;
            this.assists = assists;
        }

        public String getName() { return name; }
        public int getGoals() { return goals; }
        public int getAssists() { return assists; }

        @Override
        public String toString() {
            return String.format("%-15s | Goals: %2d | Assists: %2d", name, goals, assists);
        }
    }

    /**
     * Additional domain model demonstrating generic reusability for academic institutions.
     */
    public static class College {
        private final String name;
        private final int goldMedals;
        private final int totalMedals;

        public College(String name, int goldMedals, int totalMedals) {
            this.name = Objects.requireNonNull(name);
            this.goldMedals = goldMedals;
            this.totalMedals = totalMedals;
        }

        public String getName() { return name; }
        public int getGoldMedals() { return goldMedals; }
        public int getTotalMedals() { return totalMedals; }

        @Override
        public String toString() {
            return String.format("%-22s | Gold: %2d | Total Medals: %2d", name, goldMedals, totalMedals);
        }
    }

    // =========================================================================
    // 2. GENERIC LEADERBOARD COMPONENT
    // =========================================================================

    /**
     * Generic ranking component capable of ranking any entity type {@code T}.
     *
     * <p><b>PECS Principle Applied:</b></p>
     * <ul>
     *   <li>{@code Comparator<? super T>}: The comparator CONSUMES elements of type {@code T} (or any supertype).</li>
     *   <li>{@code Collection<? extends T>}: Input collections PRODUCE elements to populate the leaderboard.</li>
     * </ul>
     *
     * @param <T> the type of element managed by this leaderboard
     */
    public static final class Leaderboard<T> {
        private final List<T> entries;
        private final Comparator<? super T> defaultComparator;

        /**
         * Constructs an empty leaderboard with a specified default ranking policy.
         *
         * @param defaultComparator consumer comparator for type T or its superclasses
         */
        public Leaderboard(Comparator<? super T> defaultComparator) {
            this.defaultComparator = Objects.requireNonNull(defaultComparator, "defaultComparator cannot be null");
            this.entries = new ArrayList<>();
        }

        /**
         * Constructs a leaderboard populated from an existing collection.
         *
         * @param items collection producing elements of type T (or subtype)
         * @param defaultComparator consumer comparator
         */
        public Leaderboard(Collection<? extends T> items, Comparator<? super T> defaultComparator) {
            this(defaultComparator);
            addAll(items);
        }

        public void add(T item) {
            entries.add(Objects.requireNonNull(item, "item cannot be null"));
        }

        /**
         * Adds all elements from a producer collection (bounded wildcard {@code ? extends T}).
         */
        public void addAll(Collection<? extends T> items) {
            Objects.requireNonNull(items, "items cannot be null");
            for (T item : items) {
                add(item);
            }
        }

        /**
         * Returns an unmodifiable snapshot of the original entries.
         */
        public List<T> getEntries() {
            return Collections.unmodifiableList(entries);
        }

        public int size() {
            return entries.size();
        }

        /**
         * Generates a ranked copy of the elements according to the default comparator.
         * <p><b>Immutability Guarantee:</b> The internal collection {@code entries}
         * is NEVER reordered or modified by this call.</p>
         *
         * @return a new, independently sorted List of entries
         */
        public List<T> ranking() {
            return rankingWith(this.defaultComparator);
        }

        /**
         * Generates a ranked copy using an alternate ad-hoc comparator.
         *
         * @param customComparator alternate comparator (must accept {@code ? super T})
         * @return a new, independently sorted List of entries
         */
        public List<T> rankingWith(Comparator<? super T> customComparator) {
            Objects.requireNonNull(customComparator, "customComparator cannot be null");
            List<T> rankedCopy = new ArrayList<>(entries);
            rankedCopy.sort(customComparator);
            return Collections.unmodifiableList(rankedCopy);
        }
    }

    // =========================================================================
    // 3. COMPARATOR DEFINITIONS
    // =========================================================================

    /**
     * Official multi-field composite comparator:
     * 1. Points: descending (higher is better)
     * 2. Goal Difference: descending (higher is better)
     * 3. Team Name: ascending (alphabetical tie-breaker)
     */
    public static final Comparator<Team> OFFICIAL_COMPARATOR = Comparator
            .comparing(Team::getPoints)
            .thenComparing(Team::getGoalDiff)
            .reversed()
            .thenComparing(Team::getName);

    /**
     * Alternative explicit formulation achieving identical semantics:
     * Demonstrates {@code Comparator.reverseOrder()} within field extractor.
     */
    public static final Comparator<Team> EXPLICIT_COMPARATOR = Comparator
            .comparing(Team::getPoints, Comparator.reverseOrder())
            .thenComparing(Team::getGoalDiff, Comparator.reverseOrder())
            .thenComparing(Team::getName);

    /**
     * Faulty Comparator #1: Integer subtraction arithmetic.
     * <p>Formula: {@code t2.points - t1.points}</p>
     * <p>Vulnerability: Underflows / overflows when comparing extreme positive and negative values.</p>
     */
    public static final Comparator<Team> FAULTY_SUBTRACTION_COMPARATOR = (t1, t2) ->
            t2.getPoints() - t1.getPoints(); // DANGEROUS: susceptible to integer overflow

    /**
     * Faulty Comparator #2: Omission of tie-breaking attributes.
     * <p>Vulnerability: Ranks solely on points, considering teams with equal points
     * equivalent even when goal differences or names diverge.</p>
     */
    public static final Comparator<Team> FAULTY_NO_TIEBREAKER_COMPARATOR = Comparator
            .comparing(Team::getPoints, Comparator.reverseOrder());

    // =========================================================================
    // 4. TEST SUITES & DEMONSTRATIONS
    // =========================================================================

    private static void printHeader(String title) {
        System.out.println();
        System.out.println("=".repeat(75));
        System.out.println(" " + title);
        System.out.println("=".repeat(75));
    }

    /**
     * Test Suite 1: Multi-field ranking with deliberate tie scenarios.
     */
    public static void testOfficialRankingAndTieBreaks() {
        printHeader("TEST SUITE 1: Multi-Field Ranking & Tie-Break Verification");

        List<Team> sampleTeams = List.of(
                new Team("Arsenal",           86, +45), // Pts tie with Man City, lower GD
                new Team("Manchester City",   86, +50), // Pts tie with Arsenal, higher GD -> should be #1
                new Team("Liverpool",         82, +35),
                new Team("Aston Villa",       68, +15),
                new Team("Tottenham",         66, +12),
                new Team("Chelsea",           63, +14),
                new Team("Newcastle",         60, +22), // Pts tie with Man Utd, higher GD -> should be ahead
                new Team("Manchester United", 60,  -1), // Pts tie with Newcastle, lower GD
                new Team("West Ham",          48,  -7), // Identical Pts & GD with Brighton -> tie broken alphabetically
                new Team("Brighton",          48,  -7), // Identical Pts & GD with West Ham -> Brighton ('B') ahead of West Ham ('W')
                new Team("Everton",           40, -11),
                new Team("Brentford",         39,  -9)
        );

        Leaderboard<Team> leaderboard = new Leaderboard<>(sampleTeams, OFFICIAL_COMPARATOR);

        // Snapshot original list state before ranking
        List<Team> originalSnapshot = new ArrayList<>(leaderboard.getEntries());

        System.out.println("Original (Unranked) List Order:");
        int idx = 1;
        for (Team t : leaderboard.getEntries()) {
            System.out.printf("  [%2d] %s%n", idx++, t);
        }

        // Execute ranking
        List<Team> ranked = leaderboard.ranking();

        System.out.println("\nRanked Output (1. Pts DESC | 2. GD DESC | 3. Name ASC):");
        idx = 1;
        for (Team t : ranked) {
            System.out.printf("  #%2d  %s%n", idx++, t);
        }

        // Correctness Assertions
        assert ranked.get(0).getName().equals("Manchester City") : "Fail: Man City must be #1 on GD";
        assert ranked.get(1).getName().equals("Arsenal")         : "Fail: Arsenal must be #2";
        assert ranked.get(6).getName().equals("Newcastle")       : "Fail: Newcastle must be ahead of Man Utd on GD";
        assert ranked.get(7).getName().equals("Manchester United") : "Fail: Man Utd must follow Newcastle";
        assert ranked.get(8).getName().equals("Brighton")        : "Fail: Brighton must precede West Ham alphabetically";
        assert ranked.get(9).getName().equals("West Ham")        : "Fail: West Ham must follow Brighton alphabetically";

        // Verify immutability of original list
        assert leaderboard.getEntries().equals(originalSnapshot) : "Fail: Original leaderboard entries were modified!";
        assert leaderboard.getEntries().size() == originalSnapshot.size() : "Fail: Entry count changed!";

        System.out.println("\n[OK] All multi-field tie-breaking assertions PASSED.");
        System.out.println("[OK] Immutability verified: original list preserved exactly unchanged.");
    }

    /**
     * Test Suite 2: Integer Subtraction Overflow Failure Demonstration.
     */
    public static void testSubtractionOverflowFailure() {
        printHeader("TEST SUITE 2: Integer Subtraction Comparator Flaw & Overflow");

        System.out.println("Problem: (t2.points - t1.points) causes signed 32-bit integer overflow");
        System.out.println("when points differences exceed Integer.MAX_VALUE (2,147,483,647).\n");

        // Extreme values demonstrating arithmetic overflow
        Team normalTeam  = new Team("Alpha Club",   1,                  +5);
        Team negativeBug = new Team("Zombie Club",  Integer.MIN_VALUE,  -10); // -2,147,483,648

        // Expected comparison: Alpha (+1) vs Zombie (-2,147,483,648)
        // In descending order, Alpha Club has MORE points, so Alpha should come FIRST.
        // Thus compare(Alpha, Zombie) should return negative (< 0).

        int safeResult = Integer.compare(negativeBug.getPoints(), normalTeam.getPoints());
        int subtractionResult = FAULTY_SUBTRACTION_COMPARATOR.compare(normalTeam, negativeBug);

        System.out.printf("Evaluating: compare(Alpha[+1], Zombie[%d])%n", Integer.MIN_VALUE);
        System.out.printf("  Integer.compare(Zombie, Alpha) [Safe]    : %d (Negative -> Alpha ranks first)%n", safeResult);
        System.out.printf("  (Zombie.pts - Alpha.pts)       [Faulty]  : %d (POSITIVE -> Zombie ranks first!)%n", subtractionResult);

        // Subtraction: (-2147483648) - 1 = +2147483647 (OVERFLOW!)
        assert subtractionResult > 0 : "Expected subtraction to overflow to positive";
        assert safeResult < 0        : "Expected safe compare to be negative";

        // Demonstrate actual incorrect sorting caused by subtraction overflow
        List<Team> extremeList = new ArrayList<>(List.of(normalTeam, negativeBug));
        extremeList.sort(FAULTY_SUBTRACTION_COMPARATOR);

        System.out.println("\nFaulty Subtraction Sorted Result:");
        for (Team t : extremeList) {
            System.out.println("  " + t);
        }

        boolean bugExposed = extremeList.get(0).getName().equals("Zombie Club");
        System.out.printf("\n[ALERT] Overflow Bug Manifested: Zombie Club ranked #1? %b%n", bugExposed);
        assert bugExposed : "Assertion failed: Subtraction overflow failed to expose inversion bug";
        System.out.println("[OK] Subtraction flaw conclusively proven and documented.");
    }

    /**
     * Test Suite 3: Demonstration of Missing Tie-Breakers.
     */
    public static void testMissingTieBreakerFlaw() {
        printHeader("TEST SUITE 3: Faulty Comparator Omitting Tie-Breakers");

        Team t1 = new Team("Alpha FC", 50, +30);
        Team t2 = new Team("Beta FC",  50, +10);

        int officialCmp = OFFICIAL_COMPARATOR.compare(t1, t2);
        int faultyCmp   = FAULTY_NO_TIEBREAKER_COMPARATOR.compare(t1, t2);

        System.out.println("Comparing Team 1 (50 pts, +30 GD) vs Team 2 (50 pts, +10 GD):");
        System.out.printf("  OFFICIAL_COMPARATOR.compare(t1, t2)           : %d (t1 decisively ahead on GD)%n", officialCmp);
        System.out.printf("  FAULTY_NO_TIEBREAKER_COMPARATOR.compare(t1, t2): %d (Identified as equal!)%n", faultyCmp);

        assert officialCmp < 0 : "Official comparator must rank t1 ahead of t2";
        assert faultyCmp == 0  : "Faulty comparator falsely treats them as tied";

        System.out.println("\n[OK] Missing tie-breaker flaw demonstrated: returns 0 despite unequal goal differences.");
    }

    /**
     * Test Suite 4: Verification of Comparator Transitivity.
     * <p>Ensures that for all triples (a, b, c):</p>
     * <ul>
     *   <li>if cmp(a, b) &gt; 0 and cmp(b, c) &gt; 0, then cmp(a, c) &gt; 0.</li>
     *   <li>if cmp(a, b) == 0 and cmp(b, c) == 0, then cmp(a, c) == 0.</li>
     * </ul>
     */
    public static void testComparatorTransitivity() {
        printHeader("TEST SUITE 4: Mathematical Verification of Comparator Transitivity");

        List<Team> testPool = List.of(
                new Team("Team A", 80, +20),
                new Team("Team B", 80, +10),
                new Team("Team C", 75, +30),
                new Team("Team D", 75, +30), // tie on pts and GD with C, name breaks tie
                new Team("Team E", 70,   0)
        );

        int tripleCount = 0;
        int transitivityViolations = 0;

        for (Team a : testPool) {
            for (Team b : testPool) {
                for (Team c : testPool) {
                    tripleCount++;
                    int ab = OFFICIAL_COMPARATOR.compare(a, b);
                    int bc = OFFICIAL_COMPARATOR.compare(b, c);
                    int ac = OFFICIAL_COMPARATOR.compare(a, c);

                    // Transitivity of superiority: if a > b and b > c => a > c
                    if (ab > 0 && bc > 0 && !(ac > 0)) {
                        transitivityViolations++;
                    }
                    // Transitivity of inferiority: if a < b and b < c => a < c
                    if (ab < 0 && bc < 0 && !(ac < 0)) {
                        transitivityViolations++;
                    }
                    // Transitivity of equivalence: if a == b and b == c => a == c
                    if (ab == 0 && bc == 0 && ac != 0) {
                        transitivityViolations++;
                    }
                }
            }
        }

        System.out.printf("Tested %d triple permutations across test set.%n", tripleCount);
        System.out.printf("Transitivity violations detected: %d%n", transitivityViolations);
        assert transitivityViolations == 0 : "Transitivity violation detected in official comparator!";
        System.out.println("[OK] Official composite comparator strictly satisfies Transitivity.");
    }

    /**
     * Test Suite 5: Demonstration of Bounded Wildcards (PECS) & Generic Reusability.
     */
    public static void testBoundedWildcardsAndReusability() {
        printHeader("TEST SUITE 5: Bounded Wildcards (PECS) & Generic Reusability");

        // 1. Consumer Super: Comparator<? super Team> accepts Comparator<Participant>
        Comparator<Participant> registrationComparator = Comparator.comparing(Participant::getRegistrationId);

        // Leaderboard<Team> accepts Comparator<Participant> because Participant is a superclass of Team
        Leaderboard<Team> idLeaderboard = new Leaderboard<>(registrationComparator);
        idLeaderboard.add(new Team("REG-0100", "Zeta",  10, 0));
        idLeaderboard.add(new Team("REG-0005", "Omega", 50, 0));

        List<Team> idRanked = idLeaderboard.ranking();
        System.out.println("Leaderboard<Team> ranked by superclass Comparator<Participant>:");
        for (Team t : idRanked) {
            System.out.println("  " + t);
        }
        assert idRanked.get(0).getRegistrationId().equals("REG-0005") : "PECS consumer super failed";

        // 2. Producer Extends: Collection<? extends Team>
        List<Team> teamBatch = List.of(new Team("Team X", 10, 0), new Team("Team Y", 20, 5));
        idLeaderboard.addAll(teamBatch); // Accepts Collection<? extends Team>
        assert idLeaderboard.size() == 4 : "PECS producer extends failed";

        // 3. Reusability for Individual Players
        System.out.println("\nGeneric Reusability for Players:");
        Comparator<Player> playerComparator = Comparator
                .comparing(Player::getGoals, Comparator.reverseOrder())
                .thenComparing(Player::getAssists, Comparator.reverseOrder())
                .thenComparing(Player::getName);

        Leaderboard<Player> playerBoard = new Leaderboard<>(playerComparator);
        playerBoard.add(new Player("Haaland", 27, 5));
        playerBoard.add(new Player("Salah",   25, 12));
        playerBoard.add(new Player("Palmer",  22, 11));

        int rank = 1;
        for (Player p : playerBoard.ranking()) {
            System.out.printf("  #%d  %s%n", rank++, p);
        }
        assert playerBoard.ranking().get(0).getName().equals("Haaland") : "Player ranking failed";

        // 4. Reusability for Colleges
        System.out.println("\nGeneric Reusability for Colleges:");
        Comparator<College> collegeComparator = Comparator
                .comparing(College::getGoldMedals, Comparator.reverseOrder())
                .thenComparing(College::getTotalMedals, Comparator.reverseOrder())
                .thenComparing(College::getName);

        Leaderboard<College> collegeBoard = new Leaderboard<>(collegeComparator);
        collegeBoard.add(new College("IIT Madras",  15, 38));
        collegeBoard.add(new College("IIT Bombay",  15, 42)); // Same golds, more total medals
        collegeBoard.add(new College("NIT Trichy",  10, 25));

        rank = 1;
        for (College c : collegeBoard.ranking()) {
            System.out.printf("  #%d  %s%n", rank++, c);
        }
        assert collegeBoard.ranking().get(0).getName().equals("IIT Bombay") : "College ranking failed";

        System.out.println("\n[OK] Bounded wildcards (? super T, ? extends T) and generic reusability verified.");
    }

    // =========================================================================
    // 5. SCALE BENCHMARK: One Composite Pass vs Three Sequential Stable Passes
    // =========================================================================

    /**
     * Simulates the legacy approach of performing 3 sequential stable sorting passes:
     * Pass 1: sort by tertiary field (name ASC)
     * Pass 2: sort by secondary field (goalDiff DESC)
     * Pass 3: sort by primary field (points DESC)
     */
    private static void threePassStableSort(List<Team> list) {
        list.sort(Comparator.comparing(Team::getName));
        list.sort(Comparator.comparing(Team::getGoalDiff).reversed());
        list.sort(Comparator.comparing(Team::getPoints).reversed());
    }

    public static void runPerformanceBenchmark() throws IOException {
        printHeader("TEST SUITE 6: Benchmark — 1 Composite Pass vs 3 Sequential Passes");
        System.out.println("Comparing execution time across 1,000, 10,000, and 100,000 teams.");
        System.out.println("Warm-up: 1 run | Measured: 5 runs | Timing: System.nanoTime()\n");

        int[] datasetSizes = {1_000, 10_000, 100_000};
        int WARMUP_RUNS = 1;
        int MEASURED_RUNS = 5;

        Path csvPath = resolveCsvPath();
        Files.createDirectories(csvPath.getParent());

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath.toFile()))) {
            writer.println("DatasetSize,Method,RunNumber,ExecutionTimeMs");

            System.out.printf("  %-12s | %-24s | %-24s | %-12s%n",
                    "Dataset Size", "1 Composite Pass (ms)", "3 Sequential Passes (ms)", "Speedup");
            System.out.println("  " + "-".repeat(78));

            Random rng = new Random(42); // Deterministic seed for reproducible comparisons

            for (int size : datasetSizes) {
                // Generate benchmark dataset
                List<Team> testData = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    String name = "Team_" + String.format("%06d", i);
                    int pts = rng.nextInt(100);
                    int gd  = rng.nextInt(101) - 50; // -50 to +50
                    testData.add(new Team(name, pts, gd));
                }

                double[] compositeTimes = new double[MEASURED_RUNS];
                double[] threePassTimes = new double[MEASURED_RUNS];

                for (int run = -WARMUP_RUNS; run < MEASURED_RUNS; run++) {
                    // Benchmark Method 1: Single Composite Comparator Pass
                    List<Team> copy1 = new ArrayList<>(testData);
                    long t0 = System.nanoTime();
                    copy1.sort(OFFICIAL_COMPARATOR);
                    long compTimeNs = System.nanoTime() - t0;

                    // Benchmark Method 2: Three Sequential Passes
                    List<Team> copy2 = new ArrayList<>(testData);
                    long t1 = System.nanoTime();
                    threePassStableSort(copy2);
                    long threePassTimeNs = System.nanoTime() - t1;

                    // Verify both methods produce identically sorted results
                    if (!copy1.equals(copy2)) {
                        throw new IllegalStateException("Verification failed: 1-pass and 3-pass results diverged!");
                    }

                    if (run >= 0) {
                        double compMs = compTimeNs / 1_000_000.0;
                        double threeMs = threePassTimeNs / 1_000_000.0;
                        compositeTimes[run] = compMs;
                        threePassTimes[run] = threeMs;

                        writer.printf("%d,CompositePass,%d,%.4f%n", size, run + 1, compMs);
                        writer.printf("%d,ThreeSequentialPasses,%d,%.4f%n", size, run + 1, threeMs);
                    }
                }

                double avgComposite = calculateAverage(compositeTimes);
                double avgThreePass = calculateAverage(threePassTimes);
                double speedup = avgThreePass / avgComposite;

                System.out.printf("  %-12s | %-24.4f | %-24.4f | %-12.2fx%n",
                        String.format("%,d", size), avgComposite, avgThreePass, speedup);
            }
        }

        System.out.println("\n[OK] Benchmark results successfully exported to: " + csvPath.toAbsolutePath());
    }

    private static double calculateAverage(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static Path resolveCsvPath() {
        Path candidate = Paths.get("Task05_Generics_Leaderboard", "data", "Q5_benchmark_results.csv");
        if (candidate.getParent().toFile().isDirectory()) {
            return candidate;
        }
        candidate = Paths.get("Task05_Generic_Leaderboard", "data", "Q5_benchmark_results.csv");
        if (candidate.getParent().toFile().isDirectory()) {
            return candidate;
        }
        return Paths.get("data", "Q5_benchmark_results.csv");
    }

    // =========================================================================
    // 6. MAIN EXECUTION ENTRYPOINT
    // =========================================================================

    public static void main(String[] args) throws IOException {
        System.out.println("===========================================================================");
        System.out.println("  Q5 — Derive a Generic Ranking Component from a Principle");
        System.out.println("  Scenario: Inter-College Sports Meet / Team Leaderboard");
        System.out.println("  Student : Ranjith Kumar.R | Register: 1924L260003 | Java 27");
        System.out.println("===========================================================================");

        testOfficialRankingAndTieBreaks();
        testSubtractionOverflowFailure();
        testMissingTieBreakerFlaw();
        testComparatorTransitivity();
        testBoundedWildcardsAndReusability();
        runPerformanceBenchmark();

        printHeader("FINAL SUMMARY: ALL Q5 TESTS AND ASSERTIONS PASSED SUCCESSFULLY");
    }
}
