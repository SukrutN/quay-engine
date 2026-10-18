# ADR 0001: Scored placement and a short improvement pass

## Status

Accepted.

## Context

A forward pick face is one area: hundreds of SKUs, a few thousand slots, one request. Planners already have a slot file, a pick forecast, and sometimes the layout that is on the floor. They need a deterministic assignment they can explain, with cube, weight, zone, and co-pick pressure they can turn up or down per building.

A mixed-integer solver can prove a tighter optimum. It also adds a license, a timeout policy, and an answer that is hard to narrate when two weights change. The faces this service is for finish in memory with a direct search.

## Decision

Place locked SKUs, then the rest by picks per day. Give each SKU the feasible open slot with the lowest weighted cost. Ties keep the lower slot id. Then run at most `maxPasses` sweeps of relocates and swaps, accepting a move only when the same objective falls.

The cost is travel (picks, return meters, ergonomic multiplier), cube waste, a penalty for slow SKUs in the golden band, a penalty for light SKUs on the floor, and a credit for co-picked pairs that decays with aisle and bay steps. Hard limits stay hard: cube, zone, and the heavy level cap. Every coefficient is a field on the document.

`replan` ignores the current layout except as a comparison. `improve` starts from that layout, which is the right call when a building will not accept a full reshuffle.

## Consequences

The result is repeatable and local to the relocate/swap neighborhood. It is not a proof that no better assignment exists. A face that needs a global proof, or a network of buildings, is a different job.

`maxPasses` bounds the work. Zero passes returns the constructive layout or the current one, unchanged. Callers who want a different tradeoff change weights. They do not fork the search.
