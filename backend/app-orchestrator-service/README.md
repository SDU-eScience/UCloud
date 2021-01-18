# UCloud: Compute Orchestrator

UCloud has applications. The applications are defined by the app-store. UCloud is, at its core, an orchestrator of
resources. This means that UCloud sends all the hard work to a provider. UCloud has its own provider, UCloud/compute.

In this section we will cover the specification and rules for which all compute providers of UCloud must follow.

## Jobs

<!-- typeref:dk.sdu.cloud.app.orchestrator.api.Job -->
A `Job` in UCloud is the core abstraction used to describe a unit of computation. `Job`s provider users a way to run
their computations through a workflow similar to their own workstations but scaling to much bigger and more machines. In
a simplified view, a `Job` describes the following information:

- The `Application` which the provider should/is/has run (see [app-store](/backend/app-store-service/README.md))
- The [input parameters](/backend/app-orchestrator-service/parameters.md), 
  [files and other resources](/backend/app-orchestrator-service/resources.md) required by a `Job`
- A reference to the appropriate [compute infrastructure](/backend/app-orchestrator-service/products.md), this includes
  a reference to the _provider_

A `Job` is started by a user request containing the `parameters` of a `Job`. This information is verified by the UCloud
orchestrator and passed to the provider referenced by the `Job` itself. Assuming that the provider accepts this
information, the `Job` is placed in its initial state, `IN_QUEUE`. The provider must support the full request from the
user. If the provider is unable to support the request it must respond with `400 Bad Request` and an appropriate error
message.

At this point, the provider has acted on this information by placing the `Job` in its own equivalent of
a [job queue](/backend/app-orchestrator-service/wiki/queue.md). Once the provider realizes that the `Job` is running, it
will contact UCloud and place the `Job` in the `RUNNING` state. This indicates to UCloud that log files can be retrieved
and that interactive interfaces (`VNC`/`WEB`) are available.

Once the `Application` terminates at the provider, the provider will update the state to `SUCCESS`. A `Job` has
terminated successfully if no internal error occurred in UCloud and in the provider. This means that a `Job` whose
software returns with a non-zero exit code is still considered successful. A `Job` might, for example, be placed
in `FAILURE` if the `Application` crashed due to a hardware/scheduler failure. Both `SUCCESS` or `FAILURE` are terminal
state. Any `Job` which is in a terminal state can no longer receive any updates or change its state.

At any point after the user submits the `Job`, they may request cancellation of the `Job`. This will stop the `Job`,
delete any [ephemeral resources](/backend/app-orchestrator-service/wiki/resources.md) and release
any [bound resources](/backend/app-orchestrator-service/wiki/resources.md).
<!-- /typeref:dk.sdu.cloud.app.orchestrator.api.Job -->

