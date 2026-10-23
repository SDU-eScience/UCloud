package dashboard

import (
	"context"
	"fmt"
	"time"

	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
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
	app.mu.Lock()
	stack := app.Stack
	client := app.k8sClient
	poller := app.poller
	known := make(map[string]string, len(app.provisioningCache))
	for _, entry := range app.provisioningCache {
		known[entry.Hostname] = entry.Status
	}
	app.mu.Unlock()

	record, recordOk := app.readClusterRecordWith(session, stack)
	if !recordOk {
		return
	}

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

	published := append([]provisioningEntry(nil), entries...)

	app.mu.Lock()
	changed := !provisioningEntriesEqual(app.provisioningCache, published)
	app.provisioningCache = published
	if changed && app.ActiveType == navHomeId {
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()

	if changed && poller != nil {
		poller.signalPollNow()
	}

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
		jobCtx, jobCancel := dashboardSessionContext(session)
		job, jobErr := provisioningJobRetrieve(jobCtx, session, entries[i].JobId)
		jobCancel()
		if jobErr == nil {
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

	if changed && poller != nil {
		poller.signalPollNow()
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

func provisioningJobRetrieve(ctx context.Context, session *ucx.Session, jobId string) (orcapi.Job, error) {
	if session == nil {
		return orcapi.Job{}, fmt.Errorf("the session is not available")
	}
	return ucxapi.JobsRetrieve.InvokeEx(ctx, session, orcapi.JobsRetrieveRequest{Id: jobId})
}

func (app *stackUiApp) clusterNodeMessage(ctx context.Context, record *shared.ClusterRecord) string {
	session := app.session

	starting := 0
	running := 0
	failedLookup := 0
	for _, node := range record.Nodes {
		if node.JobId == "" {
			running++
			continue
		}

		job, err := provisioningJobRetrieve(ctx, session, node.JobId)
		if err != nil {
			failedLookup++
			continue
		}

		if job.Status.State.IsFinal() {
			running++
		} else {
			starting++
		}
	}

	if failedLookup > 0 {
		message := fmt.Sprintf(
			"Could not check %d of %d nodes. See the node logs for details.",
			failedLookup,
			len(record.Nodes),
		)
		if starting > 0 {
			message += fmt.Sprintf(
				" The cluster is starting. %d of %d nodes are not running yet.",
				starting,
				len(record.Nodes),
			)
		}
		return message
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
	entries := append([]provisioningEntry(nil), app.provisioningCache...)
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
