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
