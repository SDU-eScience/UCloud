# Configuration

<div class="info-box warning">
<i class="fa fa-triangle-exclamation"></i>
<div>

This page is still a work-in-progress.

</div>
</div>


This page will serve as a reference to the configuration of the UCloud Integration Module.

The configuration is split up to several files.

<dl>
<dt>

`server.yml`

</dt>
<dd>Communication and internal database configuration for the UCloud Integration Module.</dd>
<dt>

`secrets.yml`

</dt>
<dd>Secret configuration, such as account details, for integrated services.</dd>
<dt>

`config.yml`

</dt>
<dd>
Configuration related to your HPC system, services, etc.
</dd>
</dl>

In the following sections we present examples of each configuration file along with possible options 
for each parameter.

## Server configuration

<figure>

```yaml
refreshToken: "<token-goes-here>"
database:
  embedded: false
  username: postgres
  password: postgrespassword
  database: postgres
  ssl: false
  host:
    address: go-slurm-postgres
```

<figcaption>

Example `server.yml` file.

</figcaption>
</figure>

The `server.yml` file contains the refresh token, used to renew the access tokens for the 
communication from UCloud/Core, along with credentials for the internal Integration Module database, 
which contains the current state.

<div class="info-box warning">
<i class="fa fa-triangle-exclamation"></i>
<div>

The contents of `server.yml` could be used to access data stored by the UCloud Integration Module on 
the local HPC system. **It is strongly recommended that the file permissions for this file is set, 
such that only the `ucloud` user can access it.**

</div>
</div>



<dl>
<dt>

`refreshToken`

</dt>

<dd>
The refresh token is used to renew the (short-lived) access token, which is used by the UCloud/Core 
to make authorized calls to the UCloud Integration Module.
</dd>
</dl>
<dl
<dt>

`database` *optional*

</dt>
<dd>

This section defines connection information for the database used by the UCloud Integration Module 
to store internal data. If this is not defined, the UCloud Integration Module will use its own 
embedded database.

<dl>
<dt>

`embedded`

</dt>
<dd>

Defines if the integration module should use its own internal (embedded) database for storing data 
or not. In case the `database` section is not defined, this will default to `true`. If set to 
`false`, the Integration Module will use the database and credentials defined by the following 
parameters.

</dd>
<dt>

`username`

</dt>
<dd>

The username for the database, in case `embedded` is set to `false`. Note that the user needs to 
have both read and write access to the database.

</dd>
<dt>

`password`

</dt>
<dd>

The password for the database, in case `embedded` is set to `false`.

</dd>
<dt>

`database`

</dt>
<dd>

The name of the database to connect to, in case `embedded` is set to `false`.

</dd>
<dt>

`ssl`

</dt>
<dd>

If set to `true` the Integration Module will only connect to the database using SSL.

If set to `false` the Integration Module will connect to the database without SSL.

The parameter is only used if `embedded` is set to `false`.

</dd>
<dt>

`host`

</dt>
<dd>

The host information used for connecting to the database. See [Host information](#host-information). 
The `port` will default to 5432 if not defined.



</dd>
</dl>
</dd>
</dl>


## Secrets configuration

TODO

## Provider and Services configuration

<figure>

```yaml
provider:
  id: go-slurm

  hosts:
    ucloud:
      address: backend
      port: 8080
      scheme: http
    self:
      address: go-slurm
      port: 8889
      scheme: http
    selfPublic:
      address: go-slurm.localhost.direct
      port: 443
      scheme: https
    ucloudPublic:
      address: ucloud.localhost.direct
      port: 443
      scheme: https

  ipc:
    directory: /var/run/ucloud

  logs:
    directory: /var/log/ucloud
    rotation:
      enabled: true
      retentionPeriodInDays: 180

  envoy:
    directory: /var/run/ucloud/envoy
    executable: /usr/bin/envoy
    funceWrapper: false

services:
  type: Slurm

  identityManagement:
    type: FreeIPA

  fileSystems:
    storage:
      management:
        type: GPFS

      payment:
        type: Resource
        unit: GB

      driveLocators:
        home:
          entity: User
          pattern: "/gpfs/home/#{localUsername}"
          title: "Home"
          freeQuota: 10

        projects:
          entity: Project
          pattern: "/gpfs/work/#{localGroupName}-#{gid}"
          title: "Work"

  ssh:
    enabled: true
    installKeys: true
    host:
      address: frontend.localhost.direct
      port: 22

  licenses:
    enabled: false

  slurm:
    fakeResourceAllocation: true

    accountManagement:
      accounting: # Usage and quota management
        type: Automatic

      accountMapper:
        type: Pattern
        users: "#{localUsername}_#{productCategory}"
        projects: "#{localGroupName}_#{productCategory}"

    web:
      enabled: true
      prefix: "goslurm-"
      suffix: ".localhost.direct"

    machines:
      u1-standard:
        partition: normal
        qos: standard

        nameSuffix: Cpu

        cpu: [ 1, 2, 4 ]
        memory: [ 1, 2, 4 ]

        cpuModel: Model
        memoryModel: Model

        payment:
          type: Resource
          unit: Cpu
          interval: Minutely
```

<figcaption>

Example `config.yml` file

</figcaption>
</figure>

### `provider`

<dl>
<dt>

`id`

</dt>
<dd>The ID for your provider.</dd>
<dt>

`hosts`

</dt>
<dd>
<dl>
<dt>

`ucloud`

</dt>
<dd>

The host information for the UCloud/Core the Integration Module should communicate with. For example 
the host name for the sandbox system, `sandbox.dev.cloud.sdu.dk`. See [Host 
information](#host-information) for more information.

</dd>
<dt>

`self`

</dt>
<dd>

The host information for this HPC system. See [Host information](#host-information) for more 
information.


</dd>
<dt>

`selfPublic`

</dt>
<dd>

Public entry-point to this HPC system. See [Host information](#host-information) for more 
information.

</dd>
<dt>

`ucloudPublic` *optional*

</dt>
<dd>

Public entry-point to the UCloud/Core system the Integration Module should communicate with. If not 
defined, this will default to the same as `hosts` → `ucloud`.

See [Host information](#host-information) for more information.

</dd>
</dl>
</dd>
<dt>

`ipc`

</dt>
<dd>
<dl>
<dt>

`directory`

</dt>
<dd>

The path to the directory used for IPC, e.g. `/var/run/ucloud`.

</dd>
</dl>
</dd>
<dt>

`logs`

</dt>
<dd>

This section specifies information about where the Integration Module is allowed to save log files.

<dl>
<dt>

`directory`

</dt>
<dd>

The path to the directory where logs will be stored. For example `/var/log/ucloud`.

</dd>
<dt>

`rotation` *optional*

</dt>
<dd>
<dl>
<dt>

`enabled`

</dt>
<dd>

Defines if log rotation should be enabled (`true`) or not (`false`). If log rotation is enabled, the 
Integration Module will write logs to the main log file for a specified number of days, defined by 
`retentionPeriodInDays`. Afterwards, the log file will be compressed automatically, and a new main
log file will be used for further logs until the retention period has been reached again.

If `rotation` is not defined, this will default to `false`.

</dd>
<dt>

`retentionPeriodInDays`

</dt>
<dd>This defines the retention period as a number of days.</dd>
</dl>
</dd>
</dl>
</dd>
<dt>

`envoy`

</dt>
<dd>

<dl>
<dt>

`directory`

</dt>
<dd>

The path to the directory that envoy will use for keeping internal state. For example 
`var/run/ucloud/envoy`.

</dd>

<dt>

`managedExternally` *optional*

</dt>
<dd>

`true` or `false`. Defaults to `false`.

</dd>
<dt>

`executable`

</dt>
<dd>

The path to the executable of envoy.

</dd>
<dt>

`funceWrapper`

</dt>
<dd>

`true` or `false`. Defaults to `false`.

</dd>
<dt>

`internalAddressToProvider` *optional*

</dt>
<dd>

The address (URL or IP) to provider. Defaults to `127.0.0.1`.

</dd>
</dl>
</dd>
</dl>

### `services`

<dl>
<dt>

`type`

</dt>
<dd>

Type of system/services provided by the HPC system. Possible values are `Slurm`, `Kubernetes` and 
`Puhuri`.

</dd>
<dt>

`identityManagement`

</dt>
<dd>

<dl>
<dt>

`type`

</dt>
<dd>

Type of system used for identity management, i.e. user and project management. Possible values are 
`Scripted`, `FreeIPA`, and `None`.

</dd>
</dl>

</dd>
<dt>

`fileSystems`

</dt>
<dd>

Defines one or more file systems. Read more under [File systems](#file-systems).

</dd>
<dt>

`ssh`

</dt>
<dd>

<dl>
<dt>

`enabled`

</dt>
<dd>

Informs the Integration Module whether SSH is enabled (`true`) or not (`false`).

</dd>
<dt>

`installKeys`

</dt>
<dd>TODO</dd>
<dt>

`host`

</dt>
<dd>

The host used for SSH. See [Host information](#host-information) for more information.

</dd>
</dl>

</dd>
<dt>

`licenses`

</dt>
<dd>
<dl>
<dt>

`enabled`

</dt>
<dd>

Whether the license product type is available for this provider or not.

</dd>
</dl>
</dd>
<dt>

`slurm`

</dt>
<dd>
<dl>
<dt>

`fakeResourceAllocation`

</dt>
<dd>TODO</dd>
<dt>

`accountManagement` *optional*

</dt>
<dd>

<dl>
<dt>

`accounting`

</dt>
<dd>

<dl>
<dt>

`type`

</dt>
<dd>

Type of accounting to use. Possible values are `Automatic`, `Scripted` and `None`.

</dd>
</dl>

</dd>
<dt>

`accountMapper`

</dt>

<dd>
<dl>
<dt>

`type`

</dt>
<dd>

Technique used for account mapping. Possible values are `Pattern`, `Scripted` or `None`.

</dd>
<dt>

`users` *(required if `type` is `Pattern`)*

</dt>
<dd>

Pattern for mapping UCloud accounts to local accounts.

</dd>
<dt>

`projects` *(required if `type` is `Pattern`)*

</dt>
<dd>

Pattern for mapping UCloud projects to local projects.

</dd>
<dt>

`script` *(required if `type` is `Scripted`)*

</dt>

<dd>

Script for mapping UCloud users and projects to the local system.

<dd>

Script for mapping 

</dd>
</dl>
</dd>
</dl>

</dd>
<dt>

`web`

</dt>
<dd>
<dl>
<dt>

`enabled`

</dt>
<dd>Whether interfaces for applications should be enabled (`true`) or not (`false`).</dd>
<dt>

`prefix`

</dt>
<dd>

The address prefix for web interfaces (if `enabled` is `true`).

</dd>
<dt>

`suffix`

</dt>
<dd>

The address suffix for web interfaces (if `enabled` is `true`).

</dd>
</dl>
</dd>
<dt>

`machines`

</dt>
<dd>

See [Machines](#machines).

</dd>
</dl>
</dd>
</dl>

### Host information {#host-information}

Host information defines the address, port and scheme to a location. For example:

```yaml
address: postgres
port: 8080
scheme: http
```

This states that the container named `postgres` is accessible over `http` on port `8080`.

<dl>
<dt>

`address`

</dt>
<dd>The name/address of the host.</dd>

<dt>

`port` *optional*

</dt>
<dd>The port number to use. If not defined, the Integration Module will attempt to use a reasonable 
default value.</dd>
<dt>

`scheme`

</dt>
<dd>

The scheme to use. For example `http`, `https`, etc.

</dd>
</dl>

### File systems

TODO


### Machines {#machines}

TODO

```
      u1-standard:
        partition: normal
        qos: standard

        nameSuffix: Cpu

        cpu: [ 1, 2, 4 ]
        memory: [ 1, 2, 4 ]

        cpuModel: Model
        memoryModel: Model

        payment:
          type: Resource
          unit: Cpu
          interval: Minutely

```
