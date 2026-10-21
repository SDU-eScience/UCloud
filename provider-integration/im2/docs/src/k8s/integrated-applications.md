# Integrated applications

Integrated applications are applications that a provider offers through the platform. This chapter describes two
kinds:

- The applications that UCloud/IM manages itself: [Syncthing](./syncthing.md) and the [integrated
  terminal](./integrated-terminal.md). UCloud/IM runs one job per owner, and it stores the configuration of the owner
  in the module database.
- The [Kubernetes cluster application](./kubernetes-clusters/overview.md). A user creates a Kubernetes cluster with
  it on the machines of the provider. The application manages its own resources and its own user interface.

The rest of this page describes the first kind. Each integrated application of this kind has a per-owner configuration
that can be retrieved, updated, reset, and in some cases restarted.

Internally, each integrated application is represented by:

- A configuration record keyed by (application name, owner)
- A dedicated job registered with UCloud
- An application-specific handler that:
  - validates configuration updates
  - decides whether the integrated job should be running
  - mutates the job spec, pod, service, and network policy where needed

Configurations are stored in the integration module database and cached in memory. Updates use an ETag mechanism to
avoid lost updates when multiple sessions modify the configuration.

## Configuration lifecycle

Integrated application configuration is managed through the UCloud user-interface and is handled by UCloud/IM through
the following operations:

- **Retrieve:** Returns the stored configuration and its ETag when configured. If no stored configuration exists, a
  default configuration is returned.
- **Update:** Updates the configuration if the expected ETag matches the current. If the job does not exist, it is
  registered automatically with the associated configuration.
- **Reset:** Resets the configuration back to the default.
- **Restart:** Triggers a restart via Kubernetes by deleting the integrated application pod (rank 0). The monitoring
  loop will recreate it.
