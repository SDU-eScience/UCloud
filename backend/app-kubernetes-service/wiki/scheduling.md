# Scheduling

## Issues to be Resolved

1. No feedback given to users about when their job will run. This is crucial 
for interactive jobs which probably accounts for the majority of our jobs.

2. The handling of jobs which cannot run immediately is undefined

3. Unable to run exclusively on a full node

4. No guarantee that all 'nodes' of a job is scheduled together

5. Java client for Kubernetes is _bad_. Jobs are not being closed when timer
runs out.

6. Handling of jobs on nodes which have restarted is undefined

7. Partial failure of some nodes is undefined

8. Scheduled downtime and maintenance

9. Extend time limit of a job

## Solution Proposals

### kube-batch

Basically a dead project appears to be superseded by volcano-sh.

### volcano-sh

- Poorly documented
- Unclear who funds this project
- Very early stage project of CNCF
- Fairly easy to install
- Supports gang-scheduling and backfill
- Should take care of the following points:
  - Fixed: 2, 4
  - Unclear but potentially fixed: 5 (highly unlikely), 6, 7

### We do it

- We could probably replace the buggy kubernetes client
  - Example, watch pods in namespace:
    `curl "https://rancher-dev.cloud.sdu.dk/k8s/clusters/c-xxxyyy/api/v1/namespaces/default/pods?watch" -u $dt -v`
  - Where `$dt` is username + password (rancher token)
  - Output will be one JSON event per line
- Run pods directly on Kubernetes
  - We manually specify the node
  - We read node information from Kubernetes
  - We manually terminate jobs if they run for too long
- Pod creation by scheduler micro-service
- Two queues, one for interactive one for batch
  - TODO How to determine this?
- Interactive queue
  - First fit
  - No reordering of jobs. This will allow us to concretely tell the user when
  their interactive job will run.
  - Issue: We have extremely unreliable estimates for how long the job will run
  - Issue: Not reordering makes unreliable estimates very bad. Maybe we could
  retrieve user input which states in which period the user wants the job to
  be scheduled.
- Batch queue
  - Reordering of jobs should be on, apart from that first fit might still work
  - Might need to add some kind of priority to this job
  - At this point we would probably also want reservations and a whole bunch
  of other features (which no one has implemented for K8s)
