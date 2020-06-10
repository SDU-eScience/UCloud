# Accounting/Storage Service

Performs accounting for storage. See
[accounting-service](../accounting-service/README.html) for more details about
accounting.

This service currently only keeps track of each user's total storage. This is
implemented via a cronjob which scans the storage of all
[users](../../auth-service.html). This service queries
[indexing-service](../indexing-service/README.html) for information about total usage.
