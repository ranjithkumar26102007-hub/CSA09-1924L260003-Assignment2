# Java Assignment 2 (CO2) — Strings and Collections: Empirical Analysis and Design Report

---

### Candidate & Course Details
- **Student Name:** Ranjith Kumar.R
- **Register Number:** 1924L260003
- **Degree / Program:** B.Tech Computer Science and Engineering
- **Course Title:** Programming in Java
- **Course Outcome:** CO2 (Strings, Regex, Collections Framework, Generics, and Utilities)
- **Repository Name:** `CSA09-1924L260003-Assignment2`
- **Execution Environment:** OpenJDK 27 (64-Bit Server VM, build 27+35-2325), Windows 11
- **Evaluation Paradigm:** Predict → Design → Implement → Measure → Analyse → Evaluate → Diagnose → Redesign → Validate

---

## Question 1: Building Text Under Load — String, StringBuilder or StringBuffer? (6 Marks)

**Bloom's Taxonomy Level:** K4 – Analyse  
**Context:** Railway Reservation System E-Ticket Generation & Shared Logging  

### 1.1 Objective and Engineering Problem
In a railway reservation infrastructure, every confirmed reservation generates a digital e-ticket combining numerous data elements: PNR, train code, passenger names, coach, berth details, quotas, and multiple tariff tiers. The naive implementation constructs this printable text block by concatenating fields inside a loop using the `+` operator.

The objective of this investigation is to rigorously evaluate whether the choice among `java.lang.String` (`+`), `java.lang.StringBuilder`, and `java.lang.StringBuffer` is negligible or critical to system throughput, heap allocation, and thread safety under high reservation volume.

### 1.2 Experimental Methodology
1. **Pre-Implementation Prediction:** Derived the theoretical time and space complexity based on Java's String immutability model and buffer resizing mechanics.
2. **Contrasting Workload Design:** Designed three distinct workloads testing short-ticket baselines, high-volume batch processing, and deep field-concatenation chains.
3. **High-Precision Timing:** Benchmarked each approach using `System.nanoTime()`, incorporating 1 initial JIT warm-up execution followed by 5 measured repetitions to calculate the arithmetic mean.
4. **Content Equivalence Verification:** Programmatically verified that all three implementations generate 100% character-identical output for every ticket.
5. **Concurrency & Thread Safety:** Designed and measured a concurrent multi-threaded simulation with 10 worker threads writing to a shared audit log to validate race condition avoidance.
6. **Data Archival:** Exported all raw per-run timings, averages, intermediate object estimations, and heap deltas to `Task01_StringBenchmark/data/benchmark_results.csv`.

---

### 1.3 Workloads and Empirical Benchmark Results

The benchmark was executed under OpenJDK 27. The table below presents the exact measurements recorded in `Task01_StringBenchmark/data/benchmark_results.csv`:

| Workload | Method | Warmup (ms) | Run 1 (ms) | Run 2 (ms) | Run 3 (ms) | Run 4 (ms) | Run 5 (ms) | **Average (ms)** | **Est. Intermediate Objects** | **Approx. Mem Delta (KB)** |
|---|---|---|---|---|---|---|---|---|---|---|
| **A (1 × 6)** | `String (+)` | 0.6533 | 0.1649 | 0.1611 | 0.2380 | 0.1574 | 0.4677 | **0.2378** | 5 | 0.00 |
| | `StringBuilder` | 0.2954 | 0.0325 | 0.0220 | 0.0215 | 0.0219 | 0.0240 | **0.0244** | 0 | 0.00 |
| | `StringBuffer` | 0.2150 | 0.0204 | 0.0138 | 0.0132 | 0.0135 | 0.0129 | **0.0148** | 0 | 0.00 |
| **B (10k × 50)** | `String (+)` | 282.5433 | 329.0104 | 73.1604 | 75.9985 | 69.8045 | 388.2437 | **187.2435** | 490,000 | 147,655.27 |
| | `StringBuilder` | 48.4824 | 19.9645 | 19.7233 | 19.1334 | 74.1077 | 91.0032 | **44.7864** | 0 | 194,048.00 |
| | `StringBuffer` | 75.2604 | 25.5246 | 29.2710 | 22.1592 | 25.6770 | 24.2890 | **25.3842** | 0 | 194,048.00 |
| **C (1k × 100)**| `String (+)` | 27.5324 | 29.2110 | 143.7890 | 187.8862 | 194.4885 | 194.1392 | **149.9028** | 99,000 | 520,192.00 |
| | `StringBuilder` | 17.6843 | 12.3452 | 4.4284 | 6.1845 | 3.4082 | 2.9051 | **5.8543** | 0 | 37,376.00 |
| | `StringBuffer` | 19.1546 | 8.7846 | 4.3096 | 3.5092 | 5.2772 | 3.2771 | **5.0315** | 0 | 37,376.00 |

#### Algorithmic Complexity Comparison
- **Repeated `+` Concatenation: $O(n^2)$**  
  For a ticket of $n$ fields with average length $L$, the $k$-th concatenation copies all previously accumulated $(k-1)L$ characters plus $L$ characters:
  $$\text{Total Copies} = \sum_{k=1}^{n} k \cdot L = L \cdot \frac{n(n+1)}{2} \in O(n^2)$$
  Across $T$ tickets, total characters copied scales as $O(T \cdot n^2)$. In Workload C ($n = 100$), this copies over 5,050,000 characters for only 1,000 tickets, resulting in **149.90 ms** execution time.
- **`StringBuilder` & `StringBuffer`: $O(n)$**  
  The internal character array (`byte[]` in Compact Strings) appends in place. Buffer expansion doubles the array capacity, yielding amortized $O(1)$ time per append. Total characters copied across $n$ fields scales as $O(n)$, executing in **5.85 ms** for Workload C (**~25× faster** than `+`).

---

### 1.4 Intermediate String Object Analysis

A critical architectural distinction must be drawn between **intermediate temporary String objects** generated during accumulation and **final output String objects** delivered to the caller:

1. **Repeated String Concatenation (`+`):**
   - Each ticket joins $n$ fields through $n$ sequential concatenation steps.
   - The first $(n - 1)$ operations create temporary intermediate `String` objects that are immediately abandoned as unreferenced garbage on the subsequent iteration.
   - The final ($n$-th) concatenation creates the $1$ persistent ticket string.
   - **Formula:**
     $$\text{Intermediate Strings} = T \times (n - 1)$$
     $$\text{Final Output Strings} = T$$
   - **Empirical Counts:**
     - **Workload A (1 × 6):** $1 \times (6 - 1) = \mathbf{5}$ intermediate objects ($1$ final output string).
     - **Workload B (10,000 × 50):** $10,000 \times (50 - 1) = \mathbf{490,000}$ intermediate objects ($10,000$ final output strings).
     - **Workload C (1,000 × 100):** $1,000 \times (100 - 1) = \mathbf{99,000}$ intermediate objects ($1,000$ final output strings).
2. **`StringBuilder` & `StringBuffer`:**
   - Both mutate an underlying contiguous buffer in place.
   - **Intermediate temporary String objects in append loop:** $\mathbf{0}$
   - **Final output String objects:** Exactly $\mathbf{1}$ string per ticket produced when `.toString()` is invoked.
   - This eliminates 490,000 heap object allocations in Workload B, drastically reducing Eden-space memory turnover.

---

### 1.5 Memory Measurement Limitations & Runtime Variability

#### Limitations of `Runtime.getRuntime()`
The recorded heap memory deltas (`totalMemory() - freeMemory()`) measure **approximate process-wide JVM heap occupancy**, not isolated per-method memory allocations. The primary limitations include:
- **Global Process Scope:** The metric captures all background allocations across the JVM, including JIT compilation threads, class loading metadata, and standard library runtime activities.
- **Asynchronous GC Interleaving:** Background garbage collection sweeps (e.g., G1 GC concurrent cycles) occur non-deterministically. If a GC cycle runs between the baseline and end timestamps, the apparent delta drops; if garbage accumulates uncollected, the delta appears inflated.
- **Heap Resizing:** The JVM dynamically expands `totalMemory()` when Eden fills, altering the reference frame between measurements.

#### Measurement Variability and the `StringBuilder` vs `StringBuffer` Comparison
In Workload B and C, the 5-run averages show `StringBuffer` closely matching or slightly leading `StringBuilder` ($25.38\text{ ms}$ vs $44.78\text{ ms}$ in Workload B; $5.03\text{ ms}$ vs $5.85\text{ ms}$ in Workload C).

**Important Engineering Clarification:**  
This empirical observation does **not** prove that `StringBuffer` is generally faster than `StringBuilder`. The observed numbers reflect two well-documented JVM runtime factors:
1. **JIT Escape Analysis & Lock Elision:** In HotSpot (JDK 27), when a synchronized object (`StringBuffer`) is instantiated locally inside a method and does not escape to other threads, the JIT C2 compiler recognizes that monitor locks are completely uncontended. It performs **lock elision**, stripping synchronization instructions (`monitorenter`/`monitorexit`) from compiled assembly.
2. **Measurement Variability:** Across five macro repetitions, run-to-run timing variations (such as Run 1 vs Run 5) arise from tiered JIT compilation transitions (C1 interpreter to C2 optimized native code), thread scheduling, and memory allocation thresholds. Without continuous low-level JVM profiling (e.g., `-Xlog:gc*`), individual run differences represent **possible sources of experimental variability**, not definitive proof of superior algorithmic throughput.

Theoretically and under real multi-threaded contention, `StringBuilder` remains the strictly preferred choice for thread-local single-threaded formatting.

---

### 1.6 Multithreaded Booking Log Concurrency Demonstration

To resolve the architectural question of how text construction behaves in a concurrent server, a multi-threaded benchmark was executed:
- **Architecture:** An `ExecutorService` with **10 concurrent worker threads**, each generating **100 tickets** ($10 \times 100 = 1,000$ total records).
- **Per-Thread Formatting:** Each worker thread assembled its individual ticket locally using an unshared, thread-confined `StringBuilder`.
- **Atomic Log Appending:** Each thread appended its terminating newline (`\n`) to its local `StringBuilder` *before* issuing a single atomic call to `sharedLog.append(localTicket)` on a shared `StringBuffer`.
- **Validation Results:**
  ```text
  Threads: 10
  Target records: 1000
  Atomic counter: 1000
  Parsed records in shared StringBuffer: 1000
  Duration: 17 ms
  SUCCESS: Exactly 1,000 complete, uncorrupted records written to shared log.
  ```
- **Atomicity Finding:** While `StringBuffer` guarantees method-level thread safety, compound actions (e.g., appending ticket text then appending a newline in two separate method calls) can interleave with other threads. Assembling the full line locally and writing it in a single synchronized append guarantees record integrity.

---

### 1.7 Q1 Architectural Conclusions and Design Decisions

| System Role | Recommended Class | Engineering Justification |
|---|---|---|
| **Per-Ticket Assembly** | `StringBuilder` | **Thread-Confined & Zero Lock Overhead:** Ticket assembly occurs strictly on the stack of the handling worker thread. Because no other thread accesses this builder, synchronization locks are redundant. `StringBuilder` eliminates all synchronization overhead and provides maximum $O(n)$ formatting throughput. |
| **Shared Central Log** | `StringBuffer` (or Concurrent Appender) | **Thread-Safe Shared Resource:** Multiple concurrent threads append completed records to a common target. `StringBuffer`'s synchronized methods prevent buffer state corruption and pointer race conditions. |
| **Avoid in Loops** | `String (+)` | **Quadratic Penalty:** Incurring $O(n^2)$ copying and generating $T \times (n-1)$ temporary garbage objects creates severe latency and heap turnover under load. |

*Full mathematical proofs, pseudocode, and modern alternatives (`String.join`, `StringJoiner`, `String.format`) are documented in [`Task01_StringBenchmark/Q1_Analysis.md`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task01_StringBenchmark/Q1_Analysis.md).*

---

## Question 2: When Is ArrayList an Appropriate Engineering Choice? (4 Marks) — [PENDING]

**Bloom's Taxonomy Level:** K4 – Analyse  
**Context:** Music Streaming Playlist Editor  
**Status:** *Pending Implementation*

### Problem Scope
A music streaming app manages playlists where users append songs at the end, insert/delete at arbitrary positions, jump directly to the $k$-th song, and iterate through playlists. The assignment requires:
- Experimental comparison of `ArrayList` vs `LinkedList` across three playlist sizes for:
  - Add at end (amortized $O(1)$ vs $O(1)$)
  - Insert/remove at front and middle ($O(n)$ shift vs $O(1)$ pointer update after $O(n)$ traversal)
  - Random access to the $k$-th element ($O(1)$ index lookup vs $O(n)$ node traversal)
  - Full list sequential iteration
- Operating boundary determination: defining when `ArrayList` is an appropriate engineering choice and when it should be replaced.
- Changed Condition: evaluating `ArrayDeque` for an "Up Next" queue supporting efficient insertions at both ends and front removals.

---

## Question 3: Engineer the hashCode–equals Contract (6 Marks) — [PENDING]

**Bloom's Taxonomy Level:** K4 – Analyse  
**Context:** College Fest Event Attendance De-Duplication  
**Status:** *Pending Implementation*

### Problem Scope
A student attendance scanning system records scans at multiple gates in a `HashSet<Student>` to ensure uniqueness. A faulty `Student` class overrides `equals()` on `regNo` but fails to override `hashCode()`. The assignment requires:
- Designing three deliberately contrasting test suites:
  1. Scans where `HashSet` appears to work correctly by coincidence.
  2. Scans deliberately designed to expose duplicate student entries.
  3. Edge-case test (e.g., mutating `regNo` after insertion or `TreeSet` inconsistency).
- Demonstrating the mechanics of hash bucket calculation, hash collision resolution, and why equal objects must possess identical hash codes.
- Resolving the "Complete the Code" task (`Attendance.java`) and verifying unique student de-duplication.

---

## Question 4: Choosing a Map for a Pharmacy Inventory (6 Marks) — [PENDING]

**Bloom's Taxonomy Level:** K4 – Analyse  
**Context:** Hospital Pharmacy Stock Lookup & Reporting  
**Status:** *Pending Implementation*

### Problem Scope
A pharmacy system performs hundreds of lookups per day by medicine code, printing evening reports in code order (natural/sorted order) and insertion order (chronological order). The assignment requires:
- Rigorous comparative analysis of `HashMap`, `TreeMap`, and `LinkedHashMap` across put, get, and iteration operations.
- Evaluating which map naturally supports each report without costly secondary sorting passes.
- Empirical benchmarking across three medicine inventory sizes recording time for 100,000 lookups and report generation durations.
- Scalability inquiry and defense against legacy classes like `java.util.Hashtable`.

---

## Question 5: Derive a Generic Ranking Component from a Principle (7 Marks) — [PENDING]

**Bloom's Taxonomy Level:** K4 – Analyse  
**Context:** Inter-College Sports Meet Leaderboard  
**Status:** *Pending Implementation*

### Problem Scope
A sports meet ranks teams by points (descending), tie-broken by goal difference (descending), then by team name (alphabetical). The component must be reusable for ranking players, colleges, and quiz teams. The assignment requires:
- Converting the consistency principle into a generic class `Leaderboard<T>`.
- Formulating a robust multi-field comparator using `Comparator.comparing()`, `.thenComparing()`, and `.reversed()`.
- Demonstrating bounded types and wildcard usage (`Comparator<? super T>`, `List<? extends T>`).
- Completing the starter code (`LeaderboardDemo.java`) and verifying against expected tie-break rankings.

---

## Question 6: Priority, Order and Fairness in a Queue (8 Marks) — [PENDING]

**Bloom's Taxonomy Level:** K5 – Evaluate  
**Context:** Hospital Emergency Room Triage System  
**Status:** *Pending Implementation*

### Problem Scope
Emergency department patients arrive with severity levels from 1 (critical) to 5 (minor). The attending physician must see the most severe patient next; among equal severities, arrival order (FIFO) must be strictly preserved. The assignment requires:
- Designing a queue-based triage solution using `PriorityQueue` with a composite `Comparator` on severity and arrival sequence counter.
- Explaining why direct printing of `PriorityQueue` does not reflect heap extraction order.
- Investigating queue growth costs ($O(\log n)$ insertion/removal) across three queue scales.
- Addressing the starvation vulnerability for severity-5 patients under continuous high-severity arrivals and designing an aging/fairness policy.
- Completing the starter code (`Triage.java`) and verifying expected extraction order.

---

## Question 7: Day-4 Design Challenge — Diagnose, Redesign and Validate (13 Marks) — [PENDING]

**Bloom's Taxonomy Level:** K5 – Evaluate  
**Status:** *Pending Implementation*

### Problem Scope
Reviewing the empirical evidence generated across Q1–Q6, select ONE solution demonstrating a significant limitation under scaling or requirement changes:
- **Part A — Diagnose the Bottleneck (2 Marks):** Identify CPU/memory hotspots, scalability growth rates, unnecessary repeated operations, and root cause class limitations.
- **Part B — Define the Design Objective (2 Marks):** Formulate measurable redesign goals (e.g., eliminate object churn, reduce $O(n)$ scans to $O(1)$).
- **Part C — Design the Revised Solution (4 Marks):** Architectural redesign, class diagrams, pseudocode, and structural trade-off analysis.
- **Part D — Predict Before Implementing (1 Mark):** Testable hypotheses formulated before experimental validation.
- **Part E — Implement and Validate (2 Marks):** Side-by-side comparative benchmarking under identical test conditions.
- **Part F — Defend the Final Design (2 Marks):** Critical evaluation, boundary conditions, and defensible engineering conclusion.
