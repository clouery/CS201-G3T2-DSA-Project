# Time-Dependent Public Transit TSP — Planning Notes

> Context: CS201 project, *"Beyond the Classic TSP: Redesigning Algorithms for Real-World Assumptions"*.
> Proposed direction: a bus/train system over cleaned GeoNames Malaysian cities, with schedules, where edge costs depend on time.

## Contents

1. [The core clarification: waiting vs travelling vs arrival](#1-the-core-clarification-waiting-vs-travelling-vs-arrival)
2. [What this means for the comparison](#2-what-this-means-for-the-comparison)
3. [Assignment framing: this is a TSP redesign](#3-assignment-framing-this-is-a-tsp-redesign)
4. [Data and network plan](#4-data-and-network-plan)
5. [Algorithms and data structures](#5-algorithms-and-data-structures)
6. [Pitfalls and things easy to miss](#6-pitfalls-and-things-easy-to-miss)
7. [Suggested milestones](#7-suggested-milestones)
8. [Bottom line](#8-bottom-line)
9. [References](#9-references)

---

## 1. The core clarification: waiting vs travelling vs arrival

For any journey starting at time `t0`:

```
arrival = t0 + W + R
W = total waiting time
R = total in-vehicle riding time
```

So:

| Objective | Minimizes | Ignores | Same as min arrival? |
|---|---|---|---|
| "Shortest waiting" | `W` | how slow the vehicle is | **No** |
| "Shortest travelling" | `R` | how long you wait for it | **No** |
| "Earliest arrival" | `W + R` | — | **This is the real one** |

### Counterexample

Standing at a stop at 08:00:

| Option | Departs | Arrives | Wait | Ride |
|---|---:|---:|---:|---:|
| Local | 08:01 | 09:40 | 1 min | 99 min |
| Express | 08:10 | 08:40 | 10 min | 30 min |

- "Take the next bus" → local → arrive **09:40**
- "Shortest total riding time" → express → arrive **08:40**
- "Shortest final arrival" → express → **08:40**

Reverse the numbers and they disagree the other way: if the local departs 08:01 and arrives 08:35, while the express departs 08:10 and arrives 08:33, then min-wait picks the local (arrives 08:35) and min-ride picks the express (arrives 08:33).

So **comparing both by final arrival time does not make them the same algorithm** — it just gives them a common yardstick. The gap between each heuristic and the true optimum is exactly what the experiments should measure.

### Two important sub-points

1. **"Take the next train that comes earliest" is a greedy local rule, not Dijkstra.** It can board a slow local when a later express overtakes it and arrives sooner. This is the classic express/local overtaking problem.
2. **"Shortest waiting" is ambiguous.** It can mean:
   - (a) at each stop, board the vehicle that departs soonest — a greedy traversal; or
   - (b) globally minimize total waiting across the journey — a time-dependent Dijkstra with wait as the edge cost.

   These are different, and neither equals min-arrival.

### The correct baseline

The only objective that directly minimizes final arrival is a **time-dependent Dijkstra** where the label is absolute arrival time and the edge cost is:

```
wait until the next departure >= current time + transfer buffer
+ ride time of that specific trip
```

If you want two user-facing modes, frame them honestly as **heuristics** ("least waiting", "least riding") and compare both against the **min-arrival baseline**. That is a legitimate and interesting experiment. What you cannot claim is that either heuristic is optimal for arrival.

Special cases where the objectives coincide:

- If all competing services have identical ride times, min-wait = min-arrival.
- If all waits are identical, min-ride = min-arrival.

In a general timetable, they do not coincide.

---

## 2. What this means for the comparison

Three separate objectives should be implemented and reported:

| Objective | Edge cost | Role |
|---|---|---|
| Min arrival | wait + ride | ground truth / optimal baseline |
| Min wait | wait | heuristic / user mode |
| Min ride | ride | heuristic / user mode |

Report, for each query:

- final arrival time,
- total waiting `W`,
- total riding `R`,
- number of transfers,
- number of legs,
- runtime and memory.

Then the headline result is the **optimality gap**: how much later do the two heuristics arrive than the optimal baseline, and on which network shapes do they fail badly. A Pareto view of arrival vs transfers is a strong addition.

---

## 3. Assignment framing: this is a TSP redesign

The repository is currently **greenfield**:

- no source code, no build system, no tests, no entry point;
- only `README.md` (placeholder), `Project_2627T1.pdf`, and the GeoNames bundle in `CS201_2026_project/`;
- `MY.txt` has 71,709 rows and 19 tab-separated columns, no header.

The spec requires:

- a **new TSP variant** (new input, output, constraints);
- at least **two meaningfully different approaches** — different data structures or algorithms, not parameter tweaks;
- experiments, complexity analysis, and trade-off discussion;
- GeoNames `MY.txt` as the **primary data source**, with any augmentation documented.

A pure point-to-point bus router does **not** satisfy that. The clean framing:

> **Time-Dependent Public Transit TSP:** given a set of cities to visit, a start city, and a departure time, choose the visiting order and the specific scheduled connections so that every city is visited, subject to transfer feasibility, minimizing final arrival (or total waiting / total riding).

The transit routing algorithm becomes the **leg-cost evaluator**; the outer problem is still TSP. This keeps the assignment's core and uses its explicitly allowed "time-dependent edge weights" direction.

### Choosing the two approaches

The two approaches should differ in representation or algorithm, not just weights. Recommended pair:

- **Approach A:** time-expanded graph + Dijkstra/A*.
- **Approach B:** time-dependent (schedule) graph + time-dependent Dijkstra or CSA.

Min-wait vs min-ride should be a **secondary objective experiment**, not the main "two approaches" claim.

---

## 4. Data and network plan

GeoNames supplies **nodes, not a network**. In `MY.txt`:

- 21,381 `P/PPL` populated places, but only **711 have population > 0**;
- 99 railway stations (`S/RSTN`), 108 bus stops (`S/BUSTP`), 62 airports (`S/AIRP`);
- **no edges, no lines, no timetables, no travel times**.

### Pipeline

1. **Clean**
   - drop coordinate outliers (roughly 6 rows);
   - deduplicate repeated name + coordinate points (roughly 25);
   - handle blank timezones;
   - validate latitude/longitude.

2. **Select cities**
   - top-N by population (e.g. 50–100), or a justified population threshold;
   - do not rely on `admin2` — about half the rows have it blank.

3. **Build edges**
   - k-nearest neighbours or a distance threshold;
   - mode by distance (bus short-haul, train long-haul);
   - travel time = distance / speed + noise;
   - ensure connectivity with a spanning tree plus extra edges.

4. **Generate schedules**
   - per route: a headway (bus 30–60 min, train 60–180 min);
   - first and last departure;
   - optional peak-frequency variation;
   - expand headways into concrete trips.

5. **Store GTFS-like CSVs**
   - `stops`, `routes`, `trips`, `stop_times`, `transfers`;
   - standard, readable, and makes the time-expanded vs time-dependent comparison natural.

6. **Document every fabricated attribute**
   - the spec explicitly requires documentation of all augmentation.

### Modelling choices to make explicitly

- **One node per city** (simple, defensible) vs **city + station layers with walking edges** (more realistic, more work). For v1, use one node per city; mention the station layer as an extension.
- **Integer minutes or seconds from a service-day origin**, allowing `> 24:00` for overnight trips. Do not modulo 24.

---

## 5. Algorithms and data structures

| Algorithm | Objective | Role | Rough complexity |
|---|---|---|---|
| Time-dependent Dijkstra | min arrival (`W + R`) | ground-truth baseline | `O((E + C) log V)` with binary search over departures |
| Min-wait Dijkstra | min `W` | heuristic / user mode | same |
| Static Dijkstra on ride times | min `R` | heuristic / user mode | `O(E log V)` |
| Time-expanded graph + Dijkstra | min arrival | second representation | `O((V' + E') log V')` |
| Connection Scan Algorithm (CSA) | min arrival | fast alternative, strong "interestingness" | sort `O(C log C)`, query `O(C)` |
| A* | min arrival | speedup | Dijkstra with a geographic lower bound |
| RAPTOR | arrival + transfers | only if transfers are first-class; likely overkill | `O(K(Σ|r| + |T| + |F|))` |

### Data structures worth highlighting

- adjacency lists;
- binary heap / priority queue;
- **sorted departure arrays + binary search for the next catchable trip**;
- hash map city → id;
- time-expanded DAG.

The binary-search-on-departures detail is what makes the time-dependent Dijkstra efficient and is worth explaining in the report.

### Why CSA is worth considering

CSA is conceptually simple: sort all connections by departure time, scan once, and keep one earliest-arrival label per stop. It is very fast and gives a clean comparison against time-dependent Dijkstra. Dibbelt et al. report roughly an order-of-magnitude speedup over time-dependent Dijkstra on London data.

---

## 6. Pitfalls and things easy to miss

- **FIFO / overtaking.** If you fabricate both local and express services, the "first departure is best" assumption breaks. Time-dependent Dijkstra with waiting is still correct, but a naive greedy is not. Either enforce non-overtaking or document the overtaking explicitly.
- **Transfer buffer.** Do not allow 0-minute transfers. Distinguish **initial wait** from **transfer wait**, and decide whether in-seat continuation counts as a transfer.
- **Disconnected graph.** Initialize to infinity and return a proper "no route" result; validate that selected cities are actually served.
- **Multiple stations per city.** Collapsing a city to one node can invent connections that do not exist. Document the abstraction.
- **Day rollover and timezone.** Malaysia is effectively UTC+8, but the data distinguishes `Asia/Kuala_Lumpur` and `Asia/Kuching`. Normalize and document.
- **Determinism.** Seed the RNG for fabricated times and schedules; use deterministic tie-breaking, or experiments will not reproduce.
- **Correctness testing.** Brute-force enumerate all simple paths on tiny toy networks and compare against Dijkstra/CSA. Test:
  - start = end;
  - departure after the last service;
  - a missed connection by one minute;
  - no route;
  - overnight trip.
- **Metrics.** Report final arrival, total wait, total ride, number of transfers, runtime, memory, and scalability as city count and schedule density grow. A Pareto view (arrival vs transfers) is a nice addition.
- **Spec compliance.** Per-experiment folders, reproducible data generation, README with run instructions, AI-use acknowledgment with 3 representative prompts, and a live demo without AI. A fancy UI does not count as an X-factor — spend the effort on algorithms and experiments.

---

## 7. Suggested milestones

1. **Lock the variant and scope** — objectives, city-set size, one-node-per-city decision.
2. **Data pipeline + network generator** — clean `MY.txt`, select cities, fabricate edges and schedules, emit GTFS-like CSVs.
3. **Baseline router** — time-dependent Dijkstra (min arrival), plus min-wait and min-ride variants; toy-network tests and brute-force validation.
4. **Second approach** — time-expanded graph or CSA; cross-check results against the baseline.
5. **Tour layer** — nearest-neighbour + 2-opt/insertion using the router as the leg evaluator; this is what makes it a TSP variant.
6. **Experiments + complexity + report** — runtime, memory, arrival quality, wait/ride breakdown, scalability, trade-offs, limitations.

---

## 8. Bottom line

- **The two algorithms are not the same**, and comparing by final arrival does not unify them. Min-wait and min-ride are heuristics; min-arrival (`W + R`) is the true objective.
- Add a **third baseline** (time-dependent Dijkstra minimizing arrival) and measure the gap.
- The bus/train idea fits the assignment only if it is framed as a **time-dependent TSP variant**, not just a point-to-point router.
- The strongest two-approach story is **time-expanded graph vs time-dependent graph/CSA**, with min-wait and min-ride as secondary objective experiments.

---

## 9. References

1. GTFS Schedule Reference — <https://gtfs.org/documentation/schedule/reference/>
2. J. Dibbelt, T. Pajor, B. Strasser, D. Wagner, "Connection Scan Algorithm," *ACM Journal of Experimental Algorithmics* 23, 2018 — <https://doi.org/10.1145/3274661> ; arXiv:1703.05997
3. D. Delling, T. Pajor, R. Werneck, "Round-Based Public Transit Routing," *Transportation Science* 49(3), 2015 — <https://doi.org/10.1287/trsc.2014.0534>
4. H. Bast et al., "Route Planning in Transportation Networks" — <https://arxiv.org/abs/1504.05140>
5. E. Pyrga, F. Schulz, D. Wagner, C. Zaroliagis, "Efficient Models for Timetable Information in Public Transportation Systems," *ACM Journal of Experimental Algorithmics* 12, 2008 — <https://doi.org/10.1145/1227161.1227166>
6. B. Dean, *Shortest Paths in FIFO Time-Dependent Networks* — <https://people.csail.mit.edu/bdean/tdsp.pdf>
