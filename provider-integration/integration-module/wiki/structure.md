# Structure of the UCloud Integration Module

---

__📝 NOTE:__ This document is still under construction.

---

This document describes the general structure of the UCloud integration module. The UCloud integration module is
implemented in Kotlin/Native and uses gradle as its build-system. As a result, the overall structure should be familiar
to anyone who has written code with Gradle or Kotlin before.

## The Source Code (`/src`)

### Plugins (`/src/nativeMain/plugins`)

- The root folder defines Kotlin interfaces for each plugin type
- A sub-folder is dedicated for each type of plugin (e.g. `compute`)
- A sub-folder of each type can be dedicated for larger plugins (e.g. `compute.slurm`)

Read more [here](./creating_a_plugin.md).

### Plugin Controllers (`/src/nativeMain/controllers`)

The plugin controller layer is responsible for receiving communication from UCloud and dispatching calls to the plugin
interface. The plugin controller simplifies the job of plugin authors by implementing common functionality.

### HTTP and IPC (`/src/nativeMain/http` and `/src/nativeMain/ipc`)

- The HTTP layer implements UCloud RPC using the `libh2o` library.
- The IPC layer implements inter-process communication between the different server modes. IPC is implemented using
  unix domain sockets.

### SQL (`/src/nativeMain/sql`)

This layer implements access to a SQLite3 database. Database migrations are defined in
`/srv/nativeMain/sql/migrations`.

### Tests `/src/nativeTest`

📝 Not yet implemented

## Dependencies (`build.gradle.kts`, `/vendor` and `/src/nativeInterop`)

- Kotlin dependencies are defined in `build.gradle.kts`
  - This includes a dependency on the Kotlin/Common UCloud library
- CInterop dependencies (i.e. dependencies on native code) is defined in multiple locations:
  - `/vendor/` contains header files and statically linked libraries for each dependency
  - `/src/nativeInterop` defines how to use the `/vendor` files
  - `build.gradle.kts` defines a dependency on the `nativeInterop` files

## Docker (`Dockerfile` and `/default_config`)

These files contain a description of a Dockerfile. This can also be used as a reference for dependencies required to
run the integration module. See the [README](../README.md) for details on how to use the container for development.
