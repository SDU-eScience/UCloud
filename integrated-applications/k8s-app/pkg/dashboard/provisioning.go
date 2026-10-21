package dashboard

import (
	"context"
	"fmt"
	"time"

	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

type provisioningEntry struct {
	Hostname string
	Group    string
	Status   string
	JobId    string
}

func (app *stackUiApp) provisioningSnapshot() []provisioningEntry {
	return app.provisioningCache
}

func (app *stackUiApp) activeTypeIsHome() bool {
	app.mu.Lock()
	defer app.mu.Unlock()
	return app.ActiveType == navHomeId
}

func (app *stackUiApp) provisioningWatcher(session *ucx.Session) {
	ticker := time.NewTicker(5 * time.Second)
	uiTicker := time.NewTicker(time.Second)
	defer ticker.Stop()
	defer uiTicker.Stop()

	for {
		app.refreshProvisioningRows(session)
		if app.activeTypeIsHome() {
			go app.refreshStackInfo()
		}
		if app.activeTypeIsHome() || app.onMaintenanceRoute() {
			go app.refreshClusterHealth()
		}
		if app.onMaintenanceRoute() {
			app.mu.Lock()
			ucx.AppUpdateUi(app)
			app.mu.Unlock()
		}

		refreshed := false
		for !refreshed {
			select {
			case <-session.Context().Done():
				return
			case <-app.provisioningKick:
				refreshed = true
			case <-ticker.C:
				refreshed = true
			case <-uiTicker.C:
				app.maintenanceUiTick()
			}
		}
	}
}

func (app *stackUiApp) refreshProvisioningRows(session *ucx.Session) {
	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return
	}

	app.mu.Lock()
	known := make(map[string]string, len(app.provisioningCache))
	for _, entry := range app.provisioningCache {
		known[entry.Hostname] = entry.Status
	}
	app.mu.Unlock()

	entries := make([]provisioningEntry, 0, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.JobId == "" || node.Hostname == "" {
			continue
		}

		status := known[node.Hostname]
		if status == "" {
			status = "Provisioning"
		}
		entries = append(entries, provisioningEntry{
			Hostname: node.Hostname,
			Group:    node.Group,
			Status:   status,
			JobId:    node.JobId,
		})
	}

	app.mu.Lock()
	changed := !provisioningEntriesEqual(app.provisioningCache, entries)
	app.provisioningCache = entries
	if changed && app.ActiveType == navHomeId {
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()

	if changed && app.poller != nil {
		app.poller.signalPollNow()
	}

	client := app.k8sClient
	if client == nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), 10*time.Second)
	defer cancel()

	existing, err := client.NodeNames(ctx)
	if err != nil {
		return
	}

	changed = false
	for i := range entries {
		if existing[entries[i].Hostname] {
			entries[i].Status = "ready"
			continue
		}
		if job, err := ucxsvc.JobRetrieve(app.Stack, entries[i].JobId); err == nil {
			switch {
			case job.Status.State == orcapi.JobStateInQueue:
				entries[i].Status = "Waiting for resources"
			case job.Status.State == orcapi.JobStateRunning:
				entries[i].Status = "Initializing"
			case job.Status.State == orcapi.JobStateSuspended:
				entries[i].Status = "Powered off"
			case job.Status.State.IsFinal():
				entries[i].Status = "Failed"
			}
		}
	}

	survivors := make([]provisioningEntry, 0, len(entries))
	for _, entry := range entries {
		if entry.Status == "ready" {
			continue
		}
		survivors = append(survivors, entry)
	}

	app.mu.Lock()
	changed = !provisioningEntriesEqual(app.provisioningCache, survivors)
	app.provisioningCache = survivors
	if changed && app.ActiveType == navHomeId {
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()

	if changed && app.poller != nil {
		app.poller.signalPollNow()
	}
}

func (app *stackUiApp) provisioningKickNow() {
	if app.provisioningKick == nil {
		return
	}
	select {
	case app.provisioningKick <- util.Empty{}:
	default:
	}
}

func (app *stackUiApp) clusterNodeMessage(record *shared.ClusterRecord) string {
	starting := 0
	running := 0
	for _, node := range record.Nodes {
		if node.JobId == "" {
			running++
			continue
		}

		job, err := ucxsvc.JobRetrieve(app.Stack, node.JobId)
		if err != nil {
			starting++
			continue
		}

		if job.Status.State.IsFinal() {
			running++
		} else {
			starting++
		}
	}

	if starting > 0 {
		return fmt.Sprintf(
			"The cluster is starting. %d of %d nodes are not running yet. See the node logs for details.",
			starting,
			len(record.Nodes),
		)
	}
	return ""
}

func provisioningEntriesEqual(a, b []provisioningEntry) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func (app *stackUiApp) provisioningResourceRows(def ResourceTypeDef) []ResourceRow {
	if def.Id != "nodes" {
		return nil
	}

	app.mu.Lock()
	entries := app.provisioningCache
	app.mu.Unlock()

	rows := make([]ResourceRow, 0, len(entries))
	for _, entry := range entries {
		role := ""
		if entry.Group == shared.GroupControlPlane {
			role = "control-plane"
		}
		rows = append(rows, ResourceRow{
			Key:   "provisioning:" + entry.Hostname,
			Group: entry.Group,
			Cells: []string{entry.Hostname, entry.Status, "Schedulable", "", role, "", "", "", ""},
		})
	}
	return rows
}

func (app *stackUiApp) provisioningJobId(hostname string) string {
	for _, entry := range app.provisioningCache {
		if entry.Hostname == hostname {
			return entry.JobId
		}
	}
	return ""
}

func (app *stackUiApp) clusterReadyForNodes() bool {
	record, ok := app.readClusterRecord()
	return ok && record.Phase == "created"
}
