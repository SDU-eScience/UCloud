# Deployment

This section only covers deployment of the UCloud software. This does not
take into account the lower layers including infrastructure deployment.

## Archiva

Apache Archiva is a piece of software used to host private maven
repositories. Archiva is currently hosted at https://archiva.dev.cloud.sdu.dk.
The project leader provides access to Archiva.

## Docker

All services are deployed as a Docker container in Kubernetes.

We host a private Docker registry at https://registry.cloud.sdu.dk (SDU
network required). Access is currently a bit of a manual process see #258.

## Kubernetes

Kubernetes handles the orchestration of containers. It is configured via
resources that describe the desired state of the cluster. Common types of
Kubernetes resources include deployments and cron jobs. Kubernetes ensures
that the state described in the resources are met in the cluster.

As a result there are no servers to configure or install software on. We simply
describe to Kubernetes how we wish to run our containers and Kubernetes takes
care of the rest. Once a server has joined the Kubernetes cluster it is ready
to run any of our micro-services.

See the [Kubernetes documentation](https://kubernetes.io/) for more details.

Access to Kubernetes is done through Rancher.

## Rancher

[Rancher](https://rancher.com) is the software we use to manage our Kubernetes
cluster. Visit their [webpage](https://rancher.com) for more information.

We have two Rancher instances (SDU network required):

1. https://rancher-dev.cloud.sdu.dk (Development)
2. https://rancher.cloud.sdu.dk (Production)

Members of the development team can login to the dev system using their GitHub
account. Access must be authorized by the project leader before the system can
be used. 

The production system is generally not accessible by developers unless needed.

The project leader controls access to both of these systems.

## Procedure and Backwards Compatibility

Services are generally first deployed to our development environment for
testing and development purposes.

All deployment goes through Kubernetes. Each service stores its Kubernetes
files in its `k8/` folder. These are kept in sync with the development version.
These files include everything needed to deploy UCloud.

When a service has gone through sufficient testing we deploy the ready
services to Kubernetes. Before deployment the following checklist is used for
affected software:

|uncheck_|  The software has been built and tested by Jenkins. Tests must pass and the build must be stable.

|uncheck_|  Migrations must occur before the deployment of the new software.

|uncheck_|  Migrations must not break the existing build. The old and new version must be able to co-exist.

|uncheck_|  Breaking changes in the external interface can only occur in major releases (Semantic versioning)

|uncheck_|  When introducing breaking changes to a call, the Elasticsearch auditing index for that call must be updated. See [Auditing](../../backend/service-common/wiki/auditing.html) for more information.

The deployment procedure itself is as follows:

1. Create an inventory of services to push
2. Determine if any migrations are needed (also in Kubernetes resources)
3. Run migrations (via `kubectl apply`)
4. Apply new deployments (via `kubectl apply`)

.. |uncheck_| raw:: html

    <input disabled="" type="checkbox">

<br>
