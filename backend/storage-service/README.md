# Storage

.. toctree::
  :hidden:
  
  Files in UCloud <./wiki/files.md>
  ./wiki/sensitivity.md
  ./wiki/permissions.md
  ./wiki/quota.md
  ../share-service/README.md

This service implements the underlying file system of UCloud. All access to
the real data _must_ be verified by this service.  This file system provides 
operations which an end-user might be familiar with from other file systems.
The file system allows for users to read and write folders and files. You
can read more about files in UCloud [here](./wiki/files.md).

![](./wiki/storage_arch.png)

You can read more about the storage backend [here](./wiki/linuxfs/README.md).
