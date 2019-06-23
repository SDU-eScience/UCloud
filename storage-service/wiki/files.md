# Files in SDUCloud

We model file system's structure after a typical unix-like file system. In
this document we describe what files are in the context of SDUCloud.

The system only supports **files**, **directories**, and **soft symbolic
links**. There is no support for hard links.

> Users cannot create soft links. We expect that soft links will become
deprecated at a later time.

The file system provides a 'home' folder for every user. In the case of
[projects](../../project-service) this is a shared folder among all project
members. Each user has a home folder in `/home.` The user "DanThrane#1234"
should have a folder in `/home/DanThrane#1234`.

Each file has exactly one **canonical path**. It is possible to get the
canonical path of a file by resolving all symlinks in the path. The canonical
path is a unique reference to the file at a given location.

We can find the **owner** of a file from its canonical path. A user owns all
the files placed in their home directory. If Alice shares the directory
`/home/alice/shared` with Bob then all files will remain owned by Alice. This
includes all the files Bob creates. As a result Alice will not lose access to
the files created by Bob when Bob leaves the share.

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

The platform controls access to files via **access control lists**. End users
don't have direct access to the ACL. Instead, they use higher-level features
such as [shares](../../share-service) and [projects](../../project-service).

All files have an associated __sensitivity level__. You can read more about
file sensitivity [here](./sensitivity.md).

## Properties

The table below summarize the properties of a file.

| Property                 | Summary                                                                     |
|--------------------------|-----------------------------------------------------------------------------|
| `fileType`               | The type of file (`FILE`/`DIRECTORY`/`LINK`)                                |
| `path`/`canonicalPath`   | The path to reach the file starting from the root. Uses `/` as a separator. |
| `owner`                  | The username of the file owner                                              |
| `fileId`                 | A unique identifier for this file                                           |
| `createdAt`/`modifiedAt` | Timestamps for the creation event and most recent modification              |
| `size`                   | The size of file in bytes                                                   |
| `acl`                    | The access control list of this file                                        |
| `sensitivityLevel`       | The sensitivity level of this file (potentially inherited)                  |
| `ownSensitivityLevel`    | The sensitivity level of this file                                          |
