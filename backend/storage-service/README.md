:orphan:

# File Storage Service

This service implements the underlying file system of UCloud. All access to
the real data _must_ be verified by this service.  This file system provides 
operations which an end-user might be familiar with from other file systems.
The file system allows for users to read and write folders and files. You
can read more about files in UCloud [here](backend/storage-service/wiki/files.html).

.. figure:: /backend/storage-service/wiki/storage_arch.png
   :align: center
   :width: 80%

## Backends

The file system can use a variety of different backends (all implemented in
this service). Each backend implement a common set of low-level operations,
e.g. reading and writing files.

The table below summarizes the list of supported backends:

| **Name**                  | **Description**                                                                                                  |
|---------------------------|--------------------------------------------------------------------------------------------------------------|
| [LinuxFS](backend/storage-service/wiki/linuxfs/README.html) | Mounts a POSIX compatible distributed filesystem. The implementation depends on Linux specific system calls. |

