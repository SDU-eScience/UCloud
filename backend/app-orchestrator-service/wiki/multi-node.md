# Communication between Jobs

## Discovery Information

A job can be scheduled on more than one replica. The orchestrator requires that backends execute the exact same command
on all the nodes. Information about other nodes will be mounted at `/etc/ucloud`. This information allows jobs to
configure themselves accordingly.

Each node is given a rank. The rank is 0-indexed. By convention index 0 is used as a primary point of contact.

The table below summarizes the files mounted at `/etc/sducloud` and their contents:

| **Name**              | **Description**                                               |
|-----------------------|-----------------------------------------------------------|
| `node-$rank.txt`      | Single line containing hostname/ip address of the 'node'. |
| `rank.txt`            | Single line containing the rank of this node.             |
| `cores.txt`           | Single line containing the amount of cores allocated.     |
| `number_of_nodes.txt` | Single line containing the number of nodes allocated.     |
| `job_id.txt`          | Single line containing the id of this job.                |

## Networking and Peering with Other Applications

Jobs are, by default, only allowed to perform networking with other nodes in the same job. A user can override this by
requesting, at job startup, networking with an existing job. This will configure the firewall accordingly and allow
networking between the two jobs. This will also automatically provide user-friendly hostnames for the job.
