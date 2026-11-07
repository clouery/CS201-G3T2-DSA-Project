# CS201 G3T2 — Time-Dependent Transit TSP

A time-dependent travelling-salesman project on a synthetic Malaysian intercity bus/train network. A traveller starts at a given city at a given time and must visit every selected city exactly once, minimizing final arrival. Unlike classic TSP, edge costs depend on when the traveller reaches the boarding stop, the graph is sparse and directional, and waiting/transfers are part of the cost.

The project compares two schedule-aware routing representations:

- **Approach A** — time-expanded graph + Dijkstra
- **Approach B** — time-dependent graph + Connection Scan Algorithm (CSA)

with time-dependent Dijkstra as a third reference, a nearest-neighbour + local-search tour layer, and a classic static TSP baseline.

Full design and rationale: [`docs/transit-tsp-plan.md`](docs/transit-tsp-plan.md).

---

## Requirements

- **Java 21 or newer**
- The Maven wrapper is included (`mvnw` / `mvnw.cmd`); no global Maven installation is required
- JUnit 5 is the only test dependency

Run all commands from the repository root — paths such as `data/gtfs` and `config.properties` are relative.

---

## Quick start

### Build and test

macOS / Linux:

```bash
./mvnw clean test
./mvnw package
```

Windows:

```bat
.\mvnw.cmd clean test
.\mvnw.cmd package
```

### Run the interactive CLI

```bash
./run.sh
```

or, after compiling:

```bash
./mvnw -q compile
java -cp target/classes tdtsp.Main
```

Windows:

```bat
.\mvnw.cmd -q compile
java -cp target/classes tdtsp.Main
```

The CLI loads `data/gtfs` and `config.properties`. It uses **stop IDs**, not city names.

| Command | Meaning |
|---|---|
| `help` | Show commands |
| `list stops -p <page>` | List stops alphabetically |
| `list stops -trip <trip_id>` | Stops visited by a trip |
| `list trips -p <page>` | List scheduled trips |
| `list routes -p <page>` | List routes |
| `tdd <origin_id> <dest_id> HH:mm` | Time-dependent Dijkstra |
| `csa <origin_id> <dest_id> HH:mm` | Connection Scan Algorithm |
| `ted <origin_id> <dest_id> HH:mm` | Time-expanded Dijkstra |
| `exit` | Quit |

Example session:

```text
> list stops -p 1
> ted 1734634 1734705 08:00
> csa 1734634 1734705 08:00
> exit
```

---

## Regenerate the network and timetable

The committed `data/processed/` and `data/gtfs/` files were generated from the **top 40 cities** with `seed=43`.

```bash
./mvnw -q compile
java -cp target/classes tdtsp.network.NetworkMain \
  data/processed/cities_top40.csv config.properties data/processed data/gtfs
```

This overwrites `data/processed/*` and `data/gtfs/*`. To generate into scratch directories instead:

```bash
java -cp target/classes tdtsp.network.NetworkMain \
  data/processed/cities_top40.csv config.properties /tmp/tdtsp-processed /tmp/tdtsp-gtfs
```

Generation is deterministic: the same city list plus the same `config.properties` produces byte-identical CSVs.

`data/processed/cities_top30.csv` is kept for unit tests and comparison; the main instance is `cities_top40.csv`.

---

## Run the experiments

```bash
./mvnw -q compile

# all four experiments
java -cp target/classes tdtsp.experiment.ExperimentMain

# one experiment
java -cp target/classes tdtsp.experiment.ExperimentMain exp2_representations
```

Each experiment reads its own `experiments/<id>/config.properties` and writes `results.csv`, `summary.csv`, `plots/`, and a generated `README.md`. Edit the config, not the generated README.

| Experiment | Research question |
|---|---|
| `exp1_heuristics` | RQ1 — how much later do min-wait and min-ride arrive than min-arrival? |
| `exp2_representations` | RQ2 — time-expanded + Dijkstra vs CSA vs TD Dijkstra in time and memory |
| `exp3_scalability` | RQ3 — scaling with city count, headway, and transfer buffer |
| `exp4_classic_baseline` | RQ4 — classic static TSP vs time-dependent tours |

Experiments **do not read** `data/gtfs`; they regenerate instances from `data/processed/cities_all.csv` and `config.properties`, overriding the seed, headways, and buffer per experiment config.

## Results snapshot

From the latest full run. Wall-clock timings vary between runs and machines; routing outcomes and tour durations are deterministic for the given config and seeds. Full tables and plots are in each experiment's `README.md`.

| Experiment | Main finding |
|---|---|
| `exp1_heuristics` | Min-wait and min-ride are not substitutes for min-arrival: at a 30-minute headway they reach the optimal arrival on 59.8% and 82.6% of legs, with mean gaps of 134.7 and 21.9 minutes. |
| `exp2_representations` | At 40 cities, CSA is roughly 21x faster per query than time-expanded Dijkstra (~4.9 µs vs ~105 µs) and uses less than half the memory (1.9 MB vs 4.3 MB); TD Dijkstra sits between them. All three agree on every query (0 mismatches). |
| `exp3_scalability` | Going from 10 to 40 cities raises query time ~1.7x (TD), ~3.1x (CSA), and ~3.4x (TE). Lowering the headway from 120 to 15 minutes raises preprocessing and memory by roughly 7–10x and query time by roughly 2.6–8x. |
| `exp4_classic_baseline` | At 40 cities, the classic static tour takes 113.3 h, TD nearest-neighbour 97.6 h, and TD nearest-neighbour + local search 79.4 h — local search improves the greedy tour by 18.6%. |

**Greedy-tour caveat.** The tour layer is greedy: it can choose an early-arriving city with poor onward connections. At 40 cities, waiting is still ~20% of elapsed time after local search, and individual legs can wait 3–7 hours at low-frequency stops. This is documented as a limitation, not solved.

---

## Project structure

```text
.
├── README.md
├── config.properties              # generation parameters and main-instance seed
├── pom.xml
├── mvnw / mvnw.cmd                # Maven wrapper
├── run.sh                         # compile + launch the CLI
├── CS201_2026_project/            # provided raw GeoNames data (unchanged)
├── data/
│   ├── processed/                 # cleaned cities, distances, edges, lines, report
│   └── gtfs/                      # GTFS-like stops/routes/trips/stop_times/transfers
├── docs/
│   ├── transit-tsp-plan.md        # full design, experiments, and rationale
│   └── network_map.svg
├── experiments/
│   ├── exp1_heuristics/
│   ├── exp2_representations/
│   ├── exp3_scalability/
│   └── exp4_classic_baseline/
└── src/
    ├── main/java/tdtsp/
    │   ├── Main.java              # CLI entry point
    │   ├── cli/                   # interactive route planner
    │   ├── network/               # graph, lines, schedules, GTFS-like I/O
    │   ├── routing/               # TD Dijkstra, CSA, time-expanded graph
    │   ├── tour/                  # NN, local search, classic baseline
    │   └── experiment/            # experiment runner and reporting
    └── test/
        ├── java/tdtsp/            # JUnit 5 tests
        └── resources/toy/         # toy timetable with hand-computed answers
```

---

## Data

**Source:** GeoNames `MY.txt` (provided in `CS201_2026_project/`). GeoNames supplies city nodes only — no edges, lines, or timetables.

**Cleaning:** drop coordinate outliers, deduplicate name + coordinate points, validate against a Malaysia bounding box, keep population-bearing populated places (`P/PPL`, `P/PPLA`, `P/PPLC`).

**Main instance:** the 40 largest places. They contain 19.12M of the 36.90M cleaned urban-place total (51.8%) and cover 13 state-level units, including Sabah, Sarawak, Penang, and the Klang Valley. See plan §4.3 for the subset justification.

**Synthetic network:** k-nearest-neighbour graph (`k = 3`) plus MST connectivity; 10 target lines (up to 12 with coverage repair), 4–8 stops each; bus under 200 km per segment, train otherwise; bus 60 km/h, train 100 km/h; bus headway 30 min, train 60 min; service 06:00–22:00; 5-minute transfer buffer; one node per city; same-stop transfers only.

**Generated main instance:** 40 cities, 83 graph edges, 11 lines (6 bus, 5 train), 566 trips, 3,430 stop times, 40 transfers, every city covered, service network connected.

### `config.properties`

| Key | Value | Meaning |
|---|---|---|
| `seed` | `43` | Network-generation seed (main instance) |
| `knn.k` | `3` | Nearest neighbours per city |
| `line.count` | `10` | Target number of lines (12 maximum with repair) |
| `line.minStops` / `line.maxStops` | `4` / `8` | Stops per line |
| `mode.thresholdKm` | `200` | Bus below, train at/above |
| `speed.busKmh` / `speed.trainKmh` | `60` / `100` | Segment speeds |
| `headway.busMin` / `headway.trainMin` | `30` / `60` | Departure headways |
| `service.firstMin` / `service.lastMin` | `360` / `1320` | 06:00–22:00 |
| `transfer.bufferMin` | `5` | Same-stop transfer buffer |

The seed controls the random line-generation walks (line starts, next stops, line lengths, coverage repair). It does not affect headways, speeds, or service hours. Seed 42 produces a disconnected service network at 40 cities; 43 is the first valid seed. The experiment folders use their own `seeds=1,2,3,4,5`.

---

## Algorithms

| Component | Approach |
|---|---|
| **Time-dependent Dijkstra** | Sorted departure arrays per directed edge + binary search; priority queue; objectives `ARRIVAL`, `WAIT`, `RIDE` |
| **CSA** | Connection list sorted by departure; one chronological scan |
| **Time-expanded Dijkstra** | Event nodes for `(trip, stop)` arrivals/departures; travel, wait, and transfer arcs |
| **Tour layer** | Nearest-neighbour, then insertion/2-opt with full re-evaluation of affected legs |
| **Classic baseline** | Complete haversine graph, NN + 2-opt, then evaluated on the real timetable |

Time-dependent, asymmetric costs make the standard 2-opt delta invalid, so the tour layer re-evaluates legs instead of using a static delta. Infeasible legs are penalized and reported, not treated as crashes.

---

## Tests

```bash
./mvnw test
```

15 tests across four classes:

- `NetworkGeneratorTest` — determinism, coverage, line/schedule validity
- `RoutersTest` — toy-network answers, cross-checks between TD Dijkstra, CSA, and time-expanded Dijkstra, secondary heuristics
- `TransitNetworkTest` — GTFS-like loading
- `TourTest` — NN/local search, classic tour, infeasibility handling

Tests that use `data/gtfs` expect the generated dataset to be present.

---

## Known limitations

- Synthetic schedules, speeds, headways, and transfer times — not real GTFS or real-time data.
- One node per city; no multiple stations, walking footpaths, or platform-level transfers.
- No time windows, real-time delays, or stochastic travel times.
- The tour is heuristic; only a single leg’s earliest arrival is optimal.
- 40 cities is the largest size the current 10–12 line / 4–8 stop design reliably generates across five seeds. Larger instances fail validation or become seed-dependent.
- GeoNames `P.PPL` splits metro areas into separate places, so the “40 cities” are 40 populated places and the summed population overcounts relative to official national figures.
- Runtime measurements vary between runs and machines; structural sizes and routing results are deterministic.

---

## Troubleshooting

- **Run from the repository root.** Relative paths (`data/gtfs`, `config.properties`) will not resolve elsewhere.
- **`java -cp target/classes ...` needs a compile first** (`./mvnw -q compile`).
- **`java -jar` is not configured** — there is no `Main-Class` manifest. Use `run.sh` or `java -cp target/classes tdtsp.Main`.
- **Experiments ignore `data/gtfs`** — they generate their own instances. Tests do read `data/gtfs`.
- **Seed 2 at 40 cities** is automatically retried with network seed 1002; this substitution is recorded in the experiment README.
- **JDK 25 may print an `Unsafe::objectFieldOffset` warning** from Maven/JVM internals. It is harmless; JDK 21 avoids it.
