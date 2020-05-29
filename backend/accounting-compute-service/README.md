:orphan:

# Accounting/Compute Service

Performs accounting for compute time. See
[accounting-service](backend/accounting-service/README.html) for more details about
accounting.

This service keeps track of total compute time used. It does not currently
track number of resources used on each node. This information is provided by
the [app-orchestrator](app-services.html) via an event stream.
This service saves it in a database to make it queryable following the
interface established by the [accounting-service](backend/accounting-service/README.html).
