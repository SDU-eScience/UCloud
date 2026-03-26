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
