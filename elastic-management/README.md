# elastic-management

Manages core settings in Elasticsearch.

Additionally it is run as a cronjob to reindex and delete old
[auditing](../service-common/wiki/auditing.md) information.

The indexes of old audit logs are
[reindexed](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html)
weekly.

Audit entries which have expired are also deleted. This is based on the
`expiry` field on audit entries.