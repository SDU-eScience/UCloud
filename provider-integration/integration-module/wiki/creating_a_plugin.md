# Plugins

The UCloud Integration Module uses a plugin-based architecture. Several plugin types exists for different integrations.
The plugin types are summarized in the table below:

| Name | Description |
|------|-------------|
| Compute | Implements compute based operations (e.g. HPC jobs, container workloads, virtual machines) |
| Connection | Used for establishing a connection between a UCloud identity and a local identity |
| IdentityMapper | Maps the output of the `Connection` plugin to a concrete `uid` and `gid` |
| SoftwareLicense | 🚧 Implements support for software licenses which can be consumed by the `Compute` plugins |
| NetworkIPs | 🚧 Implements support for static IP addresses which can be consumed by the `Compute` plugins. This is also known as 'public IPs'. |
| Ingress | 🚧 Implements support for L7 (HTTP) ingresses which can be consumed by the `Compute` plugins. This is also known as 'public links'. |
| Storage | 🚧 Implements support for storage `Compute` plugins |

🚧 = Not yet implemented

## Configuration

Plugins are configured in the `plugins.json` file (`/etc/ucloud/plugins.json`). An example configuration file is shown
below:

```json
{
    "compute": {
        "sample": {
            "foo": "bar"
        }
    },
    "connection": {
        "ticket": {}
    },
    "identityMapper": {
        "direct": {}
    }
}

```

This shows that the following plugins are active:

| Type | Plugin | Note |
|------|--------|------|
| `compute` | `sample` | The sample plugin has received additional configuration `{"foo": "bar"}` |
| `connection` | `ticket` | |
| `identityMapper` | `direct` | |

Plugins can read their configuration by overriding the `initialize` method:

```kotlin
override fun PluginContext.initialize() {
    config.plugins.compute!!["foo"] // bar
}
```

## Registering a Plugin

Plugins are registered by adding an entry in the `PluginLoader`:

```kotlin
private val computePlugins = mapOf<String, () -> ComputePlugin>(
    "sample" to { SampleComputePlugin() }
)
```