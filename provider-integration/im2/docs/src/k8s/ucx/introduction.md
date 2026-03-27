# UCX applications

UCX is a backend-driven UI and orchestration layer used for building advanced UCloud application flows.
It is useful when a plain static application definition is not enough, for example when a workflow needs to:

- create multiple resources as one logical stack,
- react to user input in real time,
- attach links/networks/IPs/jobs dynamically,
- and keep a custom UI synchronized with backend state.

UCX can be used in two main modes:

- **Custom application creation:** You provide a full UCX application that controls the entire creation process of an 
  application. This replaces the normal YAML driven application development flow with a UCX one instead. 
  The application YAML will simply refer to the container hosting the UCX application.
- **Inside a running job:** You expose UCX-powered controls from an existing workload to customize job behavior while it runs.

This is typically needed for advanced stacks such as managed Kubernetes control planes and other multi-resource services.

## Mental model

A UCX application is a Go type implementing `ucx.Application`:

- `UserInterface()` returns a UI tree (`ucx.UiNode`).
- exported struct fields are the model state sent to the frontend.
- `OnMessage(...)` handles incoming model input and unhandled UI events.
- `Session()` gives access to RPC calls (`ucxapi`) and helper services (`ucxsvc`).

At runtime:

1. `ucx.AppServe(factory)` accepts a session.
2. frontend sends `SysHello`.
3. backend sends UI mount + current model.
4. user interaction generates model input or UI events.
5. backend updates state and streams model patches.

## Recommended structure

For provider implementations, this structure works well:

- Keep UI logic in `UserInterface()`.
- Keep validation and state transitions in `OnMessage(...)` and event handlers.
- Use `ucxsvc` for stack/resource helpers.
- Use direct `ucxapi` calls for functionality not covered by `ucxsvc`.
