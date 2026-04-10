# API reference

This page focuses on the high-level APIs used by UCX applications:

- `ucloud.dk/shared/pkg/ucx/ucxsvc` (recommended helpers)
- `ucloud.dk/shared/pkg/ucx/ucxapi` (typed RPC calls)

## `ucxsvc` high-level helpers

`ucxsvc` wraps common stack workflows and resource orchestration.

### Stack lifecycle

| Function                                    | Purpose                                                 |
|---------------------------------------------|---------------------------------------------------------|
| `StackCreate(app, id, stackType)`           | Allocate a new stack context and labels/mount metadata  |
| `StackFromJob(app, job)`                    | Reconstruct stack context from a job with stack labels  |
| `StackWriteFile(stack, path, data)`         | Write stack file with default permissions               |
| `StackWriteFileEx(stack, path, data, mode)` | Write stack file with explicit permissions              |
| `StackWriteInitScript(stack, script)`       | Write init script and return labels for VM/job creation |
| `StackCopyFile(stack, fileName)`            | Ask frontend to copy stack file contents to clipboard   |
| `StackDownloadFile(stack, fileName)`        | Ask frontend to download stack file                     |
| `StackConfirmAndOpen(stack)`                | Confirm stack and open it in frontend                   |

Error handling for stack helpers is controlled by the stack state itself.
When a stack operation fails, `ucxsvc` marks `stack.Ok = false` and sends a user-facing failure message.
After this, stack helper calls become safe no-ops, so it is generally safe to keep calling the helper functions in
sequence.

If you prefer explicit early exit, check `stack.Ok` and return immediately:

```go
if !stack.Ok {
    return
}
```

Resources created as part of a stack are automatically cleaned up if `StackConfirmAndOpen(stack)` is not called within
two minutes of stack creation. As a result, stack flows must end by calling `StackConfirmAndOpen(stack)`. Note that
`StackConfirmAndOpen(stack)` will itself only confirm the stack creation if the internal `Ok` property is `true` and it
is thus safe to call it without checking the `Ok` property prior to calling it.

Each stack also has a state directory created automatically by `StackCreate(...)`. `StackWriteFile(...)` and
`StackWriteFileEx(...)` write files into this directory and are ideal for small initialization scripts and configuration
files.

The mount location is controlled by `stack.MountPath` and can be changed before creating jobs/VMs. By default,
`stack.MountPath` is `/etc/ucloud-stack`.

Unless explicitly disabled (`SkipStackState` in `VirtualMachineSpec`), the state directory is mounted read+write on all
jobs/VMs created through stack helpers.

File ownership and permissions in the state directory default to `ucloud:ucloud` with directory mode `0770`.
`StackWriteFile(...)` writes files with default mode `0660` and `StackWriteFileEx(...)` allows overriding file mode.

`StackWriteFile(...)` is limited to 64 KiB per file. For more advanced setup logic, prefer init scripts plus custom
application/VM images.

Example:

```go
stack, ok := ucxsvc.StackCreate(app, app.JobName, "Kubernetes")
if !ok {
    return
}

ucxsvc.StackWriteFile(stack, "join-token.txt", util.SecureToken())

// Optional: override default mount path before creating jobs/VMs.
stack.MountPath = "/mnt/ucloud-stack"

initLabels := ucxsvc.StackWriteInitScript(stack, `
    cat /mnt/ucloud-stack/join-token.txt > /var/lib/ucloud/join-token.txt
`)

// Create a VirtualMachineCreate and passing the labels from initLabels.
// NOTE: Only one init script per job is possible. Init scripts are currently 
// only supported by virtual machines.

ucxsvc.StackConfirmAndOpen(stack)
```

### Reconstructing stack context in job-connected UCX

If your job session includes stack labels, reconstruct the stack directly from SysHello job context:

```go
type app struct {
    // ...
    Stack *ucxsvc.Stack `ucx:"-"`
}

func (app *app) OnSysHello(payload string) {
    var req orcapi.AppUcxConnectJobProviderRequest
    if err := json.Unmarshal([]byte(payload), &req); err != nil {
        return
    }

    stack, ok := ucxsvc.StackFromJob(app, req.Job)
    if ok {
        app.Stack = stack
    }
}
```

`StackFromJob(...)` resolves mount path from job file attachments when available and falls back to `/etc/ucloud-stack`.

### Resource attachments

| Function                            | Returns                                            |
|-------------------------------------|----------------------------------------------------|
| `PublicIpCreate(stack)`             | `orcapi.AppParameterValue` for public IP           |
| `PublicLinkCreate(stack, name)`     | `orcapi.AppParameterValue` for ingress/public link |
| `PrivateNetworkCreate(stack, name)` | `orcapi.AppParameterValue` for private network     |

### Jobs and virtual machines

| Function                            | Purpose                                             |
|-------------------------------------|-----------------------------------------------------|
| `JobCreate(stack, spec)`            | Create a job with stack labels merged automatically |
| `VirtualMachineCreate(stack, spec)` | Create VM job from `VirtualMachineSpec`             |

`VirtualMachineSpec` fields:

- `Product` and `Image` select machine+app image.
- `Hostname`, `Attachments`, `Labels` configure VM launch.
- `DiskSize` (gigabytes) defaults to 50 if omitted.
- `SkipStackState` controls automatic stack state mount attachment.

### UI feedback helpers

| Function                  | Effect                        |
|---------------------------|-------------------------------|
| `UiSendFailure(app, msg)` | Show frontend error message   |
| `UiSendSuccess(app, msg)` | Show frontend success message |
| `RouterPushPage(app, path)` | Programmatically push UCX router path (`p`) |

## `ucxapi` typed RPC calls

`ucxapi` exposes typed `ucx.Rpc[Req, Resp]` calls. Standard usage:

```go
session := *app.Session()
products, err := ucxapi.JobsRetrieveProducts.Invoke(session, util.Empty{})
if err != nil {
    ucxsvc.UiSendFailure(app, "Could not retrieve products")
    return
}
```

### Stack RPCs

| RPC              | Request                        | Response       |
|------------------|--------------------------------|----------------|
| `StackAvailable` | `fndapi.FindByStringId`        | `bool`         |
| `StackCreate`    | `ucxapi.StackCreateRequest`    | `ucxapi.Stack` |
| `StackDataWrite` | `ucxapi.StackDataWriteRequest` | `util.Empty`   |
| `StackConfirm`   | `fndapi.FindByStringId`        | `util.Empty`   |
| `StackOpen`      | `fndapi.FindByStringId`        | `util.Empty`   |
| `StackRefresh`   | `util.Empty`                   | `util.Empty`   |
| `StackCopyFile`  | `ucxapi.StackDownloadFileRequest` | `util.Empty` |
| `StackDownloadFile` | `ucxapi.StackDownloadFileRequest` | `util.Empty` |

### Job RPCs

| RPC                    |
|------------------------|
| `JobsCreate`           |
| `JobsBrowse`           |
| `JobsRetrieve`         |
| `JobsRename`           |
| `JobsTerminate`        |
| `JobsExtend`           |
| `JobsSuspend`          |
| `JobsUnsuspend`        |
| `JobsRetrieveProducts` |

### Networking RPCs

| Domain           | RPCs                                                                                                                                                                   |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Public links     | `PublicLinksCreate`, `PublicLinksDelete`, `PublicLinksBrowse`, `PublicLinksRetrieve`, `PublicLinksUpdateLabels`, `PublicLinksRetrieveProducts`                         |
| Public IPs       | `PublicIpsCreate`, `PublicIpsDelete`, `PublicIpsBrowse`, `PublicIpsRetrieve`, `PublicIpsUpdateLabels`, `PublicIpsUpdateFirewall`, `PublicIpsRetrieveProducts`          |
| Private networks | `PrivateNetworksCreate`, `PrivateNetworksDelete`, `PrivateNetworksBrowse`, `PrivateNetworksRetrieve`, `PrivateNetworksUpdateLabels`, `PrivateNetworksRetrieveProducts` |

### Storage and license RPCs

| Domain   | RPCs                                                                                                                             |
|----------|----------------------------------------------------------------------------------------------------------------------------------|
| Drives   | `DrivesCreate`, `DrivesDelete`, `DrivesBrowse`, `DrivesRetrieve`, `DrivesRename`, `DrivesUpdateLabels`, `DrivesRetrieveProducts` |
| Licenses | `LicensesCreate`, `LicensesDelete`, `LicensesBrowse`, `LicensesRetrieve`, `LicensesUpdateLabels`, `LicensesRetrieveProducts`     |

### UI RPCs

| RPC              | Purpose                                                   |
|------------------|-----------------------------------------------------------|
| `UiSendMessage`  | Show a success/error message in the frontend              |
| `RouterPushPage` | Frontend-only: push route path (`p` query parameter)      |

`RouterPushPage` has the same effect as clicking `ucx.Link(...)`.
It is implemented by the stack page frontend and only available in job-connected UCX sessions.

## Notes on choosing API level

- Use `ucxsvc` first for stack-oriented flows.
- Use direct `ucxapi` calls when you need full control or unsupported operations.
- Keep user-visible errors explicit (`UiSendFailure`) when RPC/resource operations fail.
