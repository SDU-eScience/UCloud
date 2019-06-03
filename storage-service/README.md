# The SDUCloud File System (storage-service)

This service implements the underlying file system of SDUCloud. All access to
the real data _must_ go through this service.

The file system can use a variety of different backends (all implemented in
this service). The only current implementation is `LinuxFS`, this
implementation can use any mounted linux file systems. More information about
our `LinuxFS` implementation can be found [here](./wiki/linuxfs/README.md).

All backends are required to notify interested services about all updates in
the file system. The events are published to the event stream service
provided by [service-common](../service-common). This is how
[indexing-service](../indexing-service) is able to implement a complete index
of the file system. You can read more about this system
[here](./wiki/events.md).

Additionally, the storage-service also provides unmanaged storage which can
import data from the managed file system. This unmanaged storage is
guaranteed to live next to the managed storage, this makes moving data back
and forth very efficient. This is provided through the workspace feature, you
can read more about it [here](./wiki/workspaces.md).
