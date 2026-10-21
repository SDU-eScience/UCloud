package dashboard

import (
	"strings"

	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

type appShellProps struct {
	Content        []ucx.UiNode
	Bottom         []ucx.UiNode
	EscapePath     string
	EscapeDisabled bool
}

func (app *stackUiApp) appShell(props appShellProps) ucx.UiNode {
	navItems := app.resourceNavItems()

	if app.k8sClient == nil {
		for i := range navItems {
			if navItems[i].Id == navHomeId {
				continue
			}
			navItems[i].Disabled = true
			for j := range navItems[i].Children {
				navItems[i].Children[j].Disabled = true
			}
		}
	}

	navTree := ucx.NavTreeEx("resourceNav", "activeType", navItems).On(ucx.UiEventActivate, func(ev ucx.UiEvent) {
		app.selectResourceType(ucx.ValueAsString(ev.Value))
	})
	navTree.Props["initialExpandedIds"] = ucx.VList([]ucx.Value{
		ucx.VString("group:Cluster"),
		ucx.VString("group:Workloads"),
	})

	layoutProps := ucx.BrowserLayoutProps{
		Sidebar:        ucx.BrowserSidebar(navTree),
		Content:        props.Content,
		EscapePath:     props.EscapePath,
		EscapeDisabled: props.EscapeDisabled,
	}

	if len(props.Bottom) > 0 {
		layoutProps.Bottom = ucx.BrowserBottom(props.Bottom...)
		layoutProps.HasBottom = true
	}

	return ucx.BrowserLayout(layoutProps)
}

func shellContentBox(maxWidth int64, children ...ucx.UiNode) ucx.UiNode {
	return ucx.Box().Sx(
		ucx.SxHeightRaw("100%"),
		ucx.SxOverflowY("auto"),
	).Children(
		ucx.Box().Sx(
			ucx.SxPx(20),
			ucx.SxPy(20),
			ucx.SxMinHeightRaw("100%"),
			ucx.SxBoxSizing("border-box"),
		).Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 24}).
				Sx(ucx.SxMaxWidth(maxWidth)).
				Children(children...),
		),
	)
}

func shellBottomNode(app *stackUiApp, backTarget string, labels ...string) ucx.UiNode {
	bottom := ucx.Toolbar().Children(
		ucx.ButtonEx("backToTable", "Back to table", ucx.ColorSecondaryMain, ucx.IconHeroArrowLeft, "", "").ButtonEscapeHint(true).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			ucxsvc.RouterPushPage(app, "browse/"+backTarget)
		}),
		ucx.Box(),
	)

	if len(labels) > 0 {
		bottom = bottom.Children(
			ucx.Text(strings.Join(labels, " / ")).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		)
	}

	return bottom
}
func dashboardResourceActionDialog(
	id string,
	title string,
	message string,
	icon ucx.IconName,
	color ucx.Color,
	cancel func(),
	confirm func(),
) ucx.UiNode {
	return ucx.DialogEx(id+"Dialog", title, true).Children(
		ucx.TextEx("", message).Sx(ucx.SxMinHeight(200)),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
			Sx(
				ucx.SxWidthAuto(),
				ucx.SxJustifyEnd,
				ucx.SxMx(-20),
				ucx.SxMb(-20),
				ucx.SxPx(20),
				ucx.SxPy(12),
				ucx.SxBackground("var(--dialogToolbar)"),
			).
			Children(
				ucx.ButtonEx(id+"Cancel", "Cancel", ucx.ColorSecondaryMain, "", "", "").
					WithShortcutKey("n").
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						cancel()
					}),
				ucx.ButtonEx(id+"Confirm", title, color, icon, "", "").
					WithShortcutKey("y").
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						confirm()
					}),
			),
	).On(ucx.UiEventClose, func(ev ucx.UiEvent) {
		cancel()
	})
}
func (app *stackUiApp) homeMetric(title string, valueNodes ...ucx.UiNode) []ucx.UiNode {
	return app.homeMetricEx(title, nil, valueNodes...)
}

func (app *stackUiApp) homeMetricEx(title string, titleNodes []ucx.UiNode, valueNodes ...ucx.UiNode) []ucx.UiNode {
	headerChildren := []ucx.UiNode{
		ucx.TextEx("", title).Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
			ucx.SxFontSize(12),
		),
	}
	headerChildren = append(headerChildren, titleNodes...)

	return []ucx.UiNode{
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 6}).
			Sx(ucx.SxAlignItemsCenter).
			Children(headerChildren...),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 4}).
			Sx(ucx.SxAlignItemsCenter).
			Children(valueNodes...),
	}
}

func homeMetricText(value string, color ucx.Color) ucx.UiNode {
	return ucx.TextEx("", value).Sx(
		ucx.SxFontWeight("600"),
		ucx.SxColor(color),
		ucx.SxFontSize(16),
	)
}

func (app *stackUiApp) homeMetricsRow(metrics ...[]ucx.UiNode) ucx.UiNode {
	cells := make([]ucx.UiNode, 0, len(metrics))
	for _, metric := range metrics {
		cells = append(cells, ucx.Box().Sx(
			ucx.SxFlexBasis("200px"),
			ucx.SxFlexGrow(1),
		).Children(metric...))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 24}).
		Sx(ucx.SxFlexWrapWrap).
		Children(cells...)
}

func pageShell(app *stackUiApp, maxWidth int64, gap int, content []ucx.UiNode) []ucx.UiNode {
	return []ucx.UiNode{app.appShell(appShellProps{
		Content: []ucx.UiNode{shellContentBox(maxWidth,
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: gap}).Children(content...),
		)},
		EscapePath: "browse/home",
	})}
}
