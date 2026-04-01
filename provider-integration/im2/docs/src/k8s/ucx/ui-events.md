# UI events

UCX has two interaction channels:

- **Model input (`OpModelInput`)** for bound field changes.
- **UI events (`OpUiEvent`)** for component events such as button clicks and form submit.

## Registering handlers

Attach handlers directly on nodes with `.On(...)`:

```go
ucx.Button("submit", "Submit", ucx.ColorPrimaryMain).
    On(ucx.UiEventClick, func(ev ucx.UiEvent) {
        app.Errors = validateState(app)
        if len(app.Errors) == 0 {
            app.SubmissionMessage = "Submission accepted"
        }
    })
```

Event types include:

- `ucx.UiEventClick`
- `ucx.UiEventSubmit`
- `ucx.UiEventChange`
- `ucx.UiEventFocus`
- `ucx.UiEventBlur`

## Reading event payloads

Event values are `ucx.Value`. Convert with helpers like `ucx.ValueAsString(...)`:

```go
ucx.ButtonEx("remove", "Remove", ucx.ColorErrorMain, ucx.IconHeroTrash, "", "./id").
    On(ucx.UiEventClick, func(ev ucx.UiEvent) {
        id := strings.TrimSpace(ucx.ValueAsString(ev.Value))
        if id == "" {
            return
        }
        removeTodoById(id)
    })
```

## Updating model vs updating UI

In most handlers you only mutate state fields.
UCX then sends a model patch automatically.

Use `ucx.AppUpdateUi(app)` only when the UI tree itself changed (for example component structure changes).

Common examples where `AppUpdateUi(...)` is needed:

- switching between pages rendered via `ucx.Router(...)` + `ucx.Link(...)`,
- conditionally adding/removing sections,
- changing the set of action buttons in a toolbar.

## Concurrency and blocking handlers

Default `.On(...)` handlers are non-blocking and run in a goroutine.
For long operations, keep this default and update status fields while work progresses.

If you must run synchronously, use `.OnEx(..., ucx.EventHandlerBlocking, ...)`.

```go
ucx.Button("syncAction", "Run", ucx.ColorWarningMain).
    OnEx(ucx.UiEventClick, ucx.EventHandlerBlocking, func(session *ucx.Session, ev ucx.UiEvent) {
        app.LastActionMessage = "Ran in blocking mode"
    })
```

For background tasks started outside normal handlers, manually hold `app.Mutex()` before mutating state and calling `ucx.AppUpdateModel(app)`.
