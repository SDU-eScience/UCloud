# indexing-service

Contains a searchable index of files. This is powered by reading the event
stream produced by the [storage-service](../storage-service). This index is
stored in Elasticsearch and is made available to other services to use. This
service should generally not be exposed directly to end-users.