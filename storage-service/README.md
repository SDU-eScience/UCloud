# The UCloud File System (`storage-service`)

This service implements the underlying file system of UCloud. All access to
the real data _must_ be verified by this service.  This file system provides 
operations which an end-user might be familiar with from other file systems.
The file system allows for users to read and write folders and files. You
can read more about files in UCloud [here](./wiki/files.md).

![Storage Architecture](wiki/storage_arch.png)

## Backends

The file system can use a variety of different backends (all implemented in
this service). Each backend implement a common set of low-level operations,
e.g. reading and writing files.

The table below summarizes the list of supported backends:

| Name                      | Description                                                                                                  |
|---------------------------|--------------------------------------------------------------------------------------------------------------|
| [LinuxFS](./wiki/linuxfs) | Mounts a POSIX compatible distributed filesystem. The implementation depends on Linux specific system calls. |

