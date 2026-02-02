# Configuration (Kubernetes)

This page serves as a reference to the configuration when `services.type` is set to `Kubernetes`.

The configuration is split into several files:

<dl>
<dt>

`server.yml`

</dt>
<dd>Communication and internal database configuration for the Integration Module.</dd>

<dt>

`config.yml`

</dt>
<dd>Configuration related to your Kubernetes environment, services, etc.</dd>
</dl>

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


## Provider and Services configuration (Kubernetes)

<figure>

```yaml
provider:
  id: my-k8s-provider

  hosts:
    ucloud:
      address: cloud.sdu.dk
      port: 443
      scheme: https
    self:
      address: provider.example.com
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
  type: Kubernetes

  fileSystem:
    name: "storage"
    mountPoint: "/mnt/storage"
    trashStagingArea: "/mnt/storage/trash"
    claimName: "ucloud-user-data"
    scanMethod:
      type: Walk

  compute:
    namespace: "ucloud-apps"
    estimatedContainerDownloadSpeed: 14.5

    inference:
      enabled: true
      ollamaDevMode: false

    modules:
      tools:
        subPath: "tools"
        claimName: "shared-tools-pvc"
        # hostPath: "/srv/tools"     # exactly one of claimName/hostPath

    web:
      enabled: true
      prefix: "apps-"
      suffix: ".example.org"

    publicIps:
      enabled: true
      name: "public-ip"

    publicLinks:
      enabled: true
      name: "public-links"
      prefix: "app-"
      suffix: ".example.com"

    ssh:
      enabled: true
      ipAddress: "203.0.113.10"
      hostname: "ssh.example.com"
      portMin: 30000
      portMax: 31000

    syncthing:
      enabled: true
      ipAddress: "203.0.113.11"
      portMin: 32000
      portMax: 33000
      relaysEnabled: true
      developmentSourceCode: "/opt/ucloud/syncthing-dev" # optional

    integratedTerminal:
      enabled: true

    virtualMachineStorageClass: "fast-ssd"  # optional

    machines:
      cpu-standard:
        payment:
          type: Resource
          unit: Cpu
          interval: Hourly

        groups:
          general:
            nameSuffix: Cpu
            cpu: [2, 4, 8]
            memory: [8, 16, 32]
            cpuModel: "AMD EPYC"
            memoryModel: "DDR4"
            allowContainers: true
            allowVirtualMachines: false
            systemReservedCpuMillis: 500

      gpu-a10:
        payment:
          type: Money
          currency: "EUR"
          interval: Hourly

        groups:
          a10:
            nameSuffix: Gpu
            gpuType: "nvidia.com/gpu"
            cpu: [8]
            memory: [64]
            gpu: [1, 2]
            price: [1.25, 2.50]
            gpuModel: "NVIDIA A10"
            allowContainers: true
            allowVirtualMachines: true
            customRuntime: "nvidia"
```

<figcaption>

Example `config.yml` file for Kubernetes.

</figcaption>
</figure>

---

## `services`

<dl>
<dt>

`type`

</dt>
<dd>

Must be `Kubernetes`.

</dd>

<dt>

`fileSystem`

</dt>
<dd>

Configuration for the filesystem backing user/project files in the Kubernetes provider. See [File system](#kubernetes-file-system).

</dd>

<dt>

`compute`

</dt>
<dd>

Compute configuration, including machine catalog, networking features, optional SSH/syncthing exposure, and modules. See [Compute](#kubernetes-compute).

</dd>
</dl>

---

## File system {#kubernetes-file-system}

<dl>
<dt>

`name`

</dt>
<dd>

A name for the filesystem.

</dd>

<dt>

`mountPoint`

</dt>
<dd>

A folder path where the filesystem is mounted. Must exist and be readable/writable.

</dd>

<dt>

`trashStagingArea`

</dt>
<dd>

A folder path used as a staging area for trash/deletions. Must exist and be readable/writable.

</dd>

<dt>

`claimName`

</dt>
<dd>

The Kubernetes PVC claim name used for the filesystem.

</dd>

<dt>

`scanMethod` *optional*

</dt>
<dd>

Controls how the filesystem is scanned.

<dl>
<dt>

`type`

</dt>
<dd>

Possible values:

* `Walk` (default if `scanMethod` is omitted)
* `Xattr`
* `Development`

</dd>

<dt>

`xattr` *(required if `type` is `Xattr`)*

</dt>
<dd>

Name of the extended attribute used by the scanner.

</dd>
</dl>

</dd>
</dl>

---

## Compute {#kubernetes-compute}

<dl>
<dt>

`namespace` *optional*

</dt>
<dd>

Kubernetes namespace used for workloads. If omitted or empty, defaults to `ucloud-apps`.

</dd>

<dt>

`estimatedContainerDownloadSpeed` *optional*

</dt>
<dd>

A floating point number (MB/s). Defaults to `14.5`.

</dd>

<dt>

`imSourceCode` *optional*

</dt>
<dd>

Optional path to Integration Module source code (used for development/diagnostics).

</dd>

<dt>

`inference` *optional*

</dt>
<dd>

Inference feature toggles.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable inference features.</dd>

<dt>

`ollamaDevMode` *optional*

</dt>
<dd>

Only used if `enabled` is `true`. Turns on development mode using ollama.

</dd>
</dl>

</dd>

<dt>

`modules` *optional*

</dt>
<dd>

A dictionary of named module entries.

Each module entry:

<dl>
<dt>

`subPath`

</dt>
<dd>Required. A sub-path within the volume source.</dd>

<dt>

`hostPath` *(exactly one of `hostPath` / `claimName` must be set)*

</dt>
<dd>Use a host path as the module’s volume source.</dd>

<dt>

`claimName` *(exactly one of `hostPath` / `claimName` must be set)*

</dt>
<dd>Use a PVC claim name as the module’s volume source.</dd>
</dl>

Constraints:

* Module names must be unique.
* The configuration must set exactly one of `hostPath` and `claimName`.

</dd>

<dt>

`machineImpersonation` *optional*

</dt>
<dd>

A dictionary mapping one machine "name" to another. This is used to treat one machine SKU as another.

</dd>

<dt>

`machines`

</dt>
<dd>

A dictionary of machine categories (compute products). See [Machines](#kubernetes-machines).

</dd>

<dt>

`web` *optional*

</dt>
<dd>

Controls web interfaces for applications.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable web interfaces.</dd>

<dt>

`prefix` *(required if enabled)*

</dt>
<dd>Address prefix used when constructing web hostnames.</dd>

<dt>

`suffix` *(required if enabled)*

</dt>
<dd>Address suffix used when constructing web hostnames.</dd>
</dl>

</dd>

<dt>

`publicIps` *optional*

</dt>
<dd>

Controls the public IP feature.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable public IP support.</dd>

<dt>

`name` *optional*

</dt>
<dd>

Defaults to `public-ip` if omitted.

</dd>
</dl>

</dd>

<dt>

`publicLinks` *optional*

</dt>
<dd>

Controls public links.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable public link support.</dd>

<dt>

`name` *optional*

</dt>
<dd>

Defaults to `public-links` if omitted.

</dd>

<dt>

`prefix` *(required if enabled)*

</dt>
<dd>Address prefix used when constructing link hostnames.</dd>

<dt>

`suffix` *(required if enabled)*

</dt>
<dd>Address suffix used when constructing link hostnames.</dd>
</dl>

</dd>

<dt>

`ssh` *optional*

</dt>
<dd>

Expose SSH access.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable SSH feature.</dd>

<dt>

`ipAddress` *(required if enabled)*

</dt>
<dd>Must be a valid IP address string.</dd>

<dt>

`hostname` *optional*

</dt>
<dd>An optional hostname to associate with SSH.</dd>

<dt>

`portMin` *(required if enabled)*

</dt>
<dd>Minimum port (must be within valid TCP port range).</dd>

<dt>

`portMax` *(required if enabled)*

</dt>
<dd>Maximum port (must be within valid TCP port range).</dd>
</dl>

</dd>

<dt>

`syncthing` *optional*

</dt>
<dd>

Exposes the Syncthing integration.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable Syncthing feature.</dd>

<dt>

`ipAddress` *(required if enabled)*

</dt>
<dd>Must be a valid IP address string.</dd>

<dt>

`portMin` *(required if enabled)*

</dt>
<dd>Minimum port (must be within valid TCP port range).</dd>

<dt>

`portMax` *(required if enabled)*

</dt>
<dd>Maximum port (must be within valid TCP port range).</dd>

<dt>

`developmentSourceCode` *optional*

</dt>
<dd>Optional path used for development.</dd>

<dt>

`relaysEnabled` *optional*

</dt>
<dd>Boolean toggle for relays.</dd>
</dl>

</dd>

<dt>

`integratedTerminal` *optional*

</dt>
<dd>

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable an integrated terminal feature.</dd>
</dl>

</dd>

<dt>

`virtualMachineStorageClass` *optional*

</dt>
<dd>

Optional Kubernetes storage class name used for virtual machine storage.

</dd>
</dl>

---

## Machines {#kubernetes-machines}

`compute.machines` is a dictionary of machine categories. Each category has:

<dl>
<dt>

`payment`

</dt>
<dd>

Defines how this machine category is charged. See [Payment](#payment).

Important constraints:

* If `payment.type` is `Money`, each machine configuration must provide `price` and it must be greater than 0.
* If `payment.type` is `Resource`, each machine configuration must not specify `price`.

</dd>

<dt>

`groups` *optional*

</dt>
<dd>

If omitted, the category itself is treated as a single implicit group.

If present, it must be a dictionary of groups. Each group defines compatible machine sizes and behavior.

</dd>
</dl>

### Machine group options

Each group supports:

<dl>
<dt>

`cpu`, `memory`, `gpu`

</dt>
<dd>

Lists of supported sizes.

* `cpu` and `memory` are required and must be lists of the same size.
* `gpu` is optional, it must either be omitted or be the same size as `cpu`.
* If `price` is present, it must have the same length as `cpu`.

A "machine configuration" is formed by zipping the lists by index.

</dd>

<dt>

`price` *optional*

</dt>
<dd>

List of prices matching the `cpu` list length. Only valid/required when the machine category payment type is `Money`.

</dd>

<dt>

`nameSuffix` *optional*

</dt>
<dd>

Controls how machine names are suffixed. Possible values: `Cpu`, `Memory`, `Gpu`.

Default:

* If `gpu` list is provided, defaults to `Gpu`
* Otherwise defaults to `Cpu`

</dd>

<dt>

`cpuModel`, `memoryModel`, `gpuModel` *optional*

</dt>
<dd>Textual descriptions of underlying hardware models.</dd>

<dt>

`allowVirtualMachines` *optional*

</dt>
<dd>Enable/disable virtual machines for this group (defaults to `false` if omitted).</dd>

<dt>

`allowContainers` *optional*

</dt>
<dd>Enable/disable containers for this group (defaults to `true` if omitted).</dd>

<dt>

`gpuType` *optional*

</dt>
<dd>

GPU resource type string (for example `nvidia.com/gpu`). Defaults to `nvidia.com/gpu` if omitted.

</dd>

<dt>

`customRuntime` *optional*

</dt>
<dd>Optional runtime hint/name for custom container runtimes.</dd>

<dt>

`systemReservedCpuMillis` *optional*

</dt>
<dd>CPU reserved for system overhead, in millicores. Defaults to `500`.</dd>
</dl>

---


### Payment {#payment}

<dl>
<dt>

`type`

</dt>
<dd>

Possible values are `Resource` or `Money`.

</dd>
<dt>

`price` *(required if `type` is `Money`)*

</dt>
<dt>

`currency` *(required if `type` is `Money`)*

</dt>

<dt>

`interval` *optional*

</dt>
<dd>

Possible values are `Minutely`, `Hourly` and `Daily`.

</dd>
<dt>

`unit`

</dt>
<dd>

Possible values are `GB`, `TB`, `PB`, `EB`, `GiB`, `TiB`, `PiB` and `EiB` for storage products, and
`Cpu`, `Memory` and `Gpu` for compute products.

</dd>
</dl>

---

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
