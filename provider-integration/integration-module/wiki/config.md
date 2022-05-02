# Configuration

This document contains a complete reference for the configuration which UCloud/IM accepts. This document is broken
into several sections, one for each configuration file.

## `core.yaml`

```yaml
# The core contains information which is relevant regardless of server mode. This also means that we cannot put any
# potentially sensitive information in this configuration file.

# (Mandatory) Contains the ID of the provider as registered with UCloud/Core
providerId: sophia

# (Optional) Inter-process communication
ipc:
  # (Mandatory) The directory to use for IPC
  directory: /var/run/ucloud

# (Optional) Logging configuration
logs:
  # (Mandatory) The directory to use for logs
  directory: /var/log/ucloud

# (Mandatory) Relevant hosts used for networking
hosts:
  # (Optional) Ourselves, this is optional but some plugins require it to be specified
  self:
    host: sophia.dtu.dk
    scheme: http
    port: 80

  # (Mandatory) The location of the UCloud/Core server used
  ucloud:
    host: cloud.sdu.dk
    scheme: https
    port: 443

```

## `server.yaml`

```yaml
# Server: This mode receives traffic which is not bound to a specific user. This mode is also responsible
#         for providing core services to the other modes. This includes: access to the database, routing
#         of traffic and launching user instances. Other instances communicate with the server instance
#         through inter-process communication (IPC).

# (Mandatory) The refresh token used for communication with UCloud/Core. It is exchanged between the orchestrator and 
# the provider during the registration step. For more information see:
# https://docs.cloud.sdu.dk/dev/docs/developer-guide/accounting-and-projects/providers.html#example-registering-a-provider
refreshToken: foo-bar-baz

# (Optional) Network configuration of the server
network:
  # (Optional) Address used for listening (HTTP traffic)
  listenAddress: 0.0.0.0
  # (Optional) Port used for listening (HTTP traffic)
  listenPort: 42000

# (Optional) Contains configuration which is helpful for development but should not be used for production.
developmentMode:
  # (Optional) Contains a list of predefined user instances which should be started by the developer
  predefinedUserInstances:
  - username: "FooBar#1234" # (Mandatory) UCloud username
    userId: 1000            # (Mandatory) Unix UID
    port: 41230             # (Mandatory) Port of the user mode instance

```

## `plugins.yaml`

```yaml
# Plugin configuration used for all plugins. This configuration file is readable by users also. The configuration file
# contains several sections for different plugin types. Some plugins are based on a product configuration, in these
# cases multiple instances of a single copy can run, targeting different products.

#######################################################################################################################
# (Optional) Connection plugins
#######################################################################################################################

# ---------------------------------------------------------------------------------------------------------------------
# OpenIdConnect: Used for integrating with OIDC
# ---------------------------------------------------------------------------------------------------------------------
connection:
  # Specifies the plugin type. The remaining configuration is specific to this plugin.
  type: OpenIdConnect

  # Certificate used for verifying OIDC tokens.
  certificate: /etc/ucloud/oidc/cert.pem

  # Determines for how long we should consider the connection valid. Once the time-to-live has expired, the user
  # must reestablish the connection.
  mappingTimeToLive:
    days: 7

  # Endpoints used in the OIDC protocol.
  endpoints:
    auth: https://oidc.sophia.dtu.dk/auth
    token: https://oidc.sophia.dtu.dk/token

  # Client information used in the OIDC protocol.
  client:
    id: foobar
    secret: foobarbaz

  # Extensions which will be invoked by the plugin when certain events occur.
  extensions:
    # Invoked when the connection has completed.
    onConnectionComplete: /opt/ucloud/extensions/oidc-complete

# ---------------------------------------------------------------------------------------------------------------------
# UCloud: Uses UCloud as an identity provider
# ---------------------------------------------------------------------------------------------------------------------
connection:
  type: UCloud

  redirectTo: http://localhost:9000/app
  extensions:
    onConnectionComplete: /opt/ucloud/example-extensions/ucloud-connection

# ---------------------------------------------------------------------------------------------------------------------
# Ticket: A manual connection plugin. Useful if users are normally created by hand.
# ---------------------------------------------------------------------------------------------------------------------
connection:
  type: Ticket

#######################################################################################################################
# (Optional) Connection plugins
#
# Plugin for managing mapping between UCloud projects and local projects.
#######################################################################################################################

# ---------------------------------------------------------------------------------------------------------------------
# Simple: A project plugin delegating all responsibilities to extension scripts
# ---------------------------------------------------------------------------------------------------------------------
projects:
  # Specifies the plugin type. The remaining configuration is specific to this plugin.
  type: Simple

  # A namespace for group IDs. All group IDs passed to the extensions will be allocated starting at the number
  # specified.
  unixGroupNamespace: 451000

  # Extensions which will be invoked by the plugin when certain events occur.
  extensions:
    all: /opt/ucloud/extensions/project-handler # (Optional) Can be used to capture all events

    projectRenamed: /opt/ucloud/extensions/project-handler

    membersAddedToProject: /opt/ucloud/extensions/project-handler
    membersRemovedFromProject: /opt/ucloud/extensions/project-handler

    projectArchived: /opt/ucloud/extensions/project-handler
    projectUnarchived: /opt/ucloud/extensions/project-handler

    roleChanged: /opt/ucloud/extensions/project-handler

    groupCreated: /opt/ucloud/extensions/project-handler
    groupRenamed: /opt/ucloud/extensions/project-handler
    groupDeleted: /opt/ucloud/extensions/project-handler

#######################################################################################################################
# (Optional) Job plugins
#
# Plugins which manage jobs (compute). Multiple job plugin instances can run in a single integration module, each
# instance targets a different sub-set of products. Each key of the "jobs" section refers to a name for the
# instance. Each instance must specify the plugin type and which products they match.
#
# The "matches" selector is validated against the compute products specified in `products.yaml`.
#######################################################################################################################

# ---------------------------------------------------------------------------------------------------------------------
# Slurm
# ---------------------------------------------------------------------------------------------------------------------
jobs:
  standard:
    type: Slurm          # Use Slurm
    matches: u1-standard # Match any u1-standard product

    # Slurm config
    partition: standard

  special:
    type: Slurm
    matches: u1-standard/u1-standard-special # Match the u1-standard-special product in u1-standard
    partition: special

  default:
    type: Slurm
    matches: * # Match any product which isn't covered by another plugin
    partition: default

#######################################################################################################################
# (Optional) File plugins
#######################################################################################################################

# ---------------------------------------------------------------------------------------------------------------------
# Posix
# ---------------------------------------------------------------------------------------------------------------------

files:
  default:
    type: Posix
    matches: *

#######################################################################################################################
# (Optional) File collection plugins
#######################################################################################################################

# ---------------------------------------------------------------------------------------------------------------------
# Posix
# ---------------------------------------------------------------------------------------------------------------------

fileCollections:
  default:
    type: Posix
    matches: *

    simpleHomeMapper:
    - title: Home
      prefix: /home

    extensions:
      additionalCollections: /opt/ucloud/extensions/collections

```

## `products.yaml`

```yaml
# Contains a list of products which are known to the provider. These products must be registered in UCloud/Core also.
# No details are placed in the configuration and are instead sent from the orchestrator.
compute:
  u1-standard:
  - u1-standard-1
  - u1-standard-2
  - u1-standard-4
  - u1-standard-8
  - u1-standard-16
  - u1-standard-32
  - u1-standard-64
  - u1-standard-special

  u1-fat:
  - u1-fat-1
  - u1-fat-2
  - u1-fat-4
  - u1-fat-8

  u2-gpu:
  - u2-gpu-1
  - u2-gpu-2
  - u2-gpu-4
  - u2-gpu-8

storage:
  u1-cephfs:
  - u1-cephfs

```

## `frontend_proxy.yaml`

```yaml
sharedSecret: foo-bar-baz-bar-baz
remote:
  host: internal-node-aa-01
  port: 42000
  scheme: http

```

