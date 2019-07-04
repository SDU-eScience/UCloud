# accounting/storage

Performs accounting for storage. See
[accounting-service](../accounting-service/README.md) for more details about
accounting.

This service currently only keeps track of each user's total storage. This is
implemented via a cronjob which scans the storage of all
[users](../auth-service). This service queries
[`indexing-service`](../indexing-service) for information about total usage.
