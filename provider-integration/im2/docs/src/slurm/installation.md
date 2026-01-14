# Installation

This document will guide you through the process of obtaining and installing UCloud/IM for Slurm. By the end of this
document you will be connected to UCloud's sandbox environment. Please see [this](#TODO) document for more information
about becoming a provider in the production environment.

## Prerequisites

The UCloud Integration Module should be deployed on the HPC frontend or a node with a similar configuration.

System minimum requirements:

- __OS:__ Linux (any distribution)
- __CPU:__ x86_64 with at least 4 vCPU
- __Memory:__ 16GB

HPC cluster minimum requirements:

- __Slurm:__ Version 20.02 and above are guaranteed to work, older versions might work.
- __Filesystem:__ A distributed filesystem exposing a standard POSIX interface
    - UCloud/IM for Slurm has integrations with: GPFS

In addition, the machine must be able to perform the following actions:

- Communicate with Slurm using the normal CLI commands (e.g. `sinfo`).
- Access to a distributed filesystem. Mount-point must be the same as on the compute nodes.
- The machine must use the same user database as the compute nodes, such that users and ids are consistent across the
  entire cluster.
- `sudo` must be installed.
- Must allow out-bound Internet connectivity (Note: Production environments also require some in-bound Internet
  connectivity).
- Must have at least one partition which should accept UCloud jobs

In order to follow along with this document you also need:

- You must have one test user on the system. The test user must have access to:
    - Some folder(s) on the distributed filesystem (e.g. `/home/$USERNAME`)
    - An associated Slurm account which can submit jobs to a Slurm partition

## Obtaining the Software

<div class="tabbed-card">

<div data-title="RHEL" data-icon="fa-brands fa-redhat">

RPM packages for RHEL are automatically built. You can download the latest RPM package
[here](https://github.com/sdu-escience/ucloud/releases/latest). Once you have downloaded the
package, you can install it with
the following command:

TODO Consider if this installation script should enforce a cgroup on user instances since this might limit abuse of
certain potential features. (Forkable applications and terminal access)

```terminal
$ sudo dnf install -y ucloud-im-rhel.rpm
```

This package will automatically configure and install UCloud/IM and you are ready for the next steps. You can verify
that the installation was successful by running the following command:

```terminal
$ ucloud version
UCloud/IM 2024.1.0
```

</div>

</div>

## Registering as a Provider

<div class="info-box info">
<i class="fa fa-info-circle"></i>
<div>

This section will create a secret file containing your credentials. By default, it will be located at
`/etc/ucloud/server.yml`. Please keep this file safe and keep a backup of it. In case of a re-installation, you
should aim to re-use this file.

</div>
</div>

Before you can start UCloud/IM, you must register as a service provider. This only needs to be done
once. You may receive a test provider in UCloud's sandbox environment by contacting the [support
team](https://support.cloud.sdu.dk).

<!--

You can
automatically register with the sandbox environment by running the following command:

```terminal
$ sudo ucloud register --sandbox
Please finish the registration by going to https://sandbox.dev.cloud.sdu.dk/app/provider/registration?token=XXXXXXXXXXXX

Waiting for registration to complete (Please keep this window open)...
Registration complete! You may now proceed with the installation.
```

-->

Once the registration is complete, you now have a provider on UCloud's sandbox environment. From the UCloud interface,
you should now be able to select your provider project from the project switcher. You can add other UCloud users to help
manage your provider by inviting them through the interface. See the [end-user documentation](https://docs.cloud.sdu.dk)
for more details.

<figure>

![](./project_switcher.png)

<figcaption>

You will be able to manage parts of your provider through UCloud's interface. To do this, you must first select the
provider project which you are automatically added to.

</figcaption>
</figure>

## Testing the Configuration

In order to test the configuration, we must first ensure that UCloud/IM is turned on.

<div class="tabbed-card">

<div data-title="RHEL" data-icon="fa-brands fa-redhat">

You can start UCloud/IM via `systemd` by running the following command:

```terminal
$ sudo systemctl start ucloud-im
```

You can verify that UCloud/IM is running by checking the status.

```terminal
$ sudo systemctl status ucloud-im
[green]●🖌️ ucloud-im.service - UCloud Integration Module
   Loaded: loaded (/usr/lib/systemd/system/ucloud-im.service; enabled; vendor preset: disabled)
   Active: active (running)
```

In case of errors, you should check the log files in `/var/log/ucloud`. At this stage, you should focus on:

```terminal
$ sudo less /var/log/ucloud/server.log
```

You might also be able to find useful information in the journal:

```terminal
$ sudo journalctl -u ucloud-im
```

You can find more guidance for troubleshooting in our [troubleshooting guide](../ops/troubleshooting.md).

</div>

</div>

The following chapters will help you configure UCloud/IM, which is required before you can start the service. Once
everything is configured, you should be able to access the filesystem and Slurm.
