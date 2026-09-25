package ucx

import (
	"fmt"

	"ucloud.dk/shared/pkg/util"
)

type UiNode struct {
	Id         string
	Component  string
	Props      map[string]Value
	BindPath   string
	Optimistic bool
	ChildNodes []UiNode
	handlers   map[string]UiEventHandler
}

type UiEventHandler func(session *Session, ev UiEvent)
type UiEventHandlerSimple func(ev UiEvent)

var interactiveComponents = map[string]bool{
	"input_text":               true,
	"input_number":             true,
	"input_slider":             true,
	"inference_chat_composer":  true,
	"inference_image_composer": true,
	"checkbox":                 true,
	"button":                   true,
	"textarea":                 true,
	"select":                   true,
	"machine_type_selector":    true,
	"radio_group":              true,
	"toggle":                   true,
	"form":                     true,
}

func NormalizeUiTree(root UiNode) UiNode {
	return normalizeUiNode(root, "root", 0)
}

func normalizeUiNode(node UiNode, parentPath string, siblingIndex int) UiNode {
	pathToken := ""
	if node.Id == "" {
		if interactiveComponents[node.Component] {
			panic(fmt.Sprintf("ucx: component '%s' requires explicit id", node.Component))
		}

		pathToken = fmt.Sprintf("%s/%d:%s", parentPath, siblingIndex, node.Component)
		node.Id = implicitUiId(pathToken)
	} else {
		pathToken = fmt.Sprintf("%s/%s", parentPath, node.Id)
	}

	for idx, child := range node.ChildNodes {
		node.ChildNodes[idx] = normalizeUiNode(child, pathToken, idx)
	}

	return node
}

func implicitUiId(pathToken string) string {
	hash := util.Sha256([]byte(pathToken))
	return "auto-" + hash[:12]
}

func requireExplicitId(id string, component string) {
	if id == "" {
		panic(fmt.Sprintf("ucx: component '%s' requires explicit id", component))
	}
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

func (n UiNode) On(event UiEventType, handler UiEventHandlerSimple) UiNode {
	return n.OnEx(event, 0, func(session *Session, ev UiEvent) {
		handler(ev)
	})
}

type EventHandlerFlag uint64

const (
	EventHandlerBlocking EventHandlerFlag = 1 << iota
)

func (n UiNode) OnEx(event UiEventType, flags EventHandlerFlag, handler UiEventHandler) UiNode {
	if event == "" {
		panic("ucx: empty event type in UiNode.On")
	}
	if handler == nil {
		panic("ucx: nil handler in UiNode.On")
	}

	if n.handlers == nil {
		n.handlers = map[string]UiEventHandler{}
	}

	if _, exists := n.handlers[string(event)]; exists {
		panic(fmt.Sprintf("ucx: duplicate handler on node '%s' for event '%s'", n.Id, event))
	}

	modifiedHandler := handler
	if flags&EventHandlerBlocking == 0 {
		modifiedHandler = func(session *Session, ev UiEvent) {
			go func() {
				app := session.app
				if app != nil {
					mu := app.Mutex()

					mu.Lock()
					defer mu.Unlock()
					defer AppUpdateModel(app)
				}
				handler(session, ev)
			}()
		}
	} else {
		// NOTE(Dan): If not launching into a goroutine, then we should not acquire the mutex since it is already held.
	}

	n.handlers[string(event)] = modifiedHandler
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

type MachineCapability string

const (
	MachineCapabilityDocker MachineCapability = "docker"
	MachineCapabilityVm     MachineCapability = "vm"
	MachineCapabilityNative MachineCapability = "native"
)

func Flex(props FlexProps) UiNode {
	return FlexEx("", props)
}

func FlexEx(id string, props FlexProps) UiNode {
	if props.Direction == "" {
		props.Direction = "row"
	}

	return UiNode{
		Id:        id,
		Component: "flex",
		Props:     ValueMarshalOrLog(props),
	}
}

func Box() UiNode {
	return BoxEx("")
}

func BoxEx(id string) UiNode {
	return UiNode{
		Id:        id,
		Component: "box",
	}
}

func Surface() UiNode {
	return SurfaceEx("")
}

func SurfaceEx(id string) UiNode {
	return UiNode{
		Id:        id,
		Component: "surface",
	}
}

func Toolbar() UiNode {
	return ToolbarEx("")
}

func ToolbarEx(id string) UiNode {
	return UiNode{
		Id:        id,
		Component: "toolbar",
	}
}

func Router(bindPath string) UiNode {
	return RouterEx("", bindPath)
}

func RouterEx(id string, bindPath string) UiNode {
	return UiNode{
		Id:        id,
		Component: "router",
		BindPath:  bindPath,
	}
}

func QueryParam(bindPath string, key string) UiNode {
	return QueryParamEx("", bindPath, key, false, true, true, true, nil)
}

func QueryParamWhenPresent(bindPath string, key string) UiNode {
	return QueryParamEx("", bindPath, key, false, true, false, true, nil)
}

func QueryParamReadOnlyWhenPresent(bindPath string, key string) UiNode {
	return QueryParamEx("", bindPath, key, false, true, false, false, nil)
}

func QueryParamEx(id string, bindPath string, key string, replace bool, removeWhenEmpty bool, sendMissing bool, writeToUrl bool, clearKeys []string) UiNode {
	clearKeyValues := make([]Value, 0, len(clearKeys))
	for _, clearKey := range clearKeys {
		clearKeyValues = append(clearKeyValues, VString(clearKey))
	}

	return UiNode{
		Id:        id,
		Component: "query_param",
		BindPath:  bindPath,
		Props: map[string]Value{
			"key":             VString(key),
			"replace":         VBool(replace),
			"removeWhenEmpty": VBool(removeWhenEmpty),
			"sendMissing":     VBool(sendMissing),
			"writeToUrl":      VBool(writeToUrl),
			"clearKeys":       VList(clearKeyValues),
		},
	}
}

func Link(to string) UiNode {
	return LinkEx("", to)
}

func LinkEx(id string, to string) UiNode {
	return UiNode{
		Id:        id,
		Component: "link",
		Props: map[string]Value{
			"to": VString(to),
		},
	}
}

func Text(text string) UiNode {
	return TextEx("", text)
}

func TextEx(id string, text string) UiNode {
	return UiNode{
		Id:        id,
		Component: "text",
		Props: map[string]Value{
			"text": VString(text),
		},
	}
}

func H1(text string) UiNode {
	return Heading(text, 1)
}

func H1Ex(id string, text string) UiNode {
	return HeadingEx(id, text, 1)
}

func H2(text string) UiNode {
	return Heading(text, 2)
}

func H2Ex(id string, text string) UiNode {
	return HeadingEx(id, text, 2)
}

func H3(text string) UiNode {
	return Heading(text, 3)
}

func H3Ex(id string, text string) UiNode {
	return HeadingEx(id, text, 3)
}

func H4(text string) UiNode {
	return Heading(text, 4)
}

func H4Ex(id string, text string) UiNode {
	return HeadingEx(id, text, 4)
}

func H5(text string) UiNode {
	return Heading(text, 5)
}

func H5Ex(id string, text string) UiNode {
	return HeadingEx(id, text, 5)
}

func H6(text string) UiNode {
	return Heading(text, 6)
}

func H6Ex(id string, text string) UiNode {
	return HeadingEx(id, text, 6)
}

func Heading(text string, level int64) UiNode {
	return HeadingEx("", text, level)
}

func HeadingEx(id string, text string, level int64) UiNode {
	return UiNode{
		Id:        id,
		Component: "heading",
		Props: map[string]Value{
			"text":  VString(text),
			"level": VS64(level),
		},
	}
}

func HeadingBound(bindPath string, level int64) UiNode {
	return HeadingBoundEx("", bindPath, level)
}

func HeadingBoundEx(id string, bindPath string, level int64) UiNode {
	return UiNode{
		Id:        id,
		Component: "heading",
		BindPath:  bindPath,
		Props: map[string]Value{
			"level": VS64(level),
		},
	}
}

func TextBound(bindPath string) UiNode {
	return TextBoundEx("", bindPath)
}

func TextBoundEx(id string, bindPath string) UiNode {
	return UiNode{Id: id, Component: "text", BindPath: bindPath}
}

func InputText(id string, label string, placeholder string, bindPath string) UiNode {
	requireExplicitId(id, "input_text")

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
	requireExplicitId(id, "input_number")

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

func InputSlider(label string, bindPath string, min float64, max float64, step float64, defaultValue float64, minMeansDefault bool) UiNode {
	return InputSliderEx(bindPath, label, bindPath, min, max, step, defaultValue, minMeansDefault)
}

func InputSliderEx(id string, label string, bindPath string, min float64, max float64, step float64, defaultValue float64, minMeansDefault bool) UiNode {
	requireExplicitId(id, "input_slider")

	return UiNode{
		Id:         id,
		Component:  "input_slider",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"label":           VString(label),
			"min":             VF64(min),
			"max":             VF64(max),
			"step":            VF64(step),
			"defaultValue":    VF64(defaultValue),
			"minMeansDefault": VBool(minMeansDefault),
		},
	}
}

func Checkbox(id string, label string, bindPath string, optimistic bool) UiNode {
	requireExplicitId(id, "checkbox")

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

func List(bindPath string, emptyText string) UiNode {
	return ListEx("", bindPath, emptyText)
}

func ListEx(id string, bindPath string, emptyText string) UiNode {
	return UiNode{Id: id, Component: "list", BindPath: bindPath, Props: map[string]Value{
		"emptyText": VString(emptyText),
	}}
}

func Icon(name IconName, color Color, size int64) UiNode {
	return IconEx("", name, color, size)
}

func IconEx(id string, name IconName, color Color, size int64) UiNode {
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
	requireExplicitId(id, "button")

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
	requireExplicitId(id, "textarea")

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
	requireExplicitId(id, "select")

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

func (n UiNode) WithShortcutKey(key string) UiNode {
	if n.Props == nil {
		n.Props = map[string]Value{}
	}
	n.Props["shortcutKey"] = VString(key)
	return n
}

func EnumSelectorNode(id string, bindPath string, options []Option) UiNode {
	requireExplicitId(id, "enum_selector")

	return UiNode{
		Id:         id,
		Component:  "enum_selector",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"options": optionsToValue(options),
		},
	}
}

func MachineTypeSelector(id string, label string, bindPath string, capabilities ...MachineCapability) UiNode {
	requireExplicitId(id, "machine_type_selector")

	if len(capabilities) == 0 {
		capabilities = []MachineCapability{
			MachineCapabilityDocker,
			MachineCapabilityVm,
			MachineCapabilityNative,
		}
	}

	allowed := map[MachineCapability]bool{
		MachineCapabilityDocker: true,
		MachineCapabilityVm:     true,
		MachineCapabilityNative: true,
	}

	capabilitySet := map[MachineCapability]bool{}
	normalizedCaps := make([]Value, 0, len(capabilities))
	for _, capability := range capabilities {
		if !allowed[capability] {
			panic(fmt.Sprintf("ucx: invalid machine capability '%s'", capability))
		}
		if capabilitySet[capability] {
			continue
		}
		capabilitySet[capability] = true
		normalizedCaps = append(normalizedCaps, VString(string(capability)))
	}

	return UiNode{
		Id:         id,
		Component:  "machine_type_selector",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]Value{
			"label":        VString(label),
			"capabilities": VList(normalizedCaps),
		},
	}
}

func (n UiNode) MachineSelectorProviderBindPath(providerBindPath string) UiNode {
	return n.propMutate("providerBindPath", VString(providerBindPath))
}

func (n UiNode) MachineSelectorProviderOnly(providerOnly bool) UiNode {
	return n.propMutate("providerOnly", VBool(providerOnly))
}

func ServiceProviderSelectorNode(id string, bindPath string) UiNode {
	requireExplicitId(id, "service_provider_selector")

	return UiNode{
		Id:         id,
		Component:  "service_provider_selector",
		BindPath:   bindPath,
		Optimistic: true,
	}
}

func (n UiNode) ServiceProviderRealProvidersOnly(realProvidersOnly bool) UiNode {
	return n.propMutate("realProvidersOnly", VBool(realProvidersOnly))
}

func (n UiNode) MachineSelectorDescription(description string) UiNode {
	return n.propMutate("description", VString(description))
}

type CostEstimateEntry struct {
	Title           string
	TitleBindPath   string
	MachineBindPath string
	CountBindPath   string
}

func CostEstimateNode(id string, entries []CostEstimateEntry) UiNode {
	requireExplicitId(id, "cost_estimate")

	normalizedEntries := make([]Value, 0, len(entries))
	for _, entry := range entries {
		object := map[string]Value{
			"title":           VString(entry.Title),
			"machineBindPath": VString(entry.MachineBindPath),
			"countBindPath":   VString(entry.CountBindPath),
		}

		if entry.TitleBindPath != "" {
			object["titleBindPath"] = VString(entry.TitleBindPath)
		}

		normalizedEntries = append(normalizedEntries, VObject(object))
	}

	return UiNode{
		Id:        id,
		Component: "cost_estimate",
		Props: map[string]Value{
			"entries": VList(normalizedEntries),
		},
	}
}

func RadioGroup(id string, label string, bindPath string, options []Option) UiNode {
	requireExplicitId(id, "radio_group")

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
	requireExplicitId(id, "toggle")

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

func DividerNode() UiNode {
	return DividerNodeEx("")
}

func DividerNodeEx(id string) UiNode {
	return UiNode{Id: id, Component: "divider"}
}

func Spinner(size int64) UiNode {
	return SpinnerEx("", size)
}

func SpinnerEx(id string, size int64) UiNode {
	return UiNode{
		Id:        id,
		Component: "spinner",
		Props: map[string]Value{
			"size": VS64(size),
		},
	}
}

func TableNode(bindPath string, columns []Option) UiNode {
	return TableNodeEx("", bindPath, columns)
}

func TableNodeEx(id string, bindPath string, columns []Option) UiNode {
	return UiNode{
		Id:        id,
		Component: "table",
		BindPath:  bindPath,
		Props: map[string]Value{
			"columns": optionsToValue(columns),
		},
	}
}

type NavItemChild struct {
	Id      string
	Label   string
	Aliases []string
	Route   string
}

type NavItem struct {
	Id       string
	Label    string
	Aliases  []string
	Route    string
	Children []NavItemChild
}

type BrowserLayoutProps struct {
	Sidebar   UiNode
	Content   []UiNode
	Bottom    UiNode
	HasBottom bool

	EscapePath     string
	EscapeDisabled bool
}

func BrowserLayout(props BrowserLayoutProps) UiNode {
	node := UiNode{
		Id:        "browserLayout",
		Component: "browser_layout",
		Props:     map[string]Value{},
	}

	if !props.EscapeDisabled {
		node.Props["escapePath"] = VString(props.EscapePath)
	}

	var children []UiNode
	if props.Sidebar.Component != "" {
		children = append(children, markBrowserSlot(props.Sidebar, "sidebarSlot", "browserSidebar"))
	}
	children = append(children, props.Content...)
	if props.HasBottom {
		children = append(children, markBrowserSlot(props.Bottom, "bottomSlot", "browserBottom"))
	}

	return node.Children(children...)
}

func markBrowserSlot(node UiNode, marker string, fallbackId string) UiNode {
	if node.Id == "" {
		node.Id = fallbackId
	}

	if node.Props == nil {
		node.Props = map[string]Value{}
	}
	node.Props[marker] = VBool(true)
	return node
}

func BrowserSidebar(children ...UiNode) UiNode {
	return BoxEx("browserSidebar").Children(children...)
}

func BrowserBottom(children ...UiNode) UiNode {
	return BoxEx("browserBottom").Children(children...)
}

func NavTree(id string, items []NavItem) UiNode {
	return NavTreeEx(id, "", items)
}

func NavTreeEx(id string, selectedBindPath string, items []NavItem) UiNode {
	return UiNode{
		Id:        id,
		Component: "nav_tree",
		BindPath:  selectedBindPath,
		Props: map[string]Value{
			"items": navItemsToValue(items),
		},
	}
}

func navItemsToValue(items []NavItem) Value {
	list := make([]Value, 0, len(items))
	for _, item := range items {
		object := map[string]Value{
			"id":    VString(item.Id),
			"label": VString(item.Label),
		}
		if len(item.Aliases) > 0 {
			aliases := make([]Value, 0, len(item.Aliases))
			for _, alias := range item.Aliases {
				aliases = append(aliases, VString(alias))
			}
			object["aliases"] = VList(aliases)
		}
		if item.Route != "" {
			object["route"] = VString(item.Route)
		}
		if len(item.Children) > 0 {
			children := make([]Value, 0, len(item.Children))
			for _, child := range item.Children {
				childObject := map[string]Value{
					"id":    VString(child.Id),
					"label": VString(child.Label),
				}
				if len(child.Aliases) > 0 {
					aliases := make([]Value, 0, len(child.Aliases))
					for _, alias := range child.Aliases {
						aliases = append(aliases, VString(alias))
					}
					childObject["aliases"] = VList(aliases)
				}
				if child.Route != "" {
					childObject["route"] = VString(child.Route)
				}
				children = append(children, VObject(childObject))
			}
			object["children"] = VList(children)
		}
		list = append(list, VObject(object))
	}
	return VList(list)
}

type ResourceTableActionKind string

const ResourceTableActionCopyText ResourceTableActionKind = "copyText"

type ResourceTableAction struct {
	Id    string
	Label string
	Icon  IconName
	Kind  ResourceTableActionKind
	Color Color
}

type ResourceTableProps struct {
	Id       string
	TableId  string
	StateKey string
	ViewId   string

	EmptyMessage     string
	HideGroupHeaders bool
	NoSorting        bool
	Actions          []ResourceTableAction
	GroupAction      *ResourceTableAction
	TrailingAction   *ResourceTableAction
}

func ResourceTable(props ResourceTableProps) UiNode {
	nodeProps := map[string]Value{
		"tableId": VString(props.TableId),
	}

	if props.StateKey != "" {
		nodeProps["stateKey"] = VString(props.StateKey)
	}

	if props.ViewId != "" {
		nodeProps["viewId"] = VString(props.ViewId)
	}

	if props.EmptyMessage != "" {
		nodeProps["emptyMessage"] = VString(props.EmptyMessage)
	}

	if props.HideGroupHeaders {
		nodeProps["showGroupHeaders"] = VBool(false)
	}

	if props.NoSorting {
		nodeProps["sorted"] = VBool(false)
	}

	if len(props.Actions) > 0 {
		actionValues := make([]Value, 0, len(props.Actions))
		for _, action := range props.Actions {
			actionValues = append(actionValues, VObject(map[string]Value{
				"id":    VString(action.Id),
				"label": VString(action.Label),
				"icon":  VIcon(action.Icon),
				"kind":  VString(string(action.Kind)),
			}))
		}
		nodeProps["actions"] = VList(actionValues)
	}

	if props.GroupAction != nil {
		nodeProps["groupAction"] = VObject(map[string]Value{
			"id":    VString(props.GroupAction.Id),
			"label": VString(props.GroupAction.Label),
			"icon":  VIcon(props.GroupAction.Icon),
		})
	}

	if props.TrailingAction != nil {
		trailing := VObject(map[string]Value{
			"id":    VString(props.TrailingAction.Id),
			"label": VString(props.TrailingAction.Label),
			"icon":  VIcon(props.TrailingAction.Icon),
		})
		if props.TrailingAction.Color != "" {
			trailing.Object["color"] = VColor(props.TrailingAction.Color)
		}
		nodeProps["trailingAction"] = trailing
	}

	return UiNode{
		Id:        props.Id,
		Component: "resource_table",
		Props:     nodeProps,
	}
}

func TableFilter(id string, stateKey string) UiNode {
	return UiNode{
		Id:        id,
		Component: "table_filter",
		Props: map[string]Value{
			"stateKey": VString(stateKey),
		},
	}
}

func TableCount(id string, stateKey string) UiNode {
	return UiNode{
		Id:        id,
		Component: "table_count",
		Props: map[string]Value{
			"stateKey": VString(stateKey),
		},
	}
}

func (n UiNode) WithTitle(title string) UiNode {
	return n.propMutate("title", VString(title))
}

func Tabs() UiNode {
	return TabsWithRouteEx("", false)
}

func TabsEx(id string) UiNode {
	return TabsWithRouteEx(id, false)
}

func TabsWithRoute(bindToRoute bool) UiNode {
	return TabsWithRouteEx("", bindToRoute)
}

func TabsWithRouteEx(id string, bindToRoute bool) UiNode {
	return UiNode{
		Id:        id,
		Component: "tabs",
		Props: map[string]Value{
			"bindToRoute": VBool(bindToRoute),
		},
	}
}

func TabsRightControls(child UiNode) UiNode {
	if child.Props == nil {
		child.Props = map[string]Value{}
	}
	child.Props["rightControls"] = VBool(true)
	return child
}

func Tab(name string, icon IconName) UiNode {
	return TabEx("", name, icon)
}

func TabEx(id string, name string, icon IconName) UiNode {
	return UiNode{
		Id:        id,
		Component: "box",
		Props: map[string]Value{
			"name": VString(name),
			"icon": VIcon(icon),
		},
	}
}

func AccordionNode(title string, open bool) UiNode {
	return AccordionNodeEx("", title, open)
}

func AccordionNodeEx(id string, title string, open bool) UiNode {
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
	requireExplicitId(id, "form")

	return UiNode{Id: id, Component: "form"}
}

func Markdown(text string) UiNode {
	return UiNode{
		Component: "markdown",
		Props: map[string]Value{
			"text": VString(text),
		},
	}
}

func MarkdownBound(bindPath string) UiNode {
	return UiNode{
		Component: "markdown",
		BindPath:  bindPath,
	}
}

func Code(text string) UiNode {
	return CodeEx("", text)
}

func CodeEx(id string, text string) UiNode {
	return UiNode{
		Id:        id,
		Component: "code",
		Props: map[string]Value{
			"text": VString(text),
		},
	}
}

func (n UiNode) WithLang(lang string) UiNode {
	if n.Props == nil {
		n.Props = map[string]Value{}
	}
	n.Props["lang"] = VString(lang)
	return n
}

func (n UiNode) WithStretch() UiNode {
	if n.Props == nil {
		n.Props = map[string]Value{}
	}
	n.Props["stretch"] = VBool(true)
	return n
}

func CodeBound(bindPath string) UiNode {
	return CodeBoundEx("", bindPath)
}

func CodeBoundEx(id string, bindPath string) UiNode {
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

type StackMachinesProps struct {
	Plain       bool
	LabelFilter util.Option[StackMachinesLabelFilter]
}

type StackMachinesLabelFilter struct {
	Label string
	Value string
}

func StackMachines(props StackMachinesProps) UiNode {
	nodeProps := map[string]Value{
		"isPlain": VBool(props.Plain),
	}

	if props.LabelFilter.Present {
		nodeProps["labelFilter"] = VObject(map[string]Value{
			"label": VString(props.LabelFilter.Value.Label),
			"value": VString(props.LabelFilter.Value.Value),
		})
	}

	return UiNode{Component: "stack_machines", Props: nodeProps}
}

func KeyboardNavigationNode(id string) KeyboardNavigationNodeBuilder {
	requireExplicitId(id, "keyboard_navigation")
	return KeyboardNavigationNodeBuilder{node: UiNode{Id: id, Component: "keyboard_navigation"}}
}

type KeyboardNavigationNodeBuilder struct {
	node UiNode
}

func (b KeyboardNavigationNodeBuilder) Sx(opts ...SxOption) KeyboardNavigationNodeBuilder {
	if b.node.Props == nil {
		b.node.Props = map[string]Value{}
	}
	b.node.Props["sx"] = Sx(opts...)
	return b
}

func (b KeyboardNavigationNodeBuilder) VerticalSelector(selector string) KeyboardNavigationNodeBuilder {
	return b.propString("navigationSelector", selector)
}

func (b KeyboardNavigationNodeBuilder) HorizontalSelector(selector string) KeyboardNavigationNodeBuilder {
	return b.propString("horizontalSelector", selector)
}

func (b KeyboardNavigationNodeBuilder) Submit(nodeId string) KeyboardNavigationNodeBuilder {
	return b.propString("submitNodeId", nodeId)
}

func (b KeyboardNavigationNodeBuilder) SubmitForm(formNodeId string) KeyboardNavigationNodeBuilder {
	return b.propString("submitNodeId", formNodeId).prop("submitEvent", VString("submit"))
}

func (b KeyboardNavigationNodeBuilder) SubmitDisabled(disabled bool) KeyboardNavigationNodeBuilder {
	return b.prop("submitDisabled", VBool(disabled))
}

func (b KeyboardNavigationNodeBuilder) prop(key string, value Value) KeyboardNavigationNodeBuilder {
	if b.node.Props == nil {
		b.node.Props = map[string]Value{}
	}
	b.node.Props[key] = value
	return b
}

func (b KeyboardNavigationNodeBuilder) propString(key string, value string) KeyboardNavigationNodeBuilder {
	if value == "" {
		return b
	}
	return b.prop(key, VString(value))
}

func (b KeyboardNavigationNodeBuilder) Children(children ...UiNode) UiNode {
	return b.node.Children(children...)
}

func FieldGroupNode() UiNode {
	return FieldGroupNodeEx("")
}

func FieldGroupNodeEx(id string) UiNode {
	return UiNode{Id: id, Component: "field_group"}
}

func FieldRowNode(title string, bindPath string) UiNode {
	return FieldRowNodeEx("", title, bindPath)
}

func FieldRowNodeEx(id string, title string, bindPath string) UiNode {
	props := map[string]Value{
		"title": VString(title),
	}

	return UiNode{
		Id:        id,
		Component: "field_row",
		BindPath:  bindPath,
		Props:     props,
	}
}

func (n UiNode) FieldRowTitle(title string) UiNode {
	return n.propMutate("title", VString(title))
}

func (n UiNode) FieldRowDescription(description string) UiNode {
	return n.propMutate("description", VString(description))
}

func (n UiNode) FieldRowBold(bold bool) UiNode {
	return n.propMutate("bold", VBool(bold))
}

func (n UiNode) FieldRowRequired(required bool) UiNode {
	return n.propMutate("required", VBool(required))
}

func (n UiNode) FieldRowError(error string) UiNode {
	return n.propMutate("error", VString(error))
}

func (n UiNode) FieldRowParameterType(parameterType string) UiNode {
	return n.propMutate("parameterType", VString(parameterType))
}

func (n UiNode) propMutate(key string, value Value) UiNode {
	if n.Props == nil {
		n.Props = map[string]Value{}
	}
	n.Props[key] = value
	return n
}

func (n UiNode) ButtonSubmitShortcut(enabled bool) UiNode {
	return n.propMutate("showShortcut", VBool(enabled))
}

func (n UiNode) ButtonBusy(bindPath string) UiNode {
	return n.propMutate("busyPath", VString(bindPath))
}

func (n UiNode) ButtonDisabledWhen(bindPath string) UiNode {
	return n.propMutate("disabledPath", VString(bindPath))
}

func (n UiNode) ButtonEscapeHint(enabled bool) UiNode {
	return n.propMutate("showEscapeHint", VBool(enabled))
}

func SidebarLayout() SidebarLayoutNodeBuilder {
	return SidebarLayoutNodeBuilder{node: UiNode{Component: "sidebar_layout"}}
}

type SidebarLayoutNodeBuilder struct {
	node       UiNode
	sidebar    UiNode
	hasSidebar bool
}

func (b SidebarLayoutNodeBuilder) Sx(opts ...SxOption) SidebarLayoutNodeBuilder {
	if b.node.Props == nil {
		b.node.Props = map[string]Value{}
	}
	b.node.Props["sx"] = Sx(opts...)
	return b
}

func (b SidebarLayoutNodeBuilder) Sidebar(sidebar UiNode) SidebarLayoutNodeBuilder {
	b.sidebar = sidebar
	b.hasSidebar = true
	return b
}

func (b SidebarLayoutNodeBuilder) Children(children ...UiNode) UiNode {
	all := make([]UiNode, 0, len(children)+1)
	all = append(all, children...)

	if b.hasSidebar {
		sidebar := b.sidebar
		props := make(map[string]Value, len(sidebar.Props)+1)
		for key, value := range sidebar.Props {
			props[key] = value
		}
		props["sidebarSlot"] = VBool(true)
		sidebar.Props = props
		all = append(all, sidebar)
	}

	return b.node.Children(all...)
}
