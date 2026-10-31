# Time-Dependent Transit TSP — Locked Project Plan

> CS201 AY2026-27 Term 1 — *"Beyond the Classic TSP: Redesigning Algorithms for Real-World Assumptions"*
>
> **Status:** direction locked. Keep the transit TSP idea; de-risk the scope.
> **Deadline:** Week 12, Sunday 11pm. **Peer evaluation:** Week 13.
> **Primary data:** GeoNames `CS201_2026_project/MY.txt`.
> **Language:** Java 21 (LTS), built with Maven (core algorithms from scratch; JUnit 5 for tests, a small hand-written SVG chart writer for plots).
>
> Group number placeholder: `G<X>T<X>` (confirm before packaging).

---

## Contents

1. [Problem definition](#1-problem-definition)
2. [Objectives and terminology](#2-objectives-and-terminology)
3. [Locked scope](#3-locked-scope)
4. [Data plan](#4-data-plan)
5. [Algorithms and data structures](#5-algorithms-and-data-structures)
6. [Experiment design](#6-experiment-design)
7. [Validation](#7-validation)
8. [Deliverables and packaging](#8-deliverables-and-packaging)
9. [Presentation plan](#9-presentation-plan)
10. [Timeline](#10-timeline)
11. [Team roles and peer evaluation](#11-team-roles-and-peer-evaluation)
12. [AI policy](#12-ai-policy)
13. [Risks and mitigations](#13-risks-and-mitigations)
14. [Compliance checklist](#14-compliance-checklist)
15. [References](#15-references)

---

## 1. Problem definition

> **Summary:** The project is a time-dependent transit TSP: visit a fixed set of Malaysian cities by scheduled bus/train from a given start and time, minimizing final arrival. Classic TSP's static, symmetric, complete, known-cost assumptions fail once schedules, waiting, and transfers are modelled.

### 1.1 Use case

A traveller in Malaysia wants to visit a fixed set of cities using scheduled buses and trains. They start at a given city at a given time. Services run on timetables, transfers take time, and the cost of moving between two cities depends on **when** the traveller arrives at the boarding city.

This is a realistic use case in which the classic TSP assumptions do not hold.

### 1.2 Classic TSP assumptions that fail

| Classic TSP assumption                        | Transit reality                                                 |
| --------------------------------------------- | --------------------------------------------------------------- |
| Pairwise costs are **static**           | Travel time depends on the departure time and the specific trip |
| Costs are **symmetric**                 | Services are directional; A→B ≠ B→A                          |
| The graph is **complete**               | Most city pairs have no direct service; transfers are required  |
| Pairwise costs are **known in advance** | The cost depends on which trip you can catch                    |
| No **waiting or transfers**             | Waiting and transfer buffers are part of the journey            |
| No **time constraints**                 | Services have first/last departures and operating hours         |

At least one of these is sufficient for the assignment; transit violates several. State this explicitly in the report and slides.

### 1.3 Variant specification

**Name:** Time-Dependent Transit TSP (TDT-TSP), open-path version.

**Input**

- A set of cities `V = {1, ..., n}`, each with coordinates.
- A start city `s ∈ V` and a departure time `t0`.
- A transit network `N = (S, R, T, ST)`:
  - `S` — stops (one per city in v1),
  - `R` — routes/lines,
  - `T` — trips (concrete vehicle runs),
  - `ST` — stop times `(trip, stop, arrival, departure)`.
- A transfer buffer `δ` (default 5 minutes).
- A service horizon `[t_first, t_last]` (e.g. 06:00–22:00).

**Output**

- A permutation `π = (s = π1, π2, ..., πn)` containing every city exactly once.
- For each consecutive pair `(πi, πi+1)`, a feasible itinerary:
  a sequence of trips with departure/arrival times, such that each boarding satisfies
  `departure ≥ current_time + δ` for transfers (no buffer at the origin), and the arrival at `πi+1` is the earliest feasible.
- The final arrival time `A` at `πn`.

**Constraints**

- Each city appears exactly once in `π`.
- The itinerary between consecutive tour cities may **pass through** other cities without counting them as tour visits. A city is *visited* only when it is a tour stop.
- Transfers must respect `δ`.
- All times lie within the service horizon; overnight trips use `> 24:00` representation.
- No time windows in v1 (extension only).

**Objective**

Minimize the final arrival time `A`.

**Closed-tour extension**

Add a return leg `πn → s` and minimize the return arrival. This is a small addition; choose the open-path version for v1 because a traveller does not need to return, and state the choice explicitly.

### 1.4 Why the redesign is necessary

A static Dijkstra on average travel times cannot represent this problem:

- the cost of an edge depends on the arrival time at its tail;
- a statically shortest path may be infeasible at the actual time (the service may not run);
- waiting and transfers are part of the cost;
- the graph is not complete, and direction matters.

The redesign is a **time-dependent graph** (or a **time-expanded graph**) with schedule-aware edge relaxation. This is the core algorithmic contribution, not just a different weight function.

---

## 2. Objectives and terminology

> **Summary:** Final arrival equals start time plus total waiting plus total riding. Minimizing waiting, minimizing riding, and minimizing arrival are three different objectives; only min-arrival (`W + R`) is the true target. Min-wait and min-ride are secondary heuristics, not the two main approaches.

For a journey starting at `t0`:

```
arrival = t0 + W + R
W = total waiting time
R = total in-vehicle riding time
```

| Objective             | Minimizes | Ignores                  | Same as min arrival?           |
| --------------------- | --------- | ------------------------ | ------------------------------ |
| "Shortest waiting"    | `W`     | how slow the vehicle is  | **No**                   |
| "Shortest travelling" | `R`     | how long you wait for it | **No**                   |
| "Earliest arrival"    | `W + R` | —                       | **This is the real one** |

Counterexample at a stop at 08:00:

| Option  | Departs | Arrives |   Wait |   Ride |
| ------- | ------: | ------: | -----: | -----: |
| Local   |   08:01 |   09:40 |  1 min | 99 min |
| Express |   08:10 |   08:40 | 10 min | 30 min |

- "Take the next bus" → local → arrive **09:40**
- "Shortest riding time" → express → arrive **08:40**
- "Shortest final arrival" → express → **08:40**

Reverse the numbers and they disagree the other way. **Comparing both by final arrival does not make them the same algorithm**; it gives them a common yardstick.

### How the objectives are used in this project

- **Primary TSP objective:** minimize final arrival `A` over the whole tour.
- **Secondary leg heuristics:** min-wait and min-ride. These are *not* the two main approaches; they are comparison modes for the experiment.
- **Two main approaches:** different routing representations/algorithms (Section 5).

Special cases where objectives coincide:

- equal ride times for all alternatives → min-wait = min-arrival;
- equal waits for all alternatives → min-ride = min-arrival.

In a general timetable they do not coincide.

---

## 3. Locked scope

> **Summary:** The scope is deliberately small: 30 cities (10/20/30/40 for scalability), one node per city, 8–12 lines, fixed headways, a 5-minute transfer buffer, and two approaches (time-expanded + Dijkstra vs time-dependent + CSA). RAPTOR, real GTFS, real-time delays, and a polished UI are out of scope.

### In scope

- **30 cities** for the main experiments (10/20/30/40 for scalability).
- **One node per city** (documented abstraction).
- **8–12 lines**, each with 4–8 stops; every selected city is on at least one line.
- **Headways:** bus 30 min, train 60 min; service 06:00–22:00.
- **Transfer buffer:** 5 min.
- **Two approaches:**
  - **A — time-expanded graph + Dijkstra**
  - **B — time-dependent graph + CSA**
- **Shared tour layer:** nearest-neighbour + insertion/2-opt with full re-evaluation.
- **Classic baseline:** static TSP on a complete distance graph, same tour heuristic.
- **Secondary leg modes:** min-wait, min-ride.
- **Language:** Java 21, Maven build.
- **Core algorithms implemented from scratch**; CSV/TSV parsing with the standard library, JUnit 5 for tests, and a small hand-written SVG chart writer for plots (no plotting dependency).

### Out of scope (do not build)

- Real GTFS feeds or real Malaysian timetables.
- RAPTOR / McRAPTOR.
- Real-time delays, stochastic travel times, and reliability objectives.
- Time windows, pickup/delivery, vehicle routing.
- Multiple stations per city, walking footpaths.
- A polished UI. A simple CLI and one map plot are enough.

### Fallback if behind schedule

If the time-expanded graph is not ready by the end of day 8, drop it and use **time-dependent Dijkstra vs CSA** as the two approaches. They are still meaningfully different algorithms (label-setting with a priority queue vs a single chronological connection scan). Keep the comparison honest in the report.

---

## 4. Data plan

> **Summary:** GeoNames supplies only city nodes — no edges, lines, or timetables. The pipeline cleans `MY.txt`, selects the top 30 cities by population, builds a connected geometric graph and 8–12 lines, generates headway-based schedules, and emits GTFS-like CSVs. Every fabricated attribute must be documented.

### 4.1 GeoNames facts

`MY.txt`:

- 71,709 rows, 19 tab-separated columns, no header;
- 21,381 `P/PPL` populated places, but only **711 have population > 0**;
- 99 railway stations (`S/RSTN`), 108 bus stops (`S/BUSTP`), 62 airports (`S/AIRP`);
- **no edges, no lines, no timetables, no travel times**.

GeoNames supplies **nodes, not a network**.

### 4.2 Cleaning

1. Drop coordinate outliers (roughly 6 rows).
2. Deduplicate repeated name + coordinate points (roughly 25).
3. Validate latitude/longitude against a Malaysia bounding box. Coordinates are used only for distance-based network generation, outlier checks, and optional map plots — the routing algorithms consume the resulting edge list and timetable, not raw coordinates.
4. Use a single local time. Malaysia is UTC+8 with no DST, and both `Asia/Kuala_Lumpur` and `Asia/Kuching` resolve to UTC+8. Ignore the timezone column; document this assumption.
5. Keep `geonameid`, `name`, `latitude`, `longitude`, `feature class`, `feature code`, `population`, `admin1`. Drop `timezone` from the routing model.

### 4.3 City selection

- Use `P/PPL`, `P/PPLA`, `P/PPLC` with population > 0.
- Select the **top 30 by population** for the main instance.
- Keep the full ordered list so the scalability experiment can take the top 10/20/30/40.
- If a selected city is geometrically isolated, add the nearest non-zero-population city to keep the network connected.
- Document: records selected, columns used, filter rationale, subset justification.

### 4.4 Network generation

1. **Geometric graph:** connect each city to its `k = 3` nearest neighbours (haversine distance).
2. **Connectivity:** add the edges of a minimum spanning tree so the graph is connected.
3. **Lines:** generate 8–12 paths of 4–8 cities using random walks or shortest paths along the geometric graph.
4. **Coverage:** every selected city must appear on at least one line. Repair by inserting uncovered cities into the nearest line.
5. **Modes:**
   - bus for lines whose longest segment is < 200 km;
   - train otherwise.
6. **Travel time:** `distance / speed`, with bus ≈ 60 km/h, train ≈ 100 km/h. Travel times are fully deterministic and reproducible.
7. **Direction:** each line runs in both directions with the same segment times (or separate directional trips; document).
8. **Seed:** every random choice uses a fixed seed stored in `config.properties` (read with `java.util.Properties`; all randomness goes through one seeded `java.util.Random`).

### 4.5 Schedule generation

- First departure 06:00, last departure 22:00.
- Headway: bus 30 min, train 60 min.
- Optional peak frequency (e.g. 15 min for bus 07:00–09:00 and 17:00–19:00) — only if time permits.
- Expand each line into concrete trips: for each departure slot, create a trip with stop times at each stop.
- Ensure no trip overlaps itself and all times are monotonically increasing.
- **Multi-day horizon (experiments):** a 30-city tour takes several days, so the routers repeat the one-day timetable for `days` consecutive days (day `d` shifted by `d × 1440` minutes, with its own trip ids). Set per experiment in `experiments/*/config.properties` (default 14).

### 4.6 GTFS-like schema

Emit CSVs under `data/gtfs/`:

| File               | Columns                                                           |
| ------------------ | ----------------------------------------------------------------- |
| `stops.csv`        | `stop_id, name, lat, lon`                                         |
| `routes.csv`       | `route_id, mode, name`                                            |
| `trips.csv`        | `trip_id, route_id, direction, start_time`                        |
| `stop_times.csv`   | `trip_id, stop_sequence, stop_id, arrival_time, departure_time`   |
| `transfers.csv`    | `from_stop, to_stop, min_transfer_time` (v1: same-stop only)      |

Times are integer minutes from the service-day start, allowing values `> 1440`.

### 4.7 Data documentation checklist

Write a dedicated report section covering:

- [ ] Records selected (counts and criteria).
- [ ] Columns used and why.
- [ ] Filtering/preprocessing steps.
- [ ] Subset justification (top 30 by population).
- [ ] Every fabricated attribute: lines, modes, speeds, headways, first/last departures, transfer buffer.
- [ ] Derived attributes (distance, travel time, trip times).
- [ ] Random seeds and generation script.
- [ ] Known limitations (synthetic schedule, one node per city, no walking).

---

## 5. Algorithms and data structures

> **Summary:** The core is schedule-aware routing: sorted departure arrays with binary search feed time-dependent Dijkstra and CSA; a time-expanded graph provides the second representation. The tour layer uses nearest-neighbour plus insertion/2-opt with full re-evaluation because time-dependent costs make the standard 2-opt delta invalid.

### 5.1 Core data structures

- Adjacency list for the geometric graph.
- **Sorted departure arrays per directed edge**, with binary search for the next departure `≥ t`.
- Min-heap / priority queue for Dijkstra (`java.util.PriorityQueue` with lazy deletion).
- Hash map city name → id (`HashMap<String, Integer>`).
- Connection array sorted by departure time for CSA.
- Time-expanded DAG for Approach A.
- Memoisation table for leg results keyed by `(from, to, departure_time)` during tour construction.

**Why adjacency list, not a matrix.** The transit graph is sparse: each city is on a few lines, so `E = O(V)`. A matrix would waste `O(V²)` space and cannot naturally hold a sorted departure array per directed edge. Dijkstra and CSA iterate over neighbours, not random pairs, so the adjacency list is the right fit. The classic baseline uses a dense distance matrix because its graph is complete — that contrast is worth reporting. At `V = 30` the memory difference is negligible, but the list matches the problem structure and scales to the time-expanded graph.

### 5.2 Classic baseline (not one of the two approaches)

- Complete graph on the selected cities with haversine distance as the cost.
- Nearest-neighbour + 2-opt to produce a tour.
- Evaluate the tour on the real transit network by routing each leg with the earliest-arrival router.
- Report final arrival, total distance, and infeasible legs. This demonstrates why the static formulation is inadequate.

### 5.3 Approach A — time-expanded graph + Dijkstra

- Create an event node for each `(trip, stop)` arrival and departure, plus a source node at `(s, t0)`.
- Add:
  - **travel arcs** from a trip's departure event to its arrival event;
  - **waiting arcs** between consecutive events at the same stop;
  - **transfer arcs** from an arrival event to a later departure event, respecting `δ`.
- Run Dijkstra on this static DAG. The graph is larger but the algorithm is simple and robust.

Complexity: `O((V' + E') log V')` time, `O(V' + E')` space, where `V'` and `E'` are the event-graph sizes.

### 5.4 Approach B — time-dependent graph + CSA

- Build a connection list: `(dep_time, from_stop, arr_time, to_stop, trip_id)`.
- Sort by `dep_time`.
- Maintain an earliest-arrival array `arr[v]`, initialised to `∞`, with `arr[s] = t0`.
- Scan connections in increasing departure order:
  - if `dep_time ≥ arr[from] + buffer(from)`, and `arr_time < arr[to]`, update `arr[to]`.
- Stop when the scan passes the target's current label (or scan the whole list).
- No priority queue; the scan is cache-friendly.

Complexity: sorting `O(C log C)` once; query `O(C)`; space `O(C + V)`.

### 5.5 Leg heuristics (secondary)

- **Min-wait:** time-dependent Dijkstra with cost = waiting time only.
- **Min-ride:** time-dependent Dijkstra with cost = in-vehicle time only.
- Both are compared against min-arrival on the same queries.

### 5.6 Tour layer

- **Nearest-neighbour:** from the current city and time, evaluate the earliest arrival to every unvisited city; pick the earliest; repeat.
- **Insertion / 2-opt:** improve the tour by re-evaluating affected legs.
- **Important:** with time-dependent, asymmetric costs, the standard 2-opt cost delta is invalid. Re-evaluate the full tour (or the affected suffix) after each move. For 30 cities this is affordable.
- **Infeasible legs:** assign a large penalty and keep the tour; report infeasibility rather than crashing.
- **Optimality:** the tour is heuristic. The project does not require a mathematically optimal TSP tour; the time-dependent Dijkstra is optimal only for a single leg.

### 5.7 Pseudocode

**Time-dependent Dijkstra (earliest arrival)**

```
function earliest_arrival(adj, s, t0, buffer):
    dist[v] = INF for all v
    dist[s] = t0
    pq = min_heap((t0, s))
    while pq not empty:
        (time, u) = pq.pop()
        if time > dist[u]: continue
        for (v, departures, ride_time) in adj[u]:
            t_ready = time + (0 if u == s else buffer)
            dep = next_departure(departures, t_ready)   # binary search
            if dep is None: continue
            arr = dep + ride_time
            if arr < dist[v]:
                dist[v] = arr
                pq.push((arr, v))
    return dist
```

**Connection Scan Algorithm**

```
function csa(connections, s, t0, buffer):
    arr[v] = INF for all v
    arr[s] = t0
    sort connections by dep_time
    for c in connections:
        if c.dep_time >= arr[c.from] + (0 if c.from == s else buffer):
            if c.arr_time < arr[c.to]:
                arr[c.to] = c.arr_time
    return arr
```

**Nearest-neighbour tour**

```
function nn_tour(cities, s, t0, router):
    tour = [s]
    time = t0
    unvisited = cities - {s}
    while unvisited not empty:
        best = None
        for v in unvisited:
            arr = router.earliest_arrival(tour.last, v, time)
            if best is None or arr < best.arr:
                best = (v, arr)
        tour.append(best.city)
        time = best.arr
        unvisited.remove(best.city)
    return tour
```

### 5.8 Complexity summary

| Component                     | Time                                 | Space            |
| ----------------------------- | ------------------------------------ | ---------------- |
| Time-dependent Dijkstra       | `O((E + C) log V)`                   | `O(V + E + C)`   |
| CSA                           | `O(C log C)` sort + `O(C)` query     | `O(C + V)`       |
| Time-expanded Dijkstra        | `O((V' + E') log V')`                | `O(V' + E')`     |
| Nearest-neighbour tour        | `O(n² × router_cost)`                | `O(V + E + C)`   |
| 2-opt with full re-evaluation | `O(n² × router_cost)` per pass       | same             |

---

## 6. Experiment design

> **Summary:** Four research questions drive the evaluation: heuristic gap, representation trade-off, scalability, and the value of the redesign versus a classic static TSP. Experiments vary city count, headway, transfer buffer, and seed, and report arrival, waiting, riding, transfers, runtime, memory, and graph size in per-experiment folders.

### Research questions

- **RQ1 — Heuristic gap:** how much later do min-wait and min-ride arrive than min-arrival, and when do they fail?
- **RQ2 — Representation trade-off:** how do time-expanded + Dijkstra and time-dependent + CSA compare in query time, preprocessing time, and memory?
- **RQ3 — Scalability:** how do both approaches scale with city count, headway, and transfer buffer?
- **RQ4 — Value of the redesign:** how does the classic static TSP tour perform on the real transit network compared with the time-dependent tours?

### Instances

- `n ∈ {10, 20, 30, 40}` cities.
- Headways: `{15, 30, 60, 120}` minutes.
- Transfer buffer: `{0, 5, 10}` minutes (report `δ = 5` as the main setting; `0` is a stress test).
- Seeds: `{1, 2, 3, 4, 5}`.
- Main configuration: `n = 30`, bus headway 30, train headway 60, `δ = 5`.

### Metrics

- Final arrival time `A`.
- Total waiting `W`.
- Total riding `R`.
- Number of transfers.
- Number of legs.
- Query runtime (µs), preprocessing runtime (ms), memory (MB, estimated from the preprocessed structure's array sizes; JVM peak heap is too noisy under garbage collection).
- Graph size: time-expanded nodes/edges, number of connections.
- Optimality gap of min-wait/min-ride vs min-arrival.
- Infeasible legs (count and percentage).

### Procedure

1. Generate the instance with a fixed seed.
2. Build the transit network and the schedule.
3. Run the classic baseline, Approach A, Approach B, min-wait, and min-ride.
4. Record metrics for the same query set.
5. Repeat for 5 seeds; report mean, median, and standard deviation.
6. Plot:
   - arrival vs `n`;
   - runtime vs `n`;
   - memory vs `n`;
   - heuristic gap vs headway;
   - classic baseline vs time-dependent arrival.

### Expected/hypothesis

- Min-wait will be close to min-arrival when services are frequent, and worse when headways are long.
- Min-ride will often wait for an express and can beat min-wait on arrival, but not always.
- CSA will be faster per query but require sorting/preprocessing; time-expanded Dijkstra will use more memory.
- The classic static tour will produce later arrivals or infeasible legs, justifying the redesign.

### Per-experiment folders

```
experiments/
  exp1_heuristics/
    config.properties
    results.csv
    plots/
    README.md
  exp2_representations/
  exp3_scalability/
  exp4_classic_baseline/
```

Each folder records the config, raw results, plots, and a short note on how to reproduce it.

---

## 7. Validation

> **Summary:** Correctness is established on toy networks with known answers, brute-force enumeration on tiny instances, and cross-checks between the time-expanded Dijkstra and CSA. Edge cases include start = end, missed connections, no route, overnight trips, and disconnected cities.

- **Toy networks** with hand-computed answers (3–5 cities, 2 lines, known timetable).
- **Brute force** on tiny instances: enumerate all simple paths and simulate the timetable; compare with Dijkstra/CSA.
- **Cross-check A vs B:** both must return the same earliest arrival for the same query (when both are correct; document any tie-breaking differences).
- **Edge cases:**
  - start = end;
  - departure after the last service;
  - missed connection by one minute;
  - no route;
  - overnight trip (`> 24:00`);
  - zero-population city;
  - disconnected city.
- **Determinism:** same seed → same network, same schedule, same results.
- **Unit tests:** JUnit 5 (`mvn test`) for `nextDeparture`, timetable parsing, Dijkstra, CSA, and tour construction. Toy networks live in `src/test/resources/toy/`.

---

## 8. Deliverables and packaging

> **Summary:** The submission is `G<X>T<X>.zip` containing source code, processed and GTFS data, per-experiment folders, tests, and a README with install/run/reproduce instructions. The repository has clear `src/`, `data/`, `experiments/`, `tests/`, and `docs/` boundaries.

### Repository structure

```
.
├── README.md
├── Project_2627T1.pdf
├── CS201_2026_project/          # provided raw data (unchanged)
├── data/
│   ├── raw/
│   ├── processed/
│   └── gtfs/
├── src/
│   ├── main/
│   │   ├── java/tdtsp/
│   │   │   ├── data/            # cleaning and selection
│   │   │   ├── network/         # graph and schedule generation, GTFS I/O
│   │   │   ├── routing/         # dijkstra, csa, time-expanded
│   │   │   ├── tour/            # nn, insertion, 2-opt, classic baseline
│   │   │   └── cli/             # command-line entry point
│   │   └── resources/
│   └── test/
│       ├── java/tdtsp/          # JUnit 5 tests mirroring main packages
│       │   ├── data/
│       │   ├── network/
│       │   ├── routing/
│       │   └── tour/
│       └── resources/toy/       # toy networks with hand-computed answers
├── experiments/
│   ├── exp1_heuristics/
│   ├── exp2_representations/
│   ├── exp3_scalability/
│   └── exp4_classic_baseline/
├── docs/
│   └── transit-tsp-plan.md
├── config.properties            # generation parameters and seed
└── pom.xml
```

Rename the `tdtsp` package if the group prefers a reverse-domain name; keep main and test packages in sync.

### Packaging

- Submit `G<X>T<X>.zip` (confirm the group number).
- Include source code, data in the used format, per-experiment folders, `pom.xml`, and `README.md`. Exclude `target/`.
- README must state:
  - folder structure;
  - build command (`mvn package`);
  - how to regenerate data;
  - how to run each experiment;
  - how to run the CLI;
  - Java and Maven versions and dependencies;
  - random seeds.

### CLI (minimal)

```
java -jar target/tdtsp.jar route --from "Kuala Lumpur" --to "Penang" --time 08:00
java -jar target/tdtsp.jar tour --cities cities.txt --start "Kuala Lumpur" --time 08:00 --method csa
```

A simple map plot of the selected cities and lines is enough; a full UI is not required.

---

## 9. Presentation plan

> **Summary:** The presentation is 40% of the grade, so it is planned from the start: 15 slides covering motivation, variant, data, both approaches with pseudocode, results, complexity, trade-offs, limitations, and AI usage. Every member must explain and modify at least one module, and the live demo runs without AI.

The spec's rubric includes **interestingness 10%, experimentation 30%, presentation 40%, X-factor 10%**. The presentation is the largest single component; plan it from the start.

### Slide outline (15 slides)

1. Title and team.
2. Motivation: why classic TSP is not enough for transit.
3. Classic TSP assumptions vs transit reality (the table from Section 1.2).
4. Use case and scope.
5. Variant: input, output, constraints, objective.
6. Data pipeline: GeoNames → cleaned cities → lines → schedules.
7. Approach A: time-expanded graph (diagram + pseudocode).
8. Approach B: time-dependent graph + CSA (diagram + pseudocode).
9. Tour layer: NN + insertion/2-opt, time-dependent re-evaluation.
10. Experiment design: RQs, instances, metrics.
11. Results: heuristic gap (RQ1).
12. Results: representation trade-off and scalability (RQ2, RQ3).
13. Results: classic baseline vs time-dependent (RQ4).
14. Complexity analysis and trade-offs.
15. Limitations, interesting findings, AI usage, and demo.

### Presentation rules

- Every member must be able to explain and modify at least one module.
- No AI during the live presentation.
- Rehearse the demo with a fixed seed so it is reproducible.
- Keep backup screenshots/CSVs in case the live demo fails.

### X-factor ideas (optional, only if time permits)

- A map visualisation of the chosen tour with waiting/riding segments.
- A Pareto plot of arrival vs transfers.
- A "what-if" slider for headway (offline, precomputed).

A fancy UI does not count as an X-factor; a meaningful visualisation of the algorithmic trade-off does.

---

## 10. Timeline

> **Summary:** The plan assumes about 15 days: data and network first, then baseline and routing, then the second approach, tour layer, experiments, and slides. Day 8 is the fallback decision point — if the time-expanded graph is behind, switch to time-dependent Dijkstra vs CSA.

Assuming ~15 days (2+ weeks). Adjust to the actual calendar.

| Day | Work                                                                           |
| --- | ------------------------------------------------------------------------------ |
| 1   | Lock variant and scope; create repo skeleton; set up Maven + JUnit; write README stub |
| 2   | Clean `MY.txt`; select top 30 cities; document data decisions                  |
| 3   | Build geometric graph, MST connectivity, lines, coverage                       |
| 4   | Generate schedules; emit GTFS-like CSVs; validate monotonic times              |
| 5   | Build loaders; toy network; unit tests for parsing and `next_departure`        |
| 6   | Classic static TSP baseline (distance + NN/2-opt)                              |
| 7   | Time-dependent Dijkstra (min arrival); min-wait and min-ride variants          |
| 8   | Test and cross-check; **fallback decision point** for Approach A               |
| 9   | Time-expanded graph + Dijkstra                                                 |
| 10  | CSA implementation                                                             |
| 11  | Cross-check A vs B; fix discrepancies                                          |
| 12  | Tour layer: NN + insertion/2-opt with full re-evaluation                       |
| 13  | Run experiments; save configs/results/plots                                    |
| 14  | Analyse results; write report sections; complexity analysis                    |
| 15  | Slides, rehearsal, packaging, final checks                                     |

**Buffer rule:** if day 8 is not on track, drop the time-expanded graph and use time-dependent Dijkstra vs CSA. Do not drop the experiments or the presentation.

---

## 11. Team roles and peer evaluation

> **Summary:** Each module has a named owner and a backup, covering data, network generation, both routing approaches, the tour layer, experiments, and presentation. Every member must be able to run the pipeline, explain the variant, and modify at least one module for the Week 13 peer evaluation.

Assign one owner per module, plus a pairing partner for peer evaluation.

| Role                                  | Owner | Backup |
| ------------------------------------- | ----- | ------ |
| Data cleaning and city selection      | TBD   | TBD    |
| Network and schedule generation       | TBD   | TBD    |
| Approach A (time-expanded + Dijkstra) | TBD   | TBD    |
| Approach B (CSA)                      | TBD   | TBD    |
| Tour layer and experiments            | TBD   | TBD    |
| Presentation and QA                   | TBD   | TBD    |

Every member must be able to:

- explain the variant and the objective;
- run the pipeline and the experiments;
- modify at least one module;
- answer questions about complexity and trade-offs.

Schedule at least two internal rehearsals before the live presentation.

---

## 12. AI policy

> **Summary:** AI is allowed for brainstorming, data generation, coding, debugging, and slides, but every use must be logged. The report needs three representative prompts, a clear original-vs-assisted statement, and no AI during the live presentation.

The spec allows AI for brainstorming, experiment design, sample data generation, coding, debugging, and slides, but requires acknowledgment.

- [ ] Keep a prompt log from day 1 (tool, date, purpose, prompt, what was used).
- [ ] Include **3 representative prompts** in the report.
- [ ] State which parts are original and which are AI-assisted.
- [ ] Do not use AI during the live presentation.
- [ ] Ensure every member can explain and modify the work without AI.

---

## 13. Risks and mitigations

> **Summary:** The main risks are data-generator bugs, scope creep, invalid time-dependent 2-opt deltas, CSA bugs, disconnected cities, and presentation under-preparation. Each has a concrete mitigation: validation and seeds, locked non-goals, full re-evaluation, A/B cross-checks, MST coverage repair, and rehearsals.

| Risk                               | Impact                                    | Mitigation                                            |
| ---------------------------------- | ----------------------------------------- | ----------------------------------------------------- |
| Data generator bugs                | Wrong results, irreproducible experiments | Validation, seeds, toy tests                          |
| Scope creep                        | Missed deadline                           | Locked non-goals; day-8 fallback                      |
| Time-dependent 2-opt invalid delta | Wrong tour improvement                    | Full re-evaluation or insertion heuristic             |
| CSA bugs                           | Wrong arrival times                       | Cross-check with Dijkstra on toy and random instances |
| Disconnected cities                | Infeasible legs                           | MST connectivity + coverage repair                    |
| Presentation under-prepared        | 40% of grade at risk                      | Start slides by day 12; two rehearsals                |
| Team availability                  | Delays                                    | Pairing, clear owners, buffer day                     |
| AI-policy violation                | Penalty                                   | Prompt log and live rehearsal without AI              |

---

## 14. Compliance checklist

> **Summary:** This checklist maps the plan to the spec: classic TSP starting point, failed assumption, formal variant, two meaningful approaches, experiments, complexity, data documentation, packaging, presentation, and AI policy. It should be reviewed before every milestone.

### Problem and design

- [ ] Starts from the classic TSP and states it.
- [ ] Identifies a realistic use case.
- [ ] States at least one classic assumption that fails.
- [ ] Defines a new TSP variant with input, output, and constraints.
- [ ] Justifies why the redesign is necessary.

### Implementation

- [ ] At least two meaningfully different approaches.
- [ ] Approaches differ in data structure and/or algorithm, not parameters.
- [ ] Classic baseline for comparison.
- [ ] Core algorithms implemented (not just library calls).

### Evaluation

- [ ] Experiment design with RQs, instances, metrics, seeds.
- [ ] Results with tables/plots.
- [ ] Complexity analysis for each approach.
- [ ] Trade-offs and limitations discussed.

### Data

- [ ] GeoNames `MY.txt` is the primary source.
- [ ] Records, columns, filtering, and subset justified.
- [ ] Every fabricated attribute documented.
- [ ] Generation is reproducible (seeds, scripts).

### Deliverables

- [ ] `G<X>T<X>.zip` with code, data, experiments, README.
- [ ] Per-experiment folders with configs, results, plots.
- [ ] README with install and run instructions.
- [ ] `pom.xml` and Java version.

### Presentation

- [ ] Slides cover motivation, variant, data, approaches, pseudocode, results, complexity, trade-offs, limitations, interesting findings, AI usage.
- [ ] Every member can explain and modify their module.
- [ ] Live demo rehearsed without AI.
- [ ] AI prompt log with 3 representative prompts.

---

## 15. References

> **Summary:** The references cover the GTFS schedule model, CSA, RAPTOR, time-dependent shortest paths, and timetable-information models. They support the design choices and the complexity claims in the report.

1. GTFS Schedule Reference — [https://gtfs.org/documentation/schedule/reference/](https://gtfs.org/documentation/schedule/reference/)
2. J. Dibbelt, T. Pajor, B. Strasser, D. Wagner, "Connection Scan Algorithm," *ACM Journal of Experimental Algorithmics* 23, 2018 — [https://doi.org/10.1145/3274661](https://doi.org/10.1145/3274661) ; arXiv:1703.05997
3. D. Delling, T. Pajor, R. Werneck, "Round-Based Public Transit Routing," *Transportation Science* 49(3), 2015 — [https://doi.org/10.1287/trsc.2014.0534](https://doi.org/10.1287/trsc.2014.0534)
4. H. Bast et al., "Route Planning in Transportation Networks" — [https://arxiv.org/abs/1504.05140](https://arxiv.org/abs/1504.05140)
5. E. Pyrga, F. Schulz, D. Wagner, C. Zaroliagis, "Efficient Models for Timetable Information in Public Transportation Systems," *ACM Journal of Experimental Algorithmics* 12, 2008 — [https://doi.org/10.1145/1227161.1227166](https://doi.org/10.1145/1227161.1227166)
6. B. Dean, *Shortest Paths in FIFO Time-Dependent Networks* — [https://people.csail.mit.edu/bdean/tdsp.pdf](https://people.csail.mit.edu/bdean/tdsp.pdf)
