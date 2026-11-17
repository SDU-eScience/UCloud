# Accounting scenarios

Use the normal core executable to generate isolated accounting data in a running
development environment:

```sh
ucloud accounting-scenarios list
ucloud accounting-scenarios run overlapping-grants
ucloud accounting-scenarios run all --out /tmp/accounting-scenarios
```

Run these commands in the development core container. For a local executable, set
`DEV_MODE=true`. Set `UCLOUD_ACCESS_TOKEN` to an administrator access token before
running a scenario. Alternatively, supply `--token`. Use `--url` to select a
development core other than `http://localhost:8080`.

The CLI refuses to run scenarios outside development. The server independently
checks development mode, and registers its administrator-only scenario endpoint
only in development. Setting `DEV_MODE` on the client does not enable the endpoint
on a production server.

## Inspect a scenario

Each invocation creates new projects, a new product category under `usagegen`, and
real grants and allocations. It does not modify existing projects or allocations.
The command prints the funding project ID. Select that project in the normal
`/app/allocations` page. Refresh the project list or session if the new project is
not visible yet.

The runner replays allocation and usage events through the accounting engine.
Replay is serialized against the regular accounting scanner. Only the new
scenario bucket is transitioned at historical timestamps; the system clock is
not changed. Normal scanning resumes after replay, and the generated state is
persisted at the current time.

Each checkpoint checks the accounting invariants and compares the resulting
amounts with explicit expectations in
`pkg/accounting/accounting_scenario_definitions.go`. A mismatch makes the command
exit unsuccessfully. Expectations are in raw accounting units, not display units.

The output directory contains `checkpoints.json`, which includes:

- Expected and actual amounts at each checkpoint.
- Wallet API snapshots for every generated funding parent.
- A fixed timestamp and project information for rendering each snapshot.
- The project, category, grant, and allocation IDs created by the run.
- Failure details if replay or an expectation failed.

Open `/app/playground/allocations` and load this file to inspect the checkpoints.
This replaces the handwritten allocations demo. It uses the same normalization,
filters, overview, table, and export as the normal page. The viewer checks the
frontend's normalized raw amounts against the expectations and reports mismatches.
Checkpoint actions cannot change live allocations.

Runs are additive. There is no automatic cleanup or broad reset command. Keep the
resource manifest to identify the data belonging to a run.

## Interpret the amounts

For consumption products, **Used** means all charges attributed to the funding
relationship, including charges assigned to retired allocations. It does not mean
consumption during the dates of an allocation.

Retired allocations have recorded attribution. Active charges remain pooled across
active allocations on the relationship. The frontend does not invent a split.
**Active balance** is active quota minus active charges; it is not guaranteed
usable headroom when upstream funding is constrained.

For capacity products, Used is the current capacity attributed to the relationship.
Historical retirement usage is not added to it.

Period and status filters select complete funding relationships. Their totals and
allocation histories are not truncated to the selected dates or status.
Expired and Upcoming match whole logical allocations, not individual slices of an
ongoing schedule. Expiring matches currently active quota ending within 30 days.

## Add a scenario

Add a named definition with chronological allocation, charge, and checkpoint steps.
Step dates are day offsets from the run timestamp. `AtMillis` can place a checkpoint
immediately before an expiry boundary. Matching nonempty `Package` values group
allocations from the same parent to the same recipient into one release schedule.

Specify expected amounts directly. Do not derive them using the frontend model or
the backend projection being checked. Include checkpoints on both sides of the
transition that the scenario explains.
