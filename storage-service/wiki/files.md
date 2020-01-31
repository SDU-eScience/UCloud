# Files in UCloud

We model file system's structure after a typical unix-like file system. In
this document we describe what files are in the context of UCloud.

The system only supports **files**, **directories**. There is no support for links.

The file system provides a 'home' folder for every user. Each user has a home 
folder in `/home.` The user "DanThrane#1234"should have a folder in
`/home/DanThrane#1234`. Projects have a folder in `/projects` but otherwise act
in a similar fashion.


Files have a **unique identifier**. The unique identifier should never be
re-used for a single file. The unique identifier tracks the file as it is
being changed. When a user moves a file or updates it the unique identifier
should remain the same. If a user deletes a file then no new file should
re-use its ID.

All files keep a timestamp for their __creation__ and their most __recent
modification__.

For accounting purposes we also keep track of the **size**. This size
measures the file in bytes. This field is meaningful only for files. The size
of a directory is **not** equal to the sum of its children's size properties.
The directory only counts the amount of bytes needed to keep track of its
children. For soft symbolic links we only count the bytes needed to keep
track of its target.

You can read about file ownership and permissions [here](./permissions.md).

All files have an associated __sensitivity level__. You can read more about
file sensitivity [here](./sensitivity.md).

## Properties

The table below summarize the properties of a file.

| Property                 | Summary                                                                     |
|--------------------------|-----------------------------------------------------------------------------|
| `fileType`               | The type of file (`FILE`/`DIRECTORY`)                                       |
| `path`                   | The path to reach the file starting from the root. Uses `/` as a separator. |
| `owner`                  | The username of the file owner                                              |
| `fileId`                 | A unique identifier for this file                                           |
| `createdAt`/`modifiedAt` | Timestamps for the creation event and most recent modification              |
| `size`                   | The size of file in bytes                                                   |
| `acl`                    | The access control list of this file                                        |
| `sensitivityLevel`       | The sensitivity level of this file (potentially inherited)                  |
| `ownSensitivityLevel`    | The sensitivity level of this file                                          |
