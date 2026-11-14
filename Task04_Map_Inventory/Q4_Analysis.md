# Q4 – Choosing a Map for a Pharmacy Inventory

## 1. Objective and scenario

A hospital pharmacy identifies each medicine by a unique medicine code. The inventory must support frequent code-based stock lookups and two reports: medicines in ascending code order and medicines in the order in which they were added. This experiment compares `HashMap`, `TreeMap`, and `LinkedHashMap` at 1,000, 10,000, and 100,000 medicines.

## 2. Experimental method

- Lookup workload: 100,000 lookups for each map and inventory size.
- Repetitions: one warm-up followed by five timed repetitions, using `System.nanoTime()`.
- Report workloads: generation of a code-order report and an insertion-order report; each has one warm-up and five measured runs.
- Correctness checks: both report orders are compared with their expected key sequences.
- The CSV file `data/Q4_benchmark_results.csv` contains the raw warm-up and five-run measurements, calculated averages, order-validation flags, and lookup checksum.
- The implementation defines an acceptable average response time of less than 1.00 ms per individual lookup.

## 3. Measured lookup results

The “100,000 lookups average” column is the elapsed time for the entire batch of 100,000 lookups. The per-lookup column is the average time for one lookup, expressed in microseconds.

| Medicines | Map | 100,000 lookups (ms) | Average per lookup (µs) | Criterion |
|---:|---|---:|---:|---|
| 1,000 | HashMap | 9.7413 | 0.0974 | Within |
| 1,000 | TreeMap | 24.6952 | 0.2470 | Within |
| 1,000 | LinkedHashMap | 7.9852 | 0.0799 | Within |
| 10,000 | HashMap | 20.7277 | 0.2073 | Within |
| 10,000 | TreeMap | 58.1725 | 0.5817 | Within |
| 10,000 | LinkedHashMap | 17.3889 | 0.1739 | Within |
| 100,000 | HashMap | 24.3986 | 0.2440 | Within |
| 100,000 | TreeMap | 96.9179 | 0.9692 | Within |
| 100,000 | LinkedHashMap | 40.8643 | 0.4086 | Within |

All measured per-lookup averages are below the implementation's 1.00 ms acceptance threshold. In these runs, `LinkedHashMap` had the lowest lookup batch time at all three sizes, followed by `HashMap`; `TreeMap` was slower for lookup but remained within the stated threshold.

## 4. Measured report-generation results

The following values are five-run averages in milliseconds.

| Medicines | Map | Code-order report (ms) | Insertion-order report (ms) |
|---:|---|---:|---:|
| 1,000 | HashMap | 3.3632 | 0.6824 |
| 1,000 | TreeMap | 0.5905 | 0.4786 |
| 1,000 | LinkedHashMap | 0.3906 | 0.1693 |
| 10,000 | HashMap | 19.5695 | 6.4420 |
| 10,000 | TreeMap | 1.6356 | 7.2877 |
| 10,000 | LinkedHashMap | 3.7701 | 4.0044 |
| 100,000 | HashMap | 63.5816 | 11.0196 |
| 100,000 | TreeMap | 17.5121 | 31.4193 |
| 100,000 | LinkedHashMap | 24.8917 | 13.1350 |

The report timings reflect the work required by this implementation, including sorting a copied entry list when a map does not natively provide code order and consulting an auxiliary insertion-order list where necessary.

## 5. Ordering validation and implementation detail

All nine map/size combinations reported both `Code_Order_Matches = true` and `Insertion_Order_Matches = true`.

This does **not** mean every map intrinsically preserves both orders:

- `TreeMap` naturally iterates by sorted medicine code. The implementation uses a separate list of keys recorded during insertion to generate the insertion-order report.
- `LinkedHashMap` naturally iterates in insertion order. The implementation copies and sorts its entries by key to generate the code-order report.
- `HashMap` guarantees neither sorted-key order nor insertion order. The implementation copies and sorts entries for the code-order report, and uses the separate insertion-order key list for the insertion-order report.

Therefore, the `true` validation flags confirm that the report-building logic produced the required sequences; they do not claim that a plain `HashMap` preserves insertion order.

## 6. Complexity comparison

| Structure | Lookup by code | Add/update | Code-order report | Insertion-order report | Ordering behavior |
|---|---|---|---|---|---|
| `HashMap` | O(1) average; O(n) worst case | O(1) average | O(n log n) when entries are copied and sorted | O(n) with a separate insertion-order list | No iteration-order guarantee |
| `TreeMap` | O(log n) | O(log n) | O(n) iteration | O(n) with a separate insertion-order list | Sorted by key |
| `LinkedHashMap` | O(1) average; O(n) worst case | O(1) average | O(n log n) when entries are copied and sorted | O(n) iteration | Insertion order |
| Sorted array + `Arrays.binarySearch` | O(log n) | O(n) to insert while maintaining sorted order | O(n) iteration | Not naturally retained; extra position/order data is needed | Sorted by code |

All three map choices and the sorted-array alternative use O(n) storage for n medicines. `LinkedHashMap` typically has extra per-entry links to maintain order. A separate insertion-order list also requires O(n) additional references for the `HashMap` and `TreeMap` approach used in this experiment.

## 7. Acceptability criterion and deployment recommendation

The implementation sets an acceptable average response-time threshold of less than 1.00 ms per individual lookup. Every measured map/size combination met this criterion. For a lookup-heavy pharmacy inventory where both sorted-code and insertion-order reports are required, `LinkedHashMap` is a reasonable default if insertion order is the primary native iteration requirement: it provides average constant-time lookup and naturally produces the insertion-order report. A sorted code report still requires sorting the entries, which accounts for additional report-generation cost.

If code-order reporting is the dominant requirement and naturally sorted traversal is more important than insertion order, `TreeMap` is a strong alternative: it maintains sorted keys and produces a code-order report with linear traversal, at the cost of O(log n) lookups and updates. If lookup performance is the main priority and neither iteration order is required from the map itself, `HashMap` is suitable, but the application must explicitly maintain and sort auxiliary data for the two reports.

The 100,000-medicine measurements show that the remaining cost is not only lookup. Building a complete report requires visiting every medicine (O(n)); sorting an unsorted map's entries adds O(n log n) work; and the insertion-order side list adds storage and traversal work. The benchmark's measured timings are specific to this machine, JVM, data, and run, and should not be treated as universal performance guarantees.

## 8. Why `Hashtable` is not preferred in new code

`Hashtable` is a legacy synchronized map. Its built-in synchronization is often unnecessary for ordinary single-threaded inventory access and does not provide sorted or insertion-order iteration. For typical modern code, use `HashMap` for unsynchronized general-purpose lookup, `LinkedHashMap` when insertion order matters, or `TreeMap` when sorted keys are required. If concurrent access is needed, choose a concurrency-specific design such as `ConcurrentHashMap` rather than selecting `Hashtable` by default.

## 9. Conclusion

The benchmark confirms that all three map implementations met the defined lookup threshold for the tested inventory sizes, while their reporting costs and iteration semantics differed. `LinkedHashMap` is a balanced choice when insertion-order output is required alongside frequent lookups; `TreeMap` is preferable when sorted-key traversal is central; and `HashMap` is appropriate when lookup is dominant and ordering is managed separately. The correct choice should follow the required ordering behavior as well as measured workload, not lookup timing alone.
