# Permissions

All files in UCloud are in a home folder. The files of normal users are in
`/home/`. Projects store files in `/projects/`.

A user owns all the files placed in their home directory. If Alice shares the
directory `/home/alice/shared` with Bob then all files will remain owned by
Alice. This includes all the files Bob creates. As a result Alice will not
lose access to the files created by Bob when Bob leaves the share.

Admins of a project (`PI` and `ADMIN`) are considered the owner of a project
directory.

The platform controls access to files via **access control lists**. Each file
can have an associated access control list (ACL). Each entry in the ACL
contains a mapping between an "entity" and their permissions on the file. An
entity can be either a user or a project + group. The following file
permissions exists:

- `READ`: The associated entity can read the file.
- `WRITE`: The associated entitiy can read and write to the file. This
includes renaming, moving and deleting the file.

Note that only the owner a file can perform certain operations such as
updating the ACL.

__A single ACL will apply to a complete sub-tree and not only direct
children.__

End users don't have direct access to the ACL. Instead, they use higher-level
features such as [shares](../../share-service) and
[project repositories](../../project-repository-service).

Each ACL entry contains a tuple of `(entity, permissions)`. The backend supports
two types of entities: users and project groups.

## Examples

The following examples use two users, Alice (A) and Bob (B). Each example
will contain a list of files and their associated ACLs. We will list the
result of some operations performed by both Alice and Bob.

### Empty ACL

```
File: /home/alice/foo
ACL: []
```

- `alice` is the owner of the file. As a result she has full access to the file.
- `bob` has no permissions and is not allowed to perform any action on it.

### Simple Share

```
File: /home/alice/
ACL: []
Owner: alice

File: /home/alice/shared
ACL: [(bob, READ_WRITE)]
Owner: alice

File: /home/alice/shared/file
ACL: []
Owner: alice

File: /home/alice/shared/directory/file
ACL: []
Owner: alice

File: /home/alice/private
ACL: []
Owner: alice
```

- Only Alice has access to `/home/alice/` and `/home/alice/private`
- Bob has read+write permissions in `/home/alice/shared`
- Bob can also create and change existing files in `/home/alice/shared`. This
includes `/home/alice/shared/file` and `/home/alice/shared/directory/file`.

## More ACL Entries

```
File: /home/alice/shared
ACL: [(bob, READ_WRITE)]
Owner: alice

File: /home/alice/shared/directory
ACL: [(bob, READ)]
Owner: alice
```

- Bob has full read+write access to `/home/alice/shared` including all sub-directories. 
- It is not possible to restrict Bob's access to `shared/directory` by
creating an ACL entry with `READ`. Bob still has `READ_WRITE` in this
directory due to the entry on `/home/alice/shared`.
