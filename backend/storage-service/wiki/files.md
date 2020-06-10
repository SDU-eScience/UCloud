# Files

We model file system's structure after a typical unix-like file system. In
this document we describe what files are in the context of UCloud.

The system only supports **files**, **directories**. There is no support for links.

The file system provides a 'home' folder for every user. Each user has a home 
folder in `/home.` The user "DanThrane#1234"should have a folder in
`/home/DanThrane#1234`. Projects have a folder in `/projects` but otherwise act
in a similar fashion.

Files are only identified by their path. There is no unique identifier for a file.

All files keep a timestamp for their their most __recent modification__.

For accounting purposes we also keep track of the **size**. This size
measures the file in bytes. This field is meaningful only for files. The size
of a directory is **not** equal to the sum of its children's size properties.
The directory only counts the amount of bytes needed to keep track of its
children.

You can read about file ownership and permissions `here <permissions.html>`__.

All files have an associated __sensitivity level__. You can read more about
file sensitivity `here <sensitivity.html>`__.

## Properties

The table below summarize the properties of a file.

| **Property**                 | **Summary**                                                                     |
|--------------------------|-----------------------------------------------------------------------------|
| `fileType`               | The type of file (`FILE`/`DIRECTORY`)                                       |
| `path`                   | The path to reach the file starting from the root. Uses `/` as a separator. |
| `owner`                  | The username of the file owner                                              |
| `modifiedAt`             | Timestamps for the most recent modification                                 |
| `size`                   | The size of file in bytes                                                   |
| `acl`                    | The access control list of this file                                        |
| `sensitivityLevel`       | The sensitivity level of this file (potentially inherited)                  |
| `ownSensitivityLevel`    | The sensitivity level of this file                                          |
