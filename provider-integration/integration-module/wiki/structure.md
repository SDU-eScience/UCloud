# Structure of the UCloud Integration Module

---

__📝 NOTE:__ This document is still under construction.

---

This document describes the general structure of the UCloud integration module. The UCloud integration module is
implemented in Kotlin/JVM and uses gradle as its build-system. As a result, the overall structure should be familiar
to anyone who has written code with Gradle or Kotlin before.

## The Source Code (`/src`)

### Plugins (`/src/main/kotlin/plugins`)

- The root folder defines Kotlin interfaces for each plugin type
- A sub-folder is dedicated for each type of plugin (e.g. `compute`)
- A sub-folder of each type can be dedicated for larger plugins (e.g. `compute.slurm`)

### Plugin Controllers (`/src/main/kotlin/controllers`)

The plugin controller layer is responsible for receiving communication from UCloud and dispatching calls to the plugin
interface. The plugin controller simplifies the job of plugin authors by implementing common functionality.

### HTTP and IPC (`/src/main/kotlin/http` and `/src/main/kotlin/ipc`)

- The HTTP layer implements UCloud RPC
- The IPC layer implements inter-process communication between the different server modes. IPC is implemented using
  unix domain sockets.

### SQL (`/src/main/kotlin/sql`)

This layer implements access to a SQL database. Database migrations are defined in
`/srv/main/kotlin/sql/migrations`.

## Dependencies (`build.gradle.kts`)

- Kotlin dependencies are defined in `build.gradle.kts`
  - This includes a dependency on the Kotlin/Common UCloud library

## Docker (`Dockerfile` and `/default_config`)

These files contain a description of a Dockerfile. This can also be used as a reference for dependencies required to
run the integration module. See the [README](../README.md) for details on how to use the container for development.
