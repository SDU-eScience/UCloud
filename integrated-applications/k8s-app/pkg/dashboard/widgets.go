package dashboard

import (
	"strings"

	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

func pageTitle(title string) ucx.UiNode {
	return ucx.Text(title).Sx(ucx.SxFontSize(20), ucx.SxFontWeight("600"))
}

func sectionHeader(title string, fontSize int64) ucx.UiNode {
	return ucx.Text(title).Sx(ucx.SxFontSize(fontSize), ucx.SxFontWeight("600"))
}

func backToolbar(app *stackUiApp, route string, labels ...string) ucx.UiNode {
	toolbar := ucx.Toolbar().Children(
		ucx.ButtonEx("backToTable", "Back to table", ucx.ColorSecondaryMain, ucx.IconHeroArrowLeft, "", "").
			ButtonEscapeHint(true).
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.RouterPushPage(app, route)
			}),
		ucx.Box(),
	)

	if len(labels) > 0 {
		toolbar = toolbar.Children(
			ucx.Text(strings.Join(labels, " / ")).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		)
	}

	return toolbar
}

func (app *stackUiApp) messagePage(bottom []ucx.UiNode, escapePath string, content ...ucx.UiNode) []ucx.UiNode {
	return []ucx.UiNode{app.appShell(appShellProps{
		Content:    []ucx.UiNode{shellContentBox(800, content...)},
		Bottom:     bottom,
		EscapePath: escapePath,
	})}
}

func (app *stackUiApp) progressName(id string, name string, route string) ucx.UiNode {
	box := ucx.Box().Sx(
		ucx.SxFlexGrow(1),
		ucx.SxMinWidth(0),
		ucx.SxTextOverflow("ellipsis"),
		ucx.SxOverflow("hidden"),
		ucx.SxWhiteSpace("nowrap"),
	)

	if route == "" {
		return box.Children(ucx.Text(name).Sx(ucx.SxFontWeight("600")))
	}

	return box.Children(
		ucx.LinkButton(id, name, ucx.ColorLinkColor).
			Sx(ucx.SxFontSize(14), ucx.SxFontWeight("400")).
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.RouterPushPage(app, route)
			}),
	)
}

func progressTagLabel(text string, color ucx.Color) ucx.UiNode {
	return ucx.Text(text).Sx(ucx.SxFontSize(12), ucx.SxColor(color), ucx.SxWhiteSpace("nowrap"))
}
