# Project Repositories

The [ACL model for files](../storage-service/wiki/permissions.md) states that a permission is granted for a complete
sub-tree of the file system. It is _not_ possible to remove permissions granted by an ancestor. As a result if a user 
has `READ` permissions in `/projects/MyProject` then this user will have `READ` permissions for _all_ descendants of 
this folder. For example this user would have `READ` permissions in `/projects/MyProject/foobar` as well as 
`/projects/MyProject/a/b/c/d/e`.

This ACL model has many performance benefits. Unfortunately, it presents some challenges in modelling permissions with
a project. This, for example, makes it impossible to have a common project home folder for all project users. Such a
home folder would require `READ` permissions. This would lead to all project members having `READ` for all of the 
project's files.

The project repository service addresses the issue discussed above. The file system of a typical project is depicted
below:

```text
/projects/MyProject/
+ Owner: Project PI
+ ACL: Empty
    Repository1/
    + Owner: Project PI
    + ACL: [Group(MyProject, Group1, [READ, WRITE]), Group(MyProject, Readers, [READ])]
        SomeFiles/ (Owner and ACL same as Repository1/)
            A.txt  (Owner and ACL same as Repository1/)
            B.doc  (Owner and ACL same as Repository1/)
            C.pdf  (Owner and ACL same as Repository1/)
    Repository2/
    + Owner: Project PI
    + ACL: []
    Repository3/
    + Owner: Project PI
    + ACL: [Group(MyProject, Readers, [READ])]

+: Metadata
```

## Scenario: Viewing a project's home folder

Clients should use `project.repositories.list` to retrieve a page of repository names. The UI may choose to display
these as ordinary folders. When a user selects a repository the client should redirect the user to
`/projects/$PROJECTID/$REPONAME`.

## Scenario: Deleting a repository

A repository can be deleted using `project.repositories.delete`.

## Scenario: Renaming a repository

A repository can be renamed using `project.repositories.update`. The UI may choose to display this as a rename operation
on a folder.

## Scenario: Updating Permissions

A repositories ACL can be updated using `project.repositories.updatePermissions`. The UI should display the groups that
belong to this project. Only the groups from the project can be added to the ACL. The permission that can be granted
match those of [shares](../share-service/README.md).
