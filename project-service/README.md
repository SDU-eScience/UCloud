# Projects

The projects feature allow for collaboration between different users across the
entire SDUCloud platform.

This project establishes the core abstractions for projects and establishes an
event stream for receiving updates about changes. Other services extend the
projects feature and subscribe to these changes to create the full project
feature. The following services are very relevant for the core functionality of
projects:

- [project-auth](../project-auth-service): Handles authentication and
  authorization for projects
  - Initializes the correct permissions of the file system

## Definition

A project in SDUCloud is a collection of `members` which is uniquely identified
by an `id`. All `members` are [users](../auth-service) identified by their
`username` and have exactly one `role`. A user always has exactly one `role`.
Each project has exactly one principal investigator (`PI`). The `PI` is
responsible for managing the project, including adding and removing users.

| Role           | Notes                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------|
| `PI`           | The primary point of contact for projects. All projects have exactly one PI.                       |
| `ADMIN`        | Administrators are allowed to perform some project management. A project can have multiple admins. |
| `DATA_STEWARD` | Has no special privileges.                                                                         |
| `USER`         | Has no special privileges.                                                                         |

**Table:** The possible roles of a project and their privileges within project
*management.

A project can be updated by adding/removing/changing any of its `members`.
Such an update will trigger a new message on the event stream. A project can
also be deleted, this will trigger a cleanup of associated resources (such as
files).

## Applications

Applications started by a member is billed to the project. Only the user who
started the job will be able to interact with the job. The job is started
with the UID corresponding to their project user. All project users will be
able to view the results.

Applications running for a project will only appear under "My Results" for
the user who started it. Once the job is complete it will appear for all
other project users.

It is crucial that only the user can interact with the application to avoid
privilege escalation.

This could be expanded to allow for cancelation by a PI/ADMIN.
