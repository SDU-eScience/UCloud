package dashboard

import (
	"context"
	"fmt"
	"time"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
)

type rolloutRestartTarget struct {
	TypeId    string
	Namespace string
	Name      string
}

func rolloutRestartSupported(typeId string) bool {
	switch typeId {
	case "deployments", "statefulsets", "daemonsets":
		return true
	default:
		return false
	}
}

func rolloutRestartSingularLabel(typeId string) string {
	switch typeId {
	case "deployments":
		return "deployment"
	case "statefulsets":
		return "statefulset"
	case "daemonsets":
		return "daemonset"
	default:
		return typeId
	}
}

func (app *stackUiApp) rolloutRestartTargetPath() string {
	target := app.rolloutRestartTarget
	if target.Namespace != "" {
		return target.Namespace + "/" + target.Name
	}
	return target.Name
}

func (app *stackUiApp) openRolloutRestartDialog(rowKey string) {
	if !rolloutRestartSupported(app.ActiveType) || app.poller == nil {
		return
	}

	row, ok := app.poller.rowForKey(rowKey)
	if !ok || len(row.Cells) == 0 {
		return
	}

	app.rolloutRestartTarget = rolloutRestartTarget{
		TypeId:    app.ActiveType,
		Namespace: row.Group,
		Name:      row.Cells[0],
	}
	app.ShowRolloutRestartDialog = true
	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) closeRolloutRestartDialog() {
	app.ShowRolloutRestartDialog = false
	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) performRolloutRestart() {
	target := app.rolloutRestartTarget
	typeLabel := rolloutRestartSingularLabel(target.TypeId)
	targetPath := app.rolloutRestartTargetPath()

	app.ShowRolloutRestartDialog = false
	ucx.AppUpdateUi(app)

	client := app.k8sClient
	session := app.session
	if client == nil || session == nil {
		return
	}

	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()

		err := client.RolloutRestart(ctx, target.TypeId, target.Namespace, target.Name)
		if err != nil {
			log.Warn("k8s-app: rollout restart of %s failed: %s", targetPath, err)
		}

		if session.Context().Err() != nil {
			return
		}

		messageCtx, messageCancel := context.WithTimeout(session.Context(), 10*time.Second)
		defer messageCancel()

		_, _ = ucxapi.UiSendMessage.InvokeEx(messageCtx, session, ucxapi.UiSendMessageRequest{
			Message: rolloutRestartResultMessage(typeLabel, targetPath, err),
			Success: err == nil,
		})
	}()
}

func rolloutRestartResultMessage(typeLabel string, targetPath string, err error) string {
	if err != nil {
		return fmt.Sprintf("Could not restart %s %s: %s", typeLabel, targetPath, err)
	}
	return fmt.Sprintf("Rollout restart of %s %s started", typeLabel, targetPath)
}

func (app *stackUiApp) rolloutRestartDialogNode() ucx.UiNode {
	return ucx.DialogEx("rolloutRestartDialog", "Restart rollout", true).Children(
		ucx.TextEx("", fmt.Sprintf(
			"Restart %s %s? Its pods are replaced one at a time.",
			rolloutRestartSingularLabel(app.rolloutRestartTarget.TypeId),
			app.rolloutRestartTargetPath(),
		)).Sx(ucx.SxMinHeight(200)),
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
				ucx.ButtonEx("rolloutRestartCancel", "Cancel", ucx.ColorSecondaryMain, "", "", "").
					WithShortcutKey("n").
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						app.closeRolloutRestartDialog()
					}),
				ucx.ButtonEx("rolloutRestartConfirm", "Restart", ucx.ColorErrorMain, ucx.IconHeroArrowPath, "", "").
					WithShortcutKey("y").
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						app.performRolloutRestart()
					}),
			),
	).On(ucx.UiEventClose, func(ev ucx.UiEvent) {
		app.closeRolloutRestartDialog()
	})
}
