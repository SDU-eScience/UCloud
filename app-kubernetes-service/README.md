# app/kubernetes

A [compute backend](../app-service) using Kubernetes for scheduling jobs.

Kubernetes is the scheduler/orchestrator already used for all SDUCloud
services. `app-kuberneteres` works by running user jobs as Kubernetes
[Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/jobs-run-to-completion/).
This means that one of the biggest challenges of this service is to map an
SDUCloud job to the corresponding Kubernetes job.

## Connecting to Kubernetes Cluster

The microservice automatically uses configuration available in `$KUBECONFIG`
or auto detects a mounted account service token. When running inside
kubernetes it is important that its service account is given the appropriate
permissions.

## Mapping from SDUCloud Jobs to K8 Jobs

In this section we will briefly describe the overall mapping between the two.
For all the details we refer directly to the source code available
[here](./src/main/kotlin/dk/sdu/cloud/app/kubernetes/services/PodService.kt).

The microservice is configured to place all jobs in a specific namespace.
This makes it easier to control the [security model](#security-model). The
metadata section of the job contains a job name which has a one-to-one
mapping with SDUCloud job names. Additionally we label SDUCloud jobs with
their own label.

The job's container specification maps almost directly to the input from the
application/tool. This computation backend only supports tools of type
`DOCKER`. The invocation maps directly to the `invocation` section of the
application.

The deadline of the job is enforced via `activeDeadlineSeconds`. The job will
never restart and it will never attempt to run two copies at the same time.

Environment variables are set as specified by the
[app-service](../app-service).

Volumes are setup to make [transferring of files](#transferring-files)
possible.

## Transferring Files

The microservice will mount the same persistent volume (PV) as the
[storage-service](../storage-service). Note that to achieve this we must copy
the internal settings of the mount to a separate PV which can be used in the
namespace dedicated for running applications.

The [workspace](../storage-service/wiki/workspaces.md) feature is used for
storing input files for applications in the same FS as SDUCloud. Special care
is taken to make sure the volume is mounted correctly. For example, all
read-only mounts (as defined by workspaces) are mounted as read-only in the
jobs. Additionally, we only mount the workspace not the entire volume. It is
_very_ important that we do this since users would otherwise have full access
to the entire file system.

## Security Model

All jobs are run in their own separate namespace. This allows for better
isolation and makes it easier to create policies which apply to SDUCloud
applications.

The service account token is not mounted as it is not needed. The default
service account token shouldn't have any permissions, but this should
protect in cases where the service account token has been configured
differently.

We grant no special privileges to the container. This ensures that user jobs
cannot change settings which would affect the physical host.

We don't put any special restrictions on the UID a user's container can run
as. We do support the UID changing behavior as specified in
[app-service](../app-service/wiki/apps.md). As a result we can force a
container to run as root or run as the real SDUCloud UID.

This means that we must assume that all jobs can run as root within the
container. This is okay since containers are meant to be a secure sandbox.
This does mean that we have to be careful when transferring files back. This
sanitization step is further discussed in the [workspaces
feature](../storage-service/wiki/workspaces.md).

A
[NetworkPolicy](https://kubernetes.io/docs/concepts/services-networking/network-policies/)
is applied to all pods running in the application namespace. This network
policy blocks all ingress and furthermore blocks all egress to private IP
addresses. A single exception is made to allow for DNS (which is running from
a private IP address).

Security regarding interactive applications is described
[here](#interactive-applications).

## Interactive Applications

Interactive applications, such as `applicationType: VNC` and
`applicationType: WEB`, allows a user to connect to a remote server and use
the application which is running.

Traffic is delivered to these applications by proxying traffic received by
this microservice directly to the pod running the service. It is the proxy's
job to implement the security model.

### Supported Protocols

We implement security on top of the protocols that deliver the interactive
applications. As a result only a limited number of protocols are supported:

- HTTP
- WebSockets

### Security

All interactive applications require the user to be authenticated. Below we
describe the handshaking procedure for opening an interactive `WEB`
application.

- User starts an application and receives a `$jobId`
- User queries the authorization endpoint for `$jobId`
- User receives a relative `$url`
- User sends a `GET` request to `$url` (`$url` is part of `cloud.sdu.dk`)
- Server responds with a 301 to `app-$jobId.cloud.sdu.dk` and sets a
  `refreshToken` cookie available on `.cloud.sdu.dk` (including subdomains).
  This cookie is HTTP only to ensure the application cannot steal the
  `refreshToken` via client-side javascript.
- User follows redirect
  - Proxy extracts and validates `refreshToken`
  - Proxy parses `$jobId` and finds job. The owner of the job is compared with
    owner of `refreshToken`. Only the owner of the job is allowed to access the
    application.
  - Proxy forwards the request to the real server
    - `refreshToken` cookies are removed from the request before forwarding