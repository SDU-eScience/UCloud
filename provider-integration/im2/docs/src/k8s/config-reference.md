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

  providerBranding:
    title: My Provider title
    shortTitle: Short provider title
    shortDescription: My short provider description
    description: /etc/ucloud/description.md
    url: https://myprovider.com
    logo: "/etc/ucloud/images/provider_logo.png"
    sections:
      - description: /etc/ucloud/section1.md
        image: "/etc/ucloud/images/firsts-section.png"
      - description: /etc/ucloud/section2.md
        image: "/etc/ucloud/images/firsts-section.png"
    productDescription:
      - category: "u1-standard"
        shortDescription: u1-standard provides computation
        section:
          description: /etc/ucloud/aproduct.md
          image: "/etc/ucloud/images/a-cpu.png"
      - category: "storage"
        shortDescription: Provides storage
        section:
          description: /etc/ucloud/bproduct.md
          image: "/etc/ucloud/images/a-hardrive.png"

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
    metadataCatalog:
      # Defaults to true in development mode and false otherwise.
      enabled: false
      # Enables catalog-backed search, recursive sizes, and accounting. Uses the same default as enabled.
      enableIntegration: false
      iops: 45000
      parallelScans: 8
      entriesPerSSTable: 16384

  compute:
    namespace: "ucloud-apps"
    estimatedContainerDownloadSpeed: 14.5

    inference:
      enabled: true
      authority: "ai.example.org"
      provider: development
      development:
        provider: localai
        server: "http://localai:8080/v1"
      access:
        administrators: []
        testers: []

      # provider: dynamo
      # dynamo:
      #   namespace: ucloud-inference

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

    virtualMachines:
      enabled: true
      storage:
        type: "CsiStaticPv" # or "HostPath"
        csi:
          storageClassName: "csi-cephfs-sc"
          driver: "cephfs.csi.ceph.com"
          nodeStageSecretRef:
            name: "csi-cephfs-secret"
            namespace: "default"
          volumeAttributes:
            clusterID: "XXXX"
            fsName: "YYYY"
            rootPath: "/volumes/ucloud"
            staticVolume: "true"
          subpathField: "volumeAttributes.rootPath"

    machines:
      cpu-standard:
        payment:
          type: Resource
          unit: Cpu
          interval: Hourly
        systemReservedCpuMillis: 500 # category-level (default if omitted)

        groups:
          general:
            nameSuffix: CpuV2
            cpu: [2, 4, 8]
            memory: [8, 16, 32]
            cpuModel: "AMD EPYC"
            memoryModel: "DDR4"
            allowContainers: true
            allowVirtualMachines: false

      gpu-a10:
        payment:
          type: Money
          currency: "EUR"
          interval: Hourly

        groups:
          a10:
            nameSuffix: GpuV2
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
## `providerBranding`
Defines the provider’s branding information and the services it exposes.

Many of the properties are self-explanatory, but some properties point at 
physical files on the hard drive, these properties are:

* `description`, points to a Markdown file on the disk.
* `logo`, points to a logo image file on the disk.
* `image`, points to an image file on the disk.

The provider is able to describe itself in more detail by using the following properties:
* `sections`, supplementary information about the provider.
* `productDescription`, information about the provider's products.
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

Inference feature configuration. When enabled, IM stores inference model metadata in its internal model catalog and exposes model catalog management through the CLI.

The [AI inference](./inference/overview.md) article explains the feature, [Model discovery and catalog](./inference/catalog.md) explains the catalog, and [Dynamo deployments](./inference/dynamo.md) explains the Dynamo backend.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable inference features.</dd>

<dt>

`authority` *optional*

</dt>
<dd>

Public hostname of the inference API. Defaults to `chat` followed by `services.compute.web.suffix`.

The hostname must resolve to the gateway of the IM, next to the `web` hostname.
IM serves the OpenAI-compatible API on this hostname. Its root path redirects to the playground in the UCloud web interface.

</dd>

<dt>

`provider` *optional*

</dt>
<dd>

Selects the inference backend integration. Defaults to `development` when omitted.

Allowed values:

* `development`
* `dynamo`

</dd>

<dt>

`development` *(required when `provider` is `development`)*

</dt>
<dd>

Development inference backend configuration.

<dl>
<dt>

`provider`

</dt>
<dd>

Development backend provider. Set to `localai` to enable LocalAI startup/bootstrap behavior in development mode.

</dd>

<dt>

`server`

</dt>
<dd>

Base URL of the OpenAI-compatible inference backend, for example `http://localai:8080/v1`.

Every model call must match this URL exactly, including the scheme, the host and the path. A value that no longer matches the backend fails at request time, not at startup.

Add the models of this backend with `ucloud inference models update`. See [Creating an entry](./inference/catalog.md#creating-an-entry).

</dd>
</dl>

For compatibility with older configuration files, `backendServer` and `developmentProvider` can still be set directly under `inference` when `development` is omitted. Both keys are deprecated. Use the nested `development` object in new configuration files.

</dd>

<dt>

`dynamo` *(required when `provider` is `dynamo`)*

</dt>
<dd>

Dynamo inference discovery configuration.

<dl>
<dt>

`namespace`

</dt>
<dd>

Kubernetes namespace where Dynamo frontend services are discovered. IM discovers services in this namespace with the `-frontend` suffix and registers models exposed by their OpenAI-compatible `/v1/models` endpoint. See [Dynamo deployments](./inference/dynamo.md) for a worked example.

</dd>
</dl>

</dd>

<dt>

`access` *optional*

</dt>
<dd>

Project-based inference access lists.

<dl>
<dt>

`administrators`

</dt>
<dd>

List of project IDs treated as inference administrators. Administrators can see all models in the UI/RPC surfaces intended for model management. Administrators are also automatically added to `testers`.

</dd>

<dt>

`testers`

</dt>
<dd>

List of project IDs used as the default allow-list for newly discovered non-public models.

</dd>
</dl>

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

`privateNetworks` *optional*

</dt>
<dd>

Controls private networks. To enable InfiniBand networks, enable private networks and configure IPv4 address pools for both Ethernet and InfiniBand:

```yaml
privateNetworks:
  enabled: true
  cidrPools: [10.200.0.0/16]
  infiniband:
    enabled: true
    cidrPools: [10.201.0.0/16]
    rdmaResourceName: nvidia.com/rdma_shared
    deviceLabel: ucloud.dk/ibdev
```

Replace the example address pools with ranges suitable for your cluster. InfiniBand networks receive a /24 from the InfiniBand pool. Automatic reclaim is enabled by default. The `rdmaResourceName` and `deviceLabel` values shown are the defaults.

Each InfiniBand-capable node must advertise the RDMA resource and have a device label. The label value is a dash-separated list of device names. Device names cannot contain dashes because dashes are used as separators. IM uses the first device, which must be the same across all eligible nodes. Kubernetes limits label values to 63 characters.

For example:

```shell
kubectl label node NODE ucloud.dk/ibdev=ib0-ib1-ib2
```

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

`virtualMachines` *optional*

</dt>
<dd>

Controls virtual machine support.

<dl>
<dt>

`enabled`

</dt>
<dd>Enable/disable virtual machines.</dd>

<dt>

`storage` *(required if enabled)*

</dt>
<dd>

Virtual machine disk backend configuration.

<dl>
<dt>

`type`

</dt>
<dd>

Storage mode for VM disks.

Allowed values:

* `HostPath`
* `CsiStaticPv`

</dd>

<dt>

`hostPath` *(required when `type` is `HostPath`)*

</dt>
<dd>Host path prefix used for creating VM disk PVs.</dd>

<dt>

`csi` *(required when `type` is `CsiStaticPv`)*

</dt>
<dd>

CSI static PV configuration used when creating VM disk PVs.

<dl>
<dt>

`storageClassName`

</dt>
<dd>Storage class name set on the generated PV and PVC.</dd>

<dt>

`driver`

</dt>
<dd>CSI driver name (for example `cephfs.csi.ceph.com`).</dd>

<dt>

`nodeStageSecretRef` *optional*

</dt>
<dd>

Optional secret reference passed to `csi.nodeStageSecretRef`.

<dl>
<dt>

`name`

</dt>
<dd>Secret name.</dd>

<dt>

`namespace`

</dt>
<dd>Secret namespace.</dd>
</dl>

</dd>

<dt>

`volumeAttributes`

</dt>
<dd>Dictionary of CSI volume attributes copied into the generated PV.</dd>

<dt>

`subpathField`

</dt>
<dd>

Must be on the form `volumeAttributes.&lt;key&gt;` and must reference an existing non-empty key in
`volumeAttributes`. IM appends the job-specific VM path suffix to this attribute when creating the PV.

</dd>
</dl>

</dd>
</dl>

</dd>
</dl>

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

<dt>

`systemReservedCpuMillis` *optional*

</dt>
<dd>

Additional CPU headroom per physical node, in millicores. The default is `500`. The value must not be negative.
Set this option on the machine category, not inside a group.

This headroom is subtracted from Kubernetes allocatable CPU. Do not include CPU that Kubernetes has already removed
from allocatable. Use the headroom for host services and infrastructure requests that the integration cannot observe.
Requests from pods in the application and task namespaces are counted separately; do not include those requests again
in this setting.

Advertised product sizes do not change. The integration calculates CPU requests for each placement using
the selected node's actual capacity. CPU limits remain at the advertised allocation:

```text
jobBudgetMillis = allocatableMillis - systemReservedCpuMillis - observedInfrastructureCpuMillis
cpuRequestMillis = floor(advertisedCpuMillis * jobBudgetMillis / capacityMillis)
cpuLimitMillis = advertisedCpuMillis
```

The job budget is clamped to zero. Placements that would receive a zero CPU request are not scheduled. For fractional
CPU products, `advertisedCpuMillis` includes the product fraction. GPU products use their attached CPU count without
applying the GPU fraction to CPU.

The scheduler checks advertised CPU allocations against physical capacity and actual pod requests, including pending
placements, against allocatable CPU minus the configured headroom. Node size is never inferred from the largest product,
so a category can contain nodes with different CPU capacities.

For example, a node with 384 CPUs of capacity, 376 allocatable CPUs, `systemReservedCpuMillis: 2000`, and no observed
infrastructure requests has a 374-CPU job budget. A product advertising 48 CPUs receives a CPU request of 46.75 CPUs
and a CPU limit of 48 CPUs. Eight such products fill the node's eight GPUs while their requests stay within the CPU budget.

Limits cap CPU bandwidth; they do not reserve specific CPUs for system services. Jobs can use CPU above their requests,
up to their advertised limits and subject to CPU-set restrictions and contention. The configured headroom is scheduling
headroom, not a runtime CPU reservation. When CPU requests differ from limits, pods do not qualify for Guaranteed QoS
or exclusive CPU allocation through the static CPU Manager. Existing pods keep their current requests and limits;
the new request calculation applies when a placement is created.

The previous `systemReservedCpuMillisPerCore` option is no longer supported. Replace it with additional per-node
headroom in `systemReservedCpuMillis`, and review existing fixed reservations for the new meaning.

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

Controls how machine names are generated. Possible values:

* Legacy: `Cpu`, `Memory`, `Gpu`
* V2: `CpuV2`, `MemoryV2`, `GpuV2`

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

`fraction` *optional*

</dt>
<dd>

Fraction for the primary resource in this group. Defaults to `1 / 1` when omitted.

Can be specified either as:

* string shorthand: `"1 / 7"` (whitespace ignored)
* structured object:

```yaml
fraction:
  numerator: 1
  denominator: 7
```

For GPU groups this is used for MIG/fractional GPU normalization and naming. For CPU groups this controls effective CPU allocation (`cpu * numerator / denominator`) for pod resource requests.

</dd>
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
