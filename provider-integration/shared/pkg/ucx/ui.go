package ucx

type UiNode struct {
	Id         string
	Component  string
	Props      map[string]Value
	BindPath   string
	Optimistic bool
	ChildNodes []UiNode
}

func (n UiNode) Sx(opts ...SxOption) UiNode {
	if n.Props == nil {
		n.Props = map[string]Value{}
	}
	n.Props["sx"] = Sx(opts...)
	return n
}

func (n UiNode) Children(children ...UiNode) UiNode {
	n.ChildNodes = children
	return n
}

type FlexProps struct {
	Direction string
	Gap       int
}

type Option struct {
	Key   string
	Value string
}

func Flex(id string, props FlexProps) UiNode {
	if props.Direction == "" {
		props.Direction = "row"
	}

	return UiNode{
		Id:        id,
		Component: "flex",
		Props:     StructToModelOrLog(props),
	}
}

func Box(id string) UiNode {
	return UiNode{
		Id:        id,
		Component: "box",
	}
}

func Text(id string, text string) UiNode {
	return UiNode{
		Id:        id,
		Component: "text",
		Props: map[string]Value{
			"text": VString(text),
		},
	}
}

func H1(id string, text string) UiNode {
	return Heading(id, text, 1)
}

func H2(id string, text string) UiNode {
	return Heading(id, text, 2)
}

func H3(id string, text string) UiNode {
	return Heading(id, text, 3)
}

func H4(id string, text string) UiNode {
	return Heading(id, text, 4)
}

func H5(id string, text string) UiNode {
	return Heading(id, text, 5)
}

func H6(id string, text string) UiNode {
	return Heading(id, text, 6)
}

func Heading(id string, text string, level int64) UiNode {
	return UiNode{
		Id:        id,
		Component: "heading",
		Props: map[string]Value{
			"text":  VString(text),
			"level": VS64(level),
		},
	}
}

func HeadingBound(id string, bindPath string, level int64) UiNode {
	return UiNode{
		Id:        id,
		Component: "heading",
		BindPath:  bindPath,
		Props: map[string]Value{
			"level": VS64(level),
		},
	}
}

func TextBound(id string, bindPath string) UiNode {
	return UiNode{Id: id, Component: "text", BindPath: bindPath}
}

func InputText(id string, label string, placeholder string, bindPath string) UiNode {
	return UiNode{
		Id:         id,
		Component:  "input_text",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"label":       VString(label),
			"placeholder": VString(placeholder),
		},
	}
}

func InputNumber(id string, label string, bindPath string, min int64, max int64) UiNode {
	return UiNode{
		Id:         id,
		Component:  "input_number",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"label": VString(label),
			"min":   VS64(min),
			"max":   VS64(max),
		},
	}
}

func Checkbox(id string, label string, bindPath string, optimistic bool) UiNode {
	return UiNode{
		Id:         id,
		Component:  "checkbox",
		BindPath:   bindPath,
		Optimistic: optimistic,
		Props: map[string]Value{
			"label": VString(label),
		},
	}
}

func List(id string, bindPath string, emptyText string) UiNode {
	return UiNode{Id: id, Component: "list", BindPath: bindPath}
}

func Icon(id string, name IconName, color Color, size int64) UiNode {
	return UiNode{
		Id:        id,
		Component: "icon",
		Props: map[string]Value{
			"name":  VIcon(name),
			"color": VColor(color),
			"size":  VS64(size),
		},
	}
}

func Button(id string, label string, color Color) UiNode {
	return UiNode{
		Id:        id,
		Component: "button",
		Props: map[string]Value{
			"label":  VString(label),
			"color":  VColor(color),
			"submit": VBool(false),
		},
	}
}

func SubmitButton(id string, label string, color Color) UiNode {
	node := Button(id, label, color)
	node.Props["submit"] = VBool(true)
	return node
}

func ButtonEx(id string, label string, color Color, iconLeft IconName, iconRight IconName, eventValuePath string) UiNode {
	props := map[string]Value{
		"label": VString(label),
		"color": VColor(color),
	}
	if iconLeft != "" {
		props["iconLeft"] = VIcon(iconLeft)
	}
	if iconRight != "" {
		props["iconRight"] = VIcon(iconRight)
	}
	if eventValuePath != "" {
		props["eventValuePath"] = VString(eventValuePath)
	}

	return UiNode{
		Id:        id,
		Component: "button",
		Props:     props,
	}
}

func TextArea(id string, label string, placeholder string, bindPath string, rows int64) UiNode {
	return UiNode{
		Id:         id,
		Component:  "textarea",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"label":       VString(label),
			"placeholder": VString(placeholder),
			"rows":        VS64(rows),
		},
	}
}

func Select(id string, label string, bindPath string, options []Option) UiNode {
	return UiNode{
		Id:         id,
		Component:  "select",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"label":   VString(label),
			"options": optionsToValue(options),
		},
	}
}

func RadioGroup(id string, label string, bindPath string, options []Option) UiNode {
	return UiNode{
		Id:         id,
		Component:  "radio_group",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"label":   VString(label),
			"options": optionsToValue(options),
		},
	}
}

func ToggleInput(id string, label string, bindPath string, optimistic bool) UiNode {
	return UiNode{
		Id:         id,
		Component:  "toggle",
		BindPath:   bindPath,
		Optimistic: optimistic,
		Props: map[string]Value{
			"label": VString(label),
		},
	}
}

func DividerNode(id string) UiNode {
	return UiNode{Id: id, Component: "divider"}
}

func Spinner(id string, size int64) UiNode {
	return UiNode{
		Id:        id,
		Component: "spinner",
		Props: map[string]Value{
			"size": VS64(size),
		},
	}
}

func TableNode(id string, bindPath string, columns []Option) UiNode {
	return UiNode{
		Id:        id,
		Component: "table",
		BindPath:  bindPath,
		Props: map[string]Value{
			"columns": optionsToValue(columns),
		},
	}
}

func Tabs(id string) UiNode {
	return UiNode{Id: id, Component: "tabs"}
}

func Tab(id string, name string, icon IconName) UiNode {
	return UiNode{
		Id:        id,
		Component: "box",
		Props: map[string]Value{
			"name": VString(name),
			"icon": VIcon(icon),
		},
	}
}

func AccordionNode(id string, title string, open bool) UiNode {
	return UiNode{
		Id:        id,
		Component: "accordion",
		Props: map[string]Value{
			"title": VString(title),
			"open":  VBool(open),
		},
	}
}

func Form(id string) UiNode {
	return UiNode{Id: id, Component: "form"}
}

func Code(id string, text string) UiNode {
	return UiNode{
		Id:        id,
		Component: "code",
		Props: map[string]Value{
			"text": VString(text),
		},
	}
}

func CodeBound(id string, bindPath string) UiNode {
	return UiNode{Id: id, Component: "code", BindPath: bindPath}
}

func optionsToValue(options []Option) Value {
	list := make([]Value, 0, len(options))
	for _, option := range options {
		list = append(list, VObject(map[string]Value{
			"key":   VString(option.Key),
			"value": VString(option.Value),
		}))
	}
	return VList(list)
}
