# UCloud/Compute

UCloud's own implementation of the [UCloud Compute Provider API](/backend/app-orchestrator-service/wiki/provider.md).
UCloud/Compute works in tandem with [UCloud/Storage](/backend/storage-service/README.md) to provide a complete
experience. 

## API Support

UCloud/Compute currently supports the following features from the compute API:

| Feature set | Status | Notes |
|-------------|--------|-------|
| `docker` | ✅ | Full support |
| `virtualMachine` | ❌ | No support |

__Table:__ Support for general compute features

### Docker

You can read more about the `docker` implementation [here](./wiki/docker.md).

| Feature | Status | Notes |
|---------|--------|-------|
| `web` | ✅ | Full support |
| `vnc` | ✅ | Full support |
| `batch` | ✅ | Full support |
| `logs` | ✅ | Full support |
| `terminal` | ✅ | Full support |
| `peers` | ✅ | Full support |

### Virtual Machines

No support.
