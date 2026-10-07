package dashboard

import (
	"context"
	"fmt"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"

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
	return dashboardResourceActionDialog(
		"rolloutRestart", "Restart", fmt.Sprintf(
			"Restart %s %s?",
			rolloutRestartSingularLabel(app.rolloutRestartTarget.TypeId),
			app.rolloutRestartTargetPath(),
		), ucx.IconHeroArrowPath, app.closeRolloutRestartDialog, app.performRolloutRestart,
	)
}
