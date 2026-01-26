# Integrated terminal

The integrated terminal is an internal integrated application that provides an interactive shell environment.
It runs as a dedicated job whose container is kept idle and is intended to be attached to by the platform. End-users
can do this through the user-interface.

Unlike Syncthing, the integrated terminal does not create an external Service. Its primary purpose is to provide a
compute environment with selected storage mounted.

When the integrated terminal is enabled and eligible to run:

- A dedicated job is registered for the owner.
- The job is configured with a list of folders (UCloud paths).
- The job is started only while it is actively used and is stopped after inactivity.

The integrated terminal is configured by which folders to mount. These are automatically added to the configuration by
the user-interface as the user requests a shell. The terminal will not run unless at least one folder is configured.

Each folder path is validated before the terminal is allowed to run:

- The drive must exist
- The drive must not be locked
- The owner must have access to the drive

The integrated terminal stops itself after a period of inactivity (15 minutes). This is based on the timestamp of
the last recorded key press for the job. If the last activity is older than the inactivity threshold, then the job is
stopped.

## Pod behavior

The container in the terminal pod is modified as follows:

- CPU/memory requests and limits are set to fixed values (500 mCPU and 2GB RAM)
- The container image is selected from a discovered application group (terminal-ubuntu)

The intent is to provide a stable runtime environment for attaching an interactive session. This pod will only be
scheduled on nodes labeled with `ucloud.dk/machine=terminal`.

## Configuration

The integrated terminal feature must be enabled through the configuration. Below is an example configuration:

```yaml
services:
  type: Kubernetes

  compute:
    integratedTerminal:
      enabled: true
```

You must also configure at least one node labeled with `ucloud.dk/machine=terminal`.
