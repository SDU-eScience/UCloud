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
   
You can read more about the storage backend [here](backend/storage-service/wiki/linuxfs/README.html).
