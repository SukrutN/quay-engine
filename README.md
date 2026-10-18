# Quay slotting

Quay slots one forward pick face. You send the slots (or a rectangular rack), the SKUs, the co-pick affinities, and the objective weights. It returns a placement, the travel and fit readings, and a comparison when you include the layout that is on the floor today.

It does not keep inventory, orders, labor standards, or a user directory. Nothing is stored after the response. The [showcase site](https://github.com/SukrutN/quay) is a visual walkthrough of one fictional face. The figures on that site belong to the showcase. This service prints the figures for the document you give it.

## Run the tests

Java 21 and Maven 3.9 or newer.

```bash
mvn -B verify
```

## Slot a face from a file

```bash
mvn -B -DskipTests package
java -jar cli/target/quay.jar place fixtures/lane.json
java -jar cli/target/quay.jar place fixtures/forward-pick.json
```

`lane.json` is a chill corner with an incumbent layout, a locked stockpot, and four SKUs. `forward-pick.json` is a 160-slot rack and thirty cartons. Both are data. The solver has no building of its own.

## Serve the API

```bash
java -jar api/target/quay-api.jar
```

The process listens on port 8091. `PORT` changes it.

```bash
curl -s http://127.0.0.1:8091/health
curl -s http://127.0.0.1:8091/v1/slotting -H 'content-type: application/json' -d '{"fixture":"lane"}'
```

An unknown fixture name is HTTP 400 with `application/problem+json`. The body is a problem document, not a made-up layout. Send an inline face under `face` when you do not want a published fixture. The OpenAPI document is at `/v1/openapi.yaml`.

## Where to read next

- [docs/architecture.md](docs/architecture.md) — why the domain is separate, and why there is no database
- [docs/customizing.md](docs/customizing.md) — the file format, walked through `fixtures/lane.json`
- [docs/adr/0001-scored-placement.md](docs/adr/0001-scored-placement.md) — why this search, and the size of face it is for
