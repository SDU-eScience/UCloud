# Data binding

UCX binds frontend input to exported Go fields using model paths.

## Field mapping rules

By default, exported field names are mapped to lower camel case:

- `JobName` -> `jobName`
- `ValidationMessage` -> `validationMessage`

You can override this with `ucx` tags:

```go
type appModel struct {
    JobName string
    Errors  map[string]string

    // Excluded from model serialization
    NextTodoId int64 `ucx:"-"`

    // Custom key in model
    StackName string `ucx:"stack.id"`
}
```

## Binding inputs and output

Bind paths point into your serialized model:

```go
ucx.InputText("jobName", "Job name", "Name your job", "jobName")
ucx.TextBound("errors.jobName")
ucx.TextBound("validationMessage")
```

Routing can also be model-bound:

```go
ucx.Router("routePath")
ucx.TextBound("routePath")
```

In this case `routePath` is synchronized with query parameter `p` on the current page.

For list rendering, use relative paths inside row templates:

```go
ucx.List("todos", "No items yet.").Children(
    ucx.TextBoundEx("todoItemText", "./text"),
    ucx.ButtonEx("removeTodo", "Remove", ucx.ColorErrorMain, ucx.IconHeroTrash, "", "./id"),
)
```

`./text` and `./id` resolve relative to the current list item.

## Handling model input

UCX sends model edits as `OpModelInput`. In most apps you:

1. normalize model values,
2. run validation,
3. clear stale status messages.

```go
func (app *myApp) OnMessage(msg ucx.Frame) {
    switch msg.Opcode {
    case ucx.OpModelInput:
        app.JobName = strings.TrimSpace(app.JobName)
        app.Errors = validateState(app)
        app.SubmissionMessage = ""
    }
}
```

`ucx.AppServe(...)` already applies model input into your struct (`ApplyModelInput`) before `OnMessage(...)` is called.

## Validation pattern

A simple and effective approach is `map[string]string` for errors:

```go
func validateState(app *myApp) map[string]string {
    errors := map[string]string{}
    if len(strings.TrimSpace(app.JobName)) < 3 {
        errors["jobName"] = "Job name must be at least 3 characters"
    }
    if app.CPU < 1 || app.CPU > 128 {
        errors["cpu"] = "CPU must be between 1 and 128"
    }
    return errors
}
```

Then render with `TextBound("errors.jobName")`, `TextBound("errors.cpu")`, etc.
