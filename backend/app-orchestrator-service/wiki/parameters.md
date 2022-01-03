# Parameters of a UCloud Job

The `Application` which is started as part of a `Job` determines which parameters a `Job` can accept. You can read more
about which parameters, and how to configure an `Application` to use
them, [here](/backend/app-store-service/wiki/apps.md). In short, you can think of a parameter as a variable declaration,
i.e. a _name_ and a _type_. The orchestrator receives _values_ for each of these parameters, if the _values_ do not
match the _type_ then UCloud will reject the request.

In this document, we will cover the values that each parameter type can accept and which effect they have on the
application.

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue-->
<!--<editor-fold desc="Generated documentation">-->
An `AppParameterValue` is value which is supplied to a parameter of an `Application`.

    
Each value type can is type-compatible with one or more `ApplicationParameter`s. The effect of a specific value depends
on its use-site, and the type of its associated parameter.

`ApplicationParameter`s have the following usage sites (see [here](/backend/app-store-service/wiki/apps.md) for a 
comprehensive guide):

- Invocation: This affects the command line arguments passed to the software.
- Environment variables: This affects the environment variables passed to the software.
- Resources: This only affects the resources which are imported into the software environment. Not all values can be
  used as a resource.



<!--</editor-fold>-->
<!-- /typedoc:dk.sdu.cloud.app.store.api.AppParameterValue-->

## Resources

Resources are a special type of value which is used purely for its side effects. Not all values are usable as resources,
as you will see below. Examples of this includes files. This allows for users to mount additional data that might be
required in their `Job`s. This is especially powerful in interactive `Job`s which might need to work on multiple
datasets.

In addition, some resources are __bound resources__. A bound resource is a resource which is bound to a `Job` in a
mutually exclusive way, this means that this resource cannot be used in another `Job` while it is running. Examples
of this include the HTTP ingress, which provide users a way to load balance traffic through a custom domain. Bound
resources are automatically unbound by UCloud at the end of a `Job`.

## Value Types

### Files

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.File:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A reference to a UCloud file

| Property | Type | Description |
|----------|------|-------------|
| `path` | `String` | The absolute path to the file or directory in UCloud |
| `readOnly` | `Boolean?` | Indicates if this file or directory should be mounted as read-only |
| `type` | `("file")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.InputFile` and `ApplicationParameter.InputDirectory`
- __Mountable as a resource:__ ✅ Yes
- __Expands to:__ The absolute path to the file or directory in the software's environment
- __Side effects:__ Includes the file or directory in the `Job`'s temporary work directory
    
The path of the file must be absolute and refers to either a UCloud directory or file.



<!--</editor-fold>-->
<!-- /typedoc-->

### Booleans

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.Bool:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A boolean value (true or false)

| Property | Type | Description |
|----------|------|-------------|
| `value` | `Boolean` | No documentation |
| `type` | `("boolean")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.Bool`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ `trueValue` of `ApplicationParameter.Bool` if value is `true` otherwise `falseValue`
- __Side effects:__ None



<!--</editor-fold>-->
<!-- /typedoc-->

### Text

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.Text:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A textual value

| Property | Type | Description |
|----------|------|-------------|
| `value` | `String` | No documentation |
| `type` | `("text")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
- __Side effects:__ None

When this is used with an `Enumeration` it must match the value of one of the associated `options`.


### TextArea

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.Text:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A textarea value

| Property | Type | Description |
|----------|------|-------------|
| `value` | `String` | No documentation |
| `type` | `("text")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.TextArea` and `ApplicationParameter.Enumeration`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
- __Side effects:__ None

When this is used with an `Enumeration` it must match the value of one of the associated `options`.


<!--</editor-fold>-->
<!-- /typedoc-->

### Integers

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.Integer:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
An integral value

| Property | Type | Description |
|----------|------|-------------|
| `value` | `Long` | No documentation |
| `type` | `("integer")` | No documentation |


- __Compatible with:__ `ApplicationParameter.Integer`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The number
- __Side effects:__ None

Internally this uses a big integer type and there are no defined limits.



<!--</editor-fold>-->
<!-- /typedoc-->

### Floating Points

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.FloatingPoint:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A floating point value

| Property | Type | Description |
|----------|------|-------------|
| `value` | `Double` | No documentation |
| `type` | `("floating_point")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.FloatingPoint`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The number
- __Side effects:__ None

Internally this uses a big decimal type and there are no defined limits.



<!--</editor-fold>-->
<!-- /typedoc-->

### Peers

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.Peer:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A reference to a separate UCloud `Job`

| Property | Type | Description |
|----------|------|-------------|
| `hostname` | `String` | No documentation |
| `jobId` | `String` | No documentation |
| `type` | `("peer")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.Peer`
- __Mountable as a resource:__ ✅ Yes
- __Expands to:__ The `hostname`
- __Side effects:__ Configures the firewall to allow bidirectional communication between this `Job` and the peering 
  `Job`



<!--</editor-fold>-->
<!-- /typedoc-->

### Licenses

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.License:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A reference to a software license, registered locally at the provider

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | No documentation |
| `type` | `("license_server")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.LicenseServer`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ `${license.address}:${license.port}/${license.key}` or 
  `${license.address}:${license.port}` if no key is provided
- __Side effects:__ None



<!--</editor-fold>-->
<!-- /typedoc-->

### Block Storage

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.BlockStorage:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A reference to block storage (Not yet implemented)

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | No documentation |
| `type` | `("block_storage")` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc-->

### Networks

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.Network:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A reference to block storage (Not yet implemented)

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | No documentation |
| `type` | `("network")` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc-->

### Ingresses

<!-- typedoc:dk.sdu.cloud.app.store.api.AppParameterValue.Ingress:includeProps=true-->
<!--<editor-fold desc="Generated documentation">-->
A reference to an HTTP ingress, registered locally at the provider

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | No documentation |
| `type` | `("ingress")` | No documentation |

    
- __Compatible with:__ `ApplicationParameter.Ingress`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ `${id}`
- __Side effects:__ Configures an HTTP ingress for the application's interactive web interface. This interface should
  not perform any validation, that is, the application should be publicly accessible.



<!--</editor-fold>-->
<!-- /typedoc-->
