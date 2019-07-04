# The SDUCloud File System (`storage-service`)

This service implements the underlying file system of SDUCloud. All access to
the real data _must_ go through this service. This means that any other
micro-service which need to consume FS services will go through this service.
This file system provides operations which an end-user might be familiar with
from other file systems. The file system allows for users to read and write
folders and files. You can read more about files in SDUCloud
[here](./wiki/files.md).

![Storage Architecture](wiki/storage_arch.png)

__Figure:__ The `storage-service` serves as the point-of-contact for all
services which needs to consume FS services. Services can be notified of
changes by listening to [events](#storage-events). The consumers listed are
just examples of how the service is used internally.

## Backends

The file system can use a variety of different backends (all implemented in
this service). Each backend implement a common set of low-level operations,
e.g. reading and writing files. The backends are also required to publish an
[event stream](#storage-events) describing the changes in the filesystem.

The table below summarizes the list of supported backends:

| Name                      | Description                                                                                                  |
|---------------------------|--------------------------------------------------------------------------------------------------------------|
| [LinuxFS](./wiki/linuxfs) | Mounts a POSIX compatible distributed filesystem. The implementation depends on Linux specific system calls. |

## Storage Events

All backends are required to notify interested services about all updates in
the file system. The events are published to the event stream service
provided by [service-common](../service-common). This is how
[indexing-service](../indexing-service) is able to implement a complete index
of the file system. You can read more about this system
[here](./wiki/events.md).

## Other Features

Additionally, the storage-service also provides unmanaged storage which can
import data from the managed file system. This unmanaged storage is
guaranteed to live next to the managed storage, this makes moving data back
and forth very efficient. This is provided through the workspace feature, you
can read more about it [here](./wiki/workspaces.md).
