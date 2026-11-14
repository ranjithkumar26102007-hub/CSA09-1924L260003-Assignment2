# Java Assignment 2 (CO2) — Strings and Collections

**Student:** Ranjith Kumar.R  
**Register Number:** 1924L260003  
**Course:** Programming in Java (CO2 — Sessions 15–26)  
**Java Version:** OpenJDK 27 (build 27+35-2325)  
**Repository Name:** `CSA09-1924L260003-Assignment2`  
**Evaluation Model:** Predict → Design → Implement → Measure → Analyse → Evaluate → Diagnose → Redesign → Validate  

---

## 1. Assignment Overview

This assignment covers advanced Java core concepts specified under **Course Outcome 2 (CO2)**:
- **Strings and Text Processing:** Immutability, `StringBuilder`, `StringBuffer`, regular expressions, and memory efficiency under high throughput.
- **Collections Framework:** In-depth comparative analysis of `List` (`ArrayList`, `LinkedList`), `Deque` (`ArrayDeque`), `Set` (`HashSet`, `TreeSet`), `Map` (`HashMap`, `TreeMap`, `LinkedHashMap`), and `PriorityQueue`.
- **Object Contracts:** Rigorous engineering of the `hashCode()` and `equals()` contract in hash-based structures.
- **Generics & Polymorphism:** Type bounds, PECS wildcard usage (`? extends T`, `? super T`), `Comparable`, and multi-tiered `Comparator` chaining.
- **Day-4 Design Challenge:** Empirical bottleneck diagnosis, algorithmic redesign, and experimental validation.

---

## 2. Project Folder Structure

```text
CampusSmartParking_Assignment2/
│   .gitignore
│   README.md
│   REPORT.md
│   
├───Task01_StringBenchmark/
│   │   Q1_Analysis.md
│   ├───data/
│   │       benchmark_results.csv
│   ├───out/
│   │       Q1TicketBenchmark.class
│   │       Q1TicketBenchmark$BenchmarkResult.class
│   │       Q1TicketBenchmark$TicketBuilder.class
│   │       Q1TicketBenchmark$Workload.class
│   ├───screenshots/
│   │       Q1-Benchmark-Full.png
│   └───src/
│           Q1TicketBenchmark.java
│           
├───Task02_CollectionsComparison/
│   │   Q2_Analysis.md
│   ├───data/
│   │       Q2_benchmark_results.csv
│   ├───out/
│   │       Q2PlaylistBenchmark.class
│   │       Q2PlaylistBenchmark$BenchmarkAction.class
│   │       Q2PlaylistBenchmark$ListSupplier.class
│   │       Q2PlaylistBenchmark$OpResult.class
│   │       Q2PlaylistBenchmark$Song.class
│   ├───screenshots/
│   └───src/
│           Q2PlaylistBenchmark.java
│           
├───Task03_HashSet_Student/
│   ├───data/
│   ├───screenshots/
│   └───src/
│           
├───Task04_Map_Inventory/
│   ├───data/
│   ├───screenshots/
│   └───src/
│           
├───Task05_Generic_Leaderboard/
│   ├───data/
│   ├───screenshots/
│   └───src/
│           
├───Task06_PriorityQueue_Triage/
│   ├───data/
│   ├───screenshots/
│   └───src/
│           
└───Task07_Design_Redesign/
    ├───data/
    ├───screenshots/
    └───src/
```

---

## 3. Compilation and Execution Guide

All tasks are configured to compile into dedicated `out/` directories to maintain clean separation between source and bytecode.

### Navigate to Project Root
```powershell
Set-Location -Path "$env:USERPROFILE\Downloads\CampusSmartParking_Assignment2"
```

### Task 01: String, StringBuilder and StringBuffer Benchmark (Q1)
```powershell
# Compile
javac -Xlint:all -d Task01_StringBenchmark/out Task01_StringBenchmark/src/Q1TicketBenchmark.java

# Run
java -cp Task01_StringBenchmark/out Q1TicketBenchmark
```

### Task 02: ArrayList vs LinkedList Collections Comparison (Q2)
```powershell
# Compile
javac -Xlint:all -d Task02_CollectionsComparison/out Task02_CollectionsComparison/src/Q2PlaylistBenchmark.java

# Run
java -cp Task02_CollectionsComparison/out Q2PlaylistBenchmark
```

### Task 03: HashSet and the hashCode-equals Contract (Q3)
```powershell
# Compile
javac -Xlint:all -d Task03_HashSet_Student/out Task03_HashSet_Student/src/Q3HashSetAttendance.java

# Run
java -cp Task03_HashSet_Student/out Q3HashSetAttendance
```

---

## 4. Benchmark Summaries

### Q1 Summary: Railway E-Ticket Generation (String vs StringBuilder vs StringBuffer)
- **Source:** [`Task01_StringBenchmark/src/Q1TicketBenchmark.java`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task01_StringBenchmark/src/Q1TicketBenchmark.java)
- **Analysis:** [`Task01_StringBenchmark/Q1_Analysis.md`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task01_StringBenchmark/Q1_Analysis.md)
- **Data:** [`Task01_StringBenchmark/data/benchmark_results.csv`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task01_StringBenchmark/data/benchmark_results.csv)

| Workload | Method | Warmup (ms) | 5-Run Avg (ms) | Intermediate Objects | Final Output Strings | Approx Mem Delta (KB)* |
|---|---|---|---|---|---|---|
| **A: 1 Ticket × 6 Fields** | `String (+)` | 0.6533 | **0.2378** | 5 | 1 | 0.00 |
| | `StringBuilder` | 0.2954 | **0.0244** | 0 | 1 | 0.00 |
| | `StringBuffer` | 0.2150 | **0.0148** | 0 | 1 | 0.00 |
| **B: 10,000 Tickets × 50 Fields** | `String (+)` | 282.5433 | **187.2435** | 490,000 | 10,000 | 147,655.27 |
| | `StringBuilder` | 48.4824 | **44.7864** | 0 | 10,000 | 194,048.00 |
| | `StringBuffer` | 75.2604 | **25.3842** | 0 | 10,000 | 194,048.00 |
| **C: 1,000 Tickets × 100 Fields** | `String (+)` | 27.5324 | **149.9028** | 99,000 | 1,000 | 520,192.00 |
| | `StringBuilder` | 17.6843 | **5.8543** | 0 | 1,000 | 37,376.00 |
| | `StringBuffer` | 19.1546 | **5.0315** | 0 | 1,000 | 37,376.00 |

- **Key Takeaways:** Repeated `+` exhibits $O(n^2)$ character copying and creates 490,000 temporary intermediate objects in Workload B. `StringBuilder` operates in $O(n)$ with 0 intermediate objects, running **~25× faster** on deep chains. Multithreaded logging verified 1,000 complete atomic records to a shared `StringBuffer` via thread-local `StringBuilder` formatting.

---

### Q2 Summary: Music Playlist Editor (ArrayList vs LinkedList vs ArrayDeque)
- **Source:** [`Task02_CollectionsComparison/src/Q2PlaylistBenchmark.java`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task02_CollectionsComparison/src/Q2PlaylistBenchmark.java)
- **Analysis:** [`Task02_CollectionsComparison/Q2_Analysis.md`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task02_CollectionsComparison/Q2_Analysis.md)
- **Data:** [`Task02_CollectionsComparison/data/Q2_benchmark_results.csv`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task02_CollectionsComparison/data/Q2_benchmark_results.csv)

| Playlist Size | Operation | ArrayList Avg (ms) | LinkedList Avg (ms) | Speedup Ratio & Verdict |
|---|---|---|---|---|
| **10,000 Songs** | Insert at Front | 2.7359 | **0.2199** | **LinkedList is 12.44× faster** ($O(1)$ head link vs $O(N)$ shift) |
| | Remove from Front | 2.6332 | **0.0668** | **LinkedList is 39.39× faster** |
| | Insert in Middle | **1.4757** | 22.2449 | **ArrayList is 15.07× faster** (fast arraycopy vs $O(N)$ traversal) |
| | Remove from Middle| **0.4782** | 13.4785 | **ArrayList is 28.18× faster** |
| | Get $k$-th Element | **0.1225** | 7.4986 | **ArrayList is 61.19× faster** ($O(1)$ index vs $O(N)$ traversal) |
| **100,000 Songs**| Insert at Front | 17.9072 | **0.1106** | **LinkedList is 161.94× faster** |
| | Remove from Front | 14.7259 | **0.0812** | **LinkedList is 181.26× faster** |
| | Insert in Middle | **6.5491** | 129.4361 | **ArrayList is 19.76× faster** |
| | Remove from Middle| **21.2797** | 150.6670 | **ArrayList is 7.08× faster** |
| | Get $k$-th Element | **0.7406** | 112.4562 | **ArrayList is 151.84× faster** |

- **Key Takeaways:**
  - `ArrayList` dominates for random access (**~152× faster** at 100k scale) and middle modifications (**~20× faster** at 100k scale because `LinkedList` must traverse $N/2$ nodes).
  - `LinkedList` only outperforms `ArrayList` when strictly inserting/removing at index 0 ($O(1)$ vs array shifting).
  - **Changed Condition:** An "Up Next" queue implemented with `ArrayDeque` delivers $O(1)$ insertions and removals at both ends without array shifting or node allocation overhead, making `ArrayDeque` the superior choice for double-ended queues.

---

### Q3 Summary: College Fest Event Attendance De-Duplication (HashSet & hashCode-equals)
- **Source:** [`Task03_HashSet_Student/src/Q3HashSetAttendance.java`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task03_HashSet_Student/src/Q3HashSetAttendance.java)
- **Analysis:** [`Task03_HashSet_Student/Q3_Analysis.md`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task03_HashSet_Student/Q3_Analysis.md)
- **Data:** [`Task03_HashSet_Student/data/Q3_benchmark_results.csv`](file:///C:/Users/User/Downloads/CampusSmartParking_Assignment2/Task03_HashSet_Student/data/Q3_benchmark_results.csv)

| Suite | Scenario | Input | Set Size | Duplicates Blocked? | Notes |
|---|---|---|---|---|---|
| A — BrokenStudent | Same reference reused | 2 | 1 | ✅ yes | Accidental correctness (same identity hash) |
| B — BrokenStudent | Distinct objects, same regNo | 5 | **5** | ❌ **NO — BUG** | Identity hashes differ; duplicates leak |
| C — FixedStudent | Distinct objects, same regNo | 6 | **4** | ✅ yes | `Objects.hash(regNo)` ensures consistent bucket |
| D — Mutable Key | regNo mutated post-insert | 1 | 1 | ⚠ Object Lost | `contains()` returns false; object trapped in old bucket |

**Scale Benchmark (FixedStudent, 1 Warm-up + 5 Measured Runs):**

| Attendance Scale | Add (ms) | Contains-Hit (ms) | Contains-Miss (ms) | Remove (ms) |
|---|---|---|---|---|
| **1,000 Students** | 0.9929 | 0.4209 | 0.4108 | 0.4529 |
| **10,000 Students** | 12.1133 | 1.7674 | 3.7801 | 6.5940 |
| **100,000 Students** | 33.2265 | 19.8672 | 24.9835 | 9.5958 |

- **Key Takeaways:** Overriding `equals` without `hashCode` is a critical silent defect — duplicates bypass detection because unequal hashes route identical records to separate buckets. `Objects.hash(regNo)` fully enforces the contract. Mutable keys corrupt internal bucket indexing. `HashSet` maintains near-$O(1)$ scaling across all tested operations.

---

## 5. Task Implementation Status

| Task ID | Assessed Area | Bloom's Level | Marks | Status |
|---|---|---|---|---|
| **Q1** | String, StringBuilder & StringBuffer (Railway E-Ticket) | K4 – Analyse | 6 | **COMPLETED** |
| **Q2** | ArrayList, LinkedList & ArrayDeque (Music Playlist Editor) | K4 – Analyse | 4 | **COMPLETED** |
| **Q3** | HashSet & the hashCode–equals Contract (Event Attendance) | K4 – Analyse | 6 | **COMPLETED** |
| **Q4** | HashMap, TreeMap & LinkedHashMap (Pharmacy Inventory) | K4 – Analyse | 6 | *PENDING* |
| **Q5** | Generics, Comparable & Comparator (Sports Leaderboard) | K4 – Analyse | 7 | *PENDING* |
| **Q6** | Queue & PriorityQueue (Hospital Emergency Triage) | K5 – Evaluate | 8 | *PENDING* |
| **Q7** | Day-4 Design Challenge: Diagnose, Redesign & Validate | K5 – Evaluate | 13 | *PENDING* |
| **Total** | | | **50** | |
