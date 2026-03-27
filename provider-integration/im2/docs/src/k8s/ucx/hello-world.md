# Hello world

This is a minimal UCX application with one bound input and one button event.

```go
package main

import (
    "fmt"
    "strings"
    "sync"

    "ucloud.dk/shared/pkg/ucx"
)

type helloApp struct {
    mu      sync.Mutex   `ucx:"-"`
    session *ucx.Session `ucx:"-"`

    Name    string
    Message string
}

func NewHelloApp() ucx.Application {
    return &helloApp{
        Name:    "UCloud",
        Message: "Click the button",
    }
}

func (app *helloApp) Mutex() *sync.Mutex      { return &app.mu }
func (app *helloApp) Session() **ucx.Session  { return &app.session }
func (app *helloApp) OnInit()                 {}

func (app *helloApp) UserInterface() ucx.UiNode {
    return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).
        Sx(ucx.SxP(4)).
        Children(
            ucx.H2("UCX hello world"),
            ucx.InputText("name", "Name", "Type your name", "name"),
            ucx.Button("sayHello", "Say hello", ucx.ColorPrimaryMain).
                On(ucx.UiEventClick, func(ev ucx.UiEvent) {
                    app.Message = fmt.Sprintf("Hello %s!", strings.TrimSpace(app.Name))
                }),
            ucx.TextBound("message"),
        )
}

func (app *helloApp) OnMessage(frame ucx.Frame) {
    switch frame.Opcode {
    case ucx.OpModelInput:
        app.Name = strings.TrimSpace(app.Name)
        if app.Name == "" {
            app.Message = "Please enter a name"
        }
    }
}

func main() {
    ucx.AppServe(func() ucx.Application {
        return NewHelloApp()
    })
}
```

## What is happening

- `Name` and `Message` are exported fields, so they are serialized into the UCX model.
- `InputText(..., "name")` binds to the `Name` field (`Name` -> `name` by default).
- button clicks are handled with `.On(ucx.UiEventClick, ...)`.
- `TextBound("message")` renders the `Message` field.
- `ucx.AppServe(...)` handles handshake, mount, model patching, and event dispatch.

## First extensions

From here, most real apps add:

- validation (`errors.<field>` paths),
- collections (`[]struct`) displayed through `ucx.List(...)`,
- and backend calls via `ucxsvc` / `ucxapi` when submit is clicked.

