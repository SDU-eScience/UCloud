# Event Stream

The project service will emit events on the `EventStreamService` whenever certain events occur. Other microservices
consume these events to act on changes in projects. For example, the storage backend can react to a new project by
allocating resources.

The following types of events exist, we refer to the in-code documentation for more details:

- `created`
- `deleted`
- `memberAdded`
- `memberDeleted`
- `memberRoleUpdated`
- `memberAddedToGroup`
- `memberRemovedFromGroup`
- `groupCreated`
- `groupDeleted`
- `groupRenamed`
