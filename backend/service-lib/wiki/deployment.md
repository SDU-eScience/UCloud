In this document we will describe the procedures and technologies involved in the deployment of UCloud services.
Additional details about our infrastructure can be found in our infrastructure documentation (private).

## Docker

All services are deployed as a Docker container in Kubernetes.

We host a private Docker registry at https://dreg.cloud.sdu.dk. Contact @hschu12 or @DanThrane for access.

## Kubernetes

Kubernetes handles the orchestration of containers. It is configured via
resources that describe the desired state of the cluster. Common types of
Kubernetes resources include deployments and cron jobs. Kubernetes ensures
that the state described in the resources are met in the cluster.

As a result there are no servers to configure or install software on. We simply
describe to Kubernetes how we wish to run our containers and Kubernetes takes
care of the rest. Once a server has joined the Kubernetes cluster it is ready
to run any of our services.

See the [Kubernetes documentation](https://kubernetes.io/) for more details.

Access to Kubernetes is done through K3s.

## K3s

[K3s](https://k3s.io) is the software we use to manage new Kubernetes clusters.

## Jenkins

Jenkins is our CI system and is responsible for building and testing code.

## Procedure and Backwards Compatibility

Under normal conditions, before deploying a new version you must ensure that
the software checks every mark in the following list:

- [ ] The software is built and tested. This process should make use of the development system, manual testing and
  automatic testing.
- [ ] Relevant migrations must occur before the deployment of the new software.
- [ ] Migrations should strive towards not breaking the existing build. The old and new version should be able to
  co-exist.
- [ ] When introducing breaking changes to a call, the Elasticsearch auditing index for that call must be updated.
  See [Auditing](./auditing.md) for more information.
