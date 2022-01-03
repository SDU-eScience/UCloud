# UCloud: Compute Orchestrator

UCloud is, at its core, an orchestrator of
resources. This means that UCloud sends all the hard work to a provider. UCloud has its own
provider, [UCloud/Compute](/backend/app-kubernetes-service/README.md).

![](/backend/app-orchestrator-service/wiki/overview.png)

In this section we will cover the specification and rules for which all compute providers of UCloud must follow. It all
starts with the core abstraction used in UCloud's compute, the `Job`.

---
<!-- typedoc:dk.sdu.cloud.app.orchestrator.api.Job:includeOwnDoc=false:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | Unique identifier for this job. |
| `owner` | `JobOwner` | A reference to the owner of this job |
| `updates` | `Array<JobUpdate>` | A list of status updates from the compute backend. |
| `billing` | `JobBilling` | Contains information related to billing information for this `Resource` |
| `specification` | `JobSpecification` | The specification used to launch this job. |
| `status` | `JobStatus` | A summary of the `Job`'s current status |
| `createdAt` | `Long` | Timestamp referencing when the request for creation was received by UCloud |
| `output` | `JobOutput?` | Information regarding the output of this job. |
| `acl` | `Array<ResourceAclEntry>?` | An ACL for this `Resource` |
| `permissions` | `ResourcePermissions?` | Permissions assigned to this resource |


<!--</editor-fold>-->
<!-- /typedoc:dk.sdu.cloud.app.orchestrator.api.Job -->

__Table:__ The data model for a `Job`

---

<!-- typedoc:dk.sdu.cloud.app.orchestrator.api.Job-->
<!--<editor-fold desc="Generated documentation">-->
A `Job` in UCloud is the core abstraction used to describe a unit of computation.


They provide users a way to run their computations through a workflow similar to their own workstations but scaling to
much bigger and more machines. In a simplified view, a `Job` describes the following information:

- The `Application` which the provider should/is/has run (see [app-store](/backend/app-store-service/README.md))
- The [input parameters](/backend/app-orchestrator-service/wiki/parameters.md),
  [files and other resources](/backend/app-orchestrator-service/wiki/resources.md) required by a `Job`
- A reference to the appropriate [compute infrastructure](/backend/app-orchestrator-service/wiki/products.md), this
  includes a reference to the _provider_
- The user who launched the `Job` and in which [`Project`](/backend/project-service/README.md)

A `Job` is started by a user request containing the `specification` of a `Job`. This information is verified by the UCloud
orchestrator and passed to the provider referenced by the `Job` itself. Assuming that the provider accepts this
information, the `Job` is placed in its initial state, `IN_QUEUE`. You can read more about the requirements of the
compute environment and how to launch the software
correctly [here](/backend/app-orchestrator-service/wiki/job_launch.md).

At this point, the provider has acted on this information by placing the `Job` in its own equivalent of
a [job queue](/backend/app-orchestrator-service/wiki/provider.md#job-scheduler). Once the provider realizes that
the `Job`
is running, it will contact UCloud and place the `Job` in the `RUNNING` state. This indicates to UCloud that log files
can be retrieved and that [interactive interfaces](/backend/app-orchestrator-service/wiki/interactive.md) (`VNC`/`WEB`)
are available.

Once the `Application` terminates at the provider, the provider will update the state to `SUCCESS`. A `Job` has
terminated successfully if no internal error occurred in UCloud and in the provider. This means that a `Job` whose
software returns with a non-zero exit code is still considered successful. A `Job` might, for example, be placed
in `FAILURE` if the `Application` crashed due to a hardware/scheduler failure. Both `SUCCESS` or `FAILURE` are terminal
state. Any `Job` which is in a terminal state can no longer receive any updates or change its state.

At any point after the user submits the `Job`, they may request cancellation of the `Job`. This will stop the `Job`,
delete any [ephemeral resources](/backend/app-orchestrator-service/wiki/job_launch.md#ephemeral-resources) and release
any [bound resources](/backend/app-orchestrator-service/wiki/parameters.md#resources).


<!--</editor-fold>-->
<!-- /typedoc:dk.sdu.cloud.app.orchestrator.api.Job -->
