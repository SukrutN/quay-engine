# Architecture

Four modules, one direction of dependency.

`quay-domain` holds the face, the objective, and the search. It has no JSON parser, no HTTP types, and no file API. A test can build a `PickFace` in memory and slot it.

`quay-application` parses a face document, loads the published fixtures, times one run, and formats the reading the CLI prints. It calls the domain. It does not know about HTTP status codes.

`quay-api` is the HTTP adapter: `POST /v1/slotting`, `GET /health`, and the OpenAPI document. Failures become problem details. Request lines are JSON logs with method, path, status, and elapsed time.

`quay-cli` is the same use case against a file path.

The domain stays free of the adapters so a change to the wire format cannot silently change who gets which slot. Both adapters call `SlotFace`.

## What a run does

Replan places locked SKUs first, then the rest in descending picks per day. SKU id breaks ties. Each SKU takes the feasible open slot with the lowest objective, and the lower slot id wins a tie. Improve starts from the current assignment instead of an empty face. Either way, a bounded pass then relocates or swaps a SKU when that move lowers the objective. A pass that finds nothing stops. `maxPasses` of zero keeps the constructed or current layout.

The objective is the sum of travel, cube waste, slow-in-golden, and light-on-floor, minus affinity credit. The weights and the hard limits (heavy level cap, zones, locks) come from the document. Travel reported to the caller is physical pick-meters, out and back, before those weights.

## Why there is no database

A face document is the whole input. The placement is a function of that document. The service does not remember a previous run, a tenant, or a user. Callers who want history keep the response. Adding a database would invent a system of record this product does not own.

## Why the showcase site is a separate repository

The site is a visual walkthrough of one fictional floor. This service is the calculator you point at other floors: different racks, explicit slot lists, zones, locks, and incumbent layouts. The site's on-screen percentages are that presentation. They are not a contract of this solver.
