# Requirements of Job Environment

This document describes how a compute provider should start `Job`s and which requirements UCloud places on the
environment in which they run.

## Multi-replica Jobs

---

__📝 NOTE:__ We expect that the mount location will become more flexible in a future release. See
issue [#2124](https://github.com/SDU-eScience/UCloud/issues/2124).

---

A `Job` can be scheduled on more than one replica. The orchestrator requires that backends execute the exact same
command on all the nodes. Information about other nodes will be mounted at `/etc/ucloud`. This information allows jobs
to configure themselves accordingly.

Each node is given a rank. The rank is 0-indexed. By convention index 0 is used as a primary point of contact.

The table below summarizes the files mounted at `/etc/ucloud` and their contents:

| **Name**              | **Description**                                           |
|-----------------------|-----------------------------------------------------------|
| `node-$rank.txt`      | Single line containing hostname/ip address of the 'node'. |
| `rank.txt`            | Single line containing the rank of this node.             |
| `cores.txt`           | Single line containing the amount of cores allocated.     |
| `number_of_nodes.txt` | Single line containing the number of nodes allocated.     |
| `job_id.txt`          | Single line containing the id of this job.                |

## Networking and Peering with Other Applications

`Job`s are, by default, only allowed to perform networking with other nodes in the same `Job`. A user can override this
by requesting, at `Job` startup, networking with an existing job. This will configure the firewall accordingly and allow
networking between the two `Job`s. This will also automatically provide user-friendly hostnames for the `Job`.
