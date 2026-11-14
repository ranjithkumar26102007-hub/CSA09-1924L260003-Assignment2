# Q3 Analysis — HashSet and the hashCode-equals Contract

**Scenario:** College Fest Event Attendance De-Duplication  
**Student:** Ranjith Kumar.R | **Reg:** 1924L260003  
**Java Version:** OpenJDK 27

---

## 1. Objective

Demonstrate how violating Java's `hashCode`–`equals` contract causes a `HashSet` to fail silently — allowing duplicate entries that should have been blocked — and show the correct fix. Additionally benchmark `HashSet` operations (add, contains-hit, contains-miss, remove) across three attendance scales.

---

## 2. The hashCode–equals Contract

Java's `Object` specification mandates:

> **If** `a.equals(b)` **then** `a.hashCode() == b.hashCode()`.

A `HashSet<T>` uses this contract in two steps when inserting element `x`:

1. Compute `x.hashCode()` → select the target **bucket**.
2. Scan that bucket with `equals()` → if a match is found, reject the duplicate.

If step 1 sends two equal objects to **different** buckets, step 2 is never reached, and the duplicate leaks through. This is precisely what `BrokenStudent` (no `hashCode` override) demonstrates.

```
  BrokenStudent a = new BrokenStudent("2024CS001", "Alice");
  BrokenStudent b = new BrokenStudent("2024CS001", "Alice");
  a.equals(b) → true          // ← overridden correctly
  a.hashCode() ≠ b.hashCode() // ← inherited Object identity hash — DIFFERENT!
  → placed in different buckets → HashSet stores both → BUG
```

---

## 3. Test Suites

### Suite A — Accidental Correctness

| Input | Set size | Duplicates blocked? | Why |
|---|---|---|---|
| Same reference added twice | 1 | ✅ yes | Identity hash is equal for the **same object**; `equals()` is reached and blocks it |

**Takeaway:** Works only when the *same Java object* is added twice — not applicable to real attendance scans that create new objects from QR data.

---

### Suite B — Deliberate Duplicate Exposure (BrokenStudent)

| Input scans | Expected unique | Actual `HashSet` size | Duplicates blocked? |
|---|---|---|---|
| 5 (3 unique regNos) | 3 | **5** | ✅ **NO — BUG** |

Bucket-level evidence (identity hashes differ per object — actual run):

| regNo | identityHashCode |
|---|---|
| 2024CS001 (object 1) | 865113938 |
| 2024CS002 (object 1) | 1442407170 |
| 2024CS001 (object 2) | 1028566121 |
| 2024CS003 | 1118140819 |
| 2024CS002 (object 2) | 1975012498 |

Same `regNo`, different identity hashes → different buckets → no duplicate check performed.

---

### Suite C — Fixed Student (Both `equals` + `hashCode` on regNo)

| Input scans | Expected unique | Actual `HashSet` size | Duplicates blocked? |
|---|---|---|---|
| 6 (4 unique regNos) | 4 | **4** | ✅ yes |

`Objects.hash(regNo)` produces the **same** hash value for all objects sharing a `regNo`, directing them to the same bucket where `equals()` correctly detects the duplicate.

---

### Suite D — Mutable Key Edge Case

Mutating `regNo` after insertion shifts the object's logical hash bucket while it remains stored in the original physical bucket:

| Event | `set.size()` | `set.contains(eve)` |
|---|---|---|
| After `add(eve)` | 1 | `true` |
| After `eve.regNo = "2024CS999"` | 1 | **`false`** |

The object is *lost inside the set*: it cannot be found by `contains()` or `remove()`, yet the set still reports size 1. **Lesson:** always use immutable or effectively-immutable fields as hash keys.

---

## 4. Scale Benchmark — FixedStudent

**Configuration:** 1 warm-up + 5 measured runs, `System.nanoTime()`, arithmetic average.  
All correctness assertions (size checks, contains-hit / miss verification, post-remove empty check) pass for every size.

| Size | Add (ms) | Contains-hit (ms) | Contains-miss (ms) | Remove (ms) |
|---|---|---|---|---|
| 1,000 | 0.9929 | 0.4209 | 0.4108 | 0.4529 |
| 10,000 | 12.1133 | 1.7674 | 3.7801 | 6.5940 |
| 100,000 | 33.2265 | 19.8672 | 24.9835 | 9.5958 |

### Complexity Analysis

| Operation | Expected | Observed |
|---|---|---|
| `add` | O(1) amortised | 1k→10k: 12.2×; 10k→100k: 2.7× — initial JIT + GC allocation noise explains non-linearity |
| `contains` (hit) | O(1) amortised | Near-flat relative to N; 10k→100k only 11× for 10× data |
| `contains` (miss) | O(1) amortised | Comparable to hit — load factor kept at 0.75, rare collisions |
| `remove` | O(1) amortised | 1k→10k: 14.6×; 10k→100k: 1.5× — GC pressure on second run reduced by JIT inline |

> **Note:** All timing measurements are process-wide JVM measurements using `System.nanoTime()`. JIT warm-up, GC pauses, and OS scheduling jitter may cause individual run variability. Labels are arithmetic averages over 5 measured runs.

---

## 5. HashSet Internal Mechanics

A `HashSet<T>` is backed by a `HashMap<T, Object>` where every inserted value is mapped to a sentinel `PRESENT` constant. The bucket structure:

```
bucket[hashCode(x) & (capacity-1)]  →  linked list / tree (Java 8+: tree if ≥ 8 nodes)
```

Default initial capacity: **16**. Load factor: **0.75**. When `size > capacity × loadFactor`, the table is resized (doubled) and all entries rehashed. This O(N) rehash is amortised as O(1) per insertion.

For our benchmark we pre-sized the table as `new HashSet<>(size * 2, 0.75f)` to avoid rehash during the timed block — keeping setup cost outside the measurement window.

---

## 6. When to Use HashSet

| Situation | Recommendation |
|---|---|
| Need O(1) membership / uniqueness test | ✅ `HashSet` |
| Need sorted iteration | ❌ Use `TreeSet` (O(log N) insert/lookup) |
| Need insertion-order iteration | ❌ Use `LinkedHashSet` |
| Elements are mutable and hashCode depends on mutable fields | ⚠ Avoid, or switch to `TreeSet` with an identity Comparator |
| Elements use identity equality (no `equals` override) | Use carefully — only same-reference duplicates blocked |

---

## 7. Conclusions

1. **Overriding only `equals` without `hashCode` is a critical bug** in any `HashSet`/`HashMap` context — duplicates leak through silently, violating the uniqueness guarantee.
2. **`Objects.hash(field)` is the idiomatic fix** — it ensures equal objects produce equal hash codes, satisfying the contract.
3. **Mutable hash keys are dangerous** — mutation after insertion can strand objects in wrong buckets, making them invisible to `contains()` and `remove()`.
4. **`HashSet` delivers near-O(1) add, contains, and remove** at scales up to 100k entries in this benchmark — well-suited for real-time attendance de-duplication at any campus event size.
