# Application Orchestrator (`app-orchestrator-service`)

The application orchestrator uses the `app-store-service` to implement the
execution of jobs (an application + user input). In many ways the application
orchestrator acts as a meta scheduler.

This service doesn't actually implement any scheduling. Instead it forwards
most of the requests it receives to an application backend. The application
backend is responsible for actually running the job. The orchestrator
receives requests directly from the user. One of the most important tasks of
this service is to ensure that all user requests are validated before they
are forwarded. This hugely simplifies backend implementation and makes it easier
to create different implementations.

![Scheduling applications](./wiki/schedule.png)

Figure: The end-user sends commands to the `app-service`. Commands are
validated and transformed into useful commands for the computation backend.
The computation backend can implemented these requests in any way they see
fit.

## Scheduling Parameters

The user can provide a number of scheduling parameters. The most important of
all are the input values required by the application.

| Parameter                | Description                                                                                              |
|--------------------------|----------------------------------------------------------------------------------------------------------|
| `parameters`             | A dictionary containing all input parameters for the job.                                                |
| `numberOfNodes`          | The amount of nodes requested for this job. The backend must run `numberOfNodes` many copies of the job. |
| `maxTime`                | Maximum time allocated for this job. The job should be terminated shortly after this deadline.           |
| `backend`                | The requested backend for this job.                                                                      |
| `mounts`                 | A list of SDUCloud mounts. See below.                                                                    |
| `sharedFileSystemMounts` | A list of shared file systems to mount. See below.                                                       |
| `peers`                  | A list of networking peers. See below                                                                    |

## Container Environment

TODO: Write this section

## Multi-Node Applications

A job can be scheduled on more than one node. The orchestrator requires that
backends execute the exact same command on all the nodes. Information about
other nodes will be mounted at `/etc/sducloud`. This information allows jobs
to configure themselves accordingly.

Each node is given a rank. The rank is 0-indexed. By convention index 0
should used as primary point of contact.

The table below summarizes the files mounted at `/etc/sducloud` and their
contents:

| Name                  | Description                                               |
|-----------------------|-----------------------------------------------------------|
| `node-$rank.txt`      | Single line containing hostname/ip address of the 'node'. |
| `rank.txt`            | Single line containing the rank of this node.             |
| `cores.txt`           | Single line containing the amount of cores allocated.     |
| `number_of_nodes.txt` | Single line containing the number of nodes allocated.     |
| `job_id.txt`          | Single line containing the id of this job.                |

## Mounting Files from SDUCloud

A user can chose to mount files directly from SDUCloud. A mount is a simple
tuple containing the SDUCloud path and container path. The files from the
SDUCloud directory will be visible inside of the container. A user can request
that the directory be mounted as read only.

Changes to the mounted directory is not visible from SDUCloud immediately.
This is because the job is working on a 'view' of the file system rather than
the actual file system. The backend implementation is required to
semantically work like a copy-on-write filesystem (but can be implemented
differently). As a result the changes are pushed to SDUCloud at the end of
the job. This is required to account due to the container environment. The
container environment allows the container to run as any UID, this includes
root. The files are sanitized before being moved into the SDUCloud system.
The sanitization step normalizes ACLs, file owners, and file permissions.
This also triggers any relevant [storage events](../../../storage-service).

## Shared File Systems

Some applications require changes to be visible immediately on other node.
This is, for example, common with multi node applications. Because of this we
support provisioning of file systems on demand. These file systems can be
mounted by one or more jobs. The files stored in these file systems are not
managed by SDUCloud at all. The only way to import and export files to and
from them is through applications.

You can read more about this feature [here](../../../app-fs-service).

## Networking and Peering with Other Applications

Jobs are, by default, only allowed to perform networking with other nodes in
the same job. A user can override this by requesting, at job startup,
networking with an existing job. This will configure the firewall accordingly
and allow networking between the two jobs. This will also automatically
provide user friendly hostnames for the job. The hostname is user specified
and follows this format: `$hostname-$rank`. For `$rank = 0` we also provide
an alias of `$hostname`.
