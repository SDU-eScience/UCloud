package dashboard

import "ucloud.dk/shared/pkg/ucx"

func dashboardResourceActionDialog(id string, title string, message string, icon ucx.IconName, cancel func(), confirm func()) ucx.UiNode {
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
				ucx.ButtonEx(id+"Confirm", title, ucx.ColorErrorMain, icon, "", "").
					WithShortcutKey("y").
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						confirm()
					}),
			),
	).On(ucx.UiEventClose, func(ev ucx.UiEvent) {
		cancel()
	})
}
