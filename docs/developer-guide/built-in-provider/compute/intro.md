<p align='center'>
<a href='/docs/developer-guide/built-in-provider/storage.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/built-in-provider/compute/compute.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Built-in Provider](/docs/developer-guide/built-in-provider/README.md) / [UCloud/Compute](/docs/developer-guide/built-in-provider/compute/README.md) / Introduction
# Introduction

UCloud's own implementation of the [UCloud Compute Provider API](/docs/developer-guide/orchestration/compute/README.md).
UCloud/Compute works in tandem with [UCloud/Storage](../storage/README.md) to provide a complete
experience. 

## API Support

UCloud/Compute currently supports the following features from the compute API:

| Feature set | Status | Notes |
|-------------|--------|-------|
| `docker` | ✅ | Full support |
| `virtualMachine` | ❌ | No support |

__Table:__ Support for general compute features

### Docker

You can read more about the `docker` implementation [here](./compute.md).

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

