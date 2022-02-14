<p align='center'>
<a href='/docs/developer-guide/built-in-provider/compute/intro.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Built-in Provider](/docs/developer-guide/built-in-provider/README.md) / [UCloud/Compute](/docs/developer-guide/built-in-provider/compute/README.md) / Docker Backend
# Docker Backend

UCloud/Compute provides full coverage of the `docker` feature-set. This implementation is powered by the
[Volcano](https://volcano.sh) scheduler for [Kubernetes](https://kubernetes.io).

## Architecture

UCloud/Compute uses a plugin based architecture for handling . All plugins are managed by the `JobManagement` class.
During job submission, this will allow plugins to modify the resulting `VolcanoJob`. Plugins can also be used for
various other tasks, such as accounting and additional job validation.

<!-- ktclassref:app-kubernetes-service:services.JobManagementPlugin -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
interface JobManagementPlugin {
    /**
     * Provides a hook for plugins to modify the [VolcanoJob]
     */
    suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {}

    /**
     * Provides a hook for plugins to cleanup after a job has finished
     *
     * Note: Plugins should assume that partial cleanup might have taken place already.
     */
    suspend fun JobManagement.onCleanup(jobId: String) {}

    /**
     * Called when the job has been submitted to Kubernetes and has started
     *
     * Note: Unlike [onCreate] the value from [jobFromServer] should not be mutated as updates will not be pushed to
     * the Kubernetes server.
     */
    suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {}

    /**
     * Called when the job completes
     *
     * Note: Unlike [onCreate] the value from [jobFromServer] should not be mutated as updates will not be pushed to
     * the Kubernetes server.
     */
    suspend fun JobManagement.onJobComplete(jobId: String, jobFromServer: VolcanoJob) {}

    /**
     * Called when the [JobManagement] system decides a batch of [VolcanoJob] is due for monitoring
     *
     * A plugin may perform various actions, such as, checking if the deadline has expired or sending accounting
     * information back to UCloud app orchestration.
     */
    suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {}
}
```
<!--</editor-fold>-->
<!-- /ktclassref -->

## Plugins

### Accounting

<!-- ktclassref:app-kubernetes-service:services.AccountingPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
No documentation
<!--</editor-fold>-->
<!-- /ktclassref -->

### ConnectToJob

<!-- ktclassref:app-kubernetes-service:services.ConnectToJobPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
No documentation
<!--</editor-fold>-->
<!-- /ktclassref -->

### Expiry

<!-- ktclassref:app-kubernetes-service:services.ExpiryPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
No documentation
<!--</editor-fold>-->
<!-- /ktclassref -->

### FairShare

<!-- ktclassref:app-kubernetes-service:services.FairSharePlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
Volcano uses namespaces to control fair-share (used to differentiate between different users). We
will use a namespace for every type of application owner. That is, if the owner is a user we will create a
namespace for them. If the application has a project attached to it we will use a namespace dedicated to
the project. Namespace creation is done, as needed, by the [FairSharePlugin].
<!--</editor-fold>-->
<!-- /ktclassref -->

### FileMount

<!-- ktclassref:app-kubernetes-service:services.FileMountPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which mounts user-input into the containers
<!--</editor-fold>-->
<!-- /ktclassref -->

### KataContainer

<!-- ktclassref:app-kubernetes-service:services.KataContainerPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which enables support for Kata Containers
<!--</editor-fold>-->
<!-- /ktclassref -->

### Minikube

<!-- ktclassref:app-kubernetes-service:services.MinikubePlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which adds better support for minikube by adding a LoadBalancer service for the Volcano job
<!--</editor-fold>-->
<!-- /ktclassref -->

### Miscellaneous

<!-- ktclassref:app-kubernetes-service:services.MiscellaneousPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which performs miscellaneous tasks

These tasks have the following in common:

- They can be implemented in only a few lines of code
- No other code depends on them
- They can all be run near the end of the plugin pipeline
<!--</editor-fold>-->
<!-- /ktclassref -->

### MultiNode

<!-- ktclassref:app-kubernetes-service:services.MultiNodePlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which adds information about other 'nodes' in the job

A 'node' in this case has a rather loose definition since there is no guarantee that the individual jobs will be
placed on a physically different node. This all depends on the Volcano scheduler.

The following information will be added to the `/etc/ucloud` folder:

- `rank.txt`: A file containing a single line with a 0-indexed rank within the job
- `number_of_nodes.txt`: A file containing a single line with a number indicating how many nodes are participating
in this job.
- `nodes.txt`: A file containing a single line of text for every node. Each line will contain a routable
hostname or IP address.
- `node-$rank.txt`: A file containing a single line of text for the $rank'th node. Format will be identical to the
one used in `nodes.txt.
<!--</editor-fold>-->
<!-- /ktclassref -->

### NetworkLimit

<!-- ktclassref:app-kubernetes-service:services.NetworkLimitPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
No documentation
<!--</editor-fold>-->
<!-- /ktclassref -->

### Parameter

<!-- ktclassref:app-kubernetes-service:services.ParameterPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which takes information from [ApplicationInvocationDescription.parameters] and makes the information
available to the user-container

Concretely this means that the following changes will be made:

- The container will receive a new command
- Environment variables will be initialized with values from the user
<!--</editor-fold>-->
<!-- /ktclassref -->

### Proxy

<!-- ktclassref:app-kubernetes-service:services.ProxyPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which sends [ProxyEvents.events] to notify this and other instances of app-kubernetes to configure the
proxy server
<!--</editor-fold>-->
<!-- /ktclassref -->

### SharedMemory

<!-- ktclassref:app-kubernetes-service:services.SharedMemoryPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which adds a /dev/shm

The size of the shared memory device is set to be limited by the RAM allocated to the job itself. If the job has
no RAM reservation attached it will default to 1GB of RAM.
<!--</editor-fold>-->
<!-- /ktclassref -->

### Task

<!-- ktclassref:app-kubernetes-service:services.TaskPlugin:documentationOnly=true -->
<!--<editor-fold desc="Generated documentation">-->
A plugin which initializes the Volcano task

This will create a single task that will start the user's container in the appropriate amount of copies with the
correct resource (CPU/RAM/GPU) allocation.

Most other plugins depend on this plugin having run.
<!--</editor-fold>-->
<!-- /ktclassref -->

## Connecting to Kubernetes Cluster

UCloud will auto-configure the connection to the Kubernetes cluster using either in-cluster configuration, from
the `ServiceAccount`, or using the `~/.kube/config` file. The Kubernetes client is implemented in `service-lib`.

## Transferring Files

Files are, for performance reasons, never transferred. All input files used in a job _must_ already be present in
UCloud/Storage. Output files will, similarly, be placed in a folder already
present on UCloud/Storage.

## Interactive Applications

UCloud/Compute supports interactive sessions for VNC, web and terminal access. When an interactive session is opened,
UCloud/Compute will create a unique session identifier which is used to authenticate users to avoid unauthorized access.
This means that even if you have the link to an interactive session, you must also have it verified by the UCloud
orchestrator.

