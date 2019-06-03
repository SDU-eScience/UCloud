# Indexing Service

Contains a searchable index of files. This is powered by reading the event
stream produced by the [storage-service](../storage-service). This index is
stored in Elasticsearch and is made available to other services to use. This
service should generally not be exposed directly to end-users.

## Services Provided

The indexing services stores a complete view of the SDUCloud's file system
structure. This view (the index) provides other services with a fast
interface to query various aspects of the file system.

The data stored inside of the index is only the metadata, more precisely, it
matches the fields of the `StorageFile`s which would normally be returned by
endpoints in [storage-service](../storage-service). Whenever changes are made
to the real file system [events](../storage-service/wiki/events.md) are
emitted, notifying other services about the change. The indexing service uses
these events to update its own view of the file system.

The service provides the following high level operations on the index:

1. Reverse lookup (Lookup a `StorageFile` given its ID)
2. Advanced query and statistics API

These APIs are only provided to other services. It is the job of these
services to ensure that file system permissions are respected.

## Staying in Sync with the File System

Events are not guaranteed to be emitted when a change occurs in the file
system. To account for this the indexing service will run a comparison script
periodically. This comparison script compares the complete index it stores
with the live view of the file system. If any part of the file system does
not match the [storage-service](../storage-service) will emit events
correcting the index.

This makes the indexing service consistent with the real file system
_eventually_. It is important to note that the indexing service doesn't
provide an always up-to-date view of the file system. Rather it provides a
view which will eventually be up-to-date with the real file system. This is
called [eventual
consistency](https://en.wikipedia.org/wiki/Eventual_consistency).
