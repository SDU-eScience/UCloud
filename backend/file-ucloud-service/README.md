UCloud/Storage is a general purpose implementation of the UCloud storage provider API. It works for any POSIX
file-system but has special support for some file-systems. For a production system, the FS must be mountable by multiple
nodes simultaneously.

We recommend that you are already familiar with the concepts of the UCloud/Core API.

## File-System Layout

UCloud/Storage maps every `FileCollection` to at least one folder on the file-system. The system stores files in the
following three top-level folders:

1. `/collections`: Stores user created `FileCollection`s. Each folder corresponds to exactly one `FileCollection`. The
   folder name matches the ID of the collection.
2. `/home`: Stores "member files" of a personal workspace.
3. `/projects`: Stores "member files" of a personal workspace, and legacy collections.

A "member files" folder in UCloud/Storage refers to the personal files of a user in a workspace. Member files store the
output of `Job` runs and other files, such as trash.

## Mapping Collections to Paths

UCloud/Storage maps a `FileCollection` to a path by using the properties stored in UCloud/Core. Specifically, the
provider generated ID and the ID. The system maps the collections using the following table.

| Provider ID Prefix | Format                   | Real Path                                 |
|:-------------------|:-------------------------|:------------------------------------------|
| `pm-`              | `pm-$project/$user`      | `/projects/$project/Members' Files/$user` |
| `p-`               | `p-$project/$repository` | `/projects/$project/$repository`          |
| `h-`               | `h-$user`                | `/home/$user`                             |
| `s-`               | `s-$shareId`             | Depends on share                          |
| None               | N/A                      | `/collections/$id`                        |

The concrete implementation of path mapping is available in `dk.sdu.cloud.file.ucloud.services.PathConverter`.

UCloud/Storage create "Member Files" (`pm-` and `h-`) collections on-demand for every user. Specifically, the system
creates them in response to the `file_collections.init` call. The system registers them with the appropriate provider
generated ID and permissions.

## Permissions and File Operations

UCloud/Storage depends on the orchestrator for all permissions. This service will store all files owned by the same
user (UID 11042). Not depending on file-system permissions, simplifies several parts of the system:

1. Application development is easier, as it can assume files are available for a specific user.
2. Shares are significantly simpler. Since shares don't need to synchronize permissions with an external system.
3. This makes applications with root permissions feasible.

The UCloud/Storage system makes use of normal calls from the POSIX FS interface. However, the system must take care of
symbolic links. It is critical that the system never follows links. If it did, then it would be trivial to circumvent
the permission checking. This could be as simple as pointing a link to another user's files.

It is not sufficient to use the `O_NOFOLLOW` flag in the `open` call. This flag only checks that the file being opened
is not a file. This flag will not check if any of the ancestor files are symbolic links, and it will follow them
implicitly. Instead, the provider will open each component of the file using `openat` which accepts a file descriptor.
This way, we ensure that no component is a symbolic link, and that we never follow them. This procedure works even if
changes occur on the file-system while we are opening components of the path. Any system which mounts these files,
namely UCloud/Compute, must use the same procedure for mounting the folder.

## Task System

The provider uses a task system for processing all operations. This task system allows for both foreground and
background processing of an operation. If an operation goes into background processing, then it will complete processing
at some point in time. This will happen even if the provider goes down during processing.

The provider will determine at the beginning of each request, if the request will take a long to process or not. If the
system expects a task to be quick, then it will process in the foreground. Foreground processes do not return until the
work is complete. This has the benefit that the result is immediately visible to the end-user.

## Shares

Documentation not yet written.

## Indexing

The indexing system of UCloud/Storage currently depends on CephFS. Files are indexed into an ElasticSearch database. The
file-system is traversed and the index is updated accordingly. The indexing algorithm attempts to skip folders by
comparing modification times with the data stored in the index.

## Accounting

The accounting system of UCloud/Storage currently depends on CephFS. The provider performs a periodic scan of the
top-level folders, as described in the earlier section about mapping collections to paths. The size of each folder is
recorded and aggregated at the workspace level. Finally, the service notifies the orchestrator about usage. If any
`charge` fails, then the workspace is recorded into a table of locked accounts. A locked account is unable to perform
any operation which can increase the amount of bytes stored in the system. If an account was previously locked, and the
current usage is below the quota, then the account is removed from the locked table.

## Syncthing

**Note: This features is in development/testing.**

The synchronization feature of UCloud/Storage depends on [Syncthing](https://syncthing.net). The provider maintains
communication with one or more Syncthing instances, running in seperate containers, through the Syncthing API.

The provider also maintains two types of resources:

- SyncDevice: A user's Syncthing device ID,
- SyncFolder: Folder on UCloud which should be synchronized by Syncthing,

as well as communication with a separate service, SyncMounter.

When a request to add a folder to Syncthing is received, the provider will add the SyncFolder resource and send a
request to SyncMounter to bind-mount the internal path to a new location. The provider will then fetch all SyncFolder
and SyncDevice resources and generate a new Syncthing configuration which will be sent to one of the Syncthing
instances.

To shield the Syncthing instances from access to the rest of the filesystem, it will only receive paths to the
bind-mounted location of folders.

The provider will attempt to add the SyncFolder to the Syncthing instance with the least usage (sum of the size of files
in the current SyncFolders for that instance) to distribute load. A draw-back to this method is that SyncDevices for a
single end-user might be added to multiple different Syncthing instances. Thus, the end-user might need to add multiple
UCloud Syncthing devices to their locally running instance of Syncthing.

When a SyncFolder is removed, the provider will request the responsible Syncthing instance to update its configuration,
then request SyncMounter to unmount the bind-mounted location of the folder. In some cases the SyncMounter might fail to
unmount the bind-mounted folder. This can happen if the Syncthing instance does not update its configuration in time. In
this case the provider will retry the request a few times, but otherwise proceed without alerting the user. In this case
the folder will be unmounted the next time the SyncMounter service is restarted.

Adding or removing SyncFolders and SyncDevices will always result in a request to one or more Syncthing instances to
overwrite their configuration, thus the configuration of a Syncthing instance is always a reflexion of a subset of the
providers knowledge.

On start-up the SyncMounter service will automatically request the provider for a list of SyncFolders, mount them, and
then mark itself as ready. The provider will wait for SyncMounter to respond that it is ready, before requesting all
Syncthing instances to update their configuration.

When the SyncMounter process ends it will unmount all SyncFolder paths and when the provider process ends, it will
request all Syncthing instances to drain/empty their configuration.

**Current status:** Requests to Syncthing might currently fail unexpectedly due to a problem with ktor and/or the
Syncthing HTTP server. The current work-around is that the provider restricts the number of continuous requests to
Syncthing by delaying requests and by retrying in case of failure. In case all retries fail, the provider will attempt
to revert the changes to the database before throwing an error.



