# Component reference

This page lists the most used UCX UI components with short examples.

## Layout

### `Flex`, `Box`

```go
ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).
    Sx(ucx.SxP(4)).
    Children(
        ucx.Box().Children(
            ucx.Text("Inside a box"),
        ),
    )
```

### `Surface`, `Toolbar`

`Surface` maps to a card-like container in the frontend.
`Toolbar` provides a common "title on left, actions on right" layout.

```go
ucx.Surface().Children(
    ucx.Toolbar().Children(
        ucx.H3("Machines"),
        ucx.Button("refresh", "Refresh", ucx.ColorPrimaryMain),
    ),
    ucx.Text("Machine list goes here"),
)
```

### `Tabs`, `Tab`

```go
ucx.Tabs().Children(
    ucx.Tab("Overview", ucx.IconHeroHome).Children(ucx.Text("Overview content")),
    ucx.Tab("Nodes", ucx.IconHeroServer).Children(ucx.Text("Nodes content")),
)
```

### `AccordionNode`

```go
ucx.AccordionNode("Advanced", false).Children(
    ucx.Text("Advanced settings..."),
)
```

## Text and status

### `H1`..`H6`, `Text`, `TextBound`

```go
ucx.H2("Create stack")
ucx.Text("Fill out the form")
ucx.TextBound("validationMessage")
```

### `Code`, `CodeBound`, `Icon`, `Spinner`, `DividerNode`

```go
ucx.Icon(ucx.IconHeroCommandLine, ucx.ColorPrimaryMain, 20)
ucx.Code("kubectl get pods -A")
ucx.CodeBound("generatedScript")
ucx.Spinner(20)
ucx.DividerNode()
```

## Inputs

All interactive components require explicit `id`. This ID must be unique for the entire mounted UI, similar to how
HTML works.

### `InputText`, `InputNumber`, `TextArea`

```go
ucx.InputText("jobName", "Job name", "Name your job", "jobName")
ucx.InputNumber("cpu", "CPU", "cpu", 1, 128)
ucx.TextArea("notes", "Notes", "Optional notes", "notes", 4)
```

### `InputSecret`, `InputSecretEx`

A read-only masked input that reveals its value while focused. Reads from a bind path, or renders a static value passed to `InputSecretEx`.

```go
ucx.InputSecretEx("apiToken", "", token, "")
```

### `Dialog`, `DialogEx`

A modal overlay rendered above the app view. Renders nothing while `open` is false. Children use normal components and event handlers; pressing Escape fires `UiEventClose`.

```go
ucx.DialogEx("confirm", "Confirm action", app.showDialog).Children(
    ucx.Text("Are you sure?"),
    ucx.Button("Yes").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
        app.showDialog = false
        ucx.AppUpdateUi(app)
    }),
).On(ucx.UiEventClose, func(ev ucx.UiEvent) {
    app.showDialog = false
    ucx.AppUpdateUi(app)
})
```


### `Checkbox`, `ToggleInput`

```go
ucx.Checkbox("notify", "Notify when ready", "notify", true)
ucx.ToggleInput("debug", "Enable debug mode", "debug", true)
```

### `Select`, `RadioGroup`

```go
machineOptions := []ucx.Option{
    {Key: "u1-standard-4", Value: "4 vCPU"},
    {Key: "u1-standard-8", Value: "8 vCPU"},
}

ucx.Select("machine", "Machine", "machine", machineOptions)
ucx.RadioGroup("network", "Network mode", "network", []ucx.Option{
    {Key: "public", Value: "Public"},
    {Key: "private", Value: "Private"},
})
```

## Data views

### `List`

```go
ucx.List("todos", "No items yet.").Children(
    ucx.Flex(ucx.FlexProps{Gap: 8}).Children(
        ucx.TextBoundEx("todoText", "./text"),
        ucx.ButtonEx("removeTodo", "Remove", ucx.ColorErrorMain, ucx.IconHeroTrash, "", "./id"),
    ),
)
```

### `StackResources`, `StackMachines`

These are stack-aware components rendered by the stack page frontend.

```go
ucx.StackResources()

ucx.StackMachines(ucx.StackMachinesProps{
    Plain: true,
    LabelFilter: util.OptValue(ucx.StackMachinesLabelFilter{
        Label: "ucloud.dk/k8s-node-group",
        Value: "worker",
    }),
})
```

### `TableNode`

```go
ucx.TableNode("nodes", []ucx.Option{
    {Key: "hostname", Value: "Hostname"},
    {Key: "status", Value: "Status"},
})
```

## Actions

### `Button`, `ButtonEx`, `SubmitButton`, `Form`

```go
ucx.Form("createForm").Children(
    ucx.InputText("name", "Name", "", "name"),
    ucx.SubmitButton("create", "Create", ucx.ColorPrimaryMain),
)

ucx.Button("refresh", "Refresh", ucx.ColorInfoMain)
ucx.ButtonEx("delete", "Delete", ucx.ColorErrorMain, ucx.IconHeroTrash, "", "./id")
```

### `Router`, `Link`

`Router(bindPath)` binds the current UCX-internal path (query parameter `p`) to model state.
`Link(to)` updates only `p` on the current page and preserves all other query parameters.

Only one active router is used; later router nodes overwrite earlier ones.

```go
ucx.Router("routePath")

ucx.Toolbar().Children(
    ucx.H2("Stack overview"),
    ucx.Link("control").Children(ucx.Text("Open control plane")),
)
```

### `SettingsAction`

Renders a settings-style row with a bold title and secondary description on the left and a control on the right.
Well suited for action panels such as downloads, external tools and destructive operations.

```go
ucx.SettingsAction("deleteAction", "Delete stack", "Permanently deletes the stack. This cannot be undone.").
    Children(ucx.ButtonEx("delete", "Delete", ucx.ColorErrorMain, "", "", ""))
```

### `ExternalLinkButton`

A button-styled link that opens an external URL in a new tab.

```go
ucx.ExternalLinkButton("openDocs", "Open documentation", "https://example.com", ucx.ColorPrimaryMain, ucx.IconHeroArrowTopRightOnSquare)
```

### `LinkButton`

A link-styled clickable label. Fires a normal `UiEventClick` like `Button`, but renders inline as a link.

```go
ucx.LinkButton("showResources", "12", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
    // handle click
})
```

### `CopyButton`, `CopyButtonEx`, `CopyButtonBound`

An icon button that copies static or bound text to the clipboard and briefly confirms with a check mark.

```go
ucx.CopyButtonEx("copyId", "1234567890").WithTooltip("Copy ID")
```

### `ProviderTitle`

Renders the display title of a provider, resolved from the provider id. With `short` set, renders the abbreviated title (for example "UCLOUD" instead of "UCloud Provider").

```go
ucx.ProviderTitleEx("providerTitle", "ucloud_provider", true)
```

## Navigation

### `NavTree`, `NavTreeEx`

The sidebar tree of a browser layout. Each `NavItem` may set `SeparatorBefore` to render a horizontal separator between it and the item above.

```go
items := []ucx.NavItem{
    {Id: "home", Label: "Home"},
    {Id: "group:Cluster", Label: "Cluster", SeparatorBefore: true, Children: []ucx.NavItemChild{
        {Id: "nodes", Label: "Nodes"},
    }},
}
ucx.NavTreeEx("nav", "activeType", items)
```

## Styling (`Sx`)

Attach style tokens with `.Sx(...)`.
You can combine multiple style options in a single call:

```go
ucx.Text("Cluster status").Sx(
    ucx.SxColor(ucx.ColorTextSecondary),
    ucx.SxFontSize(13),
)
```

The most common pattern is to style containers and keep children mostly semantic:

```go
ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Sx(
    ucx.SxP(3),
    ucx.SxBorderWidth(1),
    ucx.SxBorderSolid,
    ucx.SxBorderColor(ucx.ColorBorderColor),
    ucx.SxBorderRadius(8),
    ucx.SxBg(ucx.ColorBackgroundCard),
).Children(
    ucx.Icon(ucx.IconHeroServer, ucx.ColorInfoMain, 18),
    ucx.Text("Control plane").Sx(ucx.SxFontWeight("600")),
)
```

### Spacing and sizing

```go
ucx.Box().Sx(
    ucx.SxP(4),
    ucx.SxMt(2),
    ucx.SxWidthPercent(100),
    ucx.SxMaxWidth(960),
)
```

### Layout and alignment

```go
ucx.Flex(ucx.FlexProps{Gap: 8}).Sx(
    ucx.SxDisplayFlex,
    ucx.SxAlignItemsCenter,
    ucx.SxJustifySpaceBetween,
    ucx.SxFlexWrapWrap,
)
```

### Typography

```go
ucx.Text("kube-system").Sx(
    ucx.SxFontSize(12),
    ucx.SxFontWeight("700"),
    ucx.SxLetterSpacing(1),
    ucx.SxTextTransform("uppercase"),
    ucx.SxColor(ucx.ColorTextSecondary),
)
```

### Borders and emphasis

```go
ucx.Box().Sx(
    ucx.SxBorderLeftWidth(3),
    ucx.SxBorderSolid,
    ucx.SxBorderLeftColor(ucx.ColorWarningMain),
    ucx.SxPl(3),
).Children(
    ucx.Text("Pending node upgrade"),
)
```

### Overflow and code blocks

```go
ucx.CodeBound("generatedScript").Sx(
    ucx.SxDisplayBlock,
    ucx.SxMaxHeight(280),
    ucx.SxOverflowY("auto"),
    ucx.SxWhiteSpace("pre"),
)
```

### Responsive-friendly row wrapping

```go
ucx.Flex(ucx.FlexProps{Gap: 8}).Sx(
    ucx.SxFlexWrapWrap,
).Children(
    ucx.Box().Sx(ucx.SxMinWidth(240), ucx.SxFlex("1 1 240px")).Children(
        ucx.Text("Section A"),
    ),
    ucx.Box().Sx(ucx.SxMinWidth(240), ucx.SxFlex("1 1 240px")).Children(
        ucx.Text("Section B"),
    ),
)
```

Common options (non-exhaustive):

- spacing: `SxP`, `SxPx`, `SxPy`, `SxMt`, ...
- layout: `SxDisplayFlex`, `SxAlignItemsCenter`, `SxJustifySpaceBetween`, ...
- flex/grid: `SxFlex`, `SxFlexGrow`, `SxFlexBasis`, `SxGridTemplateColumns`, ...
- color: `SxColor`, `SxBg`, `SxBorderColor`
- sizing: `SxWidth`, `SxHeight`, `SxWidthPercent`, `SxHeightPercent`
- text/overflow: `SxWhiteSpace`, `SxWordBreak`, `SxOverflowX`, `SxOverflowY`
