# audit-ingestion-service

A service ingesting [audit](../service-common/wiki/auditing.md) information
from event streams and storing it in elasticsearch. This is required for
[alerts](../alerting-service/README.md) to function correctly. Indexes are
further managed over time by [elastic management](../elastic-management).

We create an elastic index for each message type daily. The index uses the
following format: `http_logs_$requestName-YYYY.MM.dd`.