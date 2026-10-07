package dashboard

import (
	"context"
	"fmt"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/dynamic"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
)

type dashboardResourceDeleteTarget struct {
	Def       ResourceTypeDef
	Namespace string
	Name      string
}

func dashboardResourceDeleteSupported(def ResourceTypeDef) bool {
	return def.Id != "nodes" && def.Id != containersTypeId && def.Gvr.Resource != ""
}

func dashboardResourceDeleteOpen(app *stackUiApp, rowKey string) {
	def, ok := app.resolveType(app.ActiveType)
	if !ok || !dashboardResourceDeleteSupported(def) || app.poller == nil {
		return
	}
	row, ok := app.poller.rowForKey(rowKey)
	if !ok || len(row.Cells) == 0 {
		return
	}

	app.resourceDeleteTarget = dashboardResourceDeleteTarget{
		Def:       def,
		Namespace: row.Group,
		Name:      row.Cells[0],
	}
	app.ShowResourceDeleteDialog = true
	ucx.AppUpdateUi(app)
}

func dashboardResourceDeleteClose(app *stackUiApp) {
	app.ShowResourceDeleteDialog = false
	ucx.AppUpdateUi(app)
}

func dashboardResourceDeletePath(target dashboardResourceDeleteTarget) string {
	path := target.Name
	if target.Namespace != "" {
		path = target.Namespace + "/" + path
	}
	return target.Def.Gvr.Resource + "/" + path
}

func dashboardResourceDeletePerform(app *stackUiApp) {
	target := app.resourceDeleteTarget
	targetPath := dashboardResourceDeletePath(target)
	dashboardResourceDeleteClose(app)

	client := app.k8sClient
	session := app.session
	if client == nil || session == nil || !dashboardResourceDeleteSupported(target.Def) {
		return
	}

	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()

		var ri dynamic.ResourceInterface = client.Dynamic.Resource(target.Def.Gvr)
		if target.Def.Namespaced {
			ri = client.Dynamic.Resource(target.Def.Gvr).Namespace(target.Namespace)
		}
		err := ri.Delete(ctx, target.Name, v1.DeleteOptions{})
		message := fmt.Sprintf("Deletion of %s started", targetPath)
		if err != nil {
			log.Warn("k8s-app: deletion of %s failed: %s", targetPath, err)
			message = fmt.Sprintf("Could not delete %s: %s", targetPath, err)
		}
		if session.Context().Err() != nil {
			return
		}

		messageCtx, messageCancel := context.WithTimeout(session.Context(), 10*time.Second)
		defer messageCancel()
		_, _ = ucxapi.UiSendMessage.InvokeEx(messageCtx, session, ucxapi.UiSendMessageRequest{
			Message: message,
			Success: err == nil,
		})
	}()
}

func dashboardResourceDeleteDialog(app *stackUiApp) ucx.UiNode {
	return dashboardResourceActionDialog(
		"resourceDelete", "Delete",
		fmt.Sprintf("Delete %s?", dashboardResourceDeletePath(app.resourceDeleteTarget)),
		ucx.IconHeroTrash,
		func() { dashboardResourceDeleteClose(app) },
		func() { dashboardResourceDeletePerform(app) },
	)
}
