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

<!-- /TOC -->

## Introduction

The storage service uses CephFS mounted as a unix file system as its storage
back-end. Authorization in the storage service is enforced by running each file operation on a unix user that is
associated with the SDUCloud user making the request. This is executed by starting a `sudo` process. As a result
the unix user that runs the storage service must be allowed to switch (using `sudo`) to any of the SDUCloud users.
On a normal server this should _not_ be implemented by letting it run as root. Instead a group should be made for all
SDUCloud users and allow the storage service only to switch to those.

The native component of the storage service is responsible for performing the actual communication with CephFS.
It is the process that runs as the other user, in other words, it is the program that is started with the `sudo`
process:

```bash
sudo -u $USER ceph-interpreter $CBOUNDARY_TOKEN $SBOUNDARY_TOKEN
```

Starting a new process is a fairly expensive procedure and comes with quite a lot of overhead. On a normal machine
this can be upwards of 15ms. As a result we do not wish to start multiple processes, but rather do as much work
as possible in a single.

TODO Some more stuff here, rewrite the above also.

## Basic Protocol

The `storage-service` (client) communicates with the `ceph-interpreter`
(server) using a simple IPC protocol implemented on top of the standard
streams (`stdin`, `stderr`, `stdout`).

All messages are separated by a _boundary token_ both the client and the server has a boundary token. These are
passed on the command line interface.

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

Both the client and server has APIs that make it relatively easy to deal with these tokens.

Two types of message arguments are supported:

  1. Basic arguments (`string`, `int`, `double`, `boolean`): A single line of text (separated by `\n`). 
  2. Blob arguments (`blob`): A binary stream of data

## Commands

### `copy`: Copies a single file

#### Description

Copies a single file.

The file available at `from` is copied to the location `to`.

#### Arguments

  1. `from: string`
  2. `to: string`

#### Output

#### Errors

### `copy-tree`

### `move`

### `list-directory`

### `list-favorites`

### `delete`

### `write`

### `tree`

### `make-dir`

### `get-xattr`

### `set-xattr`

### `delete-xattr`

### `list-xattr`

### `stat`

### `read`