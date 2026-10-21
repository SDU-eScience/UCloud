package dashboard

import (
	"context"
	"fmt"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx"
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

	app.runAsyncAction("", nil, func() error {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()
		return dashboardResourceInterface(client, target.Def, target.Namespace).Delete(ctx, target.Name, v1.DeleteOptions{})
	}, func(err error) {
		message := fmt.Sprintf("Deletion of %s started", targetPath)
		if err != nil {
			log.Warn("k8s-app: deletion of %s failed: %s", targetPath, err)
			message = fmt.Sprintf("Could not delete %s: %s", targetPath, err)
		}
		dashboardSendUiMessage(session, message, err == nil)
	})
}

func dashboardResourceDeleteDialog(app *stackUiApp) ucx.UiNode {
	return dashboardResourceActionDialog(
		"resourceDelete", "Delete",
		fmt.Sprintf("Delete %s?", dashboardResourceDeletePath(app.resourceDeleteTarget)),
		ucx.IconHeroTrash, ucx.ColorErrorMain,
		func() { dashboardResourceDeleteClose(app) },
		func() { dashboardResourceDeletePerform(app) },
	)
}

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

func rolloutRestartBusy(typeId string, obj *unstructured.Unstructured) bool {
	if !rolloutRestartSupported(typeId) {
		return false
	}
	if firstInt64(obj, "status", "observedGeneration") < obj.GetGeneration() {
		return true
	}
	if typeId == "deployments" {
		desired := firstInt64(obj, "spec", "replicas")
		active := firstInt64(obj, "status", "replicas")
		return firstInt64(obj, "status", "updatedReplicas") != desired ||
			firstInt64(obj, "status", "readyReplicas") < max(desired, active)
	}
	if typeId == "daemonsets" {
		desired := firstInt64(obj, "status", "desiredNumberScheduled")
		countsChanging := firstInt64(obj, "status", "currentNumberScheduled") != desired ||
			firstInt64(obj, "status", "numberReady") != desired ||
			firstInt64(obj, "status", "numberMisscheduled") > 0
		if countsChanging {
			return true
		}
		if firstString(obj, "spec", "updateStrategy", "type") == "OnDelete" {
			return false
		}
		return firstInt64(obj, "status", "updatedNumberScheduled") != desired
	}

	desired := firstInt64(obj, "spec", "replicas")
	countsChanging := firstInt64(obj, "status", "replicas") != desired ||
		firstInt64(obj, "status", "readyReplicas") != desired
	if countsChanging {
		return true
	}
	if firstString(obj, "spec", "updateStrategy", "type") == "OnDelete" {
		return false
	}
	partition := firstInt64(obj, "spec", "updateStrategy", "rollingUpdate", "partition")
	updateTarget := max(0, desired-partition)
	if firstInt64(obj, "status", "updatedReplicas") < updateTarget {
		return true
	}
	if partition > 0 || desired == 0 {
		return false
	}
	return firstString(obj, "status", "currentRevision") != firstString(obj, "status", "updateRevision")
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

	app.runAsyncAction("", nil, func() error {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()
		err := client.RolloutRestart(ctx, target.TypeId, target.Namespace, target.Name)
		if err != nil {
			log.Warn("k8s-app: rollout restart of %s failed: %s", targetPath, err)
		}
		return err
	}, func(err error) {
		dashboardSendUiMessage(session, rolloutRestartResultMessage(typeLabel, targetPath, err), err == nil)
	})
}

func rolloutRestartResultMessage(typeLabel string, targetPath string, err error) string {
	if err != nil {
		return fmt.Sprintf("Could not restart %s %s: %s", typeLabel, targetPath, err)
	}
	return fmt.Sprintf("Rollout restart of %s %s started", typeLabel, targetPath)
}

func (app *stackUiApp) rolloutRestartDialogNode() ucx.UiNode {
	return dashboardResourceActionDialog(
		"rolloutRestart", "Restart", fmt.Sprintf(
			"Restart %s %s?",
			rolloutRestartSingularLabel(app.rolloutRestartTarget.TypeId),
			app.rolloutRestartTargetPath(),
		), ucx.IconHeroArrowPath, ucx.ColorErrorMain, app.closeRolloutRestartDialog, app.performRolloutRestart,
	)
}
