# file-favorite-service

Manages the favorite status of user files.

This is accomplished by using a mapping between a file's ID and a username.

When a user requests a file to be favorited we will perform a lookup on the
path to map it to its id.

Favoriting information is attached to many file related calls. This
information is merged via the [file gateway](../file-gateway-service). The file
gateway will query this service in bulk by file ID.

This service handles file deletions and invalidations by listening to the
[events](../storage-service/wiki/events.md) from the storage service. When a
file is deleted or invalidated we will remove it from the database.

Listing all favorited files relies on performing a reverse lookup from file
ID to `StorageFile`. This functionality is provided by the
[indexing-service](../indexing-service).