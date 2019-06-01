# Workspaces

Workspaces are an internal feature for storing unmanaged data on the same
file system as SDUCloud. Workspaces are allowed to import and export data
efficiently to the managed portion of SDUCloud.

This feature is in particular useful for implementing
[applications](../../app-service) which need to efficiently transfer data back
and forth to perform computations.

A workspace is a simple directory created in the same file system. No
[events](./events.md) are emitted for any changes in this directory. When a
workspace is created a service can specify which data to 'mount' and from
which user. All permissions are checked to ensure that this user can access
the specified data.

Data can be mounted as read-only or read+write. For read-only data we create
hard-links to the real files, as a result no copying is performed. Services
using this feature should additionally ensure that the underlying volume is
mounted as a read-only. For read+write we copy the files from their original
location to the workspace directory.

Once a workspace has been created a service can mount and use the data inside
it as they see fit. No special care needs to be taking with regards to
UIDs/GIDs. All data access has been pre-authenticated in the creation step.
__It is important that read-only mounts are mounted as read-only by the
service.__

Once a service is done using a workspace files are transferred back into the
managed part of SDUCloud. Before we do this we sanitize all data. The
sanitization step normalizes all permissions, ACLs and UIDs. We also remove
all symbolic links found within the workspace. [Events](./events.md) are
emitted when the workspace is closed.

It should be noted that the changes a service makes in a workspace is not
visible inside of SDUCloud _before_ the workspace is closed.