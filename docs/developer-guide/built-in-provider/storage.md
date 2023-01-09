<p align='center'>
<a href='/docs/developer-guide/legacy/projects-legacy/favorites.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/built-in-provider/compute/intro.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Built-in Provider](/docs/developer-guide/built-in-provider/README.md) / UCloud/Storage
# UCloud/Storage

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

Syncthing is provided in UCloud as an integrated application. This essentially means that the
orchestrator is providing a very limited API to the provider centered around pushing configuration. The
orchestrator expects the provider to use this configuration to update the storage and compute systems, in such
a way that Syncthing responds to the requested changes.

The orchestrator has the following assumptions about how the provider does this:

 1. The provider must register a job with UCloud (through `JobsControl.register`).
    - It is assumed that every user gets their own job.
    - Currently, the frontend does a lot of the work in finding the jobs, but the orchestrator might change to
      expose an endpoint which returns the relevant job IDs instead.
 2. The application must have at least one parameter called `stateFolder` which should be an input directory.
 3. The folder which has the state must contain a file called `ucloud_device_id.txt`. This file must contain the
    device ID of the Syncthing server.

 The UCloud compute plugin implements this in a fairly straight forward way. These are the keypoints:
 
 1. Every user is allocated a single state folder (on-demand). This state folder lives in their personal
    workspace. The folder is called `Syncthing`.
 2. This state folder contains a configuration file called `ucloud_config.json`. This file essentially contains
    the configuration received by the orchestrator in JSON format.
 3. A custom wrapper application with Syncthing embedded is started as a completely normal compute job and is managed by
    the normal compute capabilities of this provider. This application will be described in further details later.
    - The job is launched on demand based on invocations of `updateConfiguration`.
 4. Using a normal job means that the provider does not have to worry about permission management and quotas. All of 
    this is already taken care of by the existing systems. This includes permission checking from the orchestrator and
    the accounting performed by the provider.
 5. The orchestrator is notified about new mounts when they appear. The container will automatically trigger a
    restart based on the new configuration.


In the UCloud provider the Syncthing application consists of a wrapper application with an embedded instance of
[Syncthing](https://syncthing.net). The `stateFolder` and any folders added to Syncthing are bind-mounted to the job the
usual way.

The wrapper application is reponsible for:

 - Starting the Syncthing process,
 - checking if a restart of the application have been prompted by checking for a specific file in `stateFolder`, and if
   so, ending any further execution,
 - listen for (and read) changes made to the configuration file `ucloud_config.json`,
 - updating Syncthing's internal configuration using the Syncthing API, based on the contents of `ucloud_config.json`,

