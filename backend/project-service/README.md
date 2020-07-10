:orphan:

# Projects

The projects feature allow for collaboration between different users across the entire UCloud platform.

This project establishes the core abstractions for projects and establishes an event stream for receiving updates about
changes. Other services extend the projects feature and subscribe to these changes to create the full project feature.

## Definition

A project in UCloud is a collection of `members` which is uniquely identified by an `id`. All `members` are 
[users](../auth-service) identified by their `username` and have exactly one `role`. A user always has exactly one
`role`. Each project has exactly one principal investigator (`PI`). The `PI` is responsible for managing the project,
including adding and removing users.

| Role           | Notes                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------|
| `PI`           | The primary point of contact for projects. All projects have exactly one PI.                       |
| `ADMIN`        | Administrators are allowed to perform some project management. A project can have multiple admins. |
| `USER`         | Has no special privileges.                                                                         |

**Table:** The possible roles of a project, and their privileges within project
*management.

A project can be updated by adding/removing/changing any of its `members`. Such an update will trigger a new message
on the event stream.

A project is sub-divided into groups:

![](wiki/structure.png)

Each project may have 0 or more groups. The groups can have 0 or more members. A group belongs to exactly one project,
and the members of a group can only be from the project it belongs to.

## Creating Projects and Sub-Projects

All projects create by end-users have exactly one parent project. Only UCloud administrators can create root-level
projects, that is a project without a parent. This allows users of UCloud to create a hierarchy of projects. The 
project hierarchy plays a significant role in accounting.

A project can be uniquely identified by the path from the root project to the leaf-project. As a result, the `title` of
a project must be unique within a single project. `title`s are case-insensitive.

Permissions and memberships are _not_ hierarchical. This means that a user must be explicitly added to every project
they need permissions in. UCloud administrators can always create a sub-project in any given project. A setting exists
for every project which allows normal users to create sub-projects.

---

__Example:__ A project hierarchy with storage permissions

```text
SDU
  - TEK
  - SAMF
  - SUND
  - HUM
  - NAT
    - IMADA
      - 
```

---

## Interactions with other features

- [Project Repositories](../project-repository-service/README.md): Learn about
  how projects interact with the storage of UCloud.
- [Accounting](../accounting-service/README.md)
