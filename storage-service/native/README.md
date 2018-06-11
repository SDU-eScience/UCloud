# Storage Service: Native Component

<!-- TOC depthTo:3 -->

- [Storage Service: Native Component](#storage-service-native-component)
    - [Introduction](#introduction)
    - [Basic Protocol](#basic-protocol)
    - [Commands](#commands)
        - [`copy`: Copies a single file](#copy-copies-a-single-file)
        - [`copy-tree`](#copy-tree)
        - [`move`](#move)
        - [`list-directory`](#list-directory)
        - [`list-favorites`](#list-favorites)
        - [`delete`](#delete)
        - [`write`](#write)
        - [`tree`](#tree)
        - [`make-dir`](#make-dir)
        - [`get-xattr`](#get-xattr)
        - [`set-xattr`](#set-xattr)
        - [`delete-xattr`](#delete-xattr)
        - [`list-xattr`](#list-xattr)
        - [`stat`](#stat)
        - [`read`](#read)
    - [Output Formats](#output-formats)
        - [Boolean](#boolean)
        - [File Attributes](#file-attributes)

<!-- /TOC -->

## Introduction

The storage service uses CephFS mounted as a unix file system as its storage
back-end. Authorization in the storage service is enforced by running each
file operation on a unix user that is associated with the SDUCloud user
making the request. This is executed by starting a `sudo` process. As a
result the unix user that runs the storage service must be allowed to switch
(using `sudo`) to any of the SDUCloud users. On a normal server this should
_not_ be implemented by letting it run as root. Instead a group should be
made for all SDUCloud users and allow the storage service only to switch to
those.

The native component of the storage service is responsible for performing the
actual communication with CephFS. It is the process that runs as the other
user, in other words, it is the program that is started with the `sudo`
process:

```bash
sudo -u $USER ceph-interpreter $CBOUNDARY_TOKEN $SBOUNDARY_TOKEN
```

Starting a new process is a fairly expensive procedure and comes with quite a
lot of overhead. On a normal machine this can be upwards of 15ms. As a result
we do not wish to start multiple processes, but rather do as much work as
possible in a single.

TODO Some more stuff here, rewrite the above also.

## Basic Protocol

The `storage-service` (client) communicates with the `ceph-interpreter`
(server) using a simple IPC protocol implemented on top of the standard
streams (`stdin`, `stderr`, `stdout`).

All messages are separated by a _boundary token_ both the client and the
server has a boundary token. These are passed on the command line interface.

__Example:__ All messages (from either side) is terminated with a boundary token

```txt
 in$ read
 in$ /tmp/hello
 in$ $CBOUNDARY_TOKEN

out$ 13
out$ Hello, World!
out$ $SBOUNDARY_TOKEN

... MORE MESSAGES ...
```

Both the client and server has APIs that make it relatively easy to deal with
these tokens.

Two types of message arguments are supported:

  1. Basic arguments (`string`, `int`, `double`, `boolean`): A single line of
  text (separated by `\n`).
  2. Blob arguments (`blob`): A binary stream of data

## Commands

### `copy`: Copies a single file

#### Description

Copies a single file.

The file available at `from` is copied to the location `to`. Both paths
should be the absolute paths and must refer to the exact location files are
to be placed. This means that it is not possible to copy a file `/a/b/c` into
the folder `/home/me` by passing the values: `from="/a/b/c" ; to="/home/me"`,
but should instead be given as: `from="/a/b/c" ; to="/home/me/c"`.

Depending on the file type of `from` the following will happen:

- File: The file is read and the bytes are written to the new location
- Directory: A new directory is created at the `to` location. The contents of
  `from` is _not_ copied, instead use `copy-tree`.
- Link: The link is fully resolved and the file/directory is copied according
  to above semantics.

File attributes are _never_ copied.

#### Arguments

  1. `from: string`
  2. `to: string`

#### Output

On success the following file attributes are emitted for newly created file:

- `FILE_TYPE`
- `IS_LINK`
- `INODE`
- `OWNER`
- `GROUP`
- `PATH`

Following this is a single line containing the numeric status code. This code
is always returned.

#### Errors

Returns 0 on success otherwise a negative number, mostly reusing `errno`.

- If `to` already exists `EEXIST` is returned
- On IO errors the relevant `errno` is returned
- If `from` does not exist `ENOENT` is returned
- If the user does not have permission to read `from` or write `to` then
  `EACCESS` is returned.

### `copy-tree`

Copies a complete file system tree.

This is equivalent to running `copy` on the output of `tree`.

If new files are created while copying those are not included in the copy.
As a result it is legal to copy a tree into itself, as this will not cause
a feedback loop.

#### Arguments

  1. `from: string`
  2. `to: string`

#### Output

The output of each copied file will match that of `copy`. Each file copied
will first be printed before the status code is returned.

#### Errors

The status code will be 0 if _any_ of the file copies are successful, otherwise
`-1` will be returned.

### `move`

Moves a file

#### Arguments

  1. `from: string`
  2. `to: string`

#### Output

The following file attributes are emitted for every single file affected by
this move (if the moved file is a directory the entire sub-tree is affected).

- `FILE_TYPE`
- `INODE`
- `PATH` (The new one)

Following this the status code is printed on its own line.

#### Errors

Returns 0 on success, otherwise a negative number.

- If `to` already exists `EEXISTS` is returned
- On IO errors the relevant errno is returned.
- If the `to` is a child of `from`, `EINVAL` is returned
- `EACCESS` is returned when the user does not have enough permissions.

### `list-directory`

Lists the files of a directory.

#### Arguments

  1. `directory: string`

#### Output

For every file contained in `directory` the following attributes are emitted:

- `FILE_TYPE`
- `TIMESTAMPS`
- `OWNER`
- `SIZE`
- `SHARES`
- `SENSITIVITY`
- `IS_LINK`
- `ANNOTATIONS`
- `INODE`
- `PATH`

#### Errors

Returns 0 on success, otherwise a negative number.

- If `directory` does not exist then `ENOENT` is returned
- If the user is not allowed to read the directory `EACCESS` is returned
- On IO errors the relevant code is returned
- `EINVAL` if `directory` is not a directory

### `list-favorites`

### `delete`

Deletes a file tree.

If new files are inside the tree while this runs those files will be deleted
as well. As a result all files are guaranteed to be removed, unless permissions
occur or IO errors occur.

As many files as possible will be deleted, a permission error will not stop
the deletion of files.

If `file` is a link, then the link itself will be deleted and not the target.

#### Arguments

- `file: string`

#### Output

For each file deleted (and only the deleted ones) the following attributes
will be emitted:

- `FILE_TYPE`
- `INODE`
- `OWNER`
- `GROUP`
- `PATH`

Following this the status code is emitted.

#### Errors

Returns 0 if any file was deleted, otherwise a negative number.

### `write`

Performs an unsized write operation to a file.

If an existing file is replaced (with `replace_existing`) then the file is
first truncated and then the new contents is written to it. This means that the
original file ID is preserved.

#### Arguments

1. `file: string`
2. `replace_existing: boolean`
3. `data: blob`

#### Output

The following file attributes will be emitted for the file written to:

- `INODE`
- `SIZE`
- `PATH`

Following this the status code is printed.

#### Errors

Returns 0 on success otherwise a negative number.

- `EEXISTS` is returned if the file already exists and `replace_existing` is
  false.
- `EINVAL` if the file already exists and `replace_existing` is true but is a
  directory.
- `EACCESS` if the user does not have permissions.
- `ENOENT` if the corresponding path does not exist (i.e. one of the parent
  directories do not exist)
- Relevant errno on IO errors

### `tree`

#### Arguments

1. `path: string`
2. `data_override: int`

#### Output

Outputs, for each file in the sub-tree, either the data specified by
`data_override` or the following defaults if `data_override` is `0`.
A value _must_ be provided for `data_override`.

Defaults:

- `FILE_TYPE`
- `UNIX_MODE`
- `OWNER`
- `GROUP`
- `SIZE`
- `TIMESTAMPS`
- `INODE`
- `CHECKSUM`
- `PATH`

Always followed by a status code.

#### Errors

0 on success, otherwise uses errno.

- `ENOENT` if path does not exist
- Relevant errno on IO errors
- `EINVAL` if path is not a folder
- `EACCESS` if no items were returned from this command
  - If some items cannot be accessed then those are silently ignored

### `make-dir`

Creates a directory. Parent directories are not created automatically.

#### Arguments

1. `path: string`

#### Output

The following attributes are emitted for the newly created directory.

- `FILE_TYPE`
- `INODE`
- `OWNER`
- `GROUP`
- `PATH`
- `UNIX_MODE`

This is always followed by a status code.

#### Errors

0 on success, otherwise uses errno.

- `EEXIST` if an item at this path already exists
- `ENOENT` if parent directories do not exist
- Relevant errno on IO errors
- `EACCESS` if the user is not allowed to create a directory

### `get-xattr`

Returns a single extended attribute on a file.

#### Arguments

1. `path: string`
2. `attribute: string`

#### Output

The attribute is printed as a single line of ASCII.

This is followed by the status code (always).

#### Errors

0 on success, otherwise uses errno.

- `ENOENT` if file does not exist or the attribute does not exist
- `EACCESS` if the user is not allowed to see the file
- Relevant errno on IO errors

### `set-xattr`

Sets a single extended attribute on a file.

The attribute will be overridden if it already exists.

The `value` must be a single line. Additional lines _may_ cause an error
to be returned, or they _may_ simple be discarded.

#### Arguments

1. `path: string`
2. `attribute: string`
3. `value: string`

#### Output

This is followed by the status code (always).

#### Errors

0 on success, otherwise uses errno.

- `ENOENT` if file does not exist
- `EACCESS` if the user is not allowed to update attributes on the file
- Relevant errno on IO errors

### `delete-xattr`

Deletes a single extended attribute from a file.

_NO_ errors are returned if the attribute does not exist.

#### Arguments

1. `path: string`
2. `attribute: string`
3. `value: string`

#### Output

This is followed by the status code (always).

#### Errors

0 on success, otherwise uses errno.

- `ENOENT` if file does not exist
- `EACCESS` if the user is not allowed to updated attributes on the file
- Relevant errno on IO errors

### `list-xattr`

Lists attribute names.

#### Output

Each attribute is new line-separated. This is always followed by a status
code.

#### Errors

0 on success, otherwise uses errno.

- `ENOENT` if file does not exist
- `EACCESS` if the user is not allowed to updated attributes on the file
- Relevant errno on IO errors

### `stat`

Returns information about a single file. Symbolic links are _not_ followed.

#### Arguments

1. `path: string`
2. `data_override: int`

#### Output

The data requested or the following defaults:

TODO

### `read`

Reads data from a file.

#### Output

First line of output is the status code, this will have been after permissions
and such as been checked. Following this is another line which contains the
size of the file. Following is that many bytes of data. For performance the
client should pre-clear that many bytes and not search for an EOF token.

## Output Formats

### Boolean

Can be either true or false. These are _always_ represented as a `'1'` or `'0'`.

### File Attributes

#### `FILE_TYPE`: File Type

Describes the type of file.

Can be one of:

- `'D'`: Directory
- `'F'`: File

For symbolic links this value will _always_ resolve to the type of the fully
resolved link. See `IS_LINK` for more information. If the symbolic link is a
link to another link this will still function correctly.

#### `INODE`: Unique File ID (inode)

Describes a unique ID of the file. For this implementation it is always the
inode.

This ID should _never_ be parsed as an integer (despite it being one), but
should rather be considered an opaque token.

#### `SIZE`: File Size

Describes the size of the file in bytes.

Always written as a signed 64-bit integer encoded in ASCII. The value is
signed to support typical clients (i.e. JVM based Kotlin, which doesn't have
support for unsigned integers).

#### `SHARES`: Shares (ACL)

#### `ANNOTATIONS`: Annotations

Contains annotations for a file.

An annotation is (currently) a single character describing an attribute the
file has. This could for example be that the file marks a project (`'P'`).
A file can have multiple annotations, which are encoded as a string with no
separator.

It is theoretically possible, although unlikely, that the list contains
duplicates. It is the responsibility of the client to remove duplicates from
the list.

Annotations are stored in extended attributes starting with the prefix
`user.annotation-$ID`. The ID should be a UUID with no separators, using a
UUID will make duplicates unlikely. Although they may still occur in the
case of race conditions, given that there is no locking on the setting
of these attributes.

#### `CHECKSUM`: Checksum

Contains the checksum and checksum type for this file.

The checksum and type are comma separated, and are guaranteed themselves to
not contain any commas.

It is not guaranteed that all files contain a checksum, in that case both
the checksum and type will come back as an empty string. The interpretation
of each checksum value is specific to their type.

The native component is not responsible for calculating or setting the
checksum, but merely returning them on demand. As a result the native component
makes no attempt at interpreting the values of these attributes.

#### `TIMESTAMPS`: Timestamps

Contains the accessed, modified and created timestamps.

The timestamps are encoded as Unix timestamps (seconds) and written as an
ASCII string.

#### `SENSITIVITY`: Sensitivity Level

Contains the sensitivity of the file.

A file can have the following types of sensitivity:

- `SENSITIVE`
- `CONFIDENTIAL`
- `OPEN_ACCESS`

If no sensitivity information is stored for the file `CONFIDENTIAL` is
returned.

The native component makes no attempt at interpreting these values. As a result
it is the responsibility of the client to ensure that the correct access rights
are set for these files.

#### `IS_LINK`: Is Link

Indicates if this file is a link. Encoded as a boolean.

#### `UNIX_MODE` Unix Mode

Contains the unix mode of the file, encoded as a base-10 integer (_not octal_)

#### `OWNER`, `GROUP`: Owner and Group

Contains the owner and group of the file.

A lookup is always performed from UID and GID. If the GID lookup fails
`"nobody"` is returned. A UID not resolving is a fatal error.

#### `PATH`: Path

Describes the absolute path to the file.

##### Attribute constraints

- _Always_ included
- _Always_ placed as the last column
- _Always_ points to the absolute _mounted_ path
- The path is _not guaranteed_ to be normalized
  - As a result it may contain things such as:
    - `.` and `..` segments

- Can contain all characters (including `','`, as a result parsing is easy
  when it is last) except for the following:
  - `'\n'`: A lot of code and UI all assume that file names are a single line
  only. As a result we impose the no new lines constraint.
- A path cannot be any longer than _1024_ characters long.
  - Any file operation that contains a path longer than this _should be
  rejected_.