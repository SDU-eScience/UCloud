# Indexing Service

Contains a searchable index of files. This is powered by reading the event
stream produced by the [storage-service](../storage-service). This index is
stored in Elasticsearch and is made available to other services to use. This
service should generally not be exposed directly to end-users.

## Services Provided

The indexing services stores a complete view of the UCloud's file system
structure. This view (the index) provides other services with a fast
interface to query various aspects of the file system.

The data stored inside of the index is only a subset of the metadata.
