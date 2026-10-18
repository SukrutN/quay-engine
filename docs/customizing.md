# Customizing a face

A face is one JSON object. `fixtures/lane.json` is the small worked case: six explicit slots, four SKUs, a chill zone, a locked stockpot, and a current layout that the proposal is measured against. `fixtures/forward-pick.json` is the other shape, a rectangular rack that the loader expands into slots.

Publish a fixture by adding the file and a line in `fixtures/index.txt`. The API name is that line. The CLI does not use the index; it reads the path you pass.

## Two ways to describe slots

Send `rack` or `slots`, not both.

A rack lists aisles, bays, and levels in dock order. The first aisle is index 0. The first bay is the bay nearest the dock mouth. Slot ids look like `A-01-L2`. Golden levels, the floor level, wide bays, and cube by level are fields on the rack. Return meters are out-and-back:

```
2 * ((dockToFirstAisle + aisleIndex * aislePitch) + (mouthToFirstBay + bayIndex * bayPitch))
```

An explicit slot carries its own `returnMeters`, `ergonomics` (`golden`, `floor`, or `beam`), and optional `zone`. Use this when the building is not a rectangle. `lane.json` is explicit: ambient and chill slots, with distances written on each slot.

## SKUs, zones, locks, current

Each SKU needs `id`, `name`, `picksPerDay`, `cubeLiters`, and `weightKg`. `zone` is optional.

Zone rule: a zoned SKU only fits a slot with the same zone. An unzoned SKU only fits an unzoned slot. Yogurt in `lane.json` is `chill`, so it can stand in `B-01-L2` or `B-01-L1` and nowhere else.

`locked` maps a SKU id to a slot id. That SKU is placed there and the search will not move it. In the lane, `LN-03` stays on `A-01-L1` because the stockpot is already in the only hole the floor team will accept.

`current` maps a slot id to a SKU id and must name every SKU exactly once. Replan still builds a fresh layout and uses current only for the comparison. Improve starts from current and keeps a move only when the objective falls. `mode` defaults to `replan` when omitted. The other value is `improve`.

## Policy

Every weight is required. The solver does not fill in a hidden default.

| Field | Role |
| --- | --- |
| `travelWeight` | Multiplies picks × return meters × the ergonomic factor |
| `wasteWeight` | Multiplies leftover cube fraction × picks. A high value pulls a small carton into a tight slot, including a beam slot |
| `affinityWeight` | Credit for co-picked pairs, decaying with aisle and bay steps |
| `goldenWeight` | Penalty for a SKU under `slowPickCutoff` sitting in a golden slot |
| `lightFloorWeight` | Penalty for a SKU at or under `lightKg` sitting on the floor |
| `heavyKg` / `maxLevelForHeavy` | Hard stop. A heavier SKU cannot use a higher level |
| `ergoGolden`, `ergoFloor`, `ergoBeam` | Travel multipliers for the three slot classes |
| `affinityDecayBays` | How fast the co-pick credit fades, in aisle-plus-bay steps |
| `maxPasses` | Relocate and swap passes after the first layout. Zero keeps that layout |

Affinities are `{ "skuA", "skuB", "strength" }` with strength in `(0, 1]`. One pair once. Direction does not matter.

## A smaller document

This is the same shape as the lane, cut to two slots, so the fields are easy to see. It is not a second published fixture.

```json
{
  "id": "example",
  "name": "Two slots",
  "mode": "replan",
  "slots": [
    {"id": "near", "aisle": "A", "aisleIndex": 0, "bay": 1, "bayIndex": 0, "level": 2, "cubeLiters": 20, "returnMeters": 6, "ergonomics": "golden"},
    {"id": "far", "aisle": "A", "aisleIndex": 0, "bay": 2, "bayIndex": 1, "level": 1, "cubeLiters": 20, "returnMeters": 22, "ergonomics": "floor"}
  ],
  "skus": [
    {"id": "fast", "name": "Fast tin", "picksPerDay": 40, "cubeLiters": 2, "weightKg": 1},
    {"id": "slow", "name": "Slow tin", "picksPerDay": 4, "cubeLiters": 2, "weightKg": 1}
  ],
  "policy": {
    "travelWeight": 1, "wasteWeight": 0, "affinityWeight": 0, "goldenWeight": 0, "lightFloorWeight": 0,
    "slowPickCutoff": 0, "lightKg": 0.5, "heavyKg": 12, "maxLevelForHeavy": 1,
    "ergoGolden": 1, "ergoFloor": 1.1, "ergoBeam": 1.3, "affinityDecayBays": 2, "maxPasses": 2
  }
}
```

Raise `travelWeight` and the fast tin's preference for `near` gets stronger. Set `maxLevelForHeavy` and a heavy SKU stops being offered the upper levels. That is the whole extension point: another building is another file.
