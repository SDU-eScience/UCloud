# Deployment Procedure

Services are generally first deployed to our development environment for
testing and development purposes.

All deployment goes through Kubernetes. Each service stores its Kubernetes
files in its `k8/` folder. These are kept in sync with the development version.
These files include everything needed to deploy SDUCloud.

When a service has gone through sufficient testing we deploy the ready services
to Kubernetes. The following steps are taken:

1. Create an inventory of services to push
2. Determine if any migrations are needed (also in Kubernetes resources)
3. Run migrations (via `kubectl apply`)
4. Apply new deployments (via `kubectl apply`)
