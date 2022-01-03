# Deployment

This section only covers deployment of the UCloud software. This does not take into account the lower layers including
infrastructure deployment.

## Deployment Infrastructure

### Docker

All services are deployed as a Docker container in Kubernetes.

We host a private Docker registry at https://dreg.cloud.sdu.dk. The majority of images are publicly available for
read-only access. Write access is granted by @DanThrane.

### Kubernetes

Kubernetes handles the orchestration of containers. It is configured via resources that describe the desired state of
the cluster. Common types of Kubernetes resources include deployments and cron jobs. Kubernetes ensures that the state
described in the resources are met in the cluster.

As a result there are no servers to configure or install software on. We simply
describe to Kubernetes how we wish to run our containers and Kubernetes takes
care of the rest. Once a server has joined the Kubernetes cluster it is ready
to run any of our micro-services.

See the [Kubernetes documentation](https://kubernetes.io/) for more details.

Access to Kubernetes is done through Rancher.

### Rancher

[Rancher](https://rancher.com) is the software we use to manage our Kubernetes
cluster. Visit their [webpage](https://rancher.com) for more information.

We have separate Rancher instances for different deployment environments.

Members of the development team can login to the dev system using their GitHub account. Access must be authorized by the
project leader before the system can be used. 

The production system is generally not accessible by developers unless needed.

The project leader controls access to both of these systems.

## Procedure and Backwards Compatibility

Services are generally first deployed to our development environment for testing and development purposes.

All deployment goes through Kubernetes. Each service stores its Kubernetes files in `k8.kts`. These files include
everything needed to deploy UCloud.

When a service has gone through sufficient testing we deploy the ready services to Kubernetes. Before deployment the
following checklist is used for affected software:

1.  The software has been built and tested by Jenkins. Tests should pass and the build should be stable.

2.  Migrations should occur before the deployment of the new software.

3.  Migrations should not break the existing build. The old and new version should be able to co-exist.

4.  Breaking changes in the external interface can only occur in major releases (Semantic versioning)

5.  When introducing breaking changes to a call, the ElasticSearch auditing index for that call should be updated. See
    [Auditing](../../backend/service-common/wiki/auditing.md) for more information.

This checklist purely advisory, it may be bypassed if the team leader/director determines that the risk is sufficiently
low or an update is urgently needed, e.g. in case of urgent security updates.

