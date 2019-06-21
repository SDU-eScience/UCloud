# The SDUCloud multi-node convention

Each pod creates a single shared volume between the init container and the
work container. This shared volume can use `emptyDir`. This directory will be
mounted at `/etc/sducloud` and will contain metadata about the other nodes. 

The rank is 0-indexed. By convention index 0 should used as primary point of
contact.

/etc/sducloud/
  - node-$rank.txt (single line containing the hostname/ip addr of the 'node')
  - rank.txt (single line containing the rank of this node)
  - cores.txt (single line containing the amount of cores allocated)
  - number_of_nodes.txt (single line containing the number of nodes allocated)
  - job_id.txt (single line containing the id of this job. Last file indicates 
    that we are ready)
  - slurm.sh (script initializing a slurm compatible environment)