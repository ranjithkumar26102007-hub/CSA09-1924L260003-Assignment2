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
│   └───src/
│           Q1TicketBenchmark.java
│           
├───Task02_CollectionsComparison/
│   ├───data/
│   ├───screenshots/
│   └───src/
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

## 3. Compilation and Execution Guide for Q1

All tasks are configured to compile into dedicated `out/` directories to maintain clean separation between source and bytecode.

### Navigate to Project Root
```powershell
Set-Location -Path "$env:USERPROFILE\Downloads\CampusSmartParking_Assignment2"
```

### Compile Q1 (Task 01)
```powershell
javac -Xlint:all -d Task01_StringBenchmark/out Task01_StringBenchmark/src/Q1TicketBenchmark.java
```

### Run Q1 Benchmark
```powershell
java -cp Task01_StringBenchmark/out Q1TicketBenchmark
```

---

## 4. Q1 Benchmark Summary: Railway E-Ticket Generation

Task 01 investigates text assembly performance for a high-volume railway e-ticket system across three implementations:
1. `String (+)`: Naive concatenation inside loops.
2. `StringBuilder`: Unsynchronized, mutable buffer.
3. `StringBuffer`: Synchronized, thread-safe mutable buffer.

### Experimental Evidence Summary (from `Task01_StringBenchmark/data/benchmark_results.csv`)

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

*\*Note: Process-wide heap memory delta via `Runtime` is an approximate engineering indicator subject to JVM garbage collection cycles and background daemon threads.*

### Key Takeaways
- **Algorithmic Complexity:** Repeated `+` concatenation copies accumulated characters on each iteration, exhibiting quadratic $O(n^2)$ behavior. `StringBuilder` uses an internal expandable buffer with amortized $O(1)$ appends, delivering linear $O(n)$ performance (**~25× faster** on deep 100-field chains).
- **Object Churn Elimination:** In Workload B, `String (+)` generated **490,000 temporary intermediate String objects**, creating severe young-gen heap turnover. `StringBuilder` created **0 intermediate String objects**.
- **Multithreaded Shared Logging:** Demonstrated a multi-threaded architecture with 10 worker threads producing 1,000 tickets. Each thread assembled records locally with thread-confined `StringBuilder`, writing complete atomic entries to a shared synchronized `StringBuffer`. Exactly **1,000 uncorrupted records** were written in 17 ms.

---

## 5. Task Implementation Status

| Task ID | Assessed Area | Bloom's Level | Marks | Status |
|---|---|---|---|---|
| **Q1** | String, StringBuilder & StringBuffer (Railway E-Ticket) | K4 – Analyse | 6 | **COMPLETED** |
| **Q2** | ArrayList, LinkedList & ArrayDeque (Music Playlist Editor) | K4 – Analyse | 4 | *PENDING* |
| **Q3** | HashSet & the hashCode–equals Contract (Event Attendance) | K4 – Analyse | 6 | *PENDING* |
| **Q4** | HashMap, TreeMap & LinkedHashMap (Pharmacy Inventory) | K4 – Analyse | 6 | *PENDING* |
| **Q5** | Generics, Comparable & Comparator (Sports Leaderboard) | K4 – Analyse | 7 | *PENDING* |
| **Q6** | Queue & PriorityQueue (Hospital Emergency Triage) | K5 – Evaluate | 8 | *PENDING* |
| **Q7** | Day-4 Design Challenge: Diagnose, Redesign & Validate | K5 – Evaluate | 13 | *PENDING* |
| **Total** | | | **50** | |
