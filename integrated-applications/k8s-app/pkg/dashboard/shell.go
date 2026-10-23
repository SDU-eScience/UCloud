package dashboard

import (
	"ucloud.dk/shared/pkg/ucx"
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

	navTree := ucx.NavTreeEx("resourceNav", "activeType", navItems).
		NavTreeActivateOnSingleClick(true).
		On(ucx.UiEventActivate, func(ev ucx.UiEvent) {
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
	return backToolbar(app, "browse/"+backTarget, labels...)
}

func pageShell(app *stackUiApp, maxWidth int64, gap int, content []ucx.UiNode) []ucx.UiNode {
	return []ucx.UiNode{app.appShell(appShellProps{
		Content: []ucx.UiNode{shellContentBox(maxWidth,
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: gap}).Children(content...),
		)},
		EscapePath: "browse/home",
	})}
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
