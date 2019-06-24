# accounting/compute

Performs accounting for compute time. See
[accounting-service](../accounting-service/README.md) for more details about
accounting.

This service keeps track of total compute time used. It does not currently
track number of resources used on each node. This information is provided by
the [`app-orchestrator`](../app-orchestrator-service) via an event stream.
This service saves it in a database to make it queryable following the
interface established by the [`accounting-service`](../accounting-service).
