:orphan:

# App Services 

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

.. figure:: /backend/app-orchestrator-service/wiki/schedule.png
   :width: 30%
   :align: center

**Figure:** The end-user sends commands to the `app-service`. Commands are
validated and transformed into useful commands for the computation backend.
The computation backend can implemented these requests in any way they see
fit.

## Scheduling Parameters

The user can provide a number of scheduling parameters. The most important of
all are the input values required by the application.

| **Parameter**            | **Description**                                                                                              |
|--------------------------|----------------------------------------------------------------------------------------------------------|
| `parameters`             | A dictionary containing all input parameters for the job.                                                |
| `numberOfNodes`          | The amount of nodes requested for this job. The backend must run `numberOfNodes` many copies of the job. |
| `maxTime`                | Maximum time allocated for this job. The job should be terminated shortly after this deadline.           |
| `backend`                | The requested backend for this job.                                                                      |
| `mounts`                 | A list of UCloud mounts. See below.                                                                    |
| `peers`                  | A list of networking peers. See below                                                                    |

## Container Environment

See [app-kubernetes](backend/app-kubernetes-service/README.html) for more information.

## Multi-Node Applications

A job can be scheduled on more than one node. The orchestrator requires that
backends execute the exact same command on all the nodes. Information about
other nodes will be mounted at `/etc/sducloud`. This information allows jobs
to configure themselves accordingly.

Each node is given a rank. The rank is 0-indexed. By convention index 0
should used as primary point of contact.

The table below summarizes the files mounted at `/etc/sducloud` and their
contents:

| **Name**              | **Description**                                               |
|-----------------------|-----------------------------------------------------------|
| `node-$rank.txt`      | Single line containing hostname/ip address of the 'node'. |
| `rank.txt`            | Single line containing the rank of this node.             |
| `cores.txt`           | Single line containing the amount of cores allocated.     |
| `number_of_nodes.txt` | Single line containing the number of nodes allocated.     |
| `job_id.txt`          | Single line containing the id of this job.                |

## Mounting Files from UCloud

A user can chose to mount files directly from UCloud. A mount is a simple
tuple containing the UCloud path and container path. The files from the
UCloud directory will be visible inside of the container.

## Networking and Peering with Other Applications

Jobs are, by default, only allowed to perform networking with other nodes in
the same job. A user can override this by requesting, at job startup,
networking with an existing job. This will configure the firewall accordingly
and allow networking between the two jobs. This will also automatically
provide user friendly hostnames for the job. The hostname is user specified
and follows this format: `$hostname-$rank`. For `$rank = 0` we also provide
an alias of `$hostname`.
