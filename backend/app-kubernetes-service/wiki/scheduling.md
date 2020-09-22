# Scheduling

## Issues to be Resolved

1. No feedback given to users about when their job will run. This is crucial for interactive jobs which probably
accounts for the majority of our jobs.

2. The handling of jobs which cannot run immediately is undefined

3. Unable to run exclusively on a full node

4. No guarantee that all 'nodes' of a job is scheduled together

5. Java client for Kubernetes is _bad_. Jobs are not being closed when timer runs out.

6. Handling of jobs on nodes which have restarted is undefined

7. Partial failure of some nodes is undefined

8. Scheduled downtime and maintenance

9. Extend time limit of a job

10. Fair-share

11. Issue with injecting files (not always injected, error prone in case of crashes/restarts of app-k8s)

12. Connect to Job: No guarantee that you will be scheduled along your partner applications

13. Logs of multiple nodes

14. Read log files from storage

## Solution Proposals

### kube-batch

Basically a dead project appears to be superseded by volcano-sh.

### volcano-sh

- Poorly documented
  - Documentation gets better if you read the docs folder
  - In the end it appears that we need to use source code for documentation
  - All the resources are in version `v1alpha1` (quite common in the world of Kubernetes, I guess)
- Unclear who funds this project
- Very early stage project of CNCF
- Fairly easy to install
- Supports gang-scheduling and backfill
- Should take care of the following points:
  - Fixed: 2, 4, 11
  - Fixed, but doesn't work: 8
  - Unclear but potentially fixed: 5 (highly unlikely), 6, 7
  - Unclear/hard to integrate: 10
  
#### Integration plan and potential issues

- Pausing the system
  - It is possible to close the queue using `vcctl queue operate -a close -n <QUEUE>` and opening using 
    `vcctl queue operate -a open -n <QUEUE>`
    - But it doesn't work in our version, since it for some reason transitions into the `Unknown` state
    - This essentially bricks the queue and it needs to be recreated
    - Except the queue cannot be deleted because the admission controller rejects it (because of the bad state)
    - So we need to scale down the admission controller, delete it, and rescale the admission controller
  - This is something we're going to have to implement ourselves
    - This is fine since we need a lot of custom code regardless
- Multi-node config
  - Solved in a backwards compatible way, see job.yml
- Deadline
  - Will likely use the exact same code (with all the same shortcomings)
  - We could monitor and stop the jobs ourselves if they exceed the limit
  - This would also allow us to extend job duration
  - It does not appear that the volcano scheduler, in any way, takes into account how long the job runs for
  - See accounting for a way to get rid of limit (this will also, definitely, solve our longstanding bug)
- Accounting
  - Will likely use the exact same code (with all the same shortcomings)
  - I think we need to change this to account for usage as the app is running
    - Every X minutes charge app for usage
    - During this step we also re-validate permissions of files used
      - In case we no longer have permissions, terminate it
    - At the end charge since last breakpoint
    - We could refund the cost of the job (accounting feature required) in case of failure on our end
- Volumes
  - Solved, see job.yml
- Dealing with partial failure
  - We can use the 'policies' feature of volcano
- 'Connect to Job'
- NetworkPolicy
  - Volcano also does some of this (via the `svc` plugin)
- GPU
  - This might just work out-of-the box using the same limits
  - It might also not work at all
  - Even better, we cannot really test this!
  - They support GPU sharing, but this appears to depend on running `nvidia-docker` (unclear why)
- Fair share
  - Fair share uses namespaces to distinguish between users
  - This would literally mean we create a namespace for every user and project
  - The queue, even though it is k8 namespaced, is actually _not_ a namespaced resource
  - Creating a NetworkPolicy might be problematic (we need to proxy to the pods)

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


---

# Features to Remember

- `/dev/shm` needs to be mounted
- Network policy should block attempts to access an application from any other application
- Network policy should allow proxy from the `app-kubernetes` service
- Network policy should allow inter-job communication
- Network policy should allow connected jobs to communicate
- The `hostname` of every pod should be routable
  - This includes at least within a multi-node job and connected jobs
- Working directory
- Environment variables
- Should only be scheduled on certain nodes
- Don't mount service account token
- Cleanup and add proxy entries
- We should generally allow 400/404 from the Kubernetes backend (as it usually just means we didn't get their in time)
- Kata containers need to use slightly different amount of CPU (due to VM overhead)
- 