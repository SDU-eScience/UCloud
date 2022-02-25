Configures the [websocket](./websockets.md) backend for this call.

Added via `websocket(path: String)`. This will cause the server to start listening for websocket connections at `path`.

## Examples

__Example:__ Configure websockets for a call

```kotlin
websocket(baseContext) // baseContext = "/api/tasks"
```
