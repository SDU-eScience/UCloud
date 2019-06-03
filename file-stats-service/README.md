# file-stats-service

Presents UI friendly statistics about the file system. This system is mostly
relying on data from [indexing-service](../indexing-service) and
[storage-service](../storage-service).

Permissions are enforced by ensuring that we only collect stats about files
that we own.