# Network and schedule generation report

- Input: `cities_top40.csv` (40 cities)
- Seed: `43`
- k-nearest neighbours: `3`
- Lines: target `10`, actual `11` (4-8 stops each; 8-12 allowed)
- Mode threshold: `200.0 km` on the line's longest segment
- Speeds: bus `60.0 km/h`, train `100.0 km/h`
- Headways: bus `30 min`, train `60 min`
- Service window: `360-1320` minutes from 00:00
- Transfer buffer: `5 min` (same-stop transfers only)

## Method

- Graph: each city is connected to its `k` nearest neighbours by haversine distance; Kruskal MST edges are added only where the k-NN graph is disconnected.
- Lines: seeded random walks of 4-7 stops, leaving one slot for coverage repair. The first line starts anywhere; every later line starts on the existing service network, so the service graph stays connected. Uncovered cities are inserted next to a graph neighbour; if no line has capacity, a short repair line is added (total at most 12).
- Mode: `bus` when the line's longest segment is below `200.0 km`, otherwise `train`.
- Segment time: `ceil(distance / speed * 60)` minutes, with a minimum of 1 minute.
- Trips: both directions, first departure `360`, last `1320`, headways `30`/`60` minutes (bus/train).

## Counts

| Item | Value |
|---|---|
| Cities | 40 |
| Graph edges | 83 (82 kNN, 1 MST) |
| Lines | 11 (6 bus, 5 train) |
| Trips | 566 |
| Stop times | 3430 |
| Transfers | 40 |
| Trips arriving after 24:00 | 160 |

MST repair added 1 edge(s) to make the graph connected.

## Lines

| Line | Mode | Stops | Longest segment (km) | One-way (min) |
|---|---|---|---|---|
| L01 | train | 6 | 201.288 | 311 |
| L02 | train | 4 | 208.207 | 240 |
| L03 | train | 7 | 242.597 | 465 |
| L04 | bus | 4 | 103.544 | 166 |
| L05 | bus | 8 | 107.710 | 249 |
| L06 | bus | 7 | 101.963 | 162 |
| L07 | bus | 5 | 193.124 | 411 |
| L08 | bus | 6 | 193.124 | 611 |
| L09 | bus | 6 | 101.963 | 312 |
| L10 | train | 8 | 714.585 | 547 |
| L11 | train | 6 | 717.869 | 1160 |

## Validation

- Graph connected: yes
- Service line network connected: yes
- Every city covered by at least one line: yes
- Every line has 4-8 stops: yes
- Stop times strictly increasing within every trip: yes
- Headways match the mode: yes
- GTFS referential integrity: yes

## Known limitations

- The network is synthetic: GeoNames supplies nodes only, so lines, modes, speeds and headways are fabricated and documented here.
- One node per city; Klang Valley and Johor Bahru suburbs are distinct stops a few kilometres apart.
- The k-NN/MST graph may add long cross-sea edges to connect Borneo to the peninsula; rail across the South China Sea is a geometric abstraction, not a real route.
- 160 trips arrive after 24:00; times are integer minutes from 00:00 and may exceed 1440, as allowed by the plan.
- Transfers are same-stop only with a fixed 5-minute buffer; there are no walking links.
