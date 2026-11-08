# The putaway day format

A putaway day is one document: the reserve rack, the SKU catalog, the manifest, the state of the forward face, and how the slots open the day. Point the CLI at it and the engine answers under three policies — velocity, affinity, and a seeded random fork the first two are measured against.

```bash
java -jar cli/target/quay.jar putaway fixtures/putaway-day.json
```

The worked example is `fixtures/putaway-day.json` — Harbor & Lane, day 07: thirty receipt lines, 226 cartons, three aisles. Everything in it is invented.

## The document

| field                 | holds                                                                   |
| --------------------- | ----------------------------------------------------------------------- |
| `rack`                | aisles, bays, levels, level cubes, wide bays, golden levels, distances.  |
| `skus`                | the catalog: picks per day, carton cube, carton weight.                  |
| `affinities`          | co-pick pairs and their strengths, 0 to 1.                               |
| `uoms`                | built-up measures per SKU (`{"MG-15": {"case": 4}}`).                    |
| `receipts`            | the manifest: one line per receipt (`id`, `skuId`, `units`, `uom`).      |
| `bands`               | A/B cutoffs over picks per day.                                          |
| `weights`             | price of golden, slow-in-golden, fast-on-floor, ergonomics, mass.        |
| `forward`             | the face: on-hand cartons, thresholds, opening moves.                    |
| `startState`          | which slots open the day already full.                                   |
| `seed`                | the random fork's seed. The same seed draws the same plan.              |

Distances are honest street geometry: dock to the aisle mouth, then down the aisle. A slot's `returnMeters` doubles it for the walk out and back; a putaway pays one-way.

## What each policy decides

**Velocity** reads the manifest fastest first (picks per day descending, SKU id, then receipt id) and sends every line to the feasible slot with the lowest cost: one-way meters, doubled in price by ergonomics (golden 1.0, floor 1.15, beam 1.35), plus a premium every slow SKU pays to sit in golden and a small one fast SKUs pay to park on the floor. Ties within a tenth of a millimeter go to the lower slot id.

**Affinity** runs the same cost, then lets partners that landed *this day* pull the choice: each edge subtracts `weight × strength × √(picks × picks) × e^(−steps / decay)` where steps is bay distance to the partner's first landing. It pays a little walk to cluster the work that leaves together.

**The fork** is the control: the manifest in arrival order, the bays in one shuffle from `java.util.Random(seed)`. The engine replays the JDK generator exactly — a 48-bit LCG and the shuffle `Collections.shuffle` specifies — so the fork is reproducible across JVM and port.

## The forward face and the replenish loop

`forward.onHand` carries the cartons already at the face. Openings run first (a negative move is stock leaving for the cross-dock, refused if it would push a line below zero). Then every line below its band's minimum raises: the signal asks for `max − on hand` cartons and stays *armed* until a receipt brings the face to at least minimum plus hysteresis. Armed receipts route forward to max first; whatever remains goes to reserve. Everything is counted in cartons, so a `case` line converts through `uoms` before it is judged.

Day 07's trace: six raises, four quiet lines, and two SKUs that close the day still armed — one because the opening moved eight cartons out, one because no second receipt arrived.

## Slot-state integrity

The rules refuse, and the refusal is a directive row, never a silent fix:

- no negative stock anywhere,
- one SKU per slot (own-SKU top-offs are allowed and encouraged),
- no over-cube: cartons beyond the liters the level publishes are refused,
- mass: anything at `heavyKg` or more is refused above `maxLevelForHeavy`.

Day 07 runs with zero refusals; the ending state is the start state plus the day, verifiable slot by slot.

## Running it through the API

```bash
curl -s http://127.0.0.1:8091/v1/putaway -H 'content-type: application/json' -d '{"fixture":"putaway-day"}'
```

Send `{"day": {…}}` for an inline document. Failures are problem documents: `urn:quay:putaway:invalid-day` for a day the rules reject, `urn:quay:putaway:unknown-day` for an unpublished fixture. The OpenAPI document carries the report schema.

## Lineage

The policies are the classic trio the storage-assignment literature compares. Dedicated placement by velocity, correlated placement by affinity, and a randomized control:

- van den Berg, 1999 — a literature survey of storage and assignment planning.
- de Koster, 2007 — the order-picking reviews that frame directed versus undirected travel.
- Gu, Goetschalckx & McGinnis, 2007 — warehouse operation research reviews.
- Bartholdi & Hackman — *Warehouse & Distribution Science*, the open textbook.
