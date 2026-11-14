# Q7 – Day-4 Design Challenge: Diagnose, Redesign and Validate

**Course:** Programming in Java (CO2 — Sessions 15–26)  
**Student:** Ranjith Kumar.R  
**Register Number:** 1924L260003  
**Repository Name:** `CSA09-1924L260003-Assignment2`  
**Task:** Task 07 — Day-4 Design Challenge (13 Marks)  
**Evaluation Model:** Diagnose $\rightarrow$ Design Objective $\rightarrow$ Redesign $\rightarrow$ Validate $\rightarrow$ Defend  

---

## Part A — Diagnose the Bottleneck (2 Marks)

### 1. Empirical Evidence from Q4 Benchmark
In Question 4 (`Task04_Map_Inventory`), we evaluated three standard Java `Map` implementations (`HashMap`, `TreeMap`, and `LinkedHashMap`) for managing a hospital pharmacy inventory across 1,000, 10,000, and 100,000 medicine records. 

While the baseline `HashMap` provided rapid $O(1)$ key lookups (24.3986 ms for 100,000 lookups at 100k scale, or 0.244 µs/lookup), its performance severely deteriorated when fulfilling real-world pharmacy reporting requirements:

| Inventory Size | 100,000 Lookups (ms) | Code-Order Report (ms) | Insertion-Order Report (ms) |
|---:|---:|---:|---:|
| **1,000 Medicines** | 9.7413 | 3.3632 | 0.6824 |
| **10,000 Medicines** | 20.7277 | 19.5695 | 6.4420 |
| **100,000 Medicines** | **24.3986** | **63.5816** | **11.0196** |

### 2. Root Cause Analysis of the Bottleneck

1. **Repeated Sorting Overhead in Code-Order Reporting:**
   - A standard `HashMap` offers zero guarantees regarding bucket iteration order.
   - To print medicines in ascending medicine code order (`MED-000001`, `MED-000002`, ...), the application was forced to:
     ```java
     List<Map.Entry<String, Medicine>> entries = new ArrayList<>(map.entrySet());
     entries.sort(Map.Entry.comparingByKey());
     ```
   - This incurs an **$O(n \log n)$ TimSort cost** on every single report request. At 100,000 medicines, this sorting took **63.58 ms** per invocation. If the pharmacy generates stock audit reports every 5 minutes across multiple ward consoles, this creates continuous CPU spikes.

2. **Temporary Object Churn and Garbage Collection Pressure:**
   - Populating `new ArrayList<>(map.entrySet())` allocates a temporary `ArrayList` header, an internal `Object[]` of length 100,000, plus TimSort temporary working arrays ($O(n/2)$ space).
   - At 100,000 medicines, each report generation allocates over **1.6 MB** of ephemeral objects that immediately become garbage, accelerating young-generation GC cycles and triggering latency pauses in clinical workflows.

3. **Auxiliary-List Search and Random Access Overhead:**
   - Because `HashMap` cannot track entry order, an auxiliary `List<String> insertionOrder` was maintained.
   - Generating an insertion-order report required iterating the auxiliary list and performing $n$ random hash lookups:
     ```java
     for (String key : insertionOrder) {
         Medicine med = map.get(key);
         appendMedicine(med, report);
     }
     ```
   - At 100,000 medicines, this required **100,000 random hash bucket lookups** and pointer chasing, taking **11.02 ms**.
   - Worse, if medicines are removed from the inventory, calling `insertionOrder.remove(code)` triggers an **$O(n)$ linear scan** over the `ArrayList`, making record deletions catastrophic ($O(n)$ instead of $O(1)$).

---

## Part B — Design Objective (2 Marks)

The goal of this redesign is to eliminate repeated computational overhead and auxiliary pointer chasing without compromising query latency or data integrity.

### Measurable Engineering Objectives:
1. **Eliminate Sorting Churn ($O(n \log n) \rightarrow O(n)$):**
   Produce code-order reports directly in $O(n)$ time via natural in-order iteration, eliminating TimSort sorting overhead and zeroing temporary collection allocations.
2. **Eliminate Random Map Lookups in Insertion Reporting:**
   Produce insertion-order reports via natural sequential iteration in $O(n)$ time without performing secondary `map.get()` lookups.
3. **Preserve Ultra-Fast $O(1)$ Lookup Latency:**
   Ensure random stock lookups remain within the acceptable SLA criterion (< 1.00 ms per lookup; empirically < 0.25 µs/lookup).
4. **Enforce Absolute Index Consistency:**
   Ensure that state-mutating operations (`add`, `update`, `remove`) maintain identical, synchronized data across both index structures, guarded by Java runtime assertions.
5. **Quantify Memory vs. Speed Trade-offs:**
   Accept an increase in heap memory (secondary pointer storage) in exchange for dramatic reductions in report latency and CPU cycles.

---

## Part C — Architectural Redesign (4 Marks)

### 1. Dual-Index Architecture
Rather than relying on a single map coupled with an auxiliary linear list, we engineer a **Synchronized Dual-Index Pharmacy Inventory**:

```text
                           ┌───────────────────────────────┐
                           │      Client API Caller        │
                           └───────────────┬───────────────┘
                                           │
                        ┌──────────────────┴──────────────────┐
                        ▼                                     ▼
        ┌──────────────────────────────┐      ┌──────────────────────────────┐
        │  TreeMap<String, Medicine>   │      │ LinkedHashMap<String, Med>   │
        │        (Code Index)          │      │      (Insertion Index)       │
        ├──────────────────────────────┤      ├──────────────────────────────┤
        │ • Red-Black Tree Structure   │      │ • Hash Table + Doubly-Linked │
        │ • Natural Alphabetical Order │      │ • Strict FIFO Arrival Order  │
        │ • Code Report: O(n) in-order │      │ • Ins Report: O(n) link walk │
        │ • Zero Sorting Required      │      │ • Lookups: O(1) Hash Access  │
        └──────────────┬───────────────┘      └──────────────┬───────────────┘
                       │                                     │
                       └──────────────────┬──────────────────┘
                                          ▼
                         ┌─────────────────────────────────┐
                         │ Shared Medicine Record Entities │
                         │ (MED-000001, "Paracetamol", 100)│
                         └─────────────────────────────────┘
```

### 2. Key Structural Principles
1. **Index 1 (`codeIndex`):** An instance of `java.util.TreeMap<String, Medicine>` ordered by key ascending. Traversal via `codeIndex.values()` iterates nodes in-order ($O(n)$), completely bypassing TimSort.
2. **Index 2 (`insertionIndex`):** An instance of `java.util.LinkedHashMap<String, Medicine>`. Iteration via `insertionIndex.values()` follows the internal doubly-linked bucket pointers ($O(n)$), completely bypassing auxiliary key lookups. Lookups (`lookup(code)`) are routed to this map for average $O(1)$ latency.
3. **Shared Object Graph (Zero Data Duplication):** Both indexes store references to the **exact same immutable `Medicine` record instances** in the heap. Only the index node wrappers (`TreeMap.Entry` and `LinkedHashMap.Entry`) are duplicated, not the payload data.
4. **Consistency Verification:** The inventory implements `verifyConsistency()` which validates that both maps have identical size and identical key-value mappings. Mutating methods are guarded by assertions:
   ```java
   assert codeIndex.size() == insertionIndex.size() : "Index size mismatch";
   ```

### 3. Implementation of Operations

```java
public boolean add(Medicine medicine) {
    if (codeIndex.containsKey(medicine.code())) {
        return false; // Duplicate code rejected
    }
    codeIndex.put(medicine.code(), medicine);
    insertionIndex.put(medicine.code(), medicine);
    assert codeIndex.size() == insertionIndex.size();
    return true;
}

public boolean update(Medicine medicine) {
    if (!codeIndex.containsKey(medicine.code())) {
        return false;
    }
    codeIndex.put(medicine.code(), medicine);
    // Updating existing key in LinkedHashMap preserves its original insertion position!
    insertionIndex.put(medicine.code(), medicine);
    assert codeIndex.size() == insertionIndex.size();
    return true;
}

public Medicine remove(String code) {
    Medicine removedTree = codeIndex.remove(code);
    Medicine removedLinked = insertionIndex.remove(code);
    assert Objects.equals(removedTree, removedLinked);
    assert codeIndex.size() == insertionIndex.size();
    return removedTree;
}

public String generateCodeOrderReport() {
    StringBuilder report = new StringBuilder(codeIndex.size() * 32);
    for (Medicine med : codeIndex.values()) {
        appendMedicine(med, report);
    }
    return report.toString(); // O(n) direct traversal, zero temporary collections!
}

public String generateInsertionOrderReport() {
    StringBuilder report = new StringBuilder(insertionIndex.size() * 32);
    for (Medicine med : insertionIndex.values()) {
        appendMedicine(med, report);
    }
    return report.toString(); // O(n) direct linked traversal, zero map lookups!
}
```

---

## Part D — Validation and Comparison Results

### 1. Experimental Methodology
- **Hardware & JVM:** OpenJDK 27 64-Bit Server VM on Windows x64.
- **Dataset:** Deterministic medicine keys (`MED-000001` to `MED-100000`) across 1,000, 10,000, and 100,000 records.
- **Timing:** `System.nanoTime()` with **1 warm-up run** followed by **5 timed repetitions**.
- **Workloads:**
  - 100,000 random stock lookups.
  - Complete code-order report generation.
  - Complete insertion-order report generation.
  - Batch mutations (1,000 updates, 1,000 removals, 1,000 re-adds).
  - Explicit counting of temporary collections and retained heap estimation.

### 2. Empirical Benchmark Measurements (Averages over 5 Runs)

The following measurements were exported directly to `data/Q7_redesign_results.csv`:

| Scale | Architecture | 100k Lookups (ms) | Code-Order Report (ms) | Insertion-Order Report (ms) | Batch Mutations (ms) | Retained Memory (KB) | Temp Collections in Code Report |
|---:|---|---:|---:|---:|---:|---:|:---:|
| **1,000** | Original HashMap | 9.4981 | 2.8616 | 0.4099 | 0.9266 | 49.5 KB | 1 |
| **1,000** | **Dual-Index** | **13.5747** | **0.4765** | **0.3426** | **3.2777** | **96.4 KB** | **0** |
| | *Speedup / Ratio* | *0.70×* | **6.01× FASTER** | **1.20× FASTER** | *0.28×* | *1.95× RAM* | **100% Eliminated** |
| | | | | | | | |
| **10,000** | Original HashMap | 7.7099 | 13.3499 | 8.1134 | 2.7997 | 494.8 KB | 1 |
| **10,000** | **Dual-Index** | **6.8322** | **2.0445** | **0.9680** | **4.2610** | **963.6 KB** | **0** |
| | *Speedup / Ratio* | *1.13×* | **6.53× FASTER** | **8.38× FASTER** | *0.66×* | *1.95× RAM* | **100% Eliminated** |
| | | | | | | | |
| **100,000**| Original HashMap | 21.4521 | 44.9624 | 21.2143 | 15.9328 | 4,947.9 KB | 1 |
| **100,000**| **Dual-Index** | **19.5883** | **8.8488** | **8.9229** | **6.8322** | **9,635.4 KB** | **0** |
| | *Speedup / Ratio* | **1.10× FASTER** | **5.08× FASTER** | **2.38× FASTER** | **2.33× FASTER** | *1.95× RAM* | **100% Eliminated** |

---

## Part E — Defend Final Design (2 Marks)

### 1. Were the Theoretical Predictions Supported?
**Yes, unequivocally:**
- **Code-Order Reporting:** Reduced from **44.96 ms** to **8.85 ms** at 100,000 medicines—a **5.08× speedup (80.3% reduction in execution time)**. Traversing the red-black tree directly completely eliminated the $O(n \log n)$ TimSort phase.
- **Insertion-Order Reporting:** Reduced from **21.21 ms** to **8.92 ms** at 100,000 medicines—a **2.38× speedup (57.9% reduction in execution time)**. Direct traversal of the doubly-linked bucket nodes eliminated 100,000 hash-code recalculations and bucket dereferences.
- **Temporary Collections:** Reduced from 1 collection (+ internal Object arrays) to **0 temporary collections**—a **100% elimination of report-time memory allocations**.
- **Mutations at Scale:** At 100,000 scale, batch mutations in Dual-Index took **6.83 ms** compared to **15.93 ms** in Original HashMap (**2.33× faster**). In the original architecture, `ArrayList.remove(code)` suffers from $O(n)$ linear searching across 100,000 elements for each deletion. In contrast, `LinkedHashMap.remove(code)` unlinks the node in $O(1)$ time and `TreeMap.remove(code)` unlinks in $O(\log n)$ time.

### 2. Memory Cost Analysis
- The dual-index structure requires maintaining two node wrappers per entry:
  - `TreeMap.Entry`: `parent`, `left`, `right`, `color`, `key`, `value` ($\approx 40$ bytes).
  - `LinkedHashMap.Entry`: `before`, `after`, `next`, `hash`, `key`, `value` ($\approx 48$ bytes).
- At 100,000 medicines, heap storage increased from **~4.83 MB** (Original) to **~9.41 MB** (Dual-Index)—an overhead factor of **1.95×**.
- In modern enterprise server environments with gigabytes of RAM, dedicating an extra 4.5 MB of heap memory to eliminate repetitive 45 ms CPU sorting spikes is an outstanding engineering trade-off.

### 3. Synchronization and Consistency Risks
- **Dual-Write Risk:** Because two separate collections are updated during mutations, a crash, exception, or thread race between updating `codeIndex` and `insertionIndex` could result in an inconsistent state.
- **Mitigation:**
  - In single-threaded execution, state consistency is guarded by assertions (`assert codeIndex.size() == insertionIndex.size()`).
  - In a multi-threaded clinical pharmacy environment, mutations must be wrapped in atomic critical sections using a `ReadWriteLock` (`ReentrantReadWriteLock`) or coordinated via an encapsulated concurrency facade.

### 4. When is the Original HashMap Approach Still Preferable?
The single `HashMap` design remains preferable when:
1. **Strictly Write-Heavy Workloads with No Reporting:** Systems that ingest continuous high-volume IoT sensor streams and never generate ordered reports.
2. **Severe Memory-Constrained Embedded Runtimes:** Microcontrollers or resource-starved edge devices where memory footprint is strictly capped under a few megabytes.
3. **Lookups Only:** Read-only caches where keys are queried by exact match and sorting is never requested.

### 5. Final Engineering Recommendation
For a **hospital pharmacy inventory system**, stock reports are frequently audited by pharmacists, ward nurses, and inventory controllers. The **Dual-Index Architecture (TreeMap + LinkedHashMap)** is the strongly recommended design:
- It maintains sub-millisecond $O(1)$ lookup compliance (< 0.20 µs/lookup).
- It provides **5× faster code-order reports** and **2.4× faster insertion-order reports**.
- It eliminates report-generation garbage collection pauses.
- It outperforms the naive `ArrayList` approach on item deletions at scale.
- The 1.95× memory footprint overhead (~4.5 MB for 100k items) is negligible in modern server deployments.
