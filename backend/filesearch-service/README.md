# File Search Service

Presents a simplified API for file search. This is mostly powered by the
[indexing-service](../indexing-service/README.md).

Permissions are enforced by ensuring we only search for files we are the
owner of.

![](./wiki/FilesearchFlow.png)

The following query parameters can be used to search the file system:
- File name
- File extension
- File types (directory or file)
- Time range for creation
- Time range for last modification
- Sensitivity
