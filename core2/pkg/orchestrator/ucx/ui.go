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
			"label": VString(label),
			"color": VColor(color),
		},
	}
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
